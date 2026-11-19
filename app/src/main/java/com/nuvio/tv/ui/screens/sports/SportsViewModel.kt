package com.nuvio.tv.ui.screens.sports

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.SportsAddon
import com.nuvio.tv.domain.model.supportsExtra
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SportsRowKind { LIVE, UPCOMING, SPORT, NETWORKS, REPLAYS }

/** A sport filter chip. [id] is the catalog suffix, e.g. "football" for `nuvio_sports_football`. */
@Immutable
data class SportFilter(
    val id: String,
    val label: String
)

/** A card, fully prepared off the main thread: the UI only reads fields. */
@Immutable
data class SportsCardModel(
    val key: String,
    val preview: MetaPreview,
    val isLive: Boolean
)

@Immutable
data class SportsRowModel(
    val key: String,
    val kind: SportsRowKind,
    /** Static title from resources; null when [title] carries a catalog-provided name. */
    @StringRes val titleRes: Int?,
    val title: String?,
    val items: List<SportsCardModel>
)

/** The "Live TV" chip's view: only the 24/7 channels catalog, paged, with optional search. */
@Immutable
data class LiveTvState(
    val query: String = "",
    val items: List<SportsCardModel> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loaded: Boolean = false,
    @StringRes val errorRes: Int? = null
)

@Immutable
data class SportsUiState(
    val filters: List<SportFilter> = emptyList(),
    /** null means "All". Kept while [isLiveTvSelected] so leaving Live TV returns to it. */
    val selectedSportId: String? = null,
    val rows: List<SportsRowModel> = emptyList(),
    val isLoading: Boolean = true,
    @StringRes val errorRes: Int? = null,
    /** True when the addon lists the 24/7 channels catalog, which backs the "Live TV" chip. */
    val hasLiveTv: Boolean = false,
    /** True when that catalog accepts a `search` extra. */
    val liveTvSearchSupported: Boolean = false,
    val isLiveTvSelected: Boolean = true,
    val liveTv: LiveTvState = LiveTvState()
)

