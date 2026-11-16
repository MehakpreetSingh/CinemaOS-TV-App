package com.nuvio.tv.core.scrapers

import com.nuvio.tv.domain.model.LocalScraperResult
import com.nuvio.tv.domain.model.ScraperInfo

/** One movie / episode lookup. [mediaType] is "movie" or "tv". */
data class CinemaScrapeRequest(
    val tmdbId: String,
    val mediaType: String,
    val season: Int,
    val episode: Int
) {
    val isMovie: Boolean get() = mediaType == "movie"
}

/** A built-in (native Kotlin) source. Returns the same result shape the JS plugins produce. */
interface CinemaScraper {
    /** Stable id, identical to the old plugin id so binge groups / play stats keep matching. */
    val id: String

    /** Display name, e.g. "CinemaOS · Movy"; ranking and MovieBox language grouping key off it. */
    val name: String
    val description: String

    /** Hard cap for one run when the scraper is part of the ordered (sequential) search. */
    val budgetMs: Long

    suspend fun getStreams(req: CinemaScrapeRequest): List<LocalScraperResult>

    fun scraperInfo(): ScraperInfo = ScraperInfo(
        id = id,
        name = name,
        description = description,
        version = "native",
        filename = "",
        supportedTypes = listOf("movie", "tv"),
        enabled = true,
        manifestEnabled = true,
        logo = null,
        contentLanguage = emptyList(),
        repositoryId = "cinemaos-native",
        formats = null
    )
}
