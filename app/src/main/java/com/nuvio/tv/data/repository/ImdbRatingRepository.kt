package com.nuvio.tv.data.repository

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide, in-memory store of real IMDb scores keyed by IMDb id (`tt...`).
 *
 * Reads are snapshot-aware, so a composable that calls [peek] recomposes when the hero or the
 * details screen later fills the entry. Cards only ever [peek]; they never trigger a request.
 */
object ImdbRatingCache {
    private val ratings = mutableStateMapOf<String, Float>()
    private val misses = ConcurrentHashMap<String, Long>()

    fun normalizeId(raw: String?): String? {
        val candidate = raw?.trim()?.substringBefore(':')?.lowercase().orEmpty()
        return candidate.takeIf { IMDB_ID.matches(it) }
    }

    /** The cached IMDb score, or null when none is known (yet). Never starts a request. */
    fun peek(imdbId: String?): Float? = normalizeId(imdbId)?.let { ratings[it] }

    internal fun put(imdbId: String, rating: Float) {
        misses.remove(imdbId)
        ratings[imdbId] = rating
    }

    internal fun markMiss(imdbId: String, ttlMs: Long) {
        misses[imdbId] = System.currentTimeMillis() + ttlMs
    }

    internal fun isMissCoolingDown(imdbId: String): Boolean {
        val until = misses[imdbId] ?: return false
        if (until > System.currentTimeMillis()) return true
        misses.remove(imdbId)
        return false
    }

    private val IMDB_ID = Regex("tt\\d+")
}

/**
 * Looks up the real IMDb rating through Cinemeta (`/meta/{movie|series}/{imdbId}.json`,
 * field `meta.imdbRating`) and keeps it in [ImdbRatingCache].
 *
 * Meant for the hero and the details screen only. Concurrent callers for the same id share one
 * request, and ids Cinemeta has no rating for are not asked again for a while.
 */
@Singleton
class ImdbRatingRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Float?>>()
    private val gate = Semaphore(MAX_CONCURRENT_REQUESTS)

    /** Cache-only lookup; safe to call from card composition. */
    fun cached(imdbId: String?): Float? = ImdbRatingCache.peek(imdbId)

    /**
     * Returns the real IMDb rating for [rawImdbId] (an id such as `tt0111161`, or `tt0111161:1:2`)
     * or null when it is unknown. [type] is the content type (`movie`, `series`, `tv`, ...).
     */
    suspend fun getRating(rawImdbId: String?, type: String?): Float? {
        val imdbId = ImdbRatingCache.normalizeId(rawImdbId) ?: return null
        ImdbRatingCache.peek(imdbId)?.let { return it }
        if (ImdbRatingCache.isMissCoolingDown(imdbId)) return null

        val key = "${kindFor(type)}:$imdbId"
        val created = CompletableDeferred<Float?>()
        val existing = inFlight.putIfAbsent(key, created)
        if (existing != null) return existing.await()

        scope.launch {
            try {
                created.complete(fetch(imdbId, kindFor(type)))
            } catch (e: CancellationException) {
                created.cancel(e)
                throw e
            } catch (e: Exception) {
                created.complete(null)
            } finally {
                inFlight.remove(key, created)
            }
        }
        return created.await()
    }

    private suspend fun fetch(imdbId: String, kind: String): Float? = gate.withPermit {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://v3-cinemeta.strem.io/meta/$kind/$imdbId.json")
                    .header("Accept", "application/json")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            val body = response.body?.string().orEmpty()
                            val rating = JSONObject(body).optJSONObject("meta")
                                ?.optString("imdbRating")
                                ?.trim()
                                ?.toFloatOrNull()
                                ?.takeIf { it > 0f }
                            if (rating != null) {
                                ImdbRatingCache.put(imdbId, rating)
                            } else {
                                ImdbRatingCache.markMiss(imdbId, MISS_TTL_MS)
                            }
                            rating
                        }
                        response.code == 404 -> {
                            ImdbRatingCache.markMiss(imdbId, MISS_TTL_MS)
                            null
                        }
                        else -> {
                            ImdbRatingCache.markMiss(imdbId, FAILURE_COOLDOWN_MS)
                            null
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cinemeta IMDb rating lookup failed for $imdbId: ${e.message}")
                ImdbRatingCache.markMiss(imdbId, FAILURE_COOLDOWN_MS)
                null
            }
        }
    }

    private fun kindFor(type: String?): String =
        when (type?.trim()?.lowercase()) {
            "series", "tv", "show", "anime" -> "series"
            else -> "movie"
        }

    private companion object {
        const val TAG = "ImdbRatingRepository"
        const val MAX_CONCURRENT_REQUESTS = 4
        const val MISS_TTL_MS = 6L * 60L * 60L * 1000L
        const val FAILURE_COOLDOWN_MS = 2L * 60L * 1000L
    }
}
