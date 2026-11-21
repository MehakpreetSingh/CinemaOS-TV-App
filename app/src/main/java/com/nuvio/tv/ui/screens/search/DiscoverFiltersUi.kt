package com.nuvio.tv.ui.screens.search

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.contentTextDirection
import kotlinx.coroutines.delay
import java.util.Locale

/** Value of the "any" row of a filter picker; never sent to the API. */
internal const val DISCOVER_ANY_VALUE = "__default__"

internal fun discoverFilterPickerId(key: DiscoverFilterKey): String = "filter:${key.name}"

@Composable
private fun discoverFilterTitle(key: DiscoverFilterKey): String = stringResource(
    when (key) {
        DiscoverFilterKey.SORT -> R.string.discover_filter_sort
        DiscoverFilterKey.GENRE -> R.string.discover_filter_genre
        DiscoverFilterKey.YEAR -> R.string.discover_filter_year
        DiscoverFilterKey.RATING -> R.string.discover_filter_rating
        DiscoverFilterKey.RUNTIME -> R.string.discover_filter_runtime
        DiscoverFilterKey.LANGUAGE -> R.string.discover_filter_language
        DiscoverFilterKey.COUNTRY -> R.string.discover_filter_country
        DiscoverFilterKey.PROVIDER -> R.string.discover_filter_provider
        DiscoverFilterKey.NETWORK -> R.string.discover_filter_network
        DiscoverFilterKey.FORMAT -> R.string.discover_filter_format
        DiscoverFilterKey.SEASON -> R.string.discover_filter_season
        DiscoverFilterKey.STATUS -> R.string.discover_filter_status
    }
)

private fun anyLabel(context: Context, key: DiscoverFilterKey): String = context.getString(
    when (key) {
        DiscoverFilterKey.YEAR -> R.string.discover_filter_any_year
        DiscoverFilterKey.RATING -> R.string.discover_filter_any_rating
        DiscoverFilterKey.RUNTIME -> R.string.discover_filter_any_runtime
        DiscoverFilterKey.LANGUAGE -> R.string.discover_filter_any_language
        DiscoverFilterKey.COUNTRY -> R.string.discover_filter_any_country
        DiscoverFilterKey.PROVIDER -> R.string.discover_filter_any_provider
        DiscoverFilterKey.NETWORK -> R.string.discover_filter_any_network
        DiscoverFilterKey.SEASON -> R.string.discover_filter_any_season
        DiscoverFilterKey.STATUS -> R.string.discover_filter_any_status
        DiscoverFilterKey.SORT, DiscoverFilterKey.GENRE, DiscoverFilterKey.FORMAT ->
            R.string.discover_genre_default
    }
)

private fun sortLabel(context: Context, sort: String, type: String): String {
    val tv = isTvLikeDiscoverType(type)
    return context.getString(
        when (sort) {
            DiscoverSort.NEWEST -> R.string.discover_sort_newest
            DiscoverSort.OLDEST -> R.string.discover_sort_oldest
            DiscoverSort.TOP_RATED -> R.string.discover_sort_top_rated
            DiscoverSort.MOST_VOTED -> R.string.discover_sort_most_voted
            DiscoverSort.REVENUE -> R.string.discover_sort_revenue
            DiscoverSort.UPCOMING ->
                if (tv) R.string.discover_sort_upcoming_tv else R.string.discover_sort_upcoming_movie
            DiscoverSort.NOW_PLAYING ->
                if (tv) R.string.discover_sort_now_playing_tv else R.string.discover_sort_now_playing_movie
            else -> R.string.discover_sort_popular
        }
    )
}

private fun ratingLabel(context: Context, value: String): String = context.getString(
    when (value) {
        "9" -> R.string.discover_rating_9
        "8" -> R.string.discover_rating_8
        "7" -> R.string.discover_rating_7
        "6" -> R.string.discover_rating_6
        else -> R.string.discover_rating_5
    }
)

private fun runtimeLabel(context: Context, value: String): String = context.getString(
    when (value) {
        "0-30" -> R.string.discover_runtime_under_30
        "30-60" -> R.string.discover_runtime_30_60
        "60-90" -> R.string.discover_runtime_60_90
        "90-120" -> R.string.discover_runtime_90_120
        else -> R.string.discover_runtime_over_120
    }
)

private fun formatLabel(context: Context, value: String): String = context.getString(
    if (value == ANIME_FORMAT_MOVIE) R.string.discover_format_movie else R.string.discover_format_tv
)

private fun seasonLabel(context: Context, value: String): String = context.getString(
    when (value) {
        "winter" -> R.string.discover_season_winter
        "spring" -> R.string.discover_season_spring
        "summer" -> R.string.discover_season_summer
        else -> R.string.discover_season_fall
    }
)

