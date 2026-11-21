package com.nuvio.tv.ui.screens.providers

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbDiscoverResult
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

enum class ProviderMediaTab { MOVIES, SERIES }

enum class ProviderSort { POPULAR, NEWEST, TOP_RATED }

@Immutable
data class ProviderUiState(
    val provider: StreamingProvider,
    val logoUrl: String? = null,
    val tab: ProviderMediaTab = ProviderMediaTab.MOVIES,
    val sort: ProviderSort = ProviderSort.POPULAR,
    val items: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: Boolean = false,
    /** Bumped on every fresh (page 1) load so the grid can scroll back to the top. */
    val generation: Int = 0
)

/**
 * Backs the Provider page: TMDB discover (`with_watch_providers` + `watch_region`) for one streaming
 * service, as movies or series, in the chosen order, paged 20 at a time.
 */
@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val logoRepository: ProviderLogoRepository,
    layoutPreferenceDataStore: LayoutPreferenceDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val providerId: String = savedStateHandle.get<String>("providerId").orEmpty()
    private val providerName: String = savedStateHandle.get<String>("providerName").orEmpty()

    private val _uiState = MutableStateFlow(
        ProviderUiState(
            provider = CinemaosStreamingProviders.byId(providerId)
                ?: StreamingProvider(providerId, providerName.ifBlank { providerId })
        )
    )
    val uiState: StateFlow<ProviderUiState> = _uiState.asStateFlow()

    val posterCardCornerRadiusDp: StateFlow<Int> = MutableStateFlow(12).also { flow ->
        viewModelScope.launch { layoutPreferenceDataStore.posterCardCornerRadiusDp.collect { flow.value = it } }
    }

    private var page = 0
    private var region: String = CinemaosStreamingProviders.watchRegion()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val logo = logoRepository.logos()[providerId]
            _uiState.update { it.copy(logoUrl = logo) }
        }
        reload()
    }

    fun selectTab(tab: ProviderMediaTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        reload()
    }

    fun selectSort(sort: ProviderSort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort) }
        reload()
    }

    fun retry() = reload()

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.error) return
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch {
            val result = runCatching { fetch(page + 1, state.tab, state.sort) }.getOrNull()
            if (result == null) {
                _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                return@launch
            }
            page += 1
            _uiState.update { current ->
                current.copy(
                    items = (current.items + result.first).distinctBy { it.id },
                    hasMore = result.second,
                    isLoadingMore = false
                )
            }
        }
    }

    private fun reload() {
        loadJob?.cancel()
        val state = _uiState.value
        _uiState.update {
            it.copy(isLoading = true, isLoadingMore = false, error = false, items = emptyList(), hasMore = false)
        }
        loadJob = viewModelScope.launch {
            var result = runCatching { fetch(1, state.tab, state.sort) }.getOrNull()
            // A service may not be offered in this region; fall back to the US catalogue.
            if (result != null && result.first.isEmpty() && region != FALLBACK_REGION) {
                region = FALLBACK_REGION
                result = runCatching { fetch(1, state.tab, state.sort) }.getOrNull()
            }
            page = 1
            _uiState.update { current ->
                if (result == null) {
                    current.copy(isLoading = false, error = true, generation = current.generation + 1)
                } else {
                    current.copy(
                        items = result.first,
                        hasMore = result.second,
                        isLoading = false,
                        error = false,
                        generation = current.generation + 1
                    )
                }
            }
        }
    }

    private suspend fun fetch(
        pageNumber: Int,
        tab: ProviderMediaTab,
        sort: ProviderSort
    ): Pair<List<MetaPreview>, Boolean> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        check(apiKey.isNotBlank()) { "TMDB key missing" }
        val language = runCatching { tmdbSettingsDataStore.settings.first().language }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "en"
        val isSeries = tab == ProviderMediaTab.SERIES
        val dateField = if (isSeries) "first_air_date" else "primary_release_date"
        val today = LocalDate.now().toString()
        val sortBy = when (sort) {
            ProviderSort.POPULAR -> "popularity.desc"
            ProviderSort.NEWEST -> "$dateField.desc"
            ProviderSort.TOP_RATED -> "vote_average.desc"
        }
        val voteCountGte = if (sort == ProviderSort.TOP_RATED) 200 else null
        val dateLte = if (sort == ProviderSort.NEWEST) today else null
        val response = if (isSeries) {
            tmdbApi.discoverTv(
                apiKey = apiKey,
                language = language,
                page = pageNumber,
                sortBy = sortBy,
                firstAirDateLte = dateLte,
                voteCountGte = voteCountGte,
                watchRegion = region,
                withWatchProviders = providerId,
                withWatchMonetizationTypes = MONETIZATION,
                includeAdult = false
            )
        } else {
            tmdbApi.discoverMovies(
                apiKey = apiKey,
                language = language,
                page = pageNumber,
                sortBy = sortBy,
                releaseDateLte = dateLte,
                voteCountGte = voteCountGte,
                watchRegion = region,
                withWatchProviders = providerId,
                withWatchMonetizationTypes = MONETIZATION,
                includeAdult = false
            )
        }
        if (!response.isSuccessful) error("TMDB discover HTTP ${response.code()}")
        val body = response.body() ?: error("TMDB discover empty")
        val items = body.results.orEmpty().mapNotNull { it.toPreview(isSeries) }.distinctBy { it.id }
        val current = body.page ?: pageNumber
        val total = minOf(body.totalPages ?: current, TMDB_MAX_PAGE)
        items to (items.isNotEmpty() && current < total)
    }

    private fun TmdbDiscoverResult.toPreview(isSeries: Boolean): MetaPreview? {
        val title = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: originalTitle?.takeIf { it.isNotBlank() }
            ?: originalName?.takeIf { it.isNotBlank() }
            ?: return null
        val date = (if (isSeries) firstAirDate else releaseDate)?.takeIf { it.isNotBlank() }
        return MetaPreview(
            id = "tmdb:$id",
            type = if (isSeries) ContentType.SERIES else ContentType.MOVIE,
            rawType = if (isSeries) "series" else "movie",
            name = title,
            poster = tmdbImage(posterPath, "w500") ?: tmdbImage(backdropPath, "w780"),
            posterShape = PosterShape.POSTER,
            background = tmdbImage(backdropPath, "w1280"),
            logo = null,
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = date?.take(4),
            released = date,
            imdbRating = null,
            tmdbRating = voteAverage?.toFloat()?.takeIf { it > 0f },
            voteCount = voteCount,
            genres = emptyList()
        )
    }

    private companion object {
        const val FALLBACK_REGION = "US"
        const val MONETIZATION = "flatrate|free|ads"
        const val TMDB_MAX_PAGE = 500
    }
}
