package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.domain.model.catalogRowLegacyKey
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.DiscoverSelectionDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.SearchHistoryDataStore
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.model.catalogRowStableKey
import com.nuvio.tv.domain.model.mergeCatalogPage
import com.nuvio.tv.domain.model.nextCatalogSkip
import com.nuvio.tv.domain.model.skipStep
import com.nuvio.tv.domain.model.stableKey
import com.nuvio.tv.domain.model.supportsExtra
import com.nuvio.tv.core.util.filterReleasedItems
import com.nuvio.tv.core.util.isUnreleased
import com.nuvio.tv.domain.repository.AddonRepository
import java.time.LocalDate
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL
import com.nuvio.tv.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val metaRepository: com.nuvio.tv.domain.repository.MetaRepository,
    private val discoverSelectionDataStore: DiscoverSelectionDataStore,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val searchHistoryDataStore: SearchHistoryDataStore,
    private val watchProgressRepository: com.nuvio.tv.domain.repository.WatchProgressRepository,
    private val watchedSeriesStateHolder: com.nuvio.tv.data.local.WatchedSeriesStateHolder,
    val posterOptions: com.nuvio.tv.ui.components.posteroptions.PosterOptionsController,
    private val serverCatalog: ServerCatalog,
    @ApplicationContext private val context: Context,
    // Last and defaulted so existing constructions keep compiling; Hilt always supplies it.
    private val discoverTmdbSource: DiscoverTmdbSource? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Saved focus state for restoring scroll/focus position after returning from details. */
    var savedFocusRowKey: String? = null
    var savedFocusItemIndex: Int = -1
    var savedRowScrollPositions: Map<String, Pair<Int, Int>> = emptyMap()
    var hasSavedSearchFocus: Boolean = false

    private val _watchedMovieIds = MutableStateFlow<Set<String>>(emptySet())
    val watchedMovieIds: StateFlow<Set<String>> = _watchedMovieIds.asStateFlow()
    val watchedSeriesIds: StateFlow<Set<String>> = watchedSeriesStateHolder.fullyWatchedSeriesIds

    private val catalogsMap = linkedMapOf<String, CatalogRow>()
    private val catalogOrder = mutableListOf<String>()

    private var activeSearchJobs: List<Job> = emptyList()

    /**
     * Load-more jobs, tracked separately from [activeSearchJobs] because that list is replaced
     * wholesale by each search run. Cancelled alongside it in [cancelSearchRun].
     */
    private val activeLoadMoreJobs: MutableSet<Job> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()
    private var searchRunJob: Job? = null
    private var activeSearchQuery: String? = null
    private var searchGeneration = 0L
    private var discoverJob: Job? = null
    private var tmdbGenresJob: Job? = null
    private var catalogRowsUpdateJob: Job? = null
    private var suggestionJob: Job? = null
    private var liveSearchJob: Job? = null
    private var lastRequestKey: String? = null
    private var lastCompletedRequestKey: String? = null
    private var hasRenderedFirstCatalog = false
    private var pendingCatalogResponses = 0
    private var lastNonEmptyQuery: String = ""
    private var revealBatchAfterNextDiscoverFetch = false
    private var hideUnreleasedContent = false

    private companion object {
        const val DISCOVER_INITIAL_LIMIT = 100
        const val DISCOVER_SHOW_MORE_BATCH = 50
        const val SUGGESTION_DEBOUNCE_MS = 150L

        /**
         * Live search fires while typing, but each run fans out to every enabled addon catalog, so
         * it waits longer than the suggestion debounce to avoid a request storm per keystroke.
         */
        const val LIVE_SEARCH_DEBOUNCE_MS = 350L

        const val MAX_SUGGESTIONS = 8
        /** Splits titles and queries into words. */
        private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
        const val MAX_RECENT_SEARCHES = 8
    }

    init {
        posterOptions.bind(viewModelScope)
        viewModelScope.launch {
            watchProgressRepository.observeWatchedMovieIds()
                .collect { ids -> _watchedMovieIds.value = ids }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.discoverLocation.distinctUntilChanged().collectLatest { location ->
                _uiState.update { it.copy(discoverLocation = location) }
                if (location == DiscoverLocation.OFF) {
                    discoverJob?.cancel()
                    discoverJob = null
                    revealBatchAfterNextDiscoverFetch = false
                    _uiState.update {
                        it.copy(
                            discoverInitialized = false,
                            discoverLoading = false,
                            discoverLoadingMore = false,
                            discoverCatalogs = emptyList(),
                            selectedDiscoverType = "movie",
                            selectedDiscoverCatalogKey = null,
                            selectedDiscoverGenre = null,
                            selectedDiscoverYear = null,
                            discoverTmdbFilters = DiscoverTmdbFilters(),
                            discoverTmdbGenres = emptyList(),
                            discoverResults = emptyList(),
                            pendingDiscoverResults = emptyList(),
                            discoverHasMore = true,
                            discoverPage = 1
                        )
                    }
                }
            }
        }
        // Combine all layout preference flows into a single collector to reduce coroutine overhead
        viewModelScope.launch {
            combine(
                layoutPreferenceDataStore.posterCardWidthDp,
                layoutPreferenceDataStore.posterLabelsEnabled,
                layoutPreferenceDataStore.catalogAddonNameEnabled,
                layoutPreferenceDataStore.posterCardHeightDp,
                layoutPreferenceDataStore.posterCardCornerRadiusDp
            ) { widthDp, labelsEnabled, addonNameEnabled, heightDp, cornerRadiusDp ->
                LayoutPrefs(widthDp, labelsEnabled, addonNameEnabled, heightDp, cornerRadiusDp)
            }.collectLatest { prefs ->
                _uiState.update {
                    it.copy(
                        posterCardWidthDp = prefs.widthDp,
                        posterLabelsEnabled = prefs.labelsEnabled,
                        catalogAddonNameEnabled = prefs.addonNameEnabled,
                        posterCardHeightDp = prefs.heightDp,
                        posterCardCornerRadiusDp = prefs.cornerRadiusDp
                    )
                }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.catalogTypeSuffixEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(catalogTypeSuffixEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.hideUnreleasedContent.collectLatest { enabled ->
                hideUnreleasedContent = enabled
                scheduleCatalogRowsUpdate()
            }
        }
        viewModelScope.launch {
            searchHistoryDataStore.recentSearches.collectLatest { recent ->
                _uiState.update { it.copy(recentSearches = recent.take(MAX_RECENT_SEARCHES)) }
            }
        }
    }

    private data class LayoutPrefs(
        val widthDp: Int,
        val labelsEnabled: Boolean,
        val addonNameEnabled: Boolean,
        val heightDp: Int,
        val cornerRadiusDp: Int
    )

    fun ensureDiscoverLoaded() {
        val state = _uiState.value
        if (state.discoverLocation == DiscoverLocation.OFF) return
        if (state.discoverInitialized || state.discoverLoading) return
        viewModelScope.launch { loadDiscoverCatalogs() }
    }

    private val metaPrefetchedIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private var metaPrefetchJob: Job? = null

    /**
     * Prefetch meta from addons in background when an item receives focus.
     * Warms the MetaRepository cache so the detail screen loads instantly.
     * Debounced to avoid flooding the network during rapid scrolling.
     */
    fun prefetchMetaOnFocus(id: String, type: String) {
        if (id.isBlank() || id in metaPrefetchedIds) return
        metaPrefetchJob?.cancel()
        metaPrefetchJob = viewModelScope.launch {
            delay(150)
            if (id in metaPrefetchedIds) return@launch
            metaPrefetchedIds.add(id)
            metaRepository.getMetaFromAllAddons(type = type, id = id)
                .first { it !is com.nuvio.tv.core.network.NetworkResult.Loading }
            watchProgressRepository.getAllEpisodeProgress(id.substringBefore(":")).first()
        }
    }

    /**
     * Returns the cached backdrop URL from a previously prefetched meta, or null.
     */
    fun getCachedBackdrop(id: String, type: String): String? {
        return metaRepository.getCachedMeta(type, id)?.backdropUrl
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> onQueryChanged(event.query)
            SearchEvent.SubmitSearch -> submitSearch()
            SearchEvent.RememberSearchFromTextInput -> rememberSearchFromTextInput()
            SearchEvent.ClearRecentSearches -> clearRecentSearches()
            is SearchEvent.RemoveRecentSearch -> removeRecentSearch(event.query)
            is SearchEvent.LoadMoreCatalog -> loadMoreCatalogItems(
                catalogId = event.catalogId,
                addonId = event.addonId,
                type = event.type
            )
            is SearchEvent.SelectDiscoverType -> selectDiscoverType(event.type)
            is SearchEvent.SelectDiscoverCatalog -> selectDiscoverCatalog(event.catalogKey)
            is SearchEvent.SelectDiscoverGenre -> selectDiscoverGenre(event.genre)
            is SearchEvent.SelectDiscoverFilter -> selectDiscoverFilter(event.key, event.value)
            SearchEvent.ResetDiscoverFilters -> resetDiscoverFilters()
            SearchEvent.LoadNextDiscoverResults -> loadNextDiscoverResults()
            SearchEvent.Retry -> {
                // An explicit retry must refetch even though nothing about the request changed.
                lastRequestKey = null
                lastCompletedRequestKey = null
                cancelSearchRun()
                performSearch(uiState.value.submittedQuery.ifBlank { uiState.value.query })
            }
        }
    }

    private fun onQueryChanged(query: String) {
        // Skip if query is exactly the same (no change at all)
        if (query == _uiState.value.query) {
            return
        }
        
        // Detect if this is a recomposition artifact: transitioning to empty from the current
        // non-empty query. This happens when focus changes and Compose recomposes the text field.
        val isRecompositionArtifact = query.isEmpty() && query != _uiState.value.query && _uiState.value.query == lastNonEmptyQuery
        
        // Track non-empty queries for recomposition detection
        if (query.isNotEmpty()) {
            lastNonEmptyQuery = query
        }

        _uiState.update {
            val trimmedInput = query.trim()
            // Narrow the strip to what still matches before anything is fetched, so a letter
            // that rules a title out drops it on that keystroke rather than a fetch later.
            // Narrowing does not clear the strip when every current title stops matching. The
            // fetch stays authoritative for an empty result.
            val narrowed = rankedSuggestions(it.suggestions, trimmedInput.lowercase())
            it.copy(
                query = query,
                suggestions = if (narrowed.isEmpty()) it.suggestions else narrowed,
                error = null,
                isSearching = false,
                // Keep whatever is on screen while a keystroke waits to run. Clearing here flashed
                // the no-results state on every letter, because on a remote each letter outlasts the
                // debounce. The screen renders skeleton rows for this window instead.
                // For recomposition artifacts (empty query during focus change), preserve catalogRows.
                catalogRows = when {
                    isRecompositionArtifact -> it.catalogRows
                    trimmedInput.length < MIN_SEARCH_QUERY_LENGTH -> emptyList()
                    else -> it.catalogRows
                }
            )
        }

        // Skip fetches for recomposition artifacts to avoid unnecessary network calls
        if (isRecompositionArtifact) {
            return
        }

        // Drop in-flight requests for the previous keystroke before scheduling the next run.
        cancelSearchRun()

        // Live search: results follow what you type, like mobile. Debounced because each run hits
        // every enabled addon catalog.
        liveSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length >= MIN_SEARCH_QUERY_LENGTH) {
            liveSearchJob = viewModelScope.launch {
                kotlinx.coroutines.delay(LIVE_SEARCH_DEBOUNCE_MS)
                performSearch(query, keepSuggestions = true)
            }
        } else {
            // Emptying the field has to retire the submitted query too. Leaving it set kept the
            // screen in its results state with nothing to show, instead of falling back to recent
            // searches, until the screen was rebuilt by navigating away and back.
            performSearch(query)
        }

        fetchSuggestions(trimmed)
    }

    /** Match rank for [title], lower being better, or null when it does not match [queryLower]. */
    private fun suggestionRank(title: String, queryLower: String): Int? {
        val titleLower = title.lowercase()
        if (titleLower == queryLower) return 0
        if (titleLower.startsWith(queryLower)) return 1
        if (titleLower.contains(queryLower)) return 2

        // Allow multi-word matches such as "wolf wall" -> "The Wolf of Wall Street".
        // Each query word must consume a different title word.
        val queryWords = queryLower.split(WORD_SEPARATOR).filter { it.isNotEmpty() }
        if (queryWords.size < 2) return null
        val unmatchedTitleWords = titleLower.split(WORD_SEPARATOR).filterTo(mutableListOf()) { it.isNotEmpty() }
        val everyWordMatches = queryWords.all { word ->
            val index = unmatchedTitleWords.indexOfFirst { it.startsWith(word) }
            if (index >= 0) unmatchedTitleWords.removeAt(index)
            index >= 0
        }
        return if (everyWordMatches) 3 else null
    }

    /**
     * The strip contents for [names], best match first, capped at [MAX_SUGGESTIONS].
     *
     * Titles of the same rank keep the order of [names], which is the addon's own relevance order.
     * Sorting them alphabetically put five titles ahead of "Jurassic Park" for "juras", and of
     * "Slow Horses" for "slo", past the few completions a TV keyboard shows.
     */
    private fun rankedSuggestions(names: Collection<String>, queryLower: String): List<String> =
        names
            .mapNotNull { name -> suggestionRank(name, queryLower)?.let { name to it } }
            .sortedBy { it.second }
            .map { it.first }
            .take(MAX_SUGGESTIONS)

    /**
     * Every catalog's titles, taken one position at a time: each catalog's first title, then each
     * one's second, and so on, in catalog order. Each catalog's best matches lead, and the result
     * does not depend on which catalog happened to answer first.
     */
    private fun mergedCatalogNames(namesByCatalog: Map<Int, List<String>>, catalogCount: Int): List<String> {
        val catalogs = (0 until catalogCount).mapNotNull { namesByCatalog[it] }
        val merged = LinkedHashSet<String>()
        for (position in 0 until (catalogs.maxOfOrNull { it.size } ?: 0)) {
            catalogs.forEach { names -> names.getOrNull(position)?.let(merged::add) }
        }
        return merged.toList()
    }

    private fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()

        if (query.length < MIN_SEARCH_QUERY_LENGTH) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }

        // Already searched, so a fetch would repeat itself and the strip is current. Leave it
        // standing: live search submits as the user types, so typing a space between words
        // trims back to the submitted query and lands here mid-query.
        if (query == _uiState.value.submittedQuery.trim() && _uiState.value.catalogRows.isNotEmpty()) {
            return
        }

        suggestionJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SUGGESTION_DEBOUNCE_MS)

            val addons = try {
                searchAddons()
            } catch (_: Exception) {
                return@launch
            }

            val allTargets = buildSearchTargets(addons)
            val firstAddonId = allTargets.firstOrNull()?.first?.id
            val searchTargets = if (firstAddonId != null) allTargets.filter { it.first.id == firstAddonId } else emptyList()
            if (searchTargets.isEmpty()) {
                _uiState.update { it.copy(suggestions = emptyList()) }
                return@launch
            }

            // Each catalog's titles in the order it returned them, keyed by its place in searchTargets.
            val namesByCatalog = java.util.concurrent.ConcurrentHashMap<Int, List<String>>()
            val queryLower = query.lowercase()
            val suggestionJobs = searchTargets.mapIndexed { catalogIndex, (addon, catalog) ->
                launch {
                    try {
                        catalogRepository.getCatalog(
                            addonBaseUrl = addon.baseUrl,
                            addonId = addon.id,
                            addonName = addon.displayName,
                            catalogId = catalog.id,
                            catalogName = catalog.name,
                            type = catalog.apiType,
                            skip = 0,
                            skipStep = 100,
                            extraArgs = mapOf("search" to query),
                            supportsSkip = false,
                            posterScreen = com.nuvio.tv.core.poster.CustomPosterScreen.SEARCH
                        ).collect { result ->
                            if (result is NetworkResult.Success && _uiState.value.query.trim() == query) {
                                val names = result.data.items.map { it.name }
                                val previous = namesByCatalog.put(catalogIndex, names)
                                // Catalog results arrive independently and are merged, so a batch
                                // whose titles all fail the filter ranks to nothing while the batch
                                // holding the match is still in flight. Only the settle below may
                                // empty the strip: an empty push tells the keyboard there are no
                                // completions, and it does not always take them back when the next
                                // batch lands.
                                if (names != previous) {
                                    val ranked = rankedSuggestions(
                                        mergedCatalogNames(namesByCatalog, searchTargets.size),
                                        queryLower
                                    )
                                    if (ranked.isNotEmpty()) {
                                        _uiState.update { it.copy(suggestions = ranked) }
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        // The settle below treats joinAll() as "collection is over".
                        throw e
                    } catch (_: Exception) {
                        // Ignore per-catalog errors for suggestions
                    }
                }
            }

            suggestionJobs.joinAll()

            // Every catalog job has completed, so this is the first point the query is known
            // to have no suggestions. Until here the strip keeps the previous query's titles
            // rather than blinking on every keystroke. It must be cleared here or it would go
            // on captioning text the field no longer contains.
            if (_uiState.value.query.trim() == query) {
                val ranked = rankedSuggestions(mergedCatalogNames(namesByCatalog, searchTargets.size), queryLower)
                _uiState.update { it.copy(suggestions = ranked) }
            }
        }
    }

    private fun submitSearch() {
        // An explicit submit just skips the remaining debounce; the live run would land anyway.
        liveSearchJob?.cancel()
        performSearch(_uiState.value.query, rememberToHistory = true)
    }

    /**
     * Moving from the text input into the results confirms the current query the same way
     * Done/submit does. Live search may already have results on screen without an explicit
     * submit; saving here
     * avoids losing useful history while still not recording every keystroke prefix.
     */
    private fun rememberSearchFromTextInput() {
        val state = _uiState.value
        val query = state.submittedQuery.trim().ifBlank { state.query.trim() }
        if (query.length < MIN_SEARCH_QUERY_LENGTH) return
        val hasRealResults = state.catalogRows.any { row ->
            row.items.any { item -> !item.id.startsWith("__placeholder_") }
        } || catalogsMap.values.any { row ->
            row.items.any { item -> !item.id.startsWith("__placeholder_") }
        }
        if (!hasRealResults) return
        viewModelScope.launch {
            searchHistoryDataStore.saveRecentSearch(query, MAX_RECENT_SEARCHES)
        }
    }

    private fun clearRecentSearches() {
        viewModelScope.launch {
            searchHistoryDataStore.clearRecentSearches()
        }
    }

    private fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            searchHistoryDataStore.removeRecentSearch(query)
        }
    }

    private fun resetCatalogAccumulator() {
        catalogsMap.clear()
        catalogOrder.clear()
        hasRenderedFirstCatalog = false
        pendingCatalogResponses = 0
    }

    private fun cancelSearchRun() {
        searchGeneration++
        searchRunJob?.cancel()
        searchRunJob = null
        activeSearchJobs.forEach { it.cancel() }
        activeSearchJobs = emptyList()
        activeLoadMoreJobs.forEach { it.cancel() }
        activeLoadMoreJobs.clear()
        activeSearchQuery = null
    }

    /**
     * Identifies a search by everything that changes what it would return: the query, the released
     * filter, and the exact set of catalogs it would hit. Enabling an addon or flipping the filter
     * changes the key, so those still refetch.
     */
    private fun buildRequestKey(
        query: String,
        searchTargets: List<Pair<Addon, CatalogDescriptor>>
    ): String = buildString {
        append(query.lowercase())
        append('|')
        append(hideUnreleasedContent)
        append('|')
        append(
            searchTargets.joinToString(separator = "|") { (addon, catalog) ->
                "${addon.baseUrl}:${catalog.apiType}:${catalog.id}"
            }
        )
    }


    /**
     * @param keepSuggestions live search runs this on every keystroke, while the field is still
     * being typed into and the suggestion strip is the whole point. Those runs leave the strip
     * alone. A submit or a retry replaces the screen with results, which retires it.
     */
    private fun performSearch(
        rawQuery: String,
        rememberToHistory: Boolean = false,
        keepSuggestions: Boolean = false
    ) {
        val query = rawQuery.trim()
        if (!keepSuggestions) {
            suggestionJob?.cancel()
        }
        _uiState.update {
            it.copy(
                submittedQuery = submittedSearchQuery(query),
                query = rawQuery,
                suggestions = if (keepSuggestions) it.suggestions else emptyList()
            )
        }

        if (query.length < MIN_SEARCH_QUERY_LENGTH) {
            cancelSearchRun()
            catalogRowsUpdateJob?.cancel()
            resetCatalogAccumulator()
            lastRequestKey = null
            lastCompletedRequestKey = null
            _uiState.update {
                it.copy(
                    isSearching = false,
                    error = null,
                    catalogRows = emptyList()
                )
            }
            ensureDiscoverLoaded()
            return
        }

        // Submit can immediately follow the debounced live-search launch. Reuse an active run for
        // the same query, but cancel a different query's entire scope before starting this one.
        if (activeSearchQuery == query && searchRunJob?.isActive == true) return
        cancelSearchRun()
        val generation = searchGeneration
        activeSearchQuery = query

        val job = viewModelScope.launch {
            val addons = try {
                searchAddons()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == searchGeneration && activeSearchQuery == query) {
                    _uiState.update { it.copy(isSearching = false, error = e.message ?: context.getString(com.nuvio.tv.R.string.search_error_load_addons_failed)) }
                }
                return@launch
            }

            if (generation != searchGeneration || activeSearchQuery != query) return@launch

            val searchTargets = buildSearchTargets(addons)

            // Same query against the same catalogs, and that run either finished or is still
            // arriving, so there is nothing new to fetch. Without this, pressing Done after live
            // search had already run the query tore the rows down and refetched everything, and
            // deleting a letter then retyping it did the same. A run that was cancelled part way
            // is deliberately not counted, so it gets to finish rather than staying half filled.
            val requestKey = buildRequestKey(query, searchTargets)
            val alreadySatisfied = requestKey == lastRequestKey &&
                (requestKey == lastCompletedRequestKey || activeSearchJobs.any { it.isActive })
            if (alreadySatisfied) {
                // An explicit submit that lands on a query the live search already finished still
                // counts as a search the user confirmed, so remember it instead of skipping out
                // of the history save below.
                if (rememberToHistory && catalogsMap.values.any { row -> row.items.isNotEmpty() }) {
                    viewModelScope.launch {
                        searchHistoryDataStore.saveRecentSearch(query, MAX_RECENT_SEARCHES)
                    }
                }
                return@launch
            }
            lastRequestKey = requestKey

            // Committed to a new run: drop the previous query's work and accumulated rows.
            activeSearchJobs.forEach { it.cancel() }
            activeSearchJobs = emptyList()
            catalogRowsUpdateJob?.cancel()
            resetCatalogAccumulator()

            // Rows are left alone here. Clearing them produced an empty frame between the old
            // results and the placeholders below, which is the flash this screen used to show.
            _uiState.update { it.copy(isSearching = true, error = null, installedAddons = addons) }

            if (searchTargets.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = context.getString(R.string.search_error_no_catalogs),
                        catalogRows = emptyList()
                    )
                }
                return@launch
            }

            // Preserve addon manifest order.
            searchTargets.forEach { (addon, catalog) ->
                val key = catalogKey(
                    addonId = addon.id,
                    addonBaseUrl = addon.baseUrl,
                    type = catalog.apiType,
                    catalogId = catalog.id
                )
                if (key !in catalogOrder) {
                    catalogOrder.add(key)
                }
            }

            // Emit placeholder rows with shimmer items so the UI shows
            // skeleton rows immediately instead of a spinner.
            val placeholderRows = searchTargets.map { (addon, catalog) ->
                val key = catalogKey(
                    addonId = addon.id,
                    addonBaseUrl = addon.baseUrl,
                    type = catalog.apiType,
                    catalogId = catalog.id
                )
                val fakeItems = (0 until 8).map { i ->
                    MetaPreview(
                        id = "__placeholder_${key}_$i",
                        type = ContentType.fromString(catalog.apiType),
                        rawType = catalog.apiType,
                        name = " ",
                        poster = PLACEHOLDER_IMAGE_URL,
                        posterShape = PosterShape.POSTER,
                        background = null,
                        logo = null,
                        description = null,
                        releaseInfo = " ",
                        imdbRating = null,
                        genres = emptyList()
                    )
                }
                CatalogRow(
                    addonId = addon.id,
                    addonName = addon.displayName,
                    addonBaseUrl = addon.baseUrl,
                    catalogId = catalog.id,
                    catalogName = catalog.name,
                    type = ContentType.fromString(catalog.apiType),
                    rawType = catalog.apiType,
                    items = fakeItems,
                    isLoading = true,
                    hasMore = false,
                    currentPage = 0,
                    supportsSkip = false,
                    skipStep = 0,
                    extraArgs = emptyMap()
                )
            }
            // Only shimmer when there is nothing real to look at. If the previous query's results
            // are still up, they stay until this query's results replace them, so refining a search
            // is a single swap rather than results -> shimmer -> results on every letter.
            _uiState.update { state ->
                val showingRealRows = state.catalogRows.any { row ->
                    row.items.firstOrNull()?.id?.startsWith("__placeholder_") != true
                }
                if (showingRealRows) state else state.copy(catalogRows = placeholderRows)
            }

            val jobs = searchTargets.map { (addon, catalog) ->
                launch {
                    loadCatalog(addon, catalog, query, generation)
                }
            }
            pendingCatalogResponses = jobs.size
            activeSearchJobs = jobs

            // Wait for all jobs to complete so we can stop showing the global loading state.
            try {
                jobs.joinAll()
            } finally {
                if (
                    generation == searchGeneration &&
                    activeSearchQuery == query &&
                    uiState.value.submittedQuery.trim() == query
                ) {
                    lastCompletedRequestKey = requestKey
                    _uiState.update { it.copy(isSearching = false) }
                    // Only explicit submit (or moving into results from the text input — see
                    // rememberSearchFromTextInput)
                    // writes history. Live search fires per keystroke and would otherwise record
                    // every prefix ("do", "dog") even when the user never confirmed the search.
                    if (rememberToHistory && catalogsMap.values.any { row -> row.items.isNotEmpty() }) {
                        viewModelScope.launch {
                            searchHistoryDataStore.saveRecentSearch(query, MAX_RECENT_SEARCHES)
                        }
                    }
                }
            }
        }
        searchRunJob = job
    }

    private suspend fun loadCatalog(
        addon: Addon,
        catalog: CatalogDescriptor,
        query: String,
        generation: Long
    ) {
        val supportsSkip = catalog.supportsExtra("skip")
        val skipStep = catalog.skipStep()
        catalogRepository.getCatalog(
            addonBaseUrl = addon.baseUrl,
            addonId = addon.id,
            addonName = addon.displayName,
            catalogId = catalog.id,
            catalogName = catalog.name,
            type = catalog.apiType,
            skip = 0,
            skipStep = skipStep,
            extraArgs = mapOf("search" to query),
            supportsSkip = supportsSkip,
            posterScreen = com.nuvio.tv.core.poster.CustomPosterScreen.SEARCH
        ).collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    if (!isCurrentSearch(generation, query)) return@collect
                    val key = catalogKey(
                        addonId = addon.id,
                        addonBaseUrl = addon.baseUrl,
                        type = catalog.apiType,
                        catalogId = catalog.id
                    )
                    catalogsMap[key] = result.data
                    pendingCatalogResponses = (pendingCatalogResponses - 1).coerceAtLeast(0)
                    scheduleCatalogRowsUpdate()
                }
                is NetworkResult.Error -> {
                    if (!isCurrentSearch(generation, query)) return@collect
                    pendingCatalogResponses = (pendingCatalogResponses - 1).coerceAtLeast(0)
                    // Ignore per-catalog errors unless we have nothing to show.
                    if (catalogsMap.isEmpty()) {
                        _uiState.update { it.copy(error = result.message ?: context.getString(com.nuvio.tv.R.string.search_error_failed)) }
                    }
                    scheduleCatalogRowsUpdate()
                }
                NetworkResult.Loading -> {
                    // No-op; screen shows global loading when empty.
                }
            }
        }
    }

    private fun isCurrentSearch(generation: Long, query: String): Boolean =
        generation == searchGeneration && uiState.value.submittedQuery.trim() == query

    /**
     * Pages one row of the current search. The page belongs to the search run that created it:
     * it is keyed on [SearchUiState.submittedQuery] rather than the live field, and every
     * assignment back into [catalogsMap] is gated on that run still being current, so a late page
     * cannot merge into a different query's rows.
     */
    private fun loadMoreCatalogItems(catalogId: String, addonId: String, type: String) {
        val (key, currentRow) = catalogsMap.entries.firstOrNull { (_, row) ->
            row.addonId == addonId && row.apiType == type && row.catalogId == catalogId
        }?.let { it.key to it.value } ?: return

        if (currentRow.isLoading || !currentRow.hasMore) {
            return
        }

        val generation = searchGeneration
        val query = uiState.value.submittedQuery.trim()
        if (query.isBlank()) {
            return
        }

        catalogsMap[key] = currentRow.copy(isLoading = true)
        scheduleCatalogRowsUpdate()

        val job = viewModelScope.launch {
            val addon = uiState.value.installedAddons.find { it.id == addonId && it.baseUrl == currentRow.addonBaseUrl }
                ?: uiState.value.installedAddons.find { it.id == addonId } ?: run {
                clearRowLoading(key, generation, query)
                return@launch
            }

            val nextSkip = currentRow.nextCatalogSkip()
            catalogRepository.getCatalog(
                addonBaseUrl = addon.baseUrl,
                addonId = addon.id,
                addonName = addon.displayName,
                catalogId = catalogId,
                catalogName = currentRow.catalogName,
                type = currentRow.apiType,
                skip = nextSkip,
                skipStep = currentRow.skipStep,
                extraArgs = mapOf("search" to query),
                supportsSkip = currentRow.supportsSkip,
                posterScreen = com.nuvio.tv.core.poster.CustomPosterScreen.SEARCH
            ).collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        if (!isCurrentSearch(generation, query)) return@collect
                        val latestRow = catalogsMap[key] ?: return@collect
                        catalogsMap[key] = latestRow.mergeCatalogPage(result.data)
                        scheduleCatalogRowsUpdate()
                    }
                    is NetworkResult.Error -> {
                        clearRowLoading(key, generation, query)
                    }
                    NetworkResult.Loading -> Unit
                }
            }
        }
        activeLoadMoreJobs.add(job)
        job.invokeOnCompletion { activeLoadMoreJobs.remove(job) }
    }

    /** Drops the loading flag for a row, but only while its search run is still current. */
    private fun clearRowLoading(key: String, generation: Long, query: String) {
        if (!isCurrentSearch(generation, query)) return
        val row = catalogsMap[key] ?: return
        if (!row.isLoading) return
        catalogsMap[key] = row.copy(isLoading = false)
        scheduleCatalogRowsUpdate()
    }

    private fun scheduleCatalogRowsUpdate() {
        catalogRowsUpdateJob?.cancel()
        catalogRowsUpdateJob = viewModelScope.launch {
            if (!hasRenderedFirstCatalog && catalogsMap.isNotEmpty()) {
                hasRenderedFirstCatalog = true
                updateCatalogRowsNow()
                return@launch
            }
            val debounceMs = when {
                pendingCatalogResponses > 5 -> 220L
                pendingCatalogResponses > 0 -> 140L
                else -> 90L
            }
            kotlinx.coroutines.delay(debounceMs)
            updateCatalogRowsNow()
        }
    }

    private fun updateCatalogRowsNow() {
        _uiState.update { state ->
            val orderedRows = catalogOrder.map { key ->
                catalogsMap[key]
                    ?: state.catalogRows.find {
                        it.stableKey() == key
                    }
            }.filterNotNull().filter { row ->
                // Keep placeholder rows (shimmer) and rows with real items.
                // Drop rows that came back empty from the API.
                val isPlaceholder = row.isLoading &&
                    row.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
                isPlaceholder || row.items.isNotEmpty()
            }
            val filteredRows = if (hideUnreleasedContent) {
                val today = LocalDate.now()
                orderedRows.map { row ->
                    if (row.isLoading && row.items.firstOrNull()?.id?.startsWith("__placeholder_") == true) {
                        row
                    } else {
                        row.filterReleasedItems(today)
                    }
                }
            } else {
                orderedRows
            }
            state.copy(
                catalogRows = filteredRows
            )
        }
    }

    private suspend fun searchAddons(): List<Addon> =
        addonRepository.getInstalledAddons().first().enabledAddons() + serverCatalog.searchAddons.first()

    private suspend fun loadDiscoverCatalogs() {
        if (_uiState.value.discoverLocation == DiscoverLocation.OFF) return
        _uiState.update { it.copy(discoverLoading = true) }
        val addons = try {
            searchAddons()
        } catch (_: Exception) {
            _uiState.update { it.copy(discoverInitialized = true, discoverLoading = false) }
            return
        }

        val discoverCatalogs = addons.flatMap { addon ->
            addon.catalogs
                .filter { catalog ->
                    !(catalog.supportsExtra("search") &&
                        catalog.extra.any { it.name.equals("search", ignoreCase = true) && it.isRequired })
                }
                .map { catalog ->
                    val genreExtra = catalog.extra
                        .firstOrNull { it.name.equals("genre", ignoreCase = true) }
                    val genreOptions = genreExtra?.options.orEmpty()
                    // Cinemeta's "New" catalog sends the release year through `genre`; those
                    // options are years and must never be listed as genres.
                    val optionsAreYears = genreOptionsAreYears(genreOptions)
                    DiscoverCatalog(
                        key = catalogRowLegacyKey(addon.id, catalog.apiType, catalog.id),
                        addonId = addon.id,
                        addonName = addon.displayName,
                        addonBaseUrl = addon.baseUrl,
                        catalogId = catalog.id,
                        catalogName = catalog.name,
                        type = catalog.apiType,
                        genres = if (optionsAreYears) emptyList() else genreOptions,
                        supportsSkip = catalog.supportsExtra("skip"),
                        skipStep = catalog.skipStep(),
                        years = if (optionsAreYears) genreOptions else emptyList(),
                        genreExtraRequired = genreExtra?.isRequired == true && genreOptions.isNotEmpty()
                    )
                }
        }.let { addonCatalogs ->
            val tmdbCatalogs = if (discoverTmdbSource?.isAvailable == true) {
                tmdbDiscoverCatalogs(context.getString(R.string.discover_tmdb_catalog_name))
            } else {
                emptyList()
            }
            tmdbCatalogs + addonCatalogs
        }

        val selectedCatalog = resolveDiscoverCatalog(
            catalogs = discoverCatalogs,
            preferredKey = discoverSelectionDataStore.getSelectedCatalogKey(),
            currentKey = _uiState.value.selectedDiscoverCatalogKey
        )
        val selectedType = selectedCatalog?.type ?: "movie"
        val (selectedGenre, selectedYear) = defaultGenreAndYear(selectedCatalog)

        _uiState.update {
            it.copy(
                installedAddons = addons,
                discoverCatalogs = discoverCatalogs,
                selectedDiscoverType = selectedType,
                selectedDiscoverCatalogKey = selectedCatalog?.key,
                selectedDiscoverGenre = selectedGenre,
                selectedDiscoverYear = selectedYear,
                discoverTmdbFilters = it.discoverTmdbFilters.adaptedTo(selectedType),
                discoverTmdbGenres = emptyList(),
                discoverInitialized = true,
                discoverLoading = false,
                discoverResults = emptyList(),
                pendingDiscoverResults = emptyList(),
                discoverHasMore = true,
                discoverPage = 1
            )
        }
        selectedCatalog?.let { catalog ->
            viewModelScope.launch {
                discoverSelectionDataStore.setSelectedCatalogKey(catalog.key)
            }
        }
        if (selectedCatalog?.isTmdbDiscover == true) loadTmdbGenres(selectedType)
        fetchDiscoverContent(reset = true)
    }

    /** A catalog whose `genre` extra is mandatory is never queried without a value. */
    private fun defaultGenreAndYear(catalog: DiscoverCatalog?): Pair<String?, String?> = when {
        catalog == null || !catalog.genreExtraRequired -> null to null
        catalog.years.isNotEmpty() -> null to catalog.years.first()
        else -> catalog.genres.firstOrNull() to null
    }

    private fun loadTmdbGenres(type: String) {
        val source = discoverTmdbSource ?: return
        // Anime movies and anime series use different TMDB genre lists.
        val format = _uiState.value.discoverTmdbFilters.format
        tmdbGenresJob?.cancel()
        tmdbGenresJob = viewModelScope.launch {
            val genres = source.genres(type, format)
            val current = _uiState.value
            if (current.selectedDiscoverType == type && current.discoverTmdbFilters.format == format) {
                _uiState.update { it.copy(discoverTmdbGenres = genres) }
            }
        }
    }

    /** Applies a state change that invalidates the current results, then refetches page one. */
    private fun refetchDiscover(transform: (SearchUiState) -> SearchUiState) {
        _uiState.update {
            transform(it).copy(
                discoverResults = emptyList(),
                pendingDiscoverResults = emptyList(),
                discoverPage = 1,
                discoverHasMore = true
            )
        }
        fetchDiscoverContent(reset = true)
    }

    private fun selectDiscoverType(type: String) {
        val state = _uiState.value
        val selectedCatalog = pickDiscoverCatalog(
            catalogs = state.discoverCatalogs,
            selectedType = type,
            preferredKey = state.selectedDiscoverCatalogKey
        )
        val typeChanged = type != state.selectedDiscoverType
        val (selectedGenre, selectedYear) = defaultGenreAndYear(selectedCatalog)
        selectedCatalog?.let { catalog ->
            viewModelScope.launch {
                discoverSelectionDataStore.setSelectedCatalogKey(catalog.key)
            }
        }
        refetchDiscover {
            it.copy(
                selectedDiscoverType = type,
                selectedDiscoverCatalogKey = selectedCatalog?.key,
                selectedDiscoverGenre = selectedGenre,
                selectedDiscoverYear = selectedYear,
                // Genre ids, networks and revenue sort differ per type, so a type change drops them.
                discoverTmdbFilters = if (typeChanged) it.discoverTmdbFilters.adaptedTo(type) else it.discoverTmdbFilters,
                discoverTmdbGenres = if (typeChanged) emptyList() else it.discoverTmdbGenres
            )
        }
        if (selectedCatalog?.isTmdbDiscover == true) loadTmdbGenres(type)
    }

    private fun selectDiscoverCatalog(catalogKey: String) {
        val state = _uiState.value
        val catalog = state.discoverCatalogs.firstOrNull { it.key == catalogKey } ?: return
        val typeChanged = catalog.type != state.selectedDiscoverType
        val (selectedGenre, selectedYear) = defaultGenreAndYear(catalog)
        viewModelScope.launch {
            discoverSelectionDataStore.setSelectedCatalogKey(catalog.key)
        }
        refetchDiscover {
            it.copy(
                selectedDiscoverCatalogKey = catalog.key,
                selectedDiscoverType = catalog.type,
                selectedDiscoverGenre = selectedGenre,
                selectedDiscoverYear = selectedYear,
                discoverTmdbFilters = if (typeChanged) it.discoverTmdbFilters.adaptedTo(catalog.type) else it.discoverTmdbFilters,
                discoverTmdbGenres = if (typeChanged) emptyList() else it.discoverTmdbGenres
            )
        }
        if (catalog.isTmdbDiscover) loadTmdbGenres(catalog.type)
    }

    private fun selectedDiscoverCatalog(state: SearchUiState = _uiState.value): DiscoverCatalog? =
        state.discoverCatalogs.firstOrNull { it.key == state.selectedDiscoverCatalogKey }

    /**
     * TMDB catalogs take any genre of their own list (ANDed with Animation + Japanese for anime).
     * An addon catalog that does not offer the genre (Kitsu Trending, Cinemeta New) hands over to
     * the first catalog of the same type that does, so every genre in the picker is selectable.
     */
    private fun selectDiscoverGenre(genre: String?) {
        val state = _uiState.value
        val catalog = selectedDiscoverCatalog(state) ?: return
        if (catalog.isTmdbDiscover) {
            refetchDiscover { it.copy(discoverTmdbFilters = it.discoverTmdbFilters.with(DiscoverFilterKey.GENRE, genre)) }
            return
        }
        fun DiscoverCatalog.offers(value: String) = genres.any { it.equals(value, ignoreCase = true) }
        val target = if (genre == null || catalog.offers(genre)) {
            catalog
        } else {
            state.discoverCatalogs.firstOrNull { !it.isTmdbDiscover && it.type == catalog.type && it.offers(genre) }
                ?: catalog
        }
        val canonicalGenre = genre?.let { g -> target.genres.firstOrNull { it.equals(g, ignoreCase = true) } ?: g }
        val (defaultGenre, defaultYear) = defaultGenreAndYear(target)
        val switched = target.key != catalog.key
        if (switched) {
            viewModelScope.launch { discoverSelectionDataStore.setSelectedCatalogKey(target.key) }
        }
        refetchDiscover {
            it.copy(
                selectedDiscoverCatalogKey = target.key,
                selectedDiscoverGenre = canonicalGenre ?: defaultGenre,
                selectedDiscoverYear = if (switched || canonicalGenre != null) defaultYear else it.selectedDiscoverYear
            )
        }
    }

    private fun selectDiscoverFilter(key: DiscoverFilterKey, value: String?) {
        if (key == DiscoverFilterKey.GENRE) {
            selectDiscoverGenre(value)
            return
        }
        val catalog = selectedDiscoverCatalog() ?: return
        when {
            catalog.isTmdbDiscover -> {
                val formatBefore = _uiState.value.discoverTmdbFilters.format
                refetchDiscover { state ->
                    val updated = state.discoverTmdbFilters.with(key, value)
                    state.copy(
                        discoverTmdbFilters = updated,
                        // A format switch clears the genre, so the old genre list must go too.
                        discoverTmdbGenres = if (updated.format != formatBefore) emptyList() else state.discoverTmdbGenres
                    )
                }
                if (key == DiscoverFilterKey.FORMAT && _uiState.value.discoverTmdbFilters.format != formatBefore) {
                    loadTmdbGenres(catalog.type)
                }
            }
            key == DiscoverFilterKey.YEAR && catalog.years.isNotEmpty() -> refetchDiscover {
                it.copy(selectedDiscoverYear = value ?: defaultGenreAndYear(catalog).second)
            }
        }
    }

    private fun resetDiscoverFilters() {
        val catalog = selectedDiscoverCatalog() ?: return
        val (defaultGenre, defaultYear) = defaultGenreAndYear(catalog)
        // Resetting an anime movie format goes back to the TV genre list.
        val formatWasReset = catalog.isTmdbDiscover && _uiState.value.discoverTmdbFilters.format != null
        refetchDiscover {
            if (catalog.isTmdbDiscover) {
                it.copy(
                    discoverTmdbFilters = DiscoverTmdbFilters(),
                    discoverTmdbGenres = if (formatWasReset) emptyList() else it.discoverTmdbGenres
                )
            } else {
                it.copy(selectedDiscoverGenre = defaultGenre, selectedDiscoverYear = defaultYear)
            }
        }
        if (formatWasReset) loadTmdbGenres(catalog.type)
    }

    private fun loadNextDiscoverResults() {
        if (_uiState.value.pendingDiscoverResults.isNotEmpty()) {
            showMoreDiscoverResults()
        } else {
            revealBatchAfterNextDiscoverFetch = true
            loadMoreDiscoverResults()
        }
    }

    private fun showMoreDiscoverResults() {
        val pending = _uiState.value.pendingDiscoverResults
        if (pending.isEmpty()) return
        val nextBatch = pending.take(DISCOVER_SHOW_MORE_BATCH)
        val remaining = pending.drop(DISCOVER_SHOW_MORE_BATCH)
        _uiState.update {
            it.copy(
                discoverResults = it.discoverResults + nextBatch,
                pendingDiscoverResults = remaining
            )
        }
    }

    private fun loadMoreDiscoverResults() {
        val state = _uiState.value
        if (state.query.trim().isNotEmpty()) return
        if (!state.discoverHasMore || state.discoverLoadingMore || state.pendingDiscoverResults.isNotEmpty()) return
        fetchDiscoverContent(reset = false)
    }

    private fun fetchDiscoverContent(reset: Boolean) {
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            val state = _uiState.value
            if (state.query.trim().isNotEmpty()) return@launch
            val selectedCatalog = state.discoverCatalogs.firstOrNull { it.key == state.selectedDiscoverCatalogKey }
                ?: return@launch

            if (reset) {
                revealBatchAfterNextDiscoverFetch = false
                _uiState.update {
                    it.copy(
                        discoverLoading = true,
                        discoverResults = emptyList(),
                        pendingDiscoverResults = emptyList(),
                        discoverPage = 1,
                        discoverHasMore = true
                    )
                }
            } else {
                _uiState.update { it.copy(discoverLoadingMore = true) }
            }

            val currentPage = if (reset) 1 else state.discoverPage + 1
            val visibleCountBeforeRequest = state.discoverResults.size

            if (selectedCatalog.isTmdbDiscover) {
                val source = discoverTmdbSource
                val page = if (source == null) {
                    null
                } else {
                    try {
                        source.discover(selectedCatalog.type, state.discoverTmdbFilters, currentPage)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                if (_uiState.value.discoverLocation == DiscoverLocation.OFF) return@launch
                if (page == null) {
                    applyDiscoverError()
                } else {
                    applyDiscoverBatch(
                        reset = reset,
                        currentPage = currentPage,
                        visibleCountBeforeRequest = visibleCountBeforeRequest,
                        incoming = page.items,
                        hasMore = page.hasMore,
                        // "Upcoming" is exactly what the hide-unreleased setting would remove.
                        filterUnreleased = hideUnreleasedContent &&
                            state.discoverTmdbFilters.sort != DiscoverSort.UPCOMING
                    )
                }
                return@launch
            }

            val skip = if (currentPage <= 1) 0 else (currentPage - 1) * selectedCatalog.skipStep
            val extraArgs = buildMap<String, String> {
                // A year-listing catalog receives the year through its `genre` extra.
                val genreExtraValue = if (selectedCatalog.years.isNotEmpty()) {
                    state.selectedDiscoverYear
                } else {
                    state.selectedDiscoverGenre
                }
                genreExtraValue?.takeIf { it.isNotBlank() }?.let { put("genre", it) }
            }

            catalogRepository.getCatalog(
                addonBaseUrl = selectedCatalog.addonBaseUrl,
                addonId = selectedCatalog.addonId,
                addonName = selectedCatalog.addonName,
                catalogId = selectedCatalog.catalogId,
                catalogName = selectedCatalog.catalogName,
                type = selectedCatalog.type,
                skip = skip,
                skipStep = selectedCatalog.skipStep,
                extraArgs = extraArgs,
                supportsSkip = selectedCatalog.supportsSkip,
                posterScreen = com.nuvio.tv.core.poster.CustomPosterScreen.SEARCH
            ).collect { result ->
                if (_uiState.value.discoverLocation == DiscoverLocation.OFF) return@collect
                when (result) {
                    is NetworkResult.Success -> applyDiscoverBatch(
                        reset = reset,
                        currentPage = currentPage,
                        visibleCountBeforeRequest = visibleCountBeforeRequest,
                        incoming = result.data.items,
                        hasMore = result.data.hasMore,
                        filterUnreleased = hideUnreleasedContent
                    )
                    is NetworkResult.Error -> applyDiscoverError()
                    NetworkResult.Loading -> Unit
                }
            }
        }
    }

    private fun applyDiscoverError() {
        revealBatchAfterNextDiscoverFetch = false
        _uiState.update {
            it.copy(
                discoverLoading = false,
                discoverLoadingMore = false,
                discoverHasMore = false
            )
        }
    }

    private fun applyDiscoverBatch(
        reset: Boolean,
        currentPage: Int,
        visibleCountBeforeRequest: Int,
        incoming: List<MetaPreview>,
        hasMore: Boolean,
        filterUnreleased: Boolean
    ) {
        val existing = if (reset) {
            emptyList()
        } else {
            _uiState.value.discoverResults + _uiState.value.pendingDiscoverResults
        }
        val existingKeys = existing.asSequence()
            .map { "${it.apiType}:${it.id}" }
            .toSet()
        val hasNewUniqueIncoming = incoming.any { item ->
            "${item.apiType}:${item.id}" !in existingKeys
        }
        val merged = if (reset) incoming else (existing + incoming)
        val rawDeduped = merged.distinctBy { "${it.apiType}:${it.id}" }
        val deduped = if (filterUnreleased) {
            val today = LocalDate.now()
            rawDeduped.filterNot { it.isUnreleased(today) }
        } else {
            rawDeduped
        }
        val shouldRevealBatch = !reset && revealBatchAfterNextDiscoverFetch
        val visibleLimit = if (reset) {
            DISCOVER_INITIAL_LIMIT
        } else if (shouldRevealBatch) {
            (visibleCountBeforeRequest + DISCOVER_SHOW_MORE_BATCH)
                .coerceAtLeast(DISCOVER_INITIAL_LIMIT)
        } else {
            visibleCountBeforeRequest.coerceAtLeast(DISCOVER_INITIAL_LIMIT)
        }
        val visible = deduped.take(visibleLimit)
        val pending = deduped.drop(visibleLimit)
        val shouldStopPagination = !reset && !hasNewUniqueIncoming
        _uiState.update {
            it.copy(
                discoverLoading = false,
                discoverLoadingMore = false,
                discoverResults = visible,
                pendingDiscoverResults = pending,
                discoverHasMore = if (shouldStopPagination) false else hasMore,
                discoverPage = if (shouldStopPagination) it.discoverPage else currentPage
            )
        }
        revealBatchAfterNextDiscoverFetch = false
    }

    private fun pickDiscoverCatalog(
        catalogs: List<DiscoverCatalog>,
        selectedType: String,
        preferredKey: String?
    ): DiscoverCatalog? {
        val filtered = catalogs.filter { it.type == selectedType }
        return filtered.firstOrNull { it.key == preferredKey } ?: filtered.firstOrNull()
    }

    private fun buildSearchTargets(addons: List<Addon>): List<Pair<Addon, CatalogDescriptor>> {
        val allSearchTargets = addons.flatMap { addon ->
            addon.catalogs
                .filter { catalog -> catalog.isSearchable() }
                .map { catalog -> addon to catalog }
        }

        return allSearchTargets
    }

    /**
     * A catalog is only searchable if a search is all it needs. One that also requires something we
     * cannot supply, a mandatory genre for instance, answers with an error or nothing at all, so
     * querying it just costs a request per keystroke and leaves a row that never fills in.
     */
    private fun CatalogDescriptor.isSearchable(): Boolean =
        supportsExtra("search") &&
            extra.none { property ->
                property.isRequired && !property.name.equals("search", ignoreCase = true)
            }

    private fun catalogKey(addonId: String, addonBaseUrl: String, type: String, catalogId: String): String {
        return catalogRowStableKey(addonId, addonBaseUrl, type, catalogId)
    }
}

internal fun resolveDiscoverCatalog(
    catalogs: List<DiscoverCatalog>,
    preferredKey: String?,
    currentKey: String?
): DiscoverCatalog? =
    catalogs.firstOrNull { it.key == preferredKey }
        ?: catalogs.firstOrNull { it.key == currentKey }
        ?: catalogs.firstOrNull()
