package com.nuvio.tv.ui.screens.collection

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.core.poster.patternForScreen
import com.nuvio.tv.core.poster.withCustomPosterUrls
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.MetaPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the full-screen movie collection page opened from the details "collection" row. Loads the
 * TMDB collection through [TmdbMetadataService.fetchMovieCollection] (which wraps TMDB's
 * `collection/{id}` endpoint, cached per language) and lists every movie by release date.
 */
@HiltViewModel
class MovieCollectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbMetadataService: TmdbMetadataService,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val collectionId: Int = savedStateHandle.get<Int>("collectionId") ?: 0

    /** Name passed through the route, shown while loading and when TMDB returns none. */
    val routeName: String? = savedStateHandle.get<String>("collectionName")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow<MovieCollectionUiState>(MovieCollectionUiState.Loading)
    val uiState: StateFlow<MovieCollectionUiState> = _uiState.asStateFlow()

    private val _posterCardCornerRadiusDp = MutableStateFlow(12)
    val posterCardCornerRadiusDp: StateFlow<Int> = _posterCardCornerRadiusDp.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            layoutPreferenceDataStore.posterCardCornerRadiusDp.collect { _posterCardCornerRadiusDp.value = it }
        }
    }

    fun retry() {
        _uiState.value = MovieCollectionUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            if (collectionId <= 0) {
                _uiState.value = MovieCollectionUiState.Error(context.getString(R.string.cast_detail_error))
                return@launch
            }
            val language = tmdbSettingsDataStore.settings.first().language
            val result = runCatching {
                tmdbMetadataService.fetchMovieCollection(collectionId = collectionId, language = language)
            }.getOrNull()

            if (result == null || result.items.isEmpty()) {
                _uiState.value = MovieCollectionUiState.Error(context.getString(R.string.cast_detail_error))
                return@launch
            }

            val pattern = layoutPreferenceDataStore.customPosterUrlPattern.first()
            val enabledScreens = layoutPreferenceDataStore.customPosterEnabledScreens.first()
            val movies = result.items
                .sortedWithUndatedLast()
                .withCustomPosterUrls(patternForScreen(pattern, CustomPosterScreen.DETAILS, enabledScreens))

            _uiState.value = MovieCollectionUiState.Success(
                MovieCollectionData(
                    name = result.name?.takeIf { it.isNotBlank() }
                        ?: routeName
                        ?: context.getString(R.string.tmdb_collections_title),
                    overview = result.overview,
                    backdrop = result.backdrop ?: movies.firstNotNullOfOrNull { it.background },
                    poster = result.poster,
                    movies = movies
                )
            )
        }
    }
}

/** Oldest release first; movies without a date go last. The sort is stable, so ties keep TMDB order. */
private fun List<MetaPreview>.sortedWithUndatedLast(): List<MetaPreview> =
    sortedWith(
        compareBy<MetaPreview> { (it.released ?: it.releaseInfo).isNullOrBlank() }
            .thenBy { it.released ?: it.releaseInfo.orEmpty() }
    )
