package com.nuvio.tv.core.cinemaos.account

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.model.WatchProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs Continue Watching and Library with the CinemaOS website (Bearer Firebase ID token).
 * Ported from the website client (Cinemaos-V3):
 *  - lib/services/continue-watching-service.ts  (watch history)
 *  - lib/services/watchlist-service.ts          (watchlist)
 *  - lib/services/storage.ts                    (persistent write queue)
 *  - lib/services/authenticated-fetch.ts        (Bearer token)
 *
 * Id mapping: the site uses TMDB ids (anime: the site's own anime id); the app keys content by
 * IMDb id ("tt..."), "tmdb:<id>" as fallback, and "anime:<id>" for site anime rows.
 * Pulls write to the preference stores directly so they never echo back to the site.
 * Everything is a no-op while signed out.
 */
@Singleton
class CinemaOsSyncService @Inject constructor(
    @ApplicationContext context: Context,
    private val account: CinemaOsAccountManager,
    private val httpClient: OkHttpClient,
    private val tmdbService: TmdbService,
    private val watchProgressPreferences: WatchProgressPreferences,
    private val libraryPreferences: LibraryPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pullMutex = Mutex()
    private val flushMutex = Mutex()
    private val json = "application/json".toMediaType()
    private val prefs = context.getSharedPreferences("cinemaos_sync", Context.MODE_PRIVATE)
    private val lastPushAt = ConcurrentHashMap<String, Long>()
    private val pendingPush = ConcurrentHashMap<String, WatchProgress>()

    private val _lastSyncedAt = MutableStateFlow(prefs.getLong(K_LAST_SYNC, 0L))
    val lastSyncedAt: StateFlow<Long> = _lastSyncedAt.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Pulls on app start (if signed in) and whenever a user signs in; then every 10 min while the app lives. */
    fun start() {
        scope.launch {
            account.session.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) pullAll()
            }
        }
        scope.launch {
            while (true) {
                delay(PERIODIC_SYNC_MS)
                if (account.isSignedIn) pullAll()
            }
        }
    }

    fun pullNow() {
        scope.launch { pullAll() }
    }

    /** Mirrors the website's visibility re-sync: pull only if the last sync is older than [maxAgeMs]. */
    fun onAppResumed(maxAgeMs: Long = RESUME_SYNC_MAX_AGE_MS) {
        if (!account.isSignedIn) return
        if (System.currentTimeMillis() - _lastSyncedAt.value < maxAgeMs) return
        pullNow()
    }

    /**
     * Mirrors getMediaWatchPosition(maxAgeMs, timeoutMs): refresh from cloud before resume when the
     * cache is older than [maxAgeMs], but never block longer than [timeoutMs].
     */
    suspend fun refreshHistoryIfStale(maxAgeMs: Long = 30_000L, timeoutMs: Long = 2_500L) {
        if (!account.isSignedIn) return
        if (System.currentTimeMillis() - _lastSyncedAt.value <= maxAgeMs) return
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { pullAll() }
    }

    /** "Sync now" button: flush pending writes, then pull. Suspends until done. */
    suspend fun syncNow() {
        if (!account.isSignedIn) return
        pullAll()
    }

    /** Mirrors forceCloudSync: pullAll() = writeQueue.flush() + syncWatchHistoryFromCloud + syncWatchlistFromCloud. */
    private suspend fun pullAll() = pullMutex.withLock {
        _isSyncing.value = true
        try {
            flushQueue()
            val a = runCatching { pullHistory() }.onFailure { Log.w(TAG, "history pull failed", it) }.isSuccess
            val b = runCatching { pullWatchlist() }.onFailure { Log.w(TAG, "watchlist pull failed", it) }.isSuccess
            if (a || b) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(K_LAST_SYNC, now).apply()
                _lastSyncedAt.value = now
            }
        } finally {
            _isSyncing.value = false
        }
    }

    // ---- Doc ids (mirror buildDocId in both website services) ----

    /** Mirrors continue-watching-service buildDocId: season/episode suffix only when truthy (non-zero). */
    private fun historyDocId(uid: String, mediaType: String, mediaId: String, season: Int?, episode: Int?) =
        "${uid}_${mediaType}_${mediaId}" +
            (if (season != null && season != 0) "_s$season" else "") +
            (if (episode != null && episode != 0) "_e$episode" else "")

    /** Mirrors watchlist-service buildDocId. */
    private fun watchlistDocId(uid: String, movieType: String, mediaId: String) = "${uid}_${movieType}_$mediaId"

    // ---- Pull: mirrors syncWatchHistoryFromCloud ----

    private suspend fun pullHistory() {
        val uid = account.session.value?.uid ?: return
        val (arr, _) = getJsonArray("/api/watch-history") ?: return
        val pendingIds = pendingDocIds("/api/watch-history", uid)
        migrateTmdbEntries()
        val local = watchProgressPreferences.allProgress.first()
            .associateBy { progressKey(it.contentId, it.season, it.episode) }
        val seenLocalKeys = HashSet<String>()
        val newSynced = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val idStr = o.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            val mediaType = o.optString("media_type", "movie")
            val isAnime = mediaType == "anime"
            val isSeries = mediaType == "tv" || isAnime
            val season = if (isSeries && o.has("season") && !o.isNull("season")) o.optInt("season") else null
            val episode = if (isSeries && o.has("episode") && !o.isNull("episode")) o.optInt("episode") else null
            val docId = o.optString("docId").takeIf { it.isNotBlank() }
                ?: historyDocId(uid, mediaType, idStr, season, episode)
            newSynced += docId
            if (docId in pendingIds) continue // a queued local write wins over the cloud snapshot
            val durationMs = (o.optDouble("duration", 0.0) * 1000).toLong()
            val positionMs = (o.optDouble("progress", 0.0) * 1000).toLong()
            if (durationMs <= 0L || positionMs <= 0L) continue
            val watchedAt = toMs(o.opt("watchedAt"))
            val tmdbNum = idStr.toIntOrNull()
            val contentId = when {
                isAnime -> "anime:$idStr"
                else -> resolveContentId(tmdbNum ?: continue, if (isSeries) "tv" else "movie", o.optString("name"))
            }
            val key = progressKey(contentId, season, episode)
            seenLocalKeys += key
            val existing = local[key]
            val fresh = existing == null || existing.lastWatched < watchedAt
            val needsArt = existing != null && (existing.logo.isNullOrBlank() || existing.backdrop.isNullOrBlank())
            if (!fresh && !needsArt) continue
            val art = enrich(contentId, tmdbNum, isSeries, isAnime, o.optString("name"))
            val cloudBackdrop = backdropUrl(o.optString("backdrop_path"))
            if (!fresh && existing != null) {
                // Newer local entry wins; only top up missing artwork.
                watchProgressPreferences.saveProgress(
                    existing.copy(
                        backdrop = existing.backdrop?.takeIf { it.isNotBlank() } ?: art?.backdrop ?: cloudBackdrop,
                        poster = existing.poster?.takeIf { it.isNotBlank() } ?: art?.poster,
                        logo = existing.logo?.takeIf { it.isNotBlank() } ?: art?.logo
                    )
                )
                continue
            }
            val videoId = if (season != null && episode != null) "$contentId:$season:$episode"
            else if (isAnime && episode != null) "$contentId:1:$episode" else contentId
            watchProgressPreferences.saveProgress(
                WatchProgress(
                    contentId = contentId,
                    contentType = if (isSeries) "series" else "movie",
                    name = o.optString("name").ifBlank { art?.title.orEmpty() },
                    poster = art?.poster,
                    backdrop = art?.backdrop ?: cloudBackdrop,
                    logo = art?.logo,
                    videoId = videoId,
                    season = season ?: if (isAnime && episode != null) 1 else null,
                    episode = episode,
                    episodeTitle = null,
                    position = positionMs,
                    duration = durationMs,
                    lastWatched = maxOf(watchedAt, existing?.lastWatched ?: 0L),
                    source = WatchProgress.SOURCE_LOCAL
                )
            )
            // Drop a stale duplicate stored under the TMDB id for the same content.
            if (tmdbNum != null && !contentId.startsWith("tmdb:")) {
                val dupKey = progressKey("tmdb:$tmdbNum", season, episode)
                local[dupKey]?.let { watchProgressPreferences.removeProgress(it.contentId, it.season, it.episode) }
            }
        }
        // Rows previously pulled that are gone from the cloud (and have no pending write) were
        // deleted on another device. The server returns at most 100 rows, so only trust a full view.
        if (arr.length() < HISTORY_PAGE_LIMIT) {
            val previous = prefs.getStringSet(K_SYNCED_HISTORY, emptySet()).orEmpty()
            for (docId in previous - newSynced - pendingIds) {
                val k = historyKeyFromDocId(uid, docId) ?: continue
                val (contentKey, season, episode) = k
                val contentId = resolveDocContentId(contentKey) ?: continue
                watchProgressPreferences.removeProgress(contentId, season, episode)
            }
        }
        prefs.edit().putStringSet(K_SYNCED_HISTORY, newSynced).apply()
    }

    /** Splits "<uid>_<type>_<id>[_s<n>][_e<n>]" into ("<type>_<id>", season, episode). */
    private fun historyKeyFromDocId(uid: String, docId: String): Triple<String, Int?, Int?>? {
        var rest = docId.removePrefix("${uid}_")
        if (rest == docId) return null
        var episode: Int? = null
        var season: Int? = null
        Regex("_e(\\d+)$").find(rest)?.let { episode = it.groupValues[1].toInt(); rest = rest.removeSuffix(it.value) }
        Regex("_s(\\d+)$").find(rest)?.let { season = it.groupValues[1].toInt(); rest = rest.removeSuffix(it.value) }
        return Triple(rest, season, episode)
    }

    private suspend fun resolveDocContentId(typeAndId: String): String? {
        val type = typeAndId.substringBefore('_')
        val id = typeAndId.substringAfter('_', "")
        if (id.isBlank()) return null
        if (type == "anime") return "anime:$id"
        val n = id.toIntOrNull() ?: return null
        return resolveContentId(n, if (type == "tv") "tv" else "movie")
    }

    // ---- Pull: mirrors syncWatchlistFromCloud ----

    private suspend fun pullWatchlist() {
        val uid = account.session.value?.uid ?: return
        val pendingIds = pendingDocIds("/api/watchlist", uid)
        val rows = mutableListOf<JSONObject>()
        var offset = 0
        var guard = 0
        while (guard++ < 40) {
            val (arr, next) = getJsonArray("/api/watchlist?offset=$offset") ?: return
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { rows += it }
            if (next == null || next <= offset) break
            offset = next
        }
        val newSynced = HashSet<String>()
        for (o in rows) {
            val idStr = o.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            val movieType = o.optString("movie_type", "movie")
            val docId = watchlistDocId(uid, movieType, idStr)
            newSynced += docId
            if (docId in pendingIds) continue
            val isAnime = movieType == "anime"
            val isSeries = movieType != "movie"
            val type = if (isSeries) "series" else "movie"
            val id = if (isAnime) "anime:$idStr"
            else resolveContentId(idStr.toIntOrNull() ?: continue, if (isSeries) "tv" else "movie")
            if (libraryPreferences.containsItem(id, type)) continue
            val images = if (id.startsWith("tt")) {
                runCatching { tmdbService.fetchImdbImages(id, if (isSeries) "tv" else "movie") }.getOrNull()
            } else null
            libraryPreferences.addItem(
                SavedLibraryItem(
                    id = id,
                    type = type,
                    name = o.optString("name"),
                    poster = images?.posterUrl
                        ?: if (id.startsWith("tt")) "https://images.metahub.space/poster/medium/$id/img" else null,
                    posterShape = PosterShape.POSTER,
                    background = backdropUrl(o.optString("backdrop_path")) ?: images?.backdropUrl,
                    description = o.optString("description").takeIf { it.isNotBlank() },
                    releaseInfo = null,
                    imdbRating = null,
                    genres = emptyList(),
                    addonBaseUrl = null,
                    addedAt = toMs(o.opt("addedAt")).takeIf { it > 0 } ?: System.currentTimeMillis()
                )
            )
        }
        // Rows synced before but missing now (no pending write) were removed on another device.
        val previous = prefs.getStringSet(K_SYNCED_WATCHLIST, emptySet()).orEmpty()
        for (docId in previous - newSynced - pendingIds) {
            val rest = docId.removePrefix("${uid}_")
            val movieType = rest.substringBefore('_')
            val idStr = rest.substringAfter('_', "")
            val contentId = resolveDocContentId("${movieType}_$idStr") ?: continue
            libraryPreferences.removeItem(contentId, if (movieType == "movie") "movie" else "series")
        }
        prefs.edit().putStringSet(K_SYNCED_WATCHLIST, newSynced).apply()
    }

    // ---- Push: mirrors addToWatchHistory / removeFromWatchHistory / saveProgressBeacon ----

    /**
     * Called from the progress save point. Throttled to ~60 s per item (the website saves
     * periodically every 60 s); a trailing push is scheduled. Completed marks push immediately.
     */
    fun onProgressSaved(progress: WatchProgress) {
        if (!account.isSignedIn || progress.duration <= 0L || progress.position < 5_000L) return
        val key = progressKey(progress.contentId, progress.season, progress.episode)
        val now = System.currentTimeMillis()
        val last = lastPushAt[key] ?: 0L
        if (now - last >= PUSH_INTERVAL_MS || progress.isCompleted()) {
            lastPushAt[key] = now
            pendingPush.remove(key)
            scope.launch { pushProgress(progress) }
        } else if (pendingPush.put(key, progress) == null) {
            scope.launch {
                delay(PUSH_INTERVAL_MS - (now - last))
                pendingPush.remove(key)?.let {
                    lastPushAt[key] = System.currentTimeMillis()
                    pushProgress(it)
                }
            }
        }
    }

    /** Mirrors saveProgressBeacon: push the latest position immediately (player stop/exit). */
    fun flushProgress(progress: WatchProgress) {
        if (!account.isSignedIn || progress.duration <= 0L || progress.position < 5_000L) return
        val key = progressKey(progress.contentId, progress.season, progress.episode)
        pendingPush.remove(key)
        lastPushAt[key] = System.currentTimeMillis()
        scope.launch { pushProgress(progress) }
    }

    /** Watched/completed marks: push with progress == duration. */
    fun onMarkedCompleted(progress: WatchProgress) {
        if (!account.isSignedIn) return
        val d = progress.duration.coerceAtLeast(1_000L)
        flushProgress(progress.copy(position = d, duration = d))
    }

    /** Mirrors removeFromWatchHistory: DELETE /api/watch-history?id=docId. */
    fun onProgressRemoved(contentId: String, season: Int?, episode: Int?) {
        val uid = account.session.value?.uid ?: return
        scope.launch {
            runCatching {
                val isAnime = contentId.startsWith("anime:")
                val isSeries = isAnime || season != null || episode != null
                val mediaType = if (isAnime) "anime" else if (isSeries) "tv" else "movie"
                val mediaId = (if (isAnime) contentId.removePrefix("anime:")
                else resolveTmdbId(contentId, if (isSeries) "tv" else "movie")?.toString()) ?: return@launch
                val docId = historyDocId(uid, mediaType, mediaId, season, episode)
                enqueue(uid, "/api/watch-history", "DELETE", docId, "")
                flushQueue()
            }.onFailure { Log.w(TAG, "history delete failed", it) }
        }
    }

    private suspend fun pushProgress(p: WatchProgress) {
        runCatching {
            val uid = account.session.value?.uid ?: return
            val isAnime = p.contentId.startsWith("anime:")
            val isSeries = isAnime || p.contentType.equals("series", true) || p.contentType.equals("tv", true)
            val mediaType = if (isAnime) "anime" else if (isSeries) "tv" else "movie"
            val mediaId = (if (isAnime) p.contentId.removePrefix("anime:")
            else resolveTmdbId(p.contentId, if (isSeries) "tv" else "movie")?.toString()) ?: return
            val item = JSONObject()
                .put("id", mediaId)
                .put("name", p.name.ifBlank { "Unknown" })
                .put("media_type", mediaType)
                .put("progress", p.position / 1000)
                .put("duration", p.duration / 1000)
            item.put("backdrop_path", toBackdropPath(p.backdrop))
            item.put("description", "")
            if (isSeries && p.episode != null) {
                if (!isAnime && p.season != null) item.put("season", p.season)
                item.put("episode", p.episode)
                // Same string the website's TV player sends.
                if (!isAnime && p.season != null) item.put("lastPosition", "Season ${p.season}, Episode ${p.episode}")
            }
            val docId = historyDocId(uid, mediaType, mediaId, if (isAnime) null else p.season, p.episode)
            enqueue(uid, "/api/watch-history", "POST", docId, JSONObject().put("userId", uid).put("item", item).toString())
            flushQueue()
        }.onFailure { Log.w(TAG, "progress push failed", it) }
    }

    // ---- Library push: mirrors addToWatchlist / removeFromWatchlist ----

    fun onLibraryChanged(item: LibraryEntryInput, added: Boolean) {
        val uid = account.session.value?.uid ?: return
        scope.launch {
            runCatching {
                val isAnime = item.itemId.startsWith("anime:")
                val isSeries = !item.itemType.equals("movie", true)
                val type = if (isAnime) "anime" else if (isSeries) "tv" else "movie"
                val mediaId = (if (isAnime) item.itemId.removePrefix("anime:")
                else (item.tmdbId ?: resolveTmdbId(item.itemId, type))?.toString()) ?: return@launch
                val docId = watchlistDocId(uid, type, mediaId)
                if (added) {
                    val it = JSONObject().put("id", mediaId).put("name", item.title.ifBlank { "Unknown" })
                        .put("movie_type", type)
                        .put("backdrop_path", toBackdropPath(item.background))
                        .put("description", item.description.orEmpty())
                    enqueue(uid, "/api/watchlist", "POST", docId, JSONObject().put("userId", uid).put("item", it).toString())
                } else {
                    enqueue(uid, "/api/watchlist", "DELETE", docId, "")
                }
                flushQueue()
            }.onFailure { Log.w(TAG, "watchlist push failed", it) }
        }
    }

    // ---- Persistent write queue: mirrors lib/services/storage.ts writeQueue ----

    private data class Pending(
        val uid: String, val path: String, val method: String, val docId: String, val body: String, val addedAt: Long
    )

    private val queueLock = Any()

    private fun loadQueue(): MutableList<Pending> = synchronized(queueLock) {
        val arr = runCatching { JSONArray(prefs.getString(K_QUEUE, "[]")) }.getOrDefault(JSONArray())
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Pending(it.optString("uid"), it.optString("path"), it.optString("method"),
                    it.optString("docId"), it.optString("body"), it.optLong("addedAt"))
            }
        }.toMutableList()
    }

    private fun saveQueue(list: List<Pending>) = synchronized(queueLock) {
        val arr = JSONArray()
        list.takeLast(500).forEach {
            arr.put(JSONObject().put("uid", it.uid).put("path", it.path).put("method", it.method)
                .put("docId", it.docId).put("body", it.body).put("addedAt", it.addedAt))
        }
        prefs.edit().putString(K_QUEUE, arr.toString()).apply()
    }

    /** Latest intent replaces an earlier add/delete for the same account + path + document. */
    private fun enqueue(uid: String, path: String, method: String, docId: String, body: String) = synchronized(queueLock) {
        val q = loadQueue().filterNot { it.uid == uid && it.path == path && it.docId == docId }.toMutableList()
        q += Pending(uid, path, method, docId, body, System.currentTimeMillis())
        saveQueue(q)
    }

    private fun pendingDocIds(path: String, uid: String): Set<String> =
        loadQueue().filter { it.uid == uid && it.path == path }.map { it.docId }.toSet()

    /** Mirrors writeQueue.flush: sequential; drop on success/4xx, keep on 401/403/429/5xx/network error. */
    private suspend fun flushQueue(): Unit = flushMutex.withLock {
        val uid = account.session.value?.uid ?: return
        val attempted = HashSet<String>()
        while (true) {
            val write = loadQueue().firstOrNull { it.uid == uid && "${it.path}|${it.docId}|${it.addedAt}" !in attempted }
                ?: return
            attempted += "${write.path}|${write.docId}|${write.addedAt}"
            val url = if (write.method == "DELETE") "${write.path}?id=${URLEncoder.encode(write.docId, "UTF-8")}" else write.path
            val code = runCatching {
                request(write.method, url, if (write.method == "DELETE") null else write.body)
            }.getOrElse { Log.w(TAG, "flush ${write.method} ${write.path} failed", it); return }
            if (code in 200..299 || code == 204 || (code in 400..499 && code != 401 && code != 403 && code != 429)) {
                synchronized(queueLock) {
                    saveQueue(loadQueue().filterNot {
                        it.uid == write.uid && it.path == write.path && it.docId == write.docId && it.addedAt == write.addedAt
                    })
                }
            } else return
        }
    }

    // ---- HTTP (mirrors authenticatedFetch; adds a one-time token refresh + retry on 401) ----

    private suspend fun <T> withAuth(block: suspend (token: String) -> Response, read: (Response) -> T): T? =
        withContext(Dispatchers.IO) {
            var token = account.getIdToken() ?: return@withContext null
            var resp = block(token)
            if (resp.code == 401) {
                resp.close()
                token = account.getIdToken(forceRefresh = true) ?: return@withContext null
                resp = block(token)
            }
            resp.use { read(it) }
        }

    private suspend fun request(method: String, path: String, body: String?): Int =
        withAuth({ token ->
            val req = Request.Builder().url(CinemaOsConfig.SITE_BASE_URL + path)
                .header("Authorization", "Bearer $token")
                .method(method, if (method == "DELETE" && body == null) null else (body ?: "").toRequestBody(json))
                .build()
            httpClient.newCall(req).execute()
        }, { r ->
            if (!r.isSuccessful) Log.w(TAG, "$method $path -> ${r.code}")
            r.code
        }) ?: 401

    /** Returns the array plus the next offset from X-Next-Offset (null when no more pages). */
    private suspend fun getJsonArray(path: String): Pair<JSONArray, Int?>? =
        withAuth({ token ->
            httpClient.newCall(
                Request.Builder().url(CinemaOsConfig.SITE_BASE_URL + path)
                    .header("Authorization", "Bearer $token").get().build()
            ).execute()
        }, { r ->
            if (!r.isSuccessful) {
                Log.w(TAG, "GET $path -> ${r.code}")
                null
            } else JSONArray(r.body?.string().orEmpty()) to r.header("X-Next-Offset")?.toIntOrNull()
        })

    // ---- Helpers ----

    private suspend fun resolveContentId(tmdbId: Int, type: String, name: String? = null): String =
        (runCatching { tmdbService.tmdbToImdb(tmdbId, type) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: cinemetaImdbByTmdb(tmdbId, type, name))
            ?: "tmdb:$tmdbId"

    /** Cinemeta fallback: search by title and match on moviedb_id. */
    private suspend fun cinemetaImdbByTmdb(tmdbId: Int, type: String, name: String?): String? {
        if (name.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val kind = if (type == "tv" || type == "series") "series" else "movie"
                val q = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                val text = httpGet("https://v3-cinemeta.strem.io/catalog/$kind/top/search=$q.json") ?: return@runCatching null
                val metas = JSONObject(text).optJSONArray("metas") ?: return@runCatching null
                for (i in 0 until metas.length()) {
                    val m = metas.optJSONObject(i) ?: continue
                    if (m.optInt("moviedb_id", 0) == tmdbId) return@runCatching m.optString("imdb_id").ifBlank { m.optString("id") }
                        .takeIf { it.startsWith("tt") }
                }
                null
            }.getOrNull()
        }
    }

    private fun httpGet(url: String): String? =
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }

    private data class Art(val title: String?, val backdrop: String?, val poster: String?, val logo: String?)

    private val artCache = ConcurrentHashMap<String, Art>()

    /** Landscape backdrop, poster and logo for a pulled item (TMDB; anime resolves through arm.haglund.dev, Kitsu fallback). */
    private suspend fun enrich(contentId: String, tmdbIdIn: Int?, isSeries: Boolean, isAnime: Boolean, name: String?): Art? {
        artCache[contentId]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                var tmdbId = tmdbIdIn
                var kitsuId: String? = null
                var type = if (isSeries) "tv" else "movie"
                if (isAnime) {
                    type = "tv"
                    tmdbId = null
                    val text = httpGet("https://arm.haglund.dev/api/v2/ids?source=anilist&id=${contentId.removePrefix("anime:")}")
                    if (text != null) {
                        val j = JSONObject(text)
                        tmdbId = j.opt("themoviedb")?.toString()?.toIntOrNull()
                        kitsuId = j.opt("kitsu")?.toString()?.takeIf { it != "null" && it.isNotBlank() }
                        if (tmdbId == null) {
                            val imdb = j.optString("imdb").takeIf { it.startsWith("tt") }
                            if (imdb != null) tmdbId = runCatching { tmdbService.imdbToTmdb(imdb, "tv") }.getOrNull()
                        }
                    }
                } else if (tmdbId == null && contentId.startsWith("tt")) {
                    tmdbId = runCatching { tmdbService.imdbToTmdb(contentId, type) }.getOrNull()
                }
                val key = tmdbService.apiKey()
                var art: Art? = null
                if (tmdbId != null && key.isNotBlank()) {
                    val text = httpGet(
                        "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$key&append_to_response=images&include_image_language=en,null"
                    )
                    if (text != null) {
                        val j = JSONObject(text)
                        val logos = j.optJSONObject("images")?.optJSONArray("logos")
                        var logoPath: String? = null
                        if (logos != null) {
                            for (lang in listOf("en", null)) {
                                for (i in 0 until logos.length()) {
                                    val l = logos.optJSONObject(i) ?: continue
                                    val iso = if (l.isNull("iso_639_1")) null else l.optString("iso_639_1")
                                    if (iso == lang) { logoPath = l.optString("file_path"); break }
                                }
                                if (logoPath != null) break
                            }
                            if (logoPath == null) logoPath = logos.optJSONObject(0)?.optString("file_path")
                        }
                        fun img(path: String?, size: String) =
                            path?.takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/$size$it" }
                        art = Art(
                            title = j.optString("title").ifBlank { j.optString("name") }.takeIf { it.isNotBlank() },
                            backdrop = img(j.optString("backdrop_path"), "w1280"),
                            poster = img(j.optString("poster_path"), "w500"),
                            logo = img(logoPath, "w500")
                        )
                    }
                }
                if (isAnime && (art?.backdrop == null || art?.logo == null) && kitsuId != null) {
                    val text = httpGet("https://kitsu.io/api/edge/anime/$kitsuId")
                    val attrs = text?.let { JSONObject(it).optJSONObject("data")?.optJSONObject("attributes") }
                    if (attrs != null) {
                        val cover = attrs.optJSONObject("coverImage")?.optString("large")?.takeIf { it.startsWith("http") }
                        val poster = attrs.optJSONObject("posterImage")?.optString("large")?.takeIf { it.startsWith("http") }
                        art = Art(
                            title = art?.title ?: attrs.optString("canonicalTitle").takeIf { it.isNotBlank() },
                            backdrop = art?.backdrop ?: cover ?: poster,
                            poster = art?.poster ?: poster,
                            logo = art?.logo
                        )
                    }
                }
                if (art == null && contentId.startsWith("tt")) {
                    val kind = if (isSeries) "series" else "movie"
                    val text = httpGet("https://v3-cinemeta.strem.io/meta/$kind/$contentId.json")
                    val m = text?.let { JSONObject(it).optJSONObject("meta") }
                    if (m != null) art = Art(
                        title = m.optString("name").takeIf { it.isNotBlank() },
                        backdrop = m.optString("background").takeIf { it.startsWith("http") },
                        poster = m.optString("poster").takeIf { it.startsWith("http") },
                        logo = m.optString("logo").takeIf { it.startsWith("http") }
                    )
                }
                art?.also { artCache[contentId] = it }
            }.onFailure { Log.w(TAG, "enrich failed for $contentId", it) }.getOrNull()
        }
    }

    /** Moves entries stored under "tmdb:<id>" to the IMDb id when a mapping exists, merging by newest. */
    private suspend fun migrateTmdbEntries() {
        val all = watchProgressPreferences.allProgress.first()
        val byKey = all.associateBy { progressKey(it.contentId, it.season, it.episode) }
        for (e in all) {
            if (!e.contentId.startsWith("tmdb:")) continue
            val n = e.contentId.removePrefix("tmdb:").substringBefore(':').toIntOrNull() ?: continue
            val isSeries = e.contentType.equals("series", true) || e.season != null
            val imdb = resolveContentId(n, if (isSeries) "tv" else "movie", e.name)
            if (imdb.startsWith("tmdb:")) continue
            val target = byKey[progressKey(imdb, e.season, e.episode)]
            watchProgressPreferences.removeProgress(e.contentId, e.season, e.episode)
            if (target != null && target.lastWatched >= e.lastWatched) continue
            val videoId = if (e.season != null && e.episode != null) "$imdb:${e.season}:${e.episode}" else imdb
            watchProgressPreferences.saveProgress(
                e.copy(
                    contentId = imdb,
                    videoId = videoId,
                    poster = e.poster ?: target?.poster,
                    backdrop = e.backdrop ?: target?.backdrop,
                    logo = e.logo ?: target?.logo
                )
            )
        }
    }

    private suspend fun resolveTmdbId(contentId: String, type: String): Int? {
        if (contentId.startsWith("tmdb:")) return contentId.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
        val id = contentId.substringBefore(':')
        if (!id.startsWith("tt")) return null
        return runCatching { tmdbService.imdbToTmdb(id, type) }.getOrNull()
    }

    private fun progressKey(contentId: String, season: Int?, episode: Int?) =
        if (season != null && episode != null) "${contentId}_s${season}e$episode" else contentId

    /** The website stores TMDB-relative backdrop paths ("/abc.jpg"); the app keeps full URLs. */
    private fun toBackdropPath(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val m = Regex("^https://image\\.tmdb\\.org/t/p/[^/]+(/.+)$").find(url)
        return m?.groupValues?.get(1) ?: url
    }

    private fun backdropUrl(raw: String): String? = when {
        raw.isBlank() || raw == "null" -> null
        raw.startsWith("http") -> raw
        else -> "https://image.tmdb.org/t/p/w780" + (if (raw.startsWith("/")) raw else "/$raw")
    }

    /** Mirrors toMs in continue-watching-service: number, ISO string, else 0. */
    private fun toMs(v: Any?): Long = when (v) {
        is Number -> v.toLong().let { if (it in 1..99_999_999_999L) it * 1000 else it }
        is String -> v.toLongOrNull()
            ?: runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
            ?: 0L
        else -> 0L
    }

    private companion object {
        const val TAG = "CinemaOsSync"
        const val PUSH_INTERVAL_MS = 60_000L
        const val PERIODIC_SYNC_MS = 10 * 60_000L
        const val RESUME_SYNC_MAX_AGE_MS = 60_000L
        const val HISTORY_PAGE_LIMIT = 100
        const val K_LAST_SYNC = "last_sync_at"
        const val K_QUEUE = "pending_writes"
        const val K_SYNCED_HISTORY = "synced_history_docs"
        const val K_SYNCED_WATCHLIST = "synced_watchlist_docs"
    }
}
