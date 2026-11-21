package com.nuvio.tv.ui.screens.search

import androidx.compose.runtime.Immutable
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbDiscoverResult
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Every filter the Discover screen can show. Addon catalogs only ever use [GENRE] and [YEAR]. */
enum class DiscoverFilterKey { SORT, GENRE, YEAR, RATING, RUNTIME, LANGUAGE, COUNTRY, PROVIDER, NETWORK }

@Immutable
data class DiscoverFilterOption(val label: String, val value: String)

internal const val TMDB_DISCOVER_KEY_PREFIX = "tmdb_discover:"
internal const val DISCOVER_DECADE_PREFIX = "decade:"
private val YEAR_OPTION = Regex("^\\d{4}$")

/**
 * True when a catalog's `genre` extra is really a year picker. Cinemeta's "New" catalog
 * (id `year`) sends the release year through the `genre` extra, so its options are
 * "2026", "2025", ... and must never be shown as genres.
 */
internal fun genreOptionsAreYears(options: List<String>): Boolean =
    options.isNotEmpty() && options.all { YEAR_OPTION.matches(it.trim()) }

/**
 * Genre choices for an addon catalog. A catalog without its own genre list (Kitsu Trending,
 * Cinemeta New) offers the genres of the other catalogs of the same type, and picking one moves
 * to a catalog that supports it (see SearchViewModel.selectAddonGenre).
 */
