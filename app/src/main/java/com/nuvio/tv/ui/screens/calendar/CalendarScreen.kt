package com.nuvio.tv.ui.screens.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.providers.CinemaosPill
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val ENTRY_FOCUS_ATTEMPTS = 12
private const val RESTORE_ONLY_ATTEMPTS = 4
private const val MAX_STRIP_POSTERS = 3

/**
 * Release calendar: a Monday-first month grid of what airs for the user's library / watchlist and
 * Continue Watching titles, with a side panel for the focused day, or a List view of the month.
 *
 * Focus: entering focuses the selected day (today by default); arrows move across the grid; Up from
 * the first week reaches the header (prev / next month, Today, List view); Right from the last column
 * or OK on a day moves into the day panel; Left or Back from the panel returns to the day. Back from a
 * title's details restores focus to the panel / list row that opened it.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contentFocusRequester = LocalContentFocusRequester.current
    val sidebarExpanded = LocalSidebarExpanded.current

    val prevRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val todayRequester = remember { FocusRequester() }
    val viewToggleRequester = remember { FocusRequester() }
    val dayRequesters = remember(state.month) { (1..state.month.lengthOfMonth()).associateWith { FocusRequester() } }
    val panelRequesters = remember { HashMap<Int, FocusRequester>() }
    fun panelRequester(index: Int) = panelRequesters.getOrPut(index) { FocusRequester() }
    val listRequesters = remember { HashMap<String, FocusRequester>() }
    fun listRequester(key: String) = listRequesters.getOrPut(key) { FocusRequester() }

    // Survive the trip to details and back.
    var restorePanelIndex by rememberSaveable { mutableStateOf(-1) }
    var restoreListKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pageHasFocus by remember { mutableStateOf(false) }
    var panelHasFocus by remember { mutableStateOf(false) }

    val selectedDayRequester = dayRequesters[state.selectedDate.dayOfMonth]
        ?.takeIf { YearMonth.from(state.selectedDate) == state.month }

    fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus() }.getOrDefault(false)

    LaunchedEffect(sidebarExpanded, state.listView, state.isLoading) {
        if (sidebarExpanded) return@LaunchedEffect
        repeat(ENTRY_FOCUS_ATTEMPTS) { attempt ->
            withFrameNanos { }
            if (pageHasFocus) return@LaunchedEffect
            val restorePending = if (state.listView) restoreListKey != null else restorePanelIndex >= 0
            val restored = if (state.listView) {
                restoreListKey?.let { listRequesters[it] }?.tryFocus() == true
            } else {
                restorePanelIndex >= 0 && restorePanelIndex < state.selectedEntries.size &&
                    panelRequesters[restorePanelIndex]?.tryFocus() == true
            }
            // Give a pending restore a few frames (lazy rows compose late) before falling back.
            val mayFallBack = !restorePending || attempt >= RESTORE_ONLY_ATTEMPTS
            val fallback = !restored && mayFallBack && (
                if (state.listView) {
                    viewToggleRequester.tryFocus()
                } else {
                    selectedDayRequester?.tryFocus() == true || dayRequesters[1]?.tryFocus() == true
                }
                )
            val focused = restored || fallback
            if (focused) return@LaunchedEffect
            delay(40)
        }
    }

    BackHandler(enabled = panelHasFocus && !state.listView && selectedDayRequester != null) {
        selectedDayRequester?.tryFocus()
    }

    val background = NuvioTheme.colors.Background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(start = 40.dp, end = 32.dp, top = 22.dp, bottom = 18.dp)
            .onFocusChanged { pageHasFocus = it.hasFocus }
            .focusRequester(contentFocusRequester)
            .focusRestorer()
            .focusGroup()
    ) {
        CalendarHeader(
            month = state.month,
            isLoading = state.isLoading,
            listView = state.listView,
            prevRequester = prevRequester,
            nextRequester = nextRequester,
            todayRequester = todayRequester,
            viewToggleRequester = viewToggleRequester,
            downTarget = if (state.listView || !state.tmdbAvailable) null else selectedDayRequester,
            onPrev = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onToday = viewModel::goToToday,
            onToggleView = {
                restorePanelIndex = -1
                restoreListKey = null
                viewModel.toggleListView()
            }
        )
        Spacer(Modifier.height(14.dp))

        if (!state.tmdbAvailable) {
            CalendarMessage(stringResource(R.string.cinemaos_calendar_unavailable))
        } else if (state.listView) {
            CalendarListView(
                state = state,
                requesterFor = ::listRequester,
                onOpen = { entry ->
                    restoreListKey = entry.key
                    onNavigateToDetail(entry.itemId, entry.itemType, entry.addonBaseUrl)
                }
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                MonthGrid(
                    month = state.month,
                    selectedDate = state.selectedDate,
                    entriesByDate = state.entriesByDate,
                    dayRequesters = dayRequesters,
                    onDayFocused = { date ->
                        restorePanelIndex = -1
                        viewModel.selectDate(date)
                    },
                    onDayClick = { date ->
                        viewModel.selectDate(date)
                        if (state.entriesByDate[date].orEmpty().isNotEmpty()) panelRequester(0).tryFocus()
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Spacer(Modifier.width(20.dp))
                DayPanel(
                    date = state.selectedDate,
                    entries = state.selectedEntries,
                    isLoading = state.isLoading,
                    hasSources = state.hasSources,
                    requesterFor = ::panelRequester,
                    leftTarget = selectedDayRequester,
                    onOpen = { index, entry ->
                        restorePanelIndex = index
                        onNavigateToDetail(entry.itemId, entry.itemType, entry.addonBaseUrl)
                    },
                    modifier = Modifier
                        .width(310.dp)
                        .fillMaxHeight()
                        .onFocusChanged { panelHasFocus = it.hasFocus }
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    month: YearMonth,
    isLoading: Boolean,
    listView: Boolean,
    prevRequester: FocusRequester,
    nextRequester: FocusRequester,
    todayRequester: FocusRequester,
    viewToggleRequester: FocusRequester,
    downTarget: FocusRequester?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onToggleView: () -> Unit
) {
    val locale = Locale.getDefault()
    val title = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    val downModifier = Modifier.focusProperties { if (downTarget != null) down = downTarget }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        CinemaosPill(
            text = "",
            selected = false,
            onClick = onPrev,
            icon = Icons.Default.ChevronLeft,
            contentDescription = stringResource(R.string.cinemaos_calendar_previous_month),
            modifier = Modifier.focusRequester(prevRequester).then(downModifier)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = Color.White,
            modifier = Modifier.widthIn(min = 210.dp)
        )
        Spacer(Modifier.width(12.dp))
        CinemaosPill(
            text = "",
            selected = false,
            onClick = onNext,
            icon = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.cinemaos_calendar_next_month),
            modifier = Modifier.focusRequester(nextRequester).then(downModifier)
        )
        if (isLoading) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.cinemaos_calendar_loading),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        Spacer(Modifier.weight(1f))
        CinemaosPill(
            text = stringResource(R.string.cinemaos_calendar_today),
            selected = false,
            onClick = onToday,
            icon = Icons.Default.Today,
            modifier = Modifier.focusRequester(todayRequester).then(downModifier)
        )
        Spacer(Modifier.width(10.dp))
        CinemaosPill(
            text = stringResource(
                if (listView) R.string.cinemaos_calendar_month_view else R.string.cinemaos_calendar_list_view
            ),
            selected = listView,
            onClick = onToggleView,
            icon = if (listView) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
            modifier = Modifier.focusRequester(viewToggleRequester).then(downModifier)
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    entriesByDate: Map<LocalDate, List<CalendarEntry>>,
    dayRequesters: Map<Int, FocusRequester>,
    onDayFocused: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val leading = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
    val totalCells = leading + month.lengthOfMonth()
    val weeks = (totalCells + 6) / 7
    val weekdays = remember(locale) {
        (0 until 7).map { DayOfWeek.MONDAY.plus(it.toLong()).getDisplayName(TextStyle.SHORT, locale) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekdays.forEach { label ->
                Text(
                    text = label.uppercase(locale),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
        }
        for (week in 0 until weeks) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (dow in 0 until 7) {
                    val day = week * 7 + dow - leading + 1
                    if (day < 1 || day > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        val date = month.atDay(day)
                        DayCell(
                            date = date,
                            entries = entriesByDate[date].orEmpty(),
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            requester = dayRequesters.getValue(day),
                            onFocused = { onDayFocused(date) },
                            onClick = { onDayClick(date) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    entries: List<CalendarEntry>,
    isToday: Boolean,
    isSelected: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.26f),
            pressedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (isToday) {
                Border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f)), 0.dp, shape)
            } else {
                Border.None
            },
            focusedBorder = Border(BorderStroke(2.dp, Color.White), 0.dp, shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isToday) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                } else {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = if (isSelected) 1f else 0.8f),
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE50914))
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    entries.take(MAX_STRIP_POSTERS).forEach { entry ->
                        PosterThumb(url = entry.poster, width = 20.dp, height = 30.dp, corner = 4.dp)
                    }
                    if (entries.size > MAX_STRIP_POSTERS) {
                        Text(
                            text = "+${entries.size - MAX_STRIP_POSTERS}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterThumb(url: String?, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(true).build() }
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DayPanel(
    date: LocalDate,
    entries: List<CalendarEntry>,
    isLoading: Boolean,
    hasSources: Boolean,
    requesterFor: (Int) -> FocusRequester,
    leftTarget: FocusRequester?,
    onOpen: (Int, CalendarEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val dateLabel = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(date) { listState.scrollToItem(0) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(16.dp)
    ) {
        Text(
            text = dateLabel,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (entries.isEmpty()) {
                stringResource(R.string.cinemaos_calendar_nothing_airing)
            } else {
                pluralStringResource(R.plurals.cinemaos_calendar_title_count, entries.size, entries.size)
            },
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(12.dp))
        when {
            entries.isNotEmpty() -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { index, entry ->
                    EntryRow(
                        entry = entry,
                        requester = requesterFor(index),
                        leftTarget = leftTarget,
                        onClick = { onOpen(index, entry) }
                    )
                }
            }
            isLoading -> Text(
                text = stringResource(R.string.cinemaos_calendar_loading),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
            !hasSources -> Text(
                text = stringResource(R.string.cinemaos_calendar_no_sources),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EntryRow(
    entry: CalendarEntry,
    requester: FocusRequester,
    leftTarget: FocusRequester?,
    onClick: () -> Unit,
    dateLabel: String? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties { if (leftTarget != null) left = leftTarget },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.22f),
            pressedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)), 0.dp, shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterThumb(url = entry.poster, width = 44.dp, height = 66.dp, corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (dateLabel != null) {
                    Text(
                        text = dateLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = entry.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entrySubtitle(entry),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                entry.episodeTitle?.let { name ->
                    Text(
                        text = name,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** "Movie", "S1 · E3" or "S1 · E16–E17 · 2 episodes". */
