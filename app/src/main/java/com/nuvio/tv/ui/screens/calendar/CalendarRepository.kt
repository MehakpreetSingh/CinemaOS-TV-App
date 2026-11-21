package com.nuvio.tv.ui.screens.calendar

import androidx.compose.runtime.Immutable
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbEpisode
import com.nuvio.tv.ui.screens.providers.CinemaosMovieReleaseDetails
import com.nuvio.tv.ui.screens.providers.CinemaosTmdbExtrasApi
import com.nuvio.tv.ui.screens.providers.CinemaosTvAiringDetails
import com.nuvio.tv.ui.screens.providers.tmdbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A title the calendar follows: from the library / watchlist or from Continue Watching. */
@Immutable
data class CalendarSource(
    val id: String,
    val isSeries: Boolean,
    val name: String,
    val poster: String?,
    val addonBaseUrl: String?,
    val tmdbId: Int?,
    val imdbId: String?
)

/** One line on a calendar day: a movie release, or one or more episodes of a series. */
@Immutable
data class CalendarEntry(
    val key: String,
    val itemId: String,
    val itemType: String,
    val addonBaseUrl: String?,
    val title: String,
    val poster: String?,
    val date: LocalDate,
    val isMovie: Boolean,
    val season: Int? = null,
    val firstEpisode: Int? = null,
    val lastEpisode: Int? = null,
    val episodeCount: Int = 0,
    val episodeTitle: String? = null
)

