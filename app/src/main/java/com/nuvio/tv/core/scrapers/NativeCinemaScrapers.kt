package com.nuvio.tv.core.scrapers

import com.nuvio.tv.domain.model.LocalScraperResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in movie / TV sources that replace the old Movy / HdHub / Wing / MovieBox QuickJS plugins.
 * Native HTTP + coroutines: no JS runtime start-up, parallel requests, real per-request timeouts.
 */
@Singleton
class NativeCinemaScrapers @Inject constructor(private val http: CinemaHttp) {
    /** Priority order: Movy > HdHub > Wing > MovieBox. */
    val providers: List<CinemaScraper> = listOf(
        MovyScraper(http),
        HdHubScraper(http),
        WingScraper(http),
        MovieBoxScraper(http)
    )

    /** Movies and non-anime series only (anime ids belong to the anime JS plugins). */
    fun supportsId(videoId: String, type: String): Boolean {
        if (Regex("^(kitsu|anilist|mal|myanimelist|anidb):", RegexOption.IGNORE_CASE).containsMatchIn(videoId)) return false
        return when (type.lowercase()) {
            "movie", "series", "tv", "show" -> true
            else -> false
        }
    }

    /**
     * Streams (scraper, results) pairs as each provider finishes. [sequential] runs them one at a time in priority
     * order (each result is delivered immediately, so the first playable source can start while the rest still
     * load); otherwise all run at once. Empty / failed providers are skipped.
     */
    fun stream(
        req: CinemaScrapeRequest,
        sequential: Boolean,
        only: List<CinemaScraper> = providers,
        stopAfterFirstResult: Boolean = false
    ): Flow<Pair<CinemaScraper, List<LocalScraperResult>>> = channelFlow {
        // anime check runs beside the first provider; results are dropped afterwards when ARM says anime
        val anime = if (!req.isMovie) async { http.isAnimeTmdb(req.tmdbId) } else null

        suspend fun run(scraper: CinemaScraper): List<LocalScraperResult> {
            val results = try {
                withTimeoutOrNull(scraper.budgetMs) { scraper.getStreams(req) } ?: emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "${scraper.name} failed: ${e.message}")
                emptyList()
            }
            if (results.isNotEmpty() && anime?.await() == true) return emptyList()
            return results
        }

        if (sequential) {
            for (scraper in only) {
                val results = run(scraper)
                if (results.isNotEmpty()) {
                    send(scraper to results)
                    if (stopAfterFirstResult) break
                }
            }
        } else {
            // Movy and HdHub outrank Wing and MovieBox: the lower tier still runs in parallel, but its results
            // are held back (at most PRIORITY_WAIT_MS) until Movy and HdHub have finished, so a fast MovieBox
            // can't win auto-play over HdHub.
            val priorityScrapers = only.filter { it.id.isPriorityTier() }
            val pending = java.util.concurrent.atomic.AtomicInteger(priorityScrapers.size)
            val priorityDone = kotlinx.coroutines.CompletableDeferred<Unit>()
            if (priorityScrapers.isEmpty()) priorityDone.complete(Unit)
            only.forEachIndexed { index, scraper ->
                launch {
                    if (index > 0) delay(index * 30L)
                    val isPriority = scraper.id.isPriorityTier()
                    val results = try {
                        run(scraper)
                    } finally {
                        if (isPriority && pending.decrementAndGet() <= 0) priorityDone.complete(Unit)
                    }
                    if (results.isEmpty()) return@launch
                    if (!isPriority) withTimeoutOrNull(PRIORITY_WAIT_MS) { priorityDone.await() }
                    send(scraper to results)
                }
            }
        }
    }

    private fun String.isPriorityTier(): Boolean =
        contains("movy", true) || contains("hdhub", true)

    private companion object {
        const val TAG = "NativeCinemaScrapers"
        const val PRIORITY_WAIT_MS = 7_000L
    }
}