@Composable
private fun entrySubtitle(entry: CalendarEntry): String {
    if (entry.isMovie) return stringResource(R.string.cinemaos_calendar_movie_release)
    val season = entry.season ?: return ""
    val first = entry.firstEpisode ?: return "S$season"
    val last = entry.lastEpisode ?: first
    return if (entry.episodeCount <= 1 || first == last) {
        "S$season · E$first"
    } else {
        "S$season · E$first–E$last · " +
            pluralStringResource(R.plurals.cinemaos_calendar_episode_count, entry.episodeCount, entry.episodeCount)
    }
}

@Composable
private fun CalendarListView(
    state: CalendarUiState,
    requesterFor: (String) -> FocusRequester,
    onOpen: (CalendarEntry) -> Unit
) {
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val days = remember(state.entriesByDate) { state.entriesByDate.keys.sorted() }
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    if (days.isEmpty()) {
        CalendarMessage(
            stringResource(
                when {
                    state.isLoading -> R.string.cinemaos_calendar_loading
                    !state.hasSources -> R.string.cinemaos_calendar_no_sources
                    else -> R.string.cinemaos_calendar_month_empty
                }
            )
        )
        return
    }
    val rows = remember(days, state.entriesByDate) { days.flatMap { d -> state.entriesByDate[d].orEmpty() } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(end = 120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        itemsIndexed(rows, key = { _, e -> "list_${e.key}" }) { index, entry ->
            val newDay = index == 0 || rows[index - 1].date != entry.date
            if (newDay) {
                val label = entry.date.format(formatter)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                Text(
                    text = if (entry.date == today) "$label · ${stringResource(R.string.cinemaos_calendar_today)}" else label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.date == today) Color.White else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 14.dp, bottom = 4.dp, start = 4.dp)
                )
            }
            EntryRow(
                entry = entry,
                requester = requesterFor(entry.key),
                leftTarget = null,
                onClick = { onOpen(entry) }
            )
        }
    }
}

@Composable
private fun CalendarMessage(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
        Text(text = text, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f))
    }
}
