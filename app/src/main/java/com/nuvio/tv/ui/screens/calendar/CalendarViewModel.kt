package com.nuvio.tv.ui.screens.calendar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val entriesByDate: Map<LocalDate, List<CalendarEntry>> = emptyMap(),
    val isLoading: Boolean = true,
    val hasSources: Boolean = true,
    val tmdbAvailable: Boolean = true,
    val listView: Boolean = false
) {
    val selectedEntries: List<CalendarEntry> get() = entriesByDate[selectedDate].orEmpty()
}

/**
 * Calendar page state: the visible month, the selected day and what airs on each day for the
 * user's library / watchlist and Continue Watching titles.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CalendarUiState(
            month = savedStateHandle.get<String>(KEY_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ?: YearMonth.now(),
            selectedDate = savedStateHandle.get<String>(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now(),
            listView = savedStateHandle.get<Boolean>(KEY_LIST) ?: false,
            tmdbAvailable = repository.isAvailable
        )
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var sources: List<CalendarSource>? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                libraryRepository.libraryItems.onStart { emit(emptyList()) }.catch { emit(emptyList()) },
                watchProgressRepository.continueWatching.onStart { emit(emptyList()) }.catch { emit(emptyList()) },
                watchProgressRepository.observeNextUpSeeds().onStart { emit(emptyList()) }.catch { emit(emptyList()) }
            ) { library, cw, seeds -> buildSources(library, cw + seeds) }
                .debounce(SOURCE_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { newSources ->
                    sources = newSources
                    _uiState.update { it.copy(hasSources = newSources.isNotEmpty()) }
                    load()
                }
        }
    }

    fun selectDate(date: LocalDate) {
        if (_uiState.value.selectedDate == date) return
        savedStateHandle[KEY_DATE] = date.toString()
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun previousMonth() = showMonth(_uiState.value.month.minusMonths(1))

    fun nextMonth() = showMonth(_uiState.value.month.plusMonths(1))

    fun goToToday() {
        val today = LocalDate.now()
        showMonth(YearMonth.from(today), today)
    }

    fun toggleListView() {
        val list = !_uiState.value.listView
        savedStateHandle[KEY_LIST] = list
        _uiState.update { it.copy(listView = list) }
    }

    private fun showMonth(month: YearMonth, select: LocalDate? = null) {
        val today = LocalDate.now()
        val date = select ?: if (YearMonth.from(today) == month) today else month.atDay(1)
        savedStateHandle[KEY_MONTH] = month.toString()
        savedStateHandle[KEY_DATE] = date.toString()
        val changed = month != _uiState.value.month
        _uiState.update { it.copy(month = month, selectedDate = date) }
        if (changed) load()
    }

    private fun load() {
        val currentSources = sources ?: return
        val month = _uiState.value.month
        loadJob?.cancel()
        val cached = repository.cached(month, currentSources)
        if (cached != null) {
            _uiState.update { it.copy(entriesByDate = cached.groupBy { e -> e.date }, isLoading = false) }
            return
        }
        _uiState.update { it.copy(entriesByDate = emptyMap(), isLoading = true) }
        loadJob = viewModelScope.launch {
            val entries = runCatching { repository.loadMonth(month, currentSources) }.getOrDefault(emptyList())
            if (_uiState.value.month != month) return@launch
            _uiState.update { it.copy(entriesByDate = entries.groupBy { e -> e.date }, isLoading = false) }
        }
    }

    private fun buildSources(library: List<LibraryEntry>, progress: List<WatchProgress>): List<CalendarSource> {
        val out = LinkedHashMap<String, CalendarSource>()
        progress
            .filter { it.contentType.equals("series", ignoreCase = true) || it.contentType.equals("tv", ignoreCase = true) }
            .sortedByDescending { it.lastWatched }
            .forEach { p ->
                val id = p.contentId.substringBefore(':').takeIf { p.contentId.startsWith("tt") } ?: p.contentId
                out.putIfAbsent(
                    id,
                    CalendarSource(
                        id = id,
                        isSeries = true,
                        name = p.name,
                        poster = p.poster,
                        addonBaseUrl = p.addonBaseUrl,
                        tmdbId = null,
                        imdbId = id.takeIf { it.startsWith("tt") }
                    )
                )
            }
        library.forEach { entry ->
            val type = entry.type.lowercase()
            val isSeries = type == "series" || type == "tv"
            if (!isSeries && type != "movie") return@forEach
            out.putIfAbsent(
                entry.id,
                CalendarSource(
                    id = entry.id,
                    isSeries = isSeries,
                    name = entry.name,
                    poster = entry.poster,
                    addonBaseUrl = entry.addonBaseUrl,
                    tmdbId = entry.tmdbId,
                    imdbId = entry.imdbId
                )
            )
        }
        val all = out.values.toList()
        return all.filter { it.isSeries }.take(MAX_SERIES) + all.filter { !it.isSeries }.take(MAX_MOVIES)
    }

    private companion object {
        const val KEY_MONTH = "calendar_month"
        const val KEY_DATE = "calendar_date"
        const val KEY_LIST = "calendar_list"
        const val SOURCE_DEBOUNCE_MS = 400L
        const val MAX_SERIES = 150
        const val MAX_MOVIES = 150
    }
}
