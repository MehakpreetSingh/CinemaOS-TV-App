package com.nuvio.tv.ui.screens.search

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.Locale

/** Value of the "any" row of a filter picker; never sent to the API. */
internal const val DISCOVER_ANY_VALUE = "__default__"

private const val FILTERS_PER_ROW = 4

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
        DiscoverFilterKey.SORT, DiscoverFilterKey.GENRE -> R.string.discover_genre_default
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

private fun languageLabel(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
        .takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
        ?: code.uppercase()

private fun countryLabel(code: String): String =
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
        .takeIf { it.isNotBlank() } ?: code

/** Options of one extra filter picker, "any" row first (except Sort and required addon years). */
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

/**
 * Rows of extra filter pickers (sort, year, rating, ...) followed by a reset button. Uses the same
 * [DiscoverDropdownPicker] as the Type / Catalog / Genre row, so D-pad focus, popup handling and
 * restore behave identically.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun DiscoverExtraFilterRows(
    keys: List<DiscoverFilterKey>,
    uiState: SearchUiState,
    catalog: DiscoverCatalog,
    expandedPicker: String?,
    onExpandedChange: (String?) -> Unit,
    onSelectFilter: (DiscoverFilterKey, String?) -> Unit,
    onReset: () -> Unit,
    blockFocus: Boolean,
    modifier: Modifier = Modifier
) {
    if (keys.isEmpty()) return
    val context = LocalContext.current
    // The last slot is the reset button.
    val slots: List<DiscoverFilterKey?> = keys + listOf(null)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        slots.chunked(FILTERS_PER_ROW).forEach { rowSlots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
            ) {
                rowSlots.forEach { key ->
                    if (key == null) {
                        DiscoverResetButton(
                            modifier = Modifier.weight(1f),
                            onClick = onReset,
                            blockFocus = blockFocus
                        )
                    } else {
                        val options = discoverFilterOptions(context, key, catalog)
                        val current = currentFilterValue(uiState, catalog, key)
                        val selectedValue = current ?: if (key == DiscoverFilterKey.SORT) {
                            DiscoverSort.POPULAR
                        } else {
                            DISCOVER_ANY_VALUE
                        }
                        val pickerId = discoverFilterPickerId(key)
                        DiscoverDropdownPicker(
                            modifier = Modifier.weight(1f),
                            title = discoverFilterTitle(key),
                            value = options.firstOrNull { it.value == selectedValue }?.label
                                ?: options.firstOrNull()?.label.orEmpty(),
                            selectedValue = selectedValue,
                            expanded = expandedPicker == pickerId,
                            options = options,
                            onExpandedChange = { shouldExpand ->
                                onExpandedChange(if (shouldExpand) pickerId else null)
                            },
                            onSelect = { option ->
                                onSelectFilter(key, option.value.takeUnless { it == DISCOVER_ANY_VALUE })
                                onExpandedChange(null)
                            },
                            blockFocus = blockFocus
                        )
                    }
                }
                // Keep every picker the same width on a short last row.
                repeat(FILTERS_PER_ROW - rowSlots.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiscoverResetButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    blockFocus: Boolean
) {
    Card(
        onClick = onClick,
        modifier = modifier.then(
            if (blockFocus) Modifier.focusProperties { canFocus = false } else Modifier
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(50)),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.2f)
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                shape = RoundedCornerShape(50)
            ),
            focusedBorder = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(50)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.discover_filters_clear_all),
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