internal fun discoverAddonGenreOptions(
    catalogs: List<DiscoverCatalog>,
    selected: DiscoverCatalog?
): List<String> {
    if (selected == null || selected.isTmdbDiscover) return emptyList()
    if (selected.genres.isNotEmpty()) return selected.genres
    return catalogs
        .asSequence()
        .filter { it.type == selected.type && !it.isTmdbDiscover }
        .flatMap { it.genres.asSequence() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .toList()
}

object DiscoverSort {
    const val POPULAR = "popularity.desc"
    const val NEWEST = "newest"
    const val OLDEST = "oldest"
    const val TOP_RATED = "top_rated"
    const val MOST_VOTED = "vote_count.desc"
    const val REVENUE = "revenue.desc"
    const val UPCOMING = "upcoming"
    const val NOW_PLAYING = "now_playing"

    fun valuesFor(type: String): List<String> = buildList {
        add(POPULAR)
        add(NEWEST)
        add(OLDEST)
        add(TOP_RATED)
        add(MOST_VOTED)
        if (type == DISCOVER_TYPE_MOVIE) add(REVENUE)
        add(UPCOMING)
        add(NOW_PLAYING)
    }
}

internal const val DISCOVER_TYPE_SERIES = "series"
internal const val DISCOVER_TYPE_ANIME = "anime"
internal const val DISCOVER_TYPE_MOVIE = "movie"

/** TMDB "Animation" genre id (shared by movies and TV). */
internal const val TMDB_GENRE_ANIMATION = "16"
/** Anime is Animation + original language Japanese. */
internal const val ANIME_ORIGINAL_LANGUAGE = "ja"

/** Series and anime are both served by TMDB's TV endpoints. */
internal fun isTvLikeDiscoverType(type: String): Boolean =
    type == DISCOVER_TYPE_SERIES || type == DISCOVER_TYPE_ANIME

/** Filter values sent to TMDB discover. Values are raw API values, labels are resolved by the UI. */
object DiscoverFilterValues {
    val ratings = listOf("9", "8", "7", "6", "5")
    val runtimes = listOf("0-30", "30-60", "60-90", "90-120", "120-")
    val languages = listOf(
        "en", "ja", "ko", "hi", "es", "fr", "de", "it", "zh", "pt", "ru",
        "ta", "te", "ml", "tr", "th", "sv", "da", "no", "ar", "pa"
    )
    val countries = listOf(
        "US", "GB", "IN", "JP", "KR", "FR", "DE", "ES", "IT", "CA", "AU",
        "CN", "HK", "BR", "MX", "TR", "SE", "DK", "NO"
    )

    /** TMDB watch provider ids; brand names are not translated. */
    val providers = listOf(
        "8" to "Netflix",
        "9|119" to "Prime Video",
        "337" to "Disney+",
        "1899" to "Max",
        "350" to "Apple TV+",
        "15" to "Hulu",
        "531" to "Paramount+",
        "283" to "Crunchyroll",
        "2336|122" to "JioHotstar",
        "232" to "Zee5",
        "237" to "SonyLiv"
    )

    /** TMDB network ids (series only). */
    val networks = listOf(
        "213" to "Netflix",
        "1024" to "Amazon Prime",
        "2739" to "Disney+",
        "49" to "HBO",
        "2552" to "Apple TV+",
        "453" to "Hulu",
        "4330" to "Paramount+",
        "174" to "AMC",
        "19" to "Fox",
        "6" to "NBC",
        "2" to "ABC",
        "16" to "CBS",
        "56" to "Cartoon Network",
        "65" to "History",
        "64" to "Discovery",
        "359" to "Showtime",
        "318" to "Starz",
        "71" to "FX",
        "4" to "BBC One",
        "332" to "BBC Two"
    )

    fun decades(): List<Int> {
        val currentDecade = LocalDate.now().year / 10 * 10
        return (currentDecade downTo 1950 step 10).toList()
    }

    fun years(): List<Int> = (LocalDate.now().year + 1 downTo 1950).toList()
}

@Immutable
data class DiscoverTmdbFilters(
    val sort: String = DiscoverSort.POPULAR,
    val genre: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val runtime: String? = null,
    val language: String? = null,
    val country: String? = null,
    val provider: String? = null,
    val network: String? = null
) {
    fun value(key: DiscoverFilterKey): String? = when (key) {
        DiscoverFilterKey.SORT -> sort
        DiscoverFilterKey.GENRE -> genre
        DiscoverFilterKey.YEAR -> year
        DiscoverFilterKey.RATING -> rating
        DiscoverFilterKey.RUNTIME -> runtime
        DiscoverFilterKey.LANGUAGE -> language
        DiscoverFilterKey.COUNTRY -> country
        DiscoverFilterKey.PROVIDER -> provider
        DiscoverFilterKey.NETWORK -> network
    }

    fun with(key: DiscoverFilterKey, value: String?): DiscoverTmdbFilters {
        val clean = value?.takeIf { it.isNotBlank() }
        return when (key) {
            DiscoverFilterKey.SORT -> copy(sort = clean ?: DiscoverSort.POPULAR)
            DiscoverFilterKey.GENRE -> copy(genre = clean)
            DiscoverFilterKey.YEAR -> copy(year = clean)
            DiscoverFilterKey.RATING -> copy(rating = clean)
            DiscoverFilterKey.RUNTIME -> copy(runtime = clean)
            DiscoverFilterKey.LANGUAGE -> copy(language = clean)
            DiscoverFilterKey.COUNTRY -> copy(country = clean)
            DiscoverFilterKey.PROVIDER -> copy(provider = clean)
            DiscoverFilterKey.NETWORK -> copy(network = clean)
        }
    }

    fun isActive(key: DiscoverFilterKey): Boolean =
        if (key == DiscoverFilterKey.SORT) sort != DiscoverSort.POPULAR else value(key) != null

    /**
     * Movie and series genre ids differ on TMDB, networks exist only for series and revenue only
     * for movies, so a type switch drops whatever the new type cannot honour.
     */
    fun adaptedTo(type: String): DiscoverTmdbFilters = copy(
        genre = null,
        network = network.takeIf { isTvLikeDiscoverType(type) },
        sort = if (type != DISCOVER_TYPE_MOVIE && sort == DiscoverSort.REVENUE) DiscoverSort.POPULAR else sort
    )
}

/** One synthetic TMDB catalog per type, selectable in the Catalog picker like any addon catalog. */
internal fun tmdbDiscoverCatalogs(catalogName: String): List<DiscoverCatalog> =
    listOf(DISCOVER_TYPE_MOVIE, DISCOVER_TYPE_SERIES, DISCOVER_TYPE_ANIME).map { type ->
        DiscoverCatalog(
            key = "$TMDB_DISCOVER_KEY_PREFIX$type",
            addonId = "tmdb_discover",
            addonName = "TMDB",
            addonBaseUrl = "",
            catalogId = "tmdb_discover_$type",
            catalogName = catalogName,
            type = type,
            genres = emptyList(),
            supportsSkip = true,
            skipStep = 20,
            isTmdbDiscover = true
        )
    }

/** Filters shown besides the always-present Type / Catalog / Genre pickers. */
internal fun discoverExtraFilterKeys(catalog: DiscoverCatalog?): List<DiscoverFilterKey> = when {
    catalog == null -> emptyList()
    catalog.isTmdbDiscover -> buildList {
        add(DiscoverFilterKey.SORT)
        add(DiscoverFilterKey.YEAR)
        add(DiscoverFilterKey.RATING)
        add(DiscoverFilterKey.RUNTIME)
        add(DiscoverFilterKey.LANGUAGE)
        add(DiscoverFilterKey.COUNTRY)
        add(DiscoverFilterKey.PROVIDER)
        if (isTvLikeDiscoverType(catalog.type)) add(DiscoverFilterKey.NETWORK)
    }
    catalog.years.isNotEmpty() -> listOf(DiscoverFilterKey.YEAR)
    else -> emptyList()
}

data class DiscoverTmdbPage(val items: List<MetaPreview>, val hasMore: Boolean)

/** TMDB-backed Discover source: the only one that supports the full CinemaOS filter set. */
@Singleton
class DiscoverTmdbSource @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore
) {
    private val genreCache = ConcurrentHashMap<String, List<DiscoverFilterOption>>()

    val isAvailable: Boolean
        get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    private suspend fun language(): String =
        runCatching { tmdbSettingsDataStore.settings.first().language }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "en"

    suspend fun genres(type: String): List<DiscoverFilterOption> {
        val all = loadGenres(isTvLikeDiscoverType(type))
        // Anime is always Animation, so offering it as a genre would be a no-op.
        return if (type == DISCOVER_TYPE_ANIME) all.filter { it.value != TMDB_GENRE_ANIMATION } else all
    }

    private suspend fun loadGenres(isTv: Boolean): List<DiscoverFilterOption> = withContext(Dispatchers.IO) {
        val language = language()
        val cacheKey = "${if (isTv) DISCOVER_TYPE_SERIES else DISCOVER_TYPE_MOVIE}|$language"
        genreCache[cacheKey]?.let { return@withContext it }
        val response = runCatching {
            if (isTv) {
                tmdbApi.getTvGenres(BuildConfig.TMDB_API_KEY, language)
            } else {
                tmdbApi.getMovieGenres(BuildConfig.TMDB_API_KEY, language)
            }
        }.getOrNull()
        val genres = response?.body()?.genres.orEmpty()
            .map { DiscoverFilterOption(label = it.name, value = it.id.toString()) }
        if (genres.isNotEmpty()) genreCache[cacheKey] = genres
        genres
    }

    suspend fun discover(type: String, filters: DiscoverTmdbFilters, page: Int): DiscoverTmdbPage =
        withContext(Dispatchers.IO) {
            val language = language()
            val isAnime = type == DISCOVER_TYPE_ANIME
            val isSeries = isTvLikeDiscoverType(type)
            val dateField = if (isSeries) "first_air_date" else "primary_release_date"
            val today = LocalDate.now()

            var dateGte: String? = null
            var dateLte: String? = null
            var year: Int? = null
            filters.year?.let { raw ->
                if (raw.startsWith(DISCOVER_DECADE_PREFIX)) {
                    raw.removePrefix(DISCOVER_DECADE_PREFIX).toIntOrNull()?.let { start ->
                        dateGte = "$start-01-01"
                        dateLte = "${start + 9}-12-31"
                    }
                } else {
                    year = raw.toIntOrNull()
                }
            }
            // A year filter already pins the dates; only add the sort's own window without one.
            if (filters.year == null) {
                when (filters.sort) {
                    DiscoverSort.UPCOMING -> dateGte = today.plusDays(1).toString()
                    DiscoverSort.NOW_PLAYING -> {
                        dateGte = today.minusDays(60).toString()
                        dateLte = today.toString()
                    }
                    DiscoverSort.NEWEST -> dateLte = today.toString()
                }
            }
            val sortBy = when (filters.sort) {
                DiscoverSort.NEWEST -> "$dateField.desc"
                DiscoverSort.OLDEST -> "$dateField.asc"
                DiscoverSort.TOP_RATED -> "vote_average.desc"
                DiscoverSort.MOST_VOTED -> "vote_count.desc"
                DiscoverSort.REVENUE -> if (isSeries) "popularity.desc" else "revenue.desc"
                DiscoverSort.UPCOMING -> "$dateField.asc"
                else -> "popularity.desc"
            }
            val voteAverageGte = filters.rating?.toDoubleOrNull()
            var voteCountGte: Int? = if (voteAverageGte != null) 50 else null
            if (filters.sort == DiscoverSort.TOP_RATED) {
                // Keeps single-vote 10.0 titles from topping the list.
                voteCountGte = maxOf(voteCountGte ?: 0, 300)
            }
            val (runtimeGte, runtimeLte) = parseRuntime(filters.runtime)
            val provider = filters.provider
            val watchRegion = provider?.let {
                filters.country ?: Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
            }
            val monetization = provider?.let { "flatrate" }

            // Anime = Animation genre + Japanese original language; a picked genre is ANDed in
            // ("16,35") and a picked language replaces the Japanese default.
            val withGenres = if (isAnime) {
                listOfNotNull(TMDB_GENRE_ANIMATION, filters.genre).joinToString(",")
            } else {
                filters.genre
            }
            val originalLanguage = filters.language ?: ANIME_ORIGINAL_LANGUAGE.takeIf { isAnime }

            val response = if (isSeries) {
                tmdbApi.discoverTv(
                    apiKey = BuildConfig.TMDB_API_KEY,
                    language = language,
                    page = page,
                    sortBy = sortBy,
                    withNetworks = filters.network,
                    firstAirDateLte = dateLte,
                    voteCountGte = voteCountGte,
                    withGenres = withGenres,
                    firstAirDateGte = dateGte,
                    voteAverageGte = voteAverageGte,
                    withOriginalLanguage = originalLanguage,
                    withOriginCountry = filters.country,
                    firstAirDateYear = year,
                    watchRegion = watchRegion,
                    withWatchProviders = provider,
                    withWatchMonetizationTypes = monetization,
                    withRuntimeGte = runtimeGte,
                    withRuntimeLte = runtimeLte,
                    includeAdult = false
                )
            } else {
                tmdbApi.discoverMovies(
                    apiKey = BuildConfig.TMDB_API_KEY,
                    language = language,
                    page = page,
                    sortBy = sortBy,
                    releaseDateLte = dateLte,
                    voteCountGte = voteCountGte,
                    withGenres = withGenres,
                    releaseDateGte = dateGte,
                    voteAverageGte = voteAverageGte,
                    withOriginalLanguage = originalLanguage,
                    withOriginCountry = filters.country,
                    year = year,
                    watchRegion = watchRegion,
                    withWatchProviders = provider,
                    withWatchMonetizationTypes = monetization,
                    withRuntimeGte = runtimeGte,
                    withRuntimeLte = runtimeLte,
                    includeAdult = false
                )
            }
            if (!response.isSuccessful) error("TMDB discover failed with HTTP ${response.code()}")
            val body = response.body() ?: error("TMDB discover returned no data")
            val items = body.results.orEmpty()
                .mapNotNull { it.toPreview(isSeries) }
                .distinctBy { it.id }
            val currentPage = body.page ?: page
            val totalPages = minOf(body.totalPages ?: currentPage, TMDB_MAX_PAGE)
            DiscoverTmdbPage(items = items, hasMore = items.isNotEmpty() && currentPage < totalPages)
        }

    private fun parseRuntime(raw: String?): Pair<Int?, Int?> {
        if (raw.isNullOrBlank()) return null to null
        val parts = raw.split('-')
        val gte = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 }
        val lte = parts.getOrNull(1)?.toIntOrNull()
        return gte to lte
    }

    private fun TmdbDiscoverResult.toPreview(isSeries: Boolean): MetaPreview? {
        val title = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: originalTitle?.takeIf { it.isNotBlank() }
            ?: originalName?.takeIf { it.isNotBlank() }
            ?: return null
        val contentType = if (isSeries) ContentType.SERIES else ContentType.MOVIE
        val date = (if (isSeries) firstAirDate else releaseDate)?.takeIf { it.isNotBlank() }
        return MetaPreview(
            id = "tmdb:$id",
            type = contentType,
            rawType = if (isSeries) "series" else "movie",
            name = title,
            poster = imageUrl(posterPath, "w500") ?: imageUrl(backdropPath, "w780"),
            posterShape = PosterShape.POSTER,
            background = imageUrl(backdropPath, "w1280"),
            logo = null,
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = date?.take(4),
            released = date,
            imdbRating = voteAverage?.toFloat()?.takeIf { it > 0f },
            voteCount = voteCount,
            genres = emptyList()
        )
    }

    private fun imageUrl(path: String?, size: String): String? {
        val clean = path?.takeIf { it.isNotBlank() } ?: return null
        return "https://image.tmdb.org/t/p/$size$clean"
    }

    private companion object {
        /** TMDB rejects discover pages above 500. */
        const val TMDB_MAX_PAGE = 500
    }
}
