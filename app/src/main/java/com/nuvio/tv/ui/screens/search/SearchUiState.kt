package com.nuvio.tv.ui.screens.search

import androidx.compose.runtime.Immutable
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.model.MetaPreview

internal const val MIN_SEARCH_QUERY_LENGTH = 2

internal fun submittedSearchQuery(rawQuery: String): String =
    rawQuery.trim().takeIf { it.length >= MIN_SEARCH_QUERY_LENGTH }.orEmpty()

internal fun shouldShowDiscoverInSearch(
    discoverLocation: DiscoverLocation,
    query: String,
    submittedQuery: String
): Boolean =
    // CinemaOS: Discover is its own tab; Search shows only search UI.
    false && discoverLocation == DiscoverLocation.IN_SEARCH &&
        query.trim().length < MIN_SEARCH_QUERY_LENGTH &&
        submittedQuery.isBlank()

@Immutable
data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val isSearching: Boolean = false,
    val error: String? = null,
    val catalogRows: List<CatalogRow> = emptyList(),
    val installedAddons: List<Addon> = emptyList(),
    val discoverLocation: DiscoverLocation = DiscoverLocation.IN_SEARCH,
    val discoverInitialized: Boolean = false,
    val discoverLoading: Boolean = false,
    val discoverLoadingMore: Boolean = false,
    val discoverCatalogs: List<DiscoverCatalog> = emptyList(),
    val selectedDiscoverType: String = "movie",
    val selectedDiscoverCatalogKey: String? = null,
    val selectedDiscoverGenre: String? = null,
    /** Year sent through a year-valued `genre` extra (Cinemeta "New"); see [DiscoverCatalog.years]. */
    val selectedDiscoverYear: String? = null,
    /** Filters for the TMDB-backed catalog ([DiscoverCatalog.isTmdbDiscover]). */
    val discoverTmdbFilters: DiscoverTmdbFilters = DiscoverTmdbFilters(),
    val discoverTmdbGenres: List<DiscoverFilterOption> = emptyList(),
    val discoverResults: List<MetaPreview> = emptyList(),
    val pendingDiscoverResults: List<MetaPreview> = emptyList(),
    val discoverHasMore: Boolean = true,
    val discoverPage: Int = 1,
    val posterLabelsEnabled: Boolean = true,
    val catalogAddonNameEnabled: Boolean = true,
    val catalogTypeSuffixEnabled: Boolean = true,
    val posterCardWidthDp: Int = 126,
    val posterCardHeightDp: Int = 189,
    val posterCardCornerRadiusDp: Int = 12,
    val recentSearches: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)

@Immutable
data class DiscoverCatalog(
    val key: String,
    val addonId: String,
    val addonName: String,
    val addonBaseUrl: String,
    val catalogId: String,
    val catalogName: String,
    val type: String,
    /** Real genre options of the catalog's `genre` extra. Never holds years. */
    val genres: List<String>,
    val supportsSkip: Boolean,
    val skipStep: Int,
    /**
     * Options of a `genre` extra that actually lists release years (Cinemeta's "New" catalog
     * sends the year through `genre`). Shown under a Year picker, not as genres.
     */
    val years: List<String> = emptyList(),
    /** The `genre` extra (genre or year) must be sent, so a value is always selected. */
    val genreExtraRequired: Boolean = false,
    /** Synthetic catalog served by TMDB discover, supporting every [DiscoverFilterKey]. */
    val isTmdbDiscover: Boolean = false
)