@HiltViewModel
class SportsViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SportsUiState())
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()

    private var addon: Addon? = null
    private var loadJob: Job? = null
    private var liveRefreshJob: Job? = null
    private var liveTvJob: Job? = null
    /** Next `skip` for the unfiltered channel list; search results are a single page. */
    private var liveTvNextSkip: Int = 0

    /** Latest fetched items per catalog id, so filter changes and live refreshes reuse them. */
    private val catalogItems = HashMap<String, List<MetaPreview>>()
    private val catalogItemsLock = Any()

    init {
        viewModelScope.launch {
            addonRepository.getSportsAddon()
                .filterNotNull()
                .distinctUntilChangedBy { it.catalogs }
                .collect { current ->
                    addon = current
                    val networks = current.catalogs.firstOrNull { it.id == SportsAddon.CATALOG_NETWORKS }
                    _uiState.update {
                        it.copy(
                            filters = buildFilters(current.catalogs),
                            hasLiveTv = networks != null,
                            liveTvSearchSupported = networks?.supportsExtra("search") == true,
                            isLiveTvSelected = it.isLiveTvSelected && networks != null
                        )
                    }
                    reload()
                    if (_uiState.value.isLiveTvSelected) loadLiveTv(reset = true)
                }
        }
        viewModelScope.launch { ensureManifest() }
    }

    private suspend fun ensureManifest() {
        if (addonRepository.getSportsAddon().first() != null) return
        // Goes through the shared addon client and manifest cache; getSportsAddon() emits on success.
        val result = addonRepository.fetchAddon(SportsAddon.MANIFEST_URL)
        if (result is NetworkResult.Error && addon == null) {
            _uiState.update { it.copy(isLoading = false, errorRes = R.string.sports_error_unavailable) }
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        if (addon == null) {
            viewModelScope.launch { ensureManifest() }
        } else {
            synchronized(catalogItemsLock) { catalogItems.clear() }
            reload()
            if (_uiState.value.isLiveTvSelected) loadLiveTv(reset = true)
        }
    }

    fun selectSport(sportId: String?) {
        val state = _uiState.value
        if (state.selectedSportId == sportId && !state.isLiveTvSelected) return
        _uiState.update { it.copy(selectedSportId = sportId, isLiveTvSelected = false) }
        if (state.selectedSportId != sportId) reload()
    }

    /** Shows only the 24/7 channels catalog. */
    fun selectLiveTv() {
        val state = _uiState.value
        if (state.isLiveTvSelected || !state.hasLiveTv) return
        _uiState.update { it.copy(isLiveTvSelected = true) }
        if (!state.liveTv.loaded || state.liveTv.errorRes != null) loadLiveTv(reset = true)
    }

    fun onLiveTvQueryChange(query: String) {
        if (query == _uiState.value.liveTv.query) return
        _uiState.update { it.copy(liveTv = it.liveTv.copy(query = query)) }
        loadLiveTv(reset = true, debounce = true)
    }

    /** Fetches the next page of channels; ignored while searching or when nothing is left. */
    fun loadMoreLiveTv() {
        val live = _uiState.value.liveTv
        if (!live.hasMore || live.isLoading || live.isLoadingMore || live.query.isNotBlank()) return
        if (liveTvJob?.isActive == true) return
        loadLiveTv(reset = false)
    }

    private fun loadLiveTv(reset: Boolean, debounce: Boolean = false) {
        val current = addon ?: return
        val catalog = current.catalogs.firstOrNull { it.id == SportsAddon.CATALOG_NETWORKS } ?: return
        liveTvJob?.cancel()
        liveTvJob = viewModelScope.launch {
            val query = _uiState.value.liveTv.query.trim()
            if (debounce) delay(LIVE_TV_SEARCH_DEBOUNCE_MS)
            val searching = query.isNotEmpty() && _uiState.value.liveTvSearchSupported
            if (reset) liveTvNextSkip = 0
            val skip = if (searching) 0 else liveTvNextSkip
            _uiState.update {
                it.copy(
                    liveTv = it.liveTv.copy(
                        isLoading = reset,
                        isLoadingMore = !reset,
                        errorRes = null,
                        items = if (reset) emptyList() else it.liveTv.items
                    )
                )
            }
            val row = fetchCatalogRow(
                addon = current,
                catalog = catalog,
                skip = skip,
                extraArgs = if (searching) mapOf("search" to query) else emptyMap()
            )
            if (!isActive) return@launch
            if (row == null) {
                _uiState.update {
                    it.copy(
                        liveTv = it.liveTv.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            loaded = true,
                            hasMore = false,
                            errorRes = if (reset) R.string.sports_error_unavailable else null
                        )
                    )
                }
                return@launch
            }
            // Without server search, narrow the loaded channels locally.
            val matching = if (query.isNotEmpty() && !searching) {
                row.items.filter { it.name.contains(query, ignoreCase = true) }
            } else {
                row.items
            }
            val cards = withContext(Dispatchers.Default) {
                matching.map { toCard(it, SportsRowKind.NETWORKS) }
            }
            if (!searching) liveTvNextSkip = row.nextSkip
            _uiState.update { state ->
                val merged = (if (reset) cards else state.liveTv.items + cards).distinctBy { it.key }
                state.copy(
                    liveTv = state.liveTv.copy(
                        items = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        loaded = true,
                        // A page that adds nothing new ends paging (guards against repeating pages).
                        hasMore = !searching && row.hasMore && cards.isNotEmpty() &&
                            merged.size > state.liveTv.items.size,
                        errorRes = null
                    )
                )
            }
        }
    }

    /** Starts the ~60s Live Now refresh; call while the screen is visible. */
    fun onScreenVisible() {
        if (liveRefreshJob?.isActive == true) return
        liveRefreshJob = viewModelScope.launch {
            while (true) {
                delay(LIVE_REFRESH_INTERVAL_MS)
                val current = addon ?: continue
                val liveCatalog = current.catalogs.firstOrNull { it.id == SportsAddon.CATALOG_LIVE } ?: continue
                val fresh = fetchCatalog(current, liveCatalog) ?: continue
                synchronized(catalogItemsLock) { catalogItems[liveCatalog.id] = fresh }
                publishRows(current, _uiState.value.selectedSportId)
            }
        }
    }

    fun onScreenHidden() {
        liveRefreshJob?.cancel()
        liveRefreshJob = null
    }

    private fun reload() {
        val current = addon ?: return
        val sportId = _uiState.value.selectedSportId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val plan = buildPlan(current.catalogs, sportId)
            val missing = plan.filter { spec ->
                synchronized(catalogItemsLock) { !catalogItems.containsKey(spec.catalog.id) }
            }
            if (missing.isNotEmpty() && _uiState.value.rows.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorRes = null) }
            }
            // Publish what is already cached right away (instant filter switches), then fill in.
            publishRows(current, sportId)
            val semaphore = Semaphore(MAX_PARALLEL_CATALOG_REQUESTS)
            var anySuccess = plan.size > missing.size
            coroutineScope {
                missing.map { spec ->
                    async {
                        semaphore.withPermit {
                            val items = fetchCatalog(current, spec.catalog)
                            if (items != null) {
                                synchronized(catalogItemsLock) { catalogItems[spec.catalog.id] = items }
                                anySuccess = true
                                // Rows appear as they arrive instead of waiting for the slowest.
                                publishRows(current, sportId)
                            }
                        }
                    }
                }.awaitAll()
            }
            publishRows(current, sportId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorRes = if (!anySuccess && plan.isNotEmpty()) R.string.sports_error_unavailable else null
                )
            }
        }
    }

    private suspend fun fetchCatalog(addon: Addon, catalog: CatalogDescriptor): List<MetaPreview>? {
        val skipRequired = catalog.extra.any { it.name.equals("skip", ignoreCase = true) && it.isRequired }
        return fetchCatalogRow(
            addon = addon,
            catalog = catalog,
            skip = 0,
            extraArgs = if (skipRequired) mapOf("skip" to "0") else emptyMap()
        )?.items
    }

    private suspend fun fetchCatalogRow(
        addon: Addon,
        catalog: CatalogDescriptor,
        skip: Int,
        extraArgs: Map<String, String>
    ): com.nuvio.tv.domain.model.CatalogRow? {
        return try {
            val result = catalogRepository.getCatalog(
                addonBaseUrl = addon.baseUrl,
                addonId = addon.id,
                addonName = addon.displayName,
                catalogId = catalog.id,
                catalogName = catalog.name,
                type = catalog.apiType,
                skip = skip,
                extraArgs = extraArgs,
                supportsSkip = catalog.supportsExtra("skip")
            ).first { it !is NetworkResult.Loading }
            val row = (result as? NetworkResult.Success)?.data ?: return null
            // Lets the player tell live events and channels from replays (VOD).
            SportsAddon.rememberCatalogItems(catalog.id, row.items.map { it.id })
            row
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Builds display models on a background dispatcher and publishes them. */
    private suspend fun publishRows(addon: Addon, sportId: String?) {
        val snapshot = synchronized(catalogItemsLock) { HashMap(catalogItems) }
        val rows = withContext(Dispatchers.Default) {
            buildPlan(addon.catalogs, sportId).mapNotNull { spec ->
                val raw = snapshot[spec.catalog.id] ?: return@mapNotNull null
                val filtered = if (spec.filterBySport && sportId != null) {
                    raw.filter { matchesSport(it, sportId) }
                } else {
                    raw
                }
                if (filtered.isEmpty()) return@mapNotNull null
                SportsRowModel(
                    key = spec.key,
                    kind = spec.kind,
                    titleRes = spec.titleRes,
                    title = spec.title,
                    items = filtered.map { toCard(it, spec.kind) }.distinctBy { it.key }
                )
            }
        }
        // Discard results for a filter the user has already left.
        if (_uiState.value.selectedSportId != sportId) return
        _uiState.update { state ->
            state.copy(rows = rows, isLoading = state.isLoading && rows.isEmpty())
        }
    }

    private data class RowSpec(
        val key: String,
        val kind: SportsRowKind,
        val catalog: CatalogDescriptor,
        @StringRes val titleRes: Int?,
        val title: String?,
        val filterBySport: Boolean = false
    )

    private fun buildPlan(catalogs: List<CatalogDescriptor>, sportId: String?): List<RowSpec> {
        val byId = catalogs.associateBy { it.id }
        val plan = mutableListOf<RowSpec>()
        byId[SportsAddon.CATALOG_LIVE]?.let {
            plan += RowSpec("live", SportsRowKind.LIVE, it, R.string.sports_row_live, null, filterBySport = true)
        }
        byId[SportsAddon.CATALOG_UPCOMING]?.let {
            plan += RowSpec("upcoming", SportsRowKind.UPCOMING, it, R.string.sports_row_upcoming, null, filterBySport = true)
        }
        val sportCatalogs = sportCatalogs(catalogs)
        val shownSports = if (sportId == null) sportCatalogs else sportCatalogs.filter { sportIdOf(it) == sportId }
        shownSports.forEach { catalog ->
            plan += RowSpec("sport_${catalog.id}", SportsRowKind.SPORT, catalog, null, catalog.name)
        }
        if (sportId == null) {
            byId[SportsAddon.CATALOG_NETWORKS]?.let {
                plan += RowSpec("networks", SportsRowKind.NETWORKS, it, R.string.sports_row_networks, null)
            }
            byId[SportsAddon.CATALOG_REPLAYS]?.let {
                plan += RowSpec("replays", SportsRowKind.REPLAYS, it, R.string.sports_row_replays, null)
            }
        } else {
            val replays = byId["${SportsAddon.CATALOG_REPLAYS}_${sportId}_recent"]
                ?: byId["${SportsAddon.CATALOG_REPLAYS}_$sportId"]
            replays?.let {
                plan += RowSpec("replays_$sportId", SportsRowKind.REPLAYS, it, R.string.sports_row_replays, null)
            }
        }
        return plan
    }

    private fun buildFilters(catalogs: List<CatalogDescriptor>): List<SportFilter> =
        sportCatalogs(catalogs).map { SportFilter(id = sportIdOf(it), label = stripLeadingEmoji(it.name)) }

    private fun toCard(item: MetaPreview, kind: SportsRowKind): SportsCardModel {
        val isLive = kind == SportsRowKind.LIVE ||
            item.releaseInfo.equals("LIVE", ignoreCase = true) ||
            item.name.contains("LIVE:", ignoreCase = true)
        val cleanName = item.name
            .replace(LIVE_PREFIX, "")
            .let { stripLeadingEmoji(it) }
            .ifBlank { item.name }
        return SportsCardModel(
            key = "${item.apiType}:${item.id}",
            preview = if (cleanName == item.name) item else item.copy(name = cleanName),
            isLive = isLive
        )
    }

    private fun matchesSport(item: MetaPreview, sportId: String): Boolean =
        item.genres.any { genre -> genre.trim().lowercase().replace(' ', '_') == sportId }

    companion object {
        private const val LIVE_REFRESH_INTERVAL_MS = 60_000L
        private const val MAX_PARALLEL_CATALOG_REQUESTS = 4
        private const val LIVE_TV_SEARCH_DEBOUNCE_MS = 400L
        private val LIVE_PREFIX = Regex("""(?i)^\s*\S*\s*LIVE\s*:\s*""")
        private val NON_SPORT_SUFFIXES = setOf("live", "upcoming", "networks")

        /** Per-sport catalogs: `nuvio_sports_<sport>`, excluding live/upcoming/networks/replays. */
        internal fun sportCatalogs(catalogs: List<CatalogDescriptor>): List<CatalogDescriptor> =
            catalogs.filter { catalog ->
                catalog.id.startsWith(SportsAddon.SPORT_CATALOG_PREFIX) &&
                    !catalog.id.startsWith(SportsAddon.CATALOG_REPLAYS) &&
                    sportIdOf(catalog) !in NON_SPORT_SUFFIXES &&
                    !catalog.isSearchOnlyCatalog()
            }

        internal fun sportIdOf(catalog: CatalogDescriptor): String =
            catalog.id.removePrefix(SportsAddon.SPORT_CATALOG_PREFIX)

        /** "⚽ Soccer" -> "Soccer"; leaves names that start with a letter or digit untouched. */
        internal fun stripLeadingEmoji(name: String): String {
            val trimmed = name.trim()
            val firstToken = trimmed.substringBefore(' ')
            return if (firstToken.isNotEmpty() && firstToken.none { it.isLetterOrDigit() } && trimmed.contains(' ')) {
                trimmed.substringAfter(' ').trim()
            } else {
                trimmed
            }
        }
    }
}