private fun statusLabel(context: Context, value: String): String = context.getString(
    if (value == ANIME_STATUS_AIRING) R.string.discover_status_airing else R.string.discover_status_ended
)

private fun languageLabel(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
        .takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
        ?: code.uppercase()

private fun countryLabel(code: String): String =
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
        .takeIf { it.isNotBlank() } ?: code

/** Options of one extra filter picker, "any" row first (except Sort, Format and required addon years). */
private fun discoverFilterOptions(
    context: Context,
    key: DiscoverFilterKey,
    catalog: DiscoverCatalog
): List<DiscoverOption> {
    val any = DiscoverOption(anyLabel(context, key), DISCOVER_ANY_VALUE)
    if (!catalog.isTmdbDiscover) {
        // Addon year picker (Cinemeta "New"): the options are the addon's own years.
        val years = catalog.years.map { DiscoverOption(it, it) }
        return if (catalog.genreExtraRequired) years else listOf(any) + years
    }
    return when (key) {
        DiscoverFilterKey.SORT -> DiscoverSort.valuesFor(catalog.type)
            .map { DiscoverOption(sortLabel(context, it, catalog.type), it) }
        DiscoverFilterKey.YEAR -> buildList {
            add(any)
            DiscoverFilterValues.decades().forEach {
                add(
                    DiscoverOption(
                        context.getString(R.string.discover_filter_decade_format, it),
                        "$DISCOVER_DECADE_PREFIX$it"
                    )
                )
            }
            DiscoverFilterValues.years().forEach { add(DiscoverOption(it.toString(), it.toString())) }
        }
        DiscoverFilterKey.RATING ->
            listOf(any) + DiscoverFilterValues.ratings.map { DiscoverOption(ratingLabel(context, it), it) }
        DiscoverFilterKey.RUNTIME ->
            listOf(any) + DiscoverFilterValues.runtimes.map { DiscoverOption(runtimeLabel(context, it), it) }
        DiscoverFilterKey.LANGUAGE ->
            listOf(any) + DiscoverFilterValues.languages.map { DiscoverOption(languageLabel(it), it) }
        DiscoverFilterKey.COUNTRY ->
            listOf(any) + DiscoverFilterValues.countries.map { DiscoverOption(countryLabel(it), it) }
        DiscoverFilterKey.PROVIDER ->
            listOf(any) + DiscoverFilterValues.providers.map { (id, name) -> DiscoverOption(name, id) }
        DiscoverFilterKey.NETWORK ->
            listOf(any) + DiscoverFilterValues.networks.map { (id, name) -> DiscoverOption(name, id) }
        DiscoverFilterKey.FORMAT ->
            DiscoverFilterValues.animeFormats.map { DiscoverOption(formatLabel(context, it), it) }
        DiscoverFilterKey.SEASON ->
            listOf(any) + DiscoverFilterValues.animeSeasons.map { DiscoverOption(seasonLabel(context, it), it) }
        DiscoverFilterKey.STATUS ->
            listOf(any) + DiscoverFilterValues.animeStatuses.map { DiscoverOption(statusLabel(context, it), it) }
        DiscoverFilterKey.GENRE -> listOf(any)
    }
}

/** Current value of an extra filter for the selected catalog, or null for "any". */
private fun currentFilterValue(uiState: SearchUiState, catalog: DiscoverCatalog, key: DiscoverFilterKey): String? =
    if (catalog.isTmdbDiscover) {
        uiState.discoverTmdbFilters.value(key)
    } else if (key == DiscoverFilterKey.YEAR) {
        uiState.selectedDiscoverYear
    } else {
        null
    }


/** One pill of the filter row: a title, the current value and the options its picker offers. */
@Immutable
internal data class DiscoverPillSpec(
    val id: String,
    val title: String,
    val valueLabel: String,
    val active: Boolean,
    val options: List<DiscoverOption>,
    val selectedValue: String?,
    val onSelect: (DiscoverOption) -> Unit
)

/** Pills for the extra filters (sort, year, rating, anime format/season/status, ...). */
@Composable
internal fun discoverFilterPills(
    keys: List<DiscoverFilterKey>,
    uiState: SearchUiState,
    catalog: DiscoverCatalog,
    onSelectFilter: (DiscoverFilterKey, String?) -> Unit
): List<DiscoverPillSpec> {
    val context = LocalContext.current
    val anyValueLabel = stringResource(R.string.discover_filter_value_any)
    return keys.map { key ->
        val options = discoverFilterOptions(context, key, catalog)
        val current = currentFilterValue(uiState, catalog, key)
        val selectedValue = current ?: when (key) {
            DiscoverFilterKey.SORT -> DiscoverSort.POPULAR
            DiscoverFilterKey.FORMAT -> ANIME_FORMAT_TV
            else -> DISCOVER_ANY_VALUE
        }
        val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        val active = if (catalog.isTmdbDiscover) {
            uiState.discoverTmdbFilters.isActive(key)
        } else {
            current != null && !catalog.genreExtraRequired
        }
        DiscoverPillSpec(
            id = discoverFilterPickerId(key),
            title = discoverFilterTitle(key),
            valueLabel = if (selectedValue == DISCOVER_ANY_VALUE) anyValueLabel else selectedLabel ?: anyValueLabel,
            active = active,
            options = options,
            selectedValue = selectedValue,
            onSelect = { option ->
                onSelectFilter(key, option.value.takeUnless { it == DISCOVER_ANY_VALUE })
            }
        )
    }
}

private val PillShape = RoundedCornerShape(50)

/** Shared capsule look of filter pills and the "Clear all" pill. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiscoverPillSurface(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val accent = NuvioTheme.colors.Secondary
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(shape = PillShape),
        colors = CardDefaults.colors(
            containerColor = if (active) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = if (active) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f)
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(
                    if (active) 1.dp else NuvioTheme.spacing.hairline,
                    if (active) accent else NuvioTheme.colors.Border
                ),
                shape = PillShape
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                shape = PillShape
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

/**
 * Compact "Label: value" pill that opens its option list in a popup overlay. When the popup closes
 * focus goes back to this pill.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DiscoverFilterPill(
    spec: DiscoverPillSpec,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    upRequester: FocusRequester?,
    blockFocus: Boolean,
    onFocused: () -> Unit,
    onMoveDown: () -> Boolean,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var wasExpanded by remember { mutableStateOf(false) }
    // Seed focused option with the current selection so reopen highlights the right row.
    var focusedOptionValue by remember(expanded) {
        mutableStateOf(if (expanded) spec.selectedValue else null)
    }
    val selectedItemFocusRequester = remember { FocusRequester() }
    val selectedBringIntoViewRequester = remember { BringIntoViewRequester() }

    // Popup content attaches focus targets a few frames after expand.
    LaunchedEffect(expanded, spec.selectedValue) {
        if (!expanded || spec.selectedValue == null) return@LaunchedEffect
        var focused = selectedItemFocusRequester.requestFocusAfterFrames(frames = 3)
        var attempt = 0
        while (!focused && attempt < 6) {
            delay(32)
            focused = runCatching { selectedItemFocusRequester.requestFocus() }.getOrDefault(false)
            attempt++
        }
        if (!focused) return@LaunchedEffect
        runCatching { selectedBringIntoViewRequester.bringIntoView() }
        // The menu may still move initial focus to the first item once the popup settles.
        delay(48)
        if (runCatching { selectedItemFocusRequester.requestFocus() }.getOrDefault(false)) {
            runCatching { selectedBringIntoViewRequester.bringIntoView() }
        }
    }

    // Closing the popup (selection or Back) hands focus back to the pill that opened it.
    LaunchedEffect(expanded) {
        if (wasExpanded && !expanded) {
            focusRequester.requestFocusAfterFrames(frames = 2)
        }
        wasExpanded = expanded
    }

    Box(modifier = modifier) {
        DiscoverPillSurface(
            active = spec.active,
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusProperties {
                    if (blockFocus) canFocus = false
                    if (upRequester != null) up = upRequester
                }
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (state.isFocused) onFocused()
                }
                .onPreviewKeyEvent { event ->
                    event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN &&
                        onMoveDown()
                }
        ) {
            Text(
                text = "${spec.title}:",
                style = MaterialTheme.typography.labelLarge,
                color = NuvioTheme.colors.TextTertiary,
                maxLines = 1
            )
            Text(
                text = spec.valueLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    textDirection = spec.valueLabel.contentTextDirection(),
                    fontWeight = if (spec.active) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) {
                    stringResource(R.string.cd_collapse, spec.title)
                } else {
                    stringResource(R.string.cd_expand, spec.title)
                },
                modifier = Modifier.size(16.dp),
                tint = if (isFocused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.TextSecondary
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                focusedOptionValue = null
                onExpandedChange(false)
            },
            modifier = Modifier
                .widthIn(min = 240.dp)
                .heightIn(max = 300.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = NuvioTheme.colors.BackgroundCard,
            tonalElevation = NuvioTheme.spacing.none,
            shadowElevation = NuvioTheme.spacing.sm,
            border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border)
        ) {
            spec.options.forEach { option ->
                val isSelected = option.value == spec.selectedValue
                val isOptionFocused = option.value == focusedOptionValue
                val itemTextColor = if (isOptionFocused) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextPrimary
                val itemBackgroundColor = when {
                    isOptionFocused -> NuvioTheme.colors.Secondary
                    isSelected -> NuvioTheme.colors.FocusBackground
                    else -> Color.Transparent
                }

                DropdownMenuItem(
                    modifier = Modifier
                        .then(
                            if (isSelected) {
                                Modifier
                                    .focusRequester(selectedItemFocusRequester)
                                    .bringIntoViewRequester(selectedBringIntoViewRequester)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = NuvioTheme.spacing.xxs)
                        .background(
                            color = itemBackgroundColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .onFocusChanged { state ->
                            val hasFocus = state.isFocused || state.hasFocus
                            focusedOptionValue = when {
                                hasFocus -> option.value
                                focusedOptionValue == option.value -> null
                                else -> focusedOptionValue
                            }
                        },
                    text = {
                        Text(
                            text = option.label,
                            color = itemTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                textDirection = option.label.contentTextDirection()
                            )
                        )
                    },
                    onClick = { spec.onSelect(option) },
                    colors = MenuDefaults.itemColors(
                        textColor = itemTextColor,
                        disabledTextColor = NuvioTheme.colors.TextDisabled
                    )
                )
            }
        }
    }
}

/**
 * The single-line, horizontally scrolling row of filter pills followed by "Clear all". It is one
 * focus group: [rowRequester] enters it (restoring the last focused pill), Up leaves to
 * [upRequester] and Down is handed to [onMoveDown] (first result of the grid).
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun DiscoverFilterPillRow(
    pills: List<DiscoverPillSpec>,
    expandedPicker: String?,
    onExpandedChange: (String?) -> Unit,
    onReset: () -> Unit,
    rowRequester: FocusRequester,
    upRequester: FocusRequester?,
    blockFocus: Boolean,
    onMoveDown: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val pillRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(id: String): FocusRequester = pillRequesters.getOrPut(id) { FocusRequester() }
    var lastFocusedId by remember { mutableStateOf(pills.firstOrNull()?.id ?: DISCOVER_CLEAR_PILL_ID) }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(rowRequester)
            .focusRestorer { requesterFor(lastFocusedId) },
        contentPadding = PaddingValues(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(items = pills, key = { it.id }) { spec ->
            DiscoverFilterPill(
                spec = spec.copy(
                    onSelect = { option ->
                        spec.onSelect(option)
                        onExpandedChange(null)
                    }
                ),
                expanded = expandedPicker == spec.id,
                onExpandedChange = { shouldExpand -> onExpandedChange(if (shouldExpand) spec.id else null) },
                focusRequester = requesterFor(spec.id),
                upRequester = upRequester,
                blockFocus = blockFocus,
                onFocused = { lastFocusedId = spec.id },
                onMoveDown = onMoveDown
            )
        }
        item(key = DISCOVER_CLEAR_PILL_ID) {
            DiscoverPillSurface(
                active = false,
                onClick = onReset,
                modifier = Modifier
                    .focusRequester(requesterFor(DISCOVER_CLEAR_PILL_ID))
                    .focusProperties {
                        if (blockFocus) canFocus = false
                        if (upRequester != null) up = upRequester
                    }
                    .onFocusChanged { if (it.isFocused) lastFocusedId = DISCOVER_CLEAR_PILL_ID }
                    .onPreviewKeyEvent { event ->
                        event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                            event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN &&
                            onMoveDown()
                    }
            ) {
                Text(
                    text = stringResource(R.string.discover_filters_clear_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = NuvioTheme.colors.TextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

private const val DISCOVER_CLEAR_PILL_ID = "clear_all"

/**
 * Movies / Series / Anime segmented switch. Every segment is focusable; the selected one is
 * filled with the accent. Down moves to [downRequester] (the filter pill row).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun DiscoverTypeSwitch(
    types: List<DiscoverOption>,
    selectedType: String,
    requesterFor: (String) -> FocusRequester,
    downRequester: FocusRequester,
    blockFocus: Boolean,
    onSelectType: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        types.forEach { type ->
            val selected = type.value == selectedType
            Card(
                onClick = { onSelectType(type.value) },
                modifier = Modifier
                    .focusRequester(requesterFor(type.value))
                    .focusProperties {
                        down = downRequester
                        if (blockFocus) canFocus = false
                    },
                shape = CardDefaults.shape(shape = PillShape),
                colors = CardDefaults.colors(
                    containerColor = if (selected) NuvioTheme.colors.Secondary else Color.Transparent,
                    focusedContainerColor = if (selected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.22f)
                ),
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                        shape = PillShape
                    )
                ),
                scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
            ) {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}