/**
 * Builds a month of [CalendarEntry]s from TMDB: for series, the episode `air_date`s of the current
 * and next season (`tv/{id}` -> `tv/{id}/season/{n}`); for movies, `release_date`. Uses the shared
 * Retrofit TMDB client (Hilt OkHttpClient with DNS over HTTPS) and the app's TMDB key. Results are
 * cached per month and per title, and at most [MAX_CONCURRENT] titles are fetched at once.
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val extrasApi: CinemaosTmdbExtrasApi,
    private val tmdbService: TmdbService
) {
    private data class Timed<T>(val value: T, val at: Long)

    private val monthCache = ConcurrentHashMap<String, Timed<List<CalendarEntry>>>()
    private val tvCache = ConcurrentHashMap<Int, Timed<CinemaosTvAiringDetails?>>()
    private val seasonCache = ConcurrentHashMap<String, Timed<List<TmdbEpisode>>>()
    private val movieCache = ConcurrentHashMap<Int, Timed<CinemaosMovieReleaseDetails?>>()

    val isAvailable: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    fun cached(month: YearMonth, sources: List<CalendarSource>): List<CalendarEntry>? =
        monthCache[cacheKey(month, sources)]?.takeIf { fresh(it.at, MONTH_TTL_MS) }?.value

    suspend fun loadMonth(month: YearMonth, sources: List<CalendarSource>): List<CalendarEntry> {
        if (!isAvailable || sources.isEmpty()) return emptyList()
        val key = cacheKey(month, sources)
        monthCache[key]?.takeIf { fresh(it.at, MONTH_TTL_MS) }?.let { return it.value }
        val entries = withContext(Dispatchers.IO) {
            val gate = Semaphore(MAX_CONCURRENT)
            coroutineScope {
                sources.map { source ->
                    async {
                        gate.withPermit {
                            runCatching {
                                if (source.isSeries) seriesEntries(source, month) else movieEntries(source, month)
                            }.getOrDefault(emptyList())
                        }
                    }
                }.awaitAll().flatten()
            }
        }.sortedWith(compareBy<CalendarEntry> { it.date }.thenBy { it.isMovie }.thenBy { it.title.lowercase() })
        monthCache[key] = Timed(entries, System.currentTimeMillis())
        return entries
    }

    private suspend fun resolveTmdbId(source: CalendarSource): Int? {
        source.tmdbId?.let { return it }
        val type = if (source.isSeries) "series" else "movie"
        return runCatching { tmdbService.ensureTmdbId(source.id, type, source.imdbId) }
            .getOrNull()?.toIntOrNull()
    }

    private suspend fun seriesEntries(source: CalendarSource, month: YearMonth): List<CalendarEntry> {
        val tmdbId = resolveTmdbId(source) ?: return emptyList()
        val details = tvDetails(tmdbId) ?: return emptyList()
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()

        val lastAired = details.lastAirDate?.let(::parseDate)
        val next = details.nextEpisodeToAir
        // Nothing upcoming and nothing aired since this month began: the show cannot appear here.
        if (next == null && (lastAired == null || lastAired.isBefore(monthStart))) return emptyList()

        val seasons = listOfNotNull(details.lastEpisodeToAir?.seasonNumber, next?.seasonNumber)
            .filter { it > 0 }
            .distinct()
        if (seasons.isEmpty()) return emptyList()

        val poster = source.poster?.takeIf { it.isNotBlank() } ?: tmdbImage(details.posterPath, "w342")
        val title = source.name.ifBlank { details.name.orEmpty() }
        val result = ArrayList<CalendarEntry>()
        seasons.forEach { seasonNumber ->
            val episodes = seasonEpisodes(tmdbId, seasonNumber)
            episodes
                .mapNotNull { ep ->
                    val date = ep.airDate?.let(::parseDate) ?: return@mapNotNull null
                    val number = ep.episodeNumber ?: return@mapNotNull null
                    if (date.isBefore(monthStart) || date.isAfter(monthEnd)) null else Triple(date, number, ep.name)
                }
                .groupBy { it.first }
                .forEach { (date, onDay) ->
                    val sorted = onDay.sortedBy { it.second }
                    result += CalendarEntry(
                        key = "${source.id}|s$seasonNumber|$date",
                        itemId = source.id,
                        itemType = "series",
                        addonBaseUrl = source.addonBaseUrl,
                        title = title,
                        poster = poster,
                        date = date,
                        isMovie = false,
                        season = seasonNumber,
                        firstEpisode = sorted.first().second,
                        lastEpisode = sorted.last().second,
                        episodeCount = sorted.size,
                        episodeTitle = sorted.singleOrNull()?.third?.takeIf { it.isNotBlank() }
                    )
                }
        }
        return result
    }

    private suspend fun movieEntries(source: CalendarSource, month: YearMonth): List<CalendarEntry> {
        val tmdbId = resolveTmdbId(source) ?: return emptyList()
        val details = movieDetails(tmdbId) ?: return emptyList()
        val date = details.releaseDate?.let(::parseDate) ?: return emptyList()
        if (YearMonth.from(date) != month) return emptyList()
        return listOf(
            CalendarEntry(
                key = "${source.id}|movie|$date",
                itemId = source.id,
                itemType = "movie",
                addonBaseUrl = source.addonBaseUrl,
                title = source.name.ifBlank { details.title.orEmpty() },
                poster = source.poster?.takeIf { it.isNotBlank() } ?: tmdbImage(details.posterPath, "w342"),
                date = date,
                isMovie = true
            )
        )
    }

    private suspend fun tvDetails(tmdbId: Int): CinemaosTvAiringDetails? {
        tvCache[tmdbId]?.takeIf { fresh(it.at, TITLE_TTL_MS) }?.let { return it.value }
        val response = extrasApi.tvAiringDetails(tmdbId, BuildConfig.TMDB_API_KEY)
        if (!response.isSuccessful) return null
        val body = response.body()
        tvCache[tmdbId] = Timed(body, System.currentTimeMillis())
        return body
    }

    private suspend fun seasonEpisodes(tmdbId: Int, season: Int): List<TmdbEpisode> {
        val key = "$tmdbId:$season"
        seasonCache[key]?.takeIf { fresh(it.at, TITLE_TTL_MS) }?.let { return it.value }
        val response = runCatching { tmdbApi.getTvSeasonDetails(tmdbId, season, BuildConfig.TMDB_API_KEY) }.getOrNull()
        val episodes = response?.takeIf { it.isSuccessful }?.body()?.episodes.orEmpty()
        if (response?.isSuccessful == true) seasonCache[key] = Timed(episodes, System.currentTimeMillis())
        return episodes
    }

    private suspend fun movieDetails(tmdbId: Int): CinemaosMovieReleaseDetails? {
        movieCache[tmdbId]?.takeIf { fresh(it.at, TITLE_TTL_MS) }?.let { return it.value }
        val response = extrasApi.movieReleaseDetails(tmdbId, BuildConfig.TMDB_API_KEY)
        if (!response.isSuccessful) return null
        val body = response.body()
        movieCache[tmdbId] = Timed(body, System.currentTimeMillis())
        return body
    }

    private fun cacheKey(month: YearMonth, sources: List<CalendarSource>): String =
        "$month|${sources.map { it.id }.sorted().hashCode()}"

    private fun fresh(at: Long, ttl: Long) = System.currentTimeMillis() - at < ttl

    private fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()

    private companion object {
        const val MAX_CONCURRENT = 4
        const val MONTH_TTL_MS = 30 * 60_000L
        const val TITLE_TTL_MS = 6 * 60 * 60_000L
    }
}
