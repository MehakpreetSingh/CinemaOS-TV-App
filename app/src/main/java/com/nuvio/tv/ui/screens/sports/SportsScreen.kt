package com.nuvio.tv.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.SportsAddon
import com.nuvio.tv.ui.components.ContentCard
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.ErrorState
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.SourceStatusFilterChip
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Sports tab: live, upcoming, per-sport, 24/7 TV and replay rows from the built-in Sports addon.
 * Selecting a card opens the regular details screen for that addon item, whose play action goes
 * through the normal streams screen to the player.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SportsScreen(
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit,
    viewModel: SportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Live Now refreshes only while this screen is started (visible).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenHidden()
        }
    }

    val horizontalPadding = NuvioTheme.spacing.xxl

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = NuvioTheme.spacing.xl, bottom = NuvioTheme.spacing.huge),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xl)
    ) {
        item(key = "sports_header", contentType = "header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.sports_title),
                    style = NuvioTheme.textStyles.headline,
                    color = NuvioTheme.colors.TextPrimary,
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                )
                if (uiState.filters.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = NuvioTheme.spacing.md)
                            .focusRestorer(),
                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
                    ) {
                        item(key = "filter_all") {
                            SourceStatusFilterChip(
                                name = stringResource(R.string.sports_filter_all),
                                isSelected = uiState.selectedSportId == null,
                                onClick = { viewModel.selectSport(null) }
                            )
                        }
                        items(uiState.filters, key = { "filter_${it.id}" }) { filter ->
                            SportsFilterChip(
                                filter = filter,
                                isSelected = uiState.selectedSportId == filter.id,
                                onSelect = viewModel::selectSport
                            )
                        }
                    }
                }
            }
        }

        val errorRes = uiState.errorRes
        when {
            uiState.rows.isEmpty() && errorRes != null -> item(key = "sports_error", contentType = "state") {
                ErrorState(
                    message = stringResource(errorRes),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)
                )
            }
            uiState.rows.isEmpty() && uiState.isLoading -> item(key = "sports_loading", contentType = "state") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = NuvioTheme.spacing.huge),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            uiState.rows.isEmpty() -> item(key = "sports_empty", contentType = "state") {
                EmptyScreenState(
                    title = stringResource(R.string.sports_empty_title),
                    subtitle = stringResource(R.string.sports_empty_subtitle),
                    icon = Icons.Default.SportsSoccer
                )
            }
            else -> items(uiState.rows, key = { it.key }, contentType = { "sports_row" }) { row ->
                SportsRow(
                    row = row,
                    horizontalPadding = horizontalPadding,
                    onItemClick = { card ->
                        onNavigateToDetail(card.preview.id, card.preview.apiType, SportsAddon.BASE_URL)
                    }
                )
            }
        }
    }
}

@Composable
private fun SportsFilterChip(
    filter: SportFilter,
    isSelected: Boolean,
    onSelect: (String?) -> Unit
) {
    SourceStatusFilterChip(
        name = filter.label,
        isSelected = isSelected,
        onClick = { onSelect(filter.id) }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SportsRow(
    row: SportsRowModel,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onItemClick: (SportsCardModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.titleRes?.let { stringResource(it) } ?: row.title.orEmpty(),
            style = NuvioTheme.textStyles.sectionTitle,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = NuvioTheme.spacing.sm)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            items(row.items, key = { it.key }, contentType = { "sports_card" }) { card ->
                Box {
                    ContentCard(
                        item = card.preview,
                        showImdbRatings = false,
                        onClick = { onItemClick(card) }
                    )
                    if (card.isLive) {
                        LiveBadge(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(NuvioTheme.spacing.sm)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.sports_live_badge),
        color = NuvioTheme.colors.OnPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(NuvioTheme.colors.Error, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
