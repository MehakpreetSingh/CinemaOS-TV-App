package com.nuvio.tv.core.cinemaos.account

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.model.WatchProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs Continue Watching and Library with the CinemaOS website (Bearer Firebase ID token).
 *
 * Id mapping: the site uses TMDB ids; the app keys content by IMDb id ("tt..."), with
 * "tmdb:<id>" as the fallback when no IMDb id can be resolved (TmdbService lookup).
 * media_type "anime" is treated as "tv"/series locally.
 *
 * This class deliberately does not depend on the repositories (they call it for pushes).
 * Pulls write to the preference stores directly so they never echo back to the site.
 * Everything is a no-op while signed out.
 */
@Singleton
class CinemaOsSyncService @Inject constructor(
    private val account: CinemaOsAccountManager,
    private val httpClient: OkHttpClient,
    private val tmdbService: TmdbService,
    private val watchProgressPreferences: WatchProgressPreferences,
    private val libraryPreferences: LibraryPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pullMutex = Mutex()
    private val json = "application/json".toMediaType()
    private val lastPushAt = ConcurrentHashMap<String, Long>()
    private val pendingPush = ConcurrentHashMap<String, WatchProgress>()

    /** Pulls on app start (if signed in) and whenever a user signs in. */
    fun start() {
        scope.launch {
            account.session.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) pullAll()
            }
        }
    }

    fun pullNow() {
        scope.launch { pullAll() }
    }

    private suspend fun pullAll() = pullMutex.withLock {
        runCatching { pullHistory() }.onFailure { Log.w(TAG, "history pull failed", it) }
        runCatching { pullWatchlist() }.onFailure { Log.w(TAG, "watchlist pull failed", it) }
    }

    // ---- Pull ----

    private suspend fun pullHistory() {
        val arr = getJsonArray("/api/watch-history")?.first ?: return
        val local = watchProgressPreferences.allProgress.first()
            .associateBy { progressKey(it.contentId, it.season, it.episode) }
        val toSave = mutableListOf<WatchProgress>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tmdbId = o.optInt("id", 0).takeIf { it > 0 } ?: continue
            val mediaType = o.optString("media_type", "movie")
            val isSeries = mediaType == "tv" || mediaType == "anime"
            val season = if (isSeries && o.has("season") && !o.isNull("season")) o.optInt("season") else null
            val episode = if (isSeries && o.has("episode") && !o.isNull("episode")) o.optInt("episode") else null
            val durationMs = (o.optDouble("duration", 0.0) * 1000).toLong()
            val progressSec = o.optDouble("progress", 0.0)
            val positionSec = if (o.has("lastPosition") && !o.isNull("lastPosition")) {
                o.optDouble("lastPosition", progressSec)
            } else progressSec
            if (durationMs <= 0L || positionSec <= 0.0) continue
            val watchedAt = parseTime(o.opt("watchedAt"))
            val contentId = resolveContentId(tmdbId, if (isSeries) "tv" else "movie")
            val existing = local[progressKey(contentId, season, episode)]
            if (existing != null && existing.lastWatched >= watchedAt) continue
            val videoId = if (season != null && episode != null) "$contentId:$season:$episode" else contentId
            toSave += WatchProgress(
                contentId = contentId,
                contentType = if (isSeries) "series" else "movie",
                name = o.optString("name"),
                poster = null,
                backdrop = backdropUrl(o.optString("backdrop_path")),
                logo = null,
                videoId = videoId,
                season = season,
                episode = episode,
                episodeTitle = null,
                position = (positionSec * 1000).toLong(),
                duration = durationMs,
                lastWatched = watchedAt,
                source = WatchProgress.SOURCE_LOCAL
            )
        }
        toSave.forEach { watchProgressPreferences.saveProgress(it) }
    }

    private suspend fun pullWatchlist() {
        var offset = 0
        var guard = 0
        while (guard++ < 20) {
            val (arr, next) = getJsonArray("/api/watchlist?offset=$offset") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val tmdbId = o.optInt("id", 0).takeIf { it > 0 } ?: continue
                val isSeries = o.optString("movie_type") != "movie"
                val type = if (isSeries) "series" else "movie"
                val id = resolveContentId(tmdbId, if (isSeries) "tv" else "movie")
                if (libraryPreferences.containsItem(id, type)) continue
                libraryPreferences.addItem(
                    SavedLibraryItem(
                        id = id,
                        type = type,
                        name = o.optString("name"),
                        poster = if (id.startsWith("tt")) "https://images.metahub.space/poster/medium/$id/img" else null,
                        posterShape = PosterShape.POSTER,
                        background = backdropUrl(o.optString("backdrop_path")),
                        description = o.optString("description").takeIf { it.isNotBlank() },
                        releaseInfo = null,
                        imdbRating = null,
                        genres = emptyList(),
                        addonBaseUrl = null,
                        addedAt = parseTime(o.opt("addedAt"))
                    )
                )
            }
            offset = next ?: return
        }
    }

    // ---- Push ----

    /** Called from the progress save point. Throttled to ~60 s per item; a trailing push is scheduled. */
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

    private suspend fun pushProgress(p: WatchProgress) {
        runCatching {
            val isSeries = p.contentType.equals("series", true) || p.contentType.equals("tv", true)
            val tmdbId = resolveTmdbId(p.contentId, if (isSeries) "tv" else "movie") ?: return
            val item = JSONObject()
                .put("id", tmdbId)
                .put("name", p.name)
                .put("media_type", if (isSeries) "tv" else "movie")
                .put("progress", p.position / 1000.0)
                .put("duration", p.duration / 1000.0)
                .put("lastPosition", p.position / 1000.0)
            p.backdrop?.let { item.put("backdrop_path", it) }
            if (isSeries && p.season != null && p.episode != null) {
                item.put("season", p.season).put("episode", p.episode)
            }
            send("POST", "/api/watch-history", JSONObject().put("item", item))
        }.onFailure { Log.w(TAG, "progress push failed", it) }
    }

    fun onLibraryChanged(item: LibraryEntryInput, added: Boolean) {
        if (!account.isSignedIn) return
        scope.launch {
            runCatching {
                val isSeries = !item.itemType.equals("movie", true)
                val type = if (isSeries) "tv" else "movie"
                val tmdbId = item.tmdbId ?: resolveTmdbId(item.itemId, type) ?: return@launch
                if (added) {
                    val body = JSONObject().put(
                        "item",
                        JSONObject().put("id", tmdbId).put("name", item.title).put("movie_type", type)
                            .apply {
                                item.background?.let { put("backdrop_path", it) }
                                item.description?.let { put("description", it) }
                            }
                    )
                    send("POST", "/api/watchlist", body)
                } else {
                    val uid = account.session.value?.uid ?: return@launch
                    send("DELETE", "/api/watchlist?id=${uid}_${type}_$tmdbId", null)
                }
            }.onFailure { Log.w(TAG, "watchlist push failed", it) }
        }
    }

    // ---- Helpers ----

    private suspend fun send(method: String, path: String, body: JSONObject?) = withContext(Dispatchers.IO) {
        val token = account.getIdToken() ?: return@withContext
        val req = Request.Builder().url(CinemaOsConfig.SITE_BASE_URL + path)
            .header("Authorization", "Bearer $token")
            .method(method, body?.toString()?.toRequestBody(json))
            .build()
        httpClient.newCall(req).execute().use { r ->
            if (!r.isSuccessful) Log.w(TAG, "$method $path -> ${r.code}")
        }
    }

    /** Returns the array plus the next offset from X-Next-Offset (null when no more pages). */
    private suspend fun getJsonArray(path: String): Pair<JSONArray, Int?>? = withContext(Dispatchers.IO) {
        val token = account.getIdToken() ?: return@withContext null
        val req = Request.Builder().url(CinemaOsConfig.SITE_BASE_URL + path)
            .header("Authorization", "Bearer $token").get().build()
        httpClient.newCall(req).execute().use { r ->
            if (!r.isSuccessful) {
                Log.w(TAG, "GET $path -> ${r.code}")
                return@withContext null
            }
            JSONArray(r.body?.string().orEmpty()) to r.header("X-Next-Offset")?.toIntOrNull()
        }
    }

    private suspend fun resolveContentId(tmdbId: Int, type: String): String =
        runCatching { tmdbService.tmdbToImdb(tmdbId, type) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "tmdb:$tmdbId"

    private suspend fun resolveTmdbId(contentId: String, type: String): Int? {
        if (contentId.startsWith("tmdb:")) return contentId.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
        val id = contentId.substringBefore(':')
        if (!id.startsWith("tt")) return null
        return runCatching { tmdbService.imdbToTmdb(id, type) }.getOrNull()
    }

    private fun progressKey(contentId: String, season: Int?, episode: Int?) =
        if (season != null && episode != null) "${contentId}_s${season}e$episode" else contentId

    private fun backdropUrl(raw: String): String? = when {
        raw.isBlank() || raw == "null" -> null
        raw.startsWith("http") -> raw
        else -> "https://image.tmdb.org/t/p/w780" + (if (raw.startsWith("/")) raw else "/$raw")
    }

    private fun parseTime(v: Any?): Long = when (v) {
        is Number -> v.toLong().let { if (it < 100_000_000_000L) it * 1000 else it }
        is String -> v.toLongOrNull()
            ?: runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
            ?: System.currentTimeMillis()
        else -> System.currentTimeMillis()
    }

    private companion object {
        const val TAG = "CinemaOsSync"
        const val PUSH_INTERVAL_MS = 60_000L
    }
}
