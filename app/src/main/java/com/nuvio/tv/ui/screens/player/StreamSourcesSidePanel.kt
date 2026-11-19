@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.components.RefreshFilterChip
import com.nuvio.tv.ui.components.SourceChipStatus
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch as coroutineLaunch

private val PanelRowShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val UhdGold = Color(0xFFE8C062)

/** Rows built from a specific [source] list, so the UI can tell when they are still catching up. */
private class SourceRowsSnapshot(val source: List<Stream>, val rows: List<SourceRowModel>)

/**
 * CinemaOS in-player sources panel, modelled on the phone app's ServerBrowser
 * (components/player/VideoPlayer.tsx:2420-2537) as two TV columns: providers on the left, the
 * focused provider's servers on the right. Hosted in the same PlayerOverlayScaffold as the
 * Audio/Subtitle menus. Uses cached source results; the refresh chip re-fetches on demand.
 */
@Composable
internal fun StreamSourcesSidePanel(
    visible: Boolean,
    uiState: PlayerUiState,
    currentFilename: String?,
    downloadHeaders: Map<String, String> = emptyMap(),
    streamsFocusRequester: FocusRequester,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onAddonFilterSelected: (String?) -> Unit,
    onStreamSelected: (Stream) -> Unit,
    onExpandStreams: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (uiState.sourcesPanelQualityMode) {
        StreamQualityPanel(
            visible = visible,
            uiState = uiState,
            currentFilename = currentFilename,
            onClose = onClose,
            onStreamSelected = onStreamSelected,
            modifier = modifier
        )
    } else {
        StreamSourcesPanelContent(
            visible, uiState, currentFilename, downloadHeaders, streamsFocusRequester, onClose, onReload,
            onAddonFilterSelected, onStreamSelected, onExpandStreams, modifier
        )
    }
}

@Composable
private fun StreamSourcesPanelContent(
    visible: Boolean,
    uiState: PlayerUiState,
    currentFilename: String?,
    downloadHeaders: Map<String, String> = emptyMap(),
    streamsFocusRequester: FocusRequester,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onAddonFilterSelected: (String?) -> Unit,
    onStreamSelected: (Stream) -> Unit,
    onExpandStreams: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(SourceFormatFilter.ALL) }
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    val chipRequesters = remember { List(SourceFormatFilter.values().size) { FocusRequester() } }
    val providerRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val rightRequester = remember { FocusRequester() }
    val lastServerIndex = remember { mutableStateMapOf<String, Int>() }
    val panelScope = rememberCoroutineScope()
    val providerListState = rememberLazyListState()
    val serverListState = rememberLazyListState()

    val allStreams = uiState.sourceAllStreams

    // Parse every stream into a display row on a background thread. This composable stays in the
    // composition while the panel is hidden, so the rows are already built by the time it opens;
    // badge/debrid updates only re-key cached parses instead of re-running the regexes.
    val built by produceState(SourceRowsSnapshot(emptyList(), emptyList()), allStreams) {
        val rows = withContext(Dispatchers.Default) { buildSourceRows(allStreams) }
        value = SourceRowsSnapshot(allStreams, rows)
    }
    val rowsPending = built.source !== allStreams && allStreams.isNotEmpty()

    val currentIdx = remember(
        allStreams, uiState.currentStreamInfoHash, uiState.currentStreamFileIdx, uiState.currentStreamAddonName,
        uiState.currentStreamUrl, uiState.currentStreamName, uiState.currentStreamDescription, currentFilename
    ) {
        findCurrentStreamIndex(
            streams = allStreams,
            currentStreamInfoHash = uiState.currentStreamInfoHash,
            currentStreamFileIdx = uiState.currentStreamFileIdx,
            currentStreamAddonName = uiState.currentStreamAddonName,
            currentStreamUrl = uiState.currentStreamUrl,
            currentStreamName = uiState.currentStreamName,
            currentStreamDescription = uiState.currentStreamDescription,
            currentFilename = currentFilename
        )
    }
    val currentStream = allStreams.getOrNull(currentIdx)
    val currentProvider = currentStream?.addonName
    val currentKey = remember(currentStream) { currentStream?.sourceContentKey() }

    val groups = remember(built, filter, currentProvider) {
        groupSourceRows(built.rows, filter, currentProvider)
    }
    val serverCount = remember(groups) { groups.sumOf { it.rows.size } }

    // The right column follows the focused provider; default to the one that is playing.
    val activeGroup = groups.firstOrNull { it.name == selectedProvider }
        ?: groups.firstOrNull { it.name == currentProvider }
        ?: groups.firstOrNull()
    val activeProvider = activeGroup?.name
    val activeServers = activeGroup?.rows.orEmpty()
    val rightTargetIdx = lastServerIndex[activeProvider]?.takeIf { it in activeServers.indices }
        ?: activeServers.indexOfFirst { it.contentKey == currentKey }.takeIf { it >= 0 }
        ?: 0
    val focusServers: () -> Unit = {
        if (activeServers.isNotEmpty()) {
            panelScope.coroutineLaunch {
                serverListState.scrollToItem(rightTargetIdx)
                withFrameNanos { }
                runCatching { rightRequester.requestFocus() }
            }
        }
    }

    // Browsing providers always shows the top of the newly selected provider's servers.
    LaunchedEffect(activeProvider) {
        if (serverListState.firstVisibleItemIndex != 0 || serverListState.firstVisibleItemScrollOffset != 0) {
            serverListState.scrollToItem(0)
        }
    }

    // Initial focus: the playing provider's card (or the first one). Lands on the first frame the
    // card is laid out instead of waiting a fixed number of frames plus 60 ms retries.
    var focusDone by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) focusDone = false }
    val hasGroups = groups.isNotEmpty()
    LaunchedEffect(visible, hasGroups) {
        if (!visible || focusDone || !hasGroups) return@LaunchedEffect
        val target = groups.indexOfFirst { it.name == currentProvider }.coerceAtLeast(0)
        val targetName = groups[target].name
        if (providerListState.layoutInfo.visibleItemsInfo.none { it.key == targetName }) {
            providerListState.scrollToItem(target)
        }
        repeat(12) {
            withFrameNanos { }
            val req = providerRequesters[targetName]
            if (req != null && runCatching { req.requestFocus() }.isSuccess) {
                focusDone = true
                return@LaunchedEffect
            }
        }
    }
    // Nothing to land on yet: keep focus inside the panel.
    LaunchedEffect(visible, hasGroups) {
        if (visible && !hasGroups) {
            withFrameNanos { }
            runCatching { chipRequesters[filter.ordinal].requestFocus() }
        }
    }

    val searching = uiState.isLoadingSourceStreams ||
        uiState.sourceChips.any { it.status == SourceChipStatus.LOADING }
    val showSkeletons = rowsPending || (groups.isEmpty() && searching)

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onClose,
        modifier = modifier,
        captureKeys = false,
        overlayTint = Color.Black.copy(alpha = 0.45f),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .width(900.dp)
                .align(Alignment.TopStart)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Top
        ) {
            // Header: title + live summary.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sources_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                SourceSummaryPill(
                    providers = groups.size,
                    servers = serverCount,
                    searching = searching
                )
            }

            val context = LocalContext.current
            val seasonEpisodeCode = if (uiState.currentSeason != null && uiState.currentEpisode != null) {
                stringResource(R.string.season_episode_format, uiState.currentSeason, uiState.currentEpisode)
            } else null
            val episodeTitle = uiState.currentEpisodeTitle?.takeIf { it.isNotBlank() }?.localizeEpisodeTitle(context)
            Text(
                text = when {
                    seasonEpisodeCode != null && episodeTitle != null -> "$seasonEpisodeCode • $episodeTitle"
                    seasonEpisodeCode != null -> seasonEpisodeCode
                    else -> uiState.title
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.md)
            )

            PlayerDownloadAction(uiState, currentFilename, downloadHeaders,
                Modifier.padding(bottom = NuvioTheme.spacing.sm))

            // Filter chips (RN FILTERS) + refresh.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.md)
            ) {
                SourceFormatFilter.values().forEachIndexed { i, f ->
                    SourceFilterChip(
                        label = f.label,
                        selected = filter == f,
                        focusRequester = chipRequesters[i],
                        downTarget = {
                            providerRequesters[activeProvider]
                                ?.takeIf { providerListState.layoutInfo.visibleItemsInfo.any { it.key == activeProvider } }
                                ?: FocusRequester.Default
                        },
                        onSelect = { filter = f }
                    )
                }
                RefreshFilterChip(
                    onClick = onReload,
                    isLoading = searching
                )
            }

            val emptyMessage: String? = when {
                uiState.sourceStreamsError != null && allStreams.isEmpty() -> uiState.sourceStreamsError
                groups.isEmpty() && !searching && !rowsPending -> stringResource(R.string.sources_no_streams)
                else -> null
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                verticalAlignment = Alignment.Top
            ) {
                // Left column: providers.
                Column(modifier = Modifier.width(330.dp).fillMaxHeight()) {
                    SourceColumnHeading(text = "Providers")
                    LazyColumn(
                        state = providerListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        if (groups.isEmpty() && showSkeletons) {
                            items(count = 6) { SourcePanelSkeletonRow(64.dp) }
                        }
                        itemsIndexed(items = groups, key = { _, g -> g.name }) { provIdx, group ->
                            val req = providerRequesters.getOrPut(group.name) {
                                if (group.name == groups.firstOrNull()?.name) streamsFocusRequester else FocusRequester()
                            }
                            SourceProviderCard(
                                provider = group,
                                playing = group.name == currentProvider,
                                shown = group.name == activeProvider,
                                focusRequester = req,
                                // Up: previous provider (default search); only the first goes to the chips.
                                upRequester = if (provIdx == 0) chipRequesters[filter.ordinal] else null,
                                onFocused = { selectedProvider = group.name },
                                onOpenServers = focusServers
                            )
                        }
                    }
                }

                // Right column: the focused provider's servers.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                ) {
                    SourceColumnHeading(
                        text = activeProvider?.let { "Servers · $it" } ?: "Servers",
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    LazyColumn(
                        state = serverListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        if (activeServers.isEmpty()) {
                            if (emptyMessage != null) {
                                item(key = "empty") {
                                    Text(
                                        text = emptyMessage,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.padding(NuvioTheme.spacing.md)
                                    )
                                }
                            } else {
                                items(count = 6) { SourcePanelSkeletonRow(76.dp) }
                            }
                        }
                        itemsIndexed(items = activeServers, key = { _, r -> r.key }) { i, row ->
                            SourceServerRow(
                                row = row,
                                active = row.contentKey == currentKey,
                                focusRequester = if (i == rightTargetIdx) rightRequester else null,
                                leftRequester = providerRequesters[activeProvider],
                                // Up: previous server (default search); only the first goes to the chips.
                                upRequester = if (i == 0) chipRequesters[filter.ordinal] else null,
                                onFocused = { activeProvider?.let { p -> lastServerIndex[p] = i } },
                                onClick = { onStreamSelected(row.stream) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceSummaryPill(providers: Int, servers: Int, searching: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.1f), PillShape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        if (searching) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(PillShape)
                    .background(NuvioTheme.colors.Secondary)
            )
        }
        Text(
            text = "$providers providers · $servers servers" + if (searching) " · searching…" else "",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

@Composable
private fun SourceColumnHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun SourceFilterChip(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    downTarget: () -> FocusRequester,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fg = when {
        focused -> Color.Black
        selected -> NuvioTheme.colors.OnSecondary
        else -> Color.White.copy(alpha = 0.85f)
    }
    Card(
        onClick = onSelect,
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusProperties { down = downTarget() }
            .onFocusChanged {
                focused = it.isFocused
                // Moving across the chips applies the filter right away.
                if (it.isFocused) onSelect()
            },
        colors = CardDefaults.colors(
            containerColor = if (selected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White
        ),
        shape = CardDefaults.shape(PillShape),
        border = sourcePanelBorder(999.dp),
        scale = CardDefaults.scale(focusedScale = 1.08f, pressedScale = 1f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SourceProviderCard(
    provider: SourceProviderModel,
    playing: Boolean,
    shown: Boolean,
    focusRequester: FocusRequester,
    upRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpenServers: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    val secondary = if (focused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
    val accent = when {
        focused -> Color.Black
        shown -> NuvioTheme.colors.Secondary
        else -> Color.Transparent
    }
    Card(
        onClick = onOpenServers,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties { if (upRequester != null) up = upRequester }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
                    onOpenServers(); true
                } else false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        colors = CardDefaults.colors(
            containerColor = if (shown) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White
        ),
        shape = CardDefaults.shape(PanelRowShape),
        border = sourcePanelBorder(14.dp),
        scale = CardDefaults.scale(focusedScale = 1.03f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(PillShape)
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (playing) NowPlayingPill(compact = true, onLight = focused)
                    Text(
                        text = "${provider.rows.size} server${if (provider.rows.size != 1) "s" else ""} · ${provider.formats}",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (provider.bestQuality > 0) {
                QualityBadge(label = sourceQualityLabel(provider.bestQuality), quality = provider.bestQuality, onLight = focused)
            }
        }
    }
}

@Composable
private fun SourceServerRow(
    row: SourceRowModel,
    active: Boolean,
    focusRequester: FocusRequester?,
    leftRequester: FocusRequester?,
    upRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    val secondary = if (focused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                left = leftRequester ?: FocusRequester.Default
                if (upRequester != null) up = upRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        colors = CardDefaults.colors(
            containerColor = if (active) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White
        ),
        shape = CardDefaults.shape(PanelRowShape),
        border = sourcePanelBorder(14.dp),
        scale = CardDefaults.scale(focusedScale = 1.02f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(PillShape)
                    .background(
                        when {
                            !active -> Color.Transparent
                            focused -> Color.Black
                            else -> NuvioTheme.colors.Secondary
                        }
                    )
            )
            QualityTile(label = row.qualityLabel, format = row.format, quality = row.quality, onLight = focused)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (focused || active) FontWeight.SemiBold else FontWeight.Normal,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                SourceSpecChips(specs = row.specs, selected = focused, maxChips = 7)
                if (row.detail != null) {
                    Text(
                        text = row.detail,
                        style = MaterialTheme.typography.labelLarge,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (active) NowPlayingPill(compact = false, onLight = focused)
        }
    }
}

/** Big quality block at the start of a server row: "4K" / "1080p" / "Auto" over the container format. */
@Composable
private fun QualityTile(label: String, format: String, quality: Int, onLight: Boolean) {
    val uhd = quality >= 2160
    val bg = when {
        uhd -> UhdGold
        onLight -> Color.Black.copy(alpha = 0.08f)
        quality >= 1080 -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.1f)
    }
    val fg = if (uhd || onLight) Color.Black else Color.White
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(78.dp)
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1
        )
        if (format.isNotBlank()) {
            Text(
                text = format,
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QualityBadge(label: String, quality: Int, onLight: Boolean) {
    val uhd = quality >= 2160
    Box(
        modifier = Modifier
            .background(
                when {
                    uhd -> UhdGold
                    onLight -> Color.Black.copy(alpha = 0.1f)
                    else -> Color.White.copy(alpha = 0.14f)
                },
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (uhd || onLight) Color.Black else Color.White
        )
    }
}

@Composable
private fun NowPlayingPill(compact: Boolean, onLight: Boolean) {
    Box(
        modifier = Modifier
            .background(if (onLight) Color.Black else NuvioTheme.colors.Secondary, PillShape)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 2.dp else 5.dp)
    ) {
        Text(
            text = "PLAYING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (onLight) Color.White else NuvioTheme.colors.OnSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun SourcePanelSkeletonRow(height: Dp) {
    val t = rememberInfiniteTransition(label = "srcSkel")
    val a by t.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "srcSkelA"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(PanelRowShape)
            .background(Color.White.copy(alpha = a))
    )
}

/** No outline: focus is shown by the white fill, which reads from across the room. */
@Composable
private fun sourcePanelBorder(radius: Dp) = CardDefaults.border(
    border = Border(
        border = BorderStroke(0.dp, Color.Transparent),
        shape = RoundedCornerShape(radius)
    ),
    focusedBorder = Border(
        border = BorderStroke(0.dp, Color.Transparent),
        shape = RoundedCornerShape(radius)
    ),
    pressedBorder = Border(
        border = BorderStroke(0.dp, Color.Transparent),
        shape = RoundedCornerShape(radius)
    )
)

internal fun findCurrentStreamIndex(
    streams: List<Stream>,
    currentStreamInfoHash: String?,
    currentStreamFileIdx: Int?,
    currentStreamAddonName: String?,
    currentStreamUrl: String?,
    currentStreamName: String?,
    currentStreamDescription: String?,
    currentFilename: String?
): Int {
    if (streams.isEmpty()) return -1

    // Strategy 1: match by infoHash + fileIdx + addonName (most precise for debrid streams)
    if (!currentStreamInfoHash.isNullOrBlank()) {
        val hashMatch = streams.indexOfFirst { stream ->
            val streamInfoHash = stream.infoHash ?: stream.clientResolve?.infoHash
            val streamFileIdx = stream.fileIdx ?: stream.clientResolve?.fileIdx
            streamInfoHash.equals(currentStreamInfoHash, ignoreCase = true) &&
                (currentStreamFileIdx == null || streamFileIdx == currentStreamFileIdx) &&
                (currentStreamAddonName == null || stream.addonName == currentStreamAddonName)
        }
        if (hashMatch >= 0) return hashMatch
    }

    // Strategy 2: match by addon + URL (works for non-debrid HTTP streams)
    if (!currentStreamUrl.isNullOrBlank() && !currentStreamAddonName.isNullOrBlank()) {
        val urlMatch = streams.indexOfFirst { stream ->
            stream.addonName == currentStreamAddonName &&
                stream.getStreamUrl() == currentStreamUrl
        }
        if (urlMatch >= 0) return urlMatch
    }

    // Fallback: match by URL only (without addon filter)
    if (!currentStreamUrl.isNullOrBlank()) {
        val urlMatch = streams.indexOfFirst { stream ->
            stream.getStreamUrl() == currentStreamUrl
        }
        if (urlMatch >= 0) return urlMatch
    }

    // Addons may issue a new URL per request, so match what identifies the release instead.
    // Filenames and templates can repeat across streams, so only trust a single result.
    if (!currentFilename.isNullOrBlank()) {
        val filenameMatches = streams.indices.filter { index ->
            val stream = streams[index]
            (stream.behaviorHints?.filename ?: stream.clientResolve?.filename) == currentFilename &&
                (currentStreamAddonName == null || stream.addonName == currentStreamAddonName)
        }
        filenameMatches.singleOrNull()?.let { return it }
    }

    if (!currentStreamName.isNullOrBlank()) {
        val nameMatches = streams.indices.filter { index ->
            val stream = streams[index]
            (stream.name ?: stream.addonName) == currentStreamName &&
                (currentStreamAddonName == null || stream.addonName == currentStreamAddonName) &&
                (currentStreamDescription == null || stream.description == currentStreamDescription)
        }
        nameMatches.singleOrNull()?.let { return it }
    }

    return -1
}

/**
 * CinemaOS quality picker: lists the qualities the CURRENT provider offers (Auto = unknown quality),
 * with the playing one checked. Selecting switches to the best link of that quality from the same
 * provider, falling back to any provider.
 */
@Composable
private fun StreamQualityPanel(
    visible: Boolean,
    uiState: PlayerUiState,
    currentFilename: String?,
    onClose: () -> Unit,
    onStreamSelected: (Stream) -> Unit,
    modifier: Modifier = Modifier
) {
    val allStreams = uiState.sourceAllStreams
    val currentIdx = remember(
        allStreams, uiState.currentStreamInfoHash, uiState.currentStreamFileIdx, uiState.currentStreamAddonName,
        uiState.currentStreamUrl, uiState.currentStreamName, uiState.currentStreamDescription, currentFilename
    ) {
        findCurrentStreamIndex(
            streams = allStreams,
            currentStreamInfoHash = uiState.currentStreamInfoHash,
            currentStreamFileIdx = uiState.currentStreamFileIdx,
            currentStreamAddonName = uiState.currentStreamAddonName,
            currentStreamUrl = uiState.currentStreamUrl,
            currentStreamName = uiState.currentStreamName,
            currentStreamDescription = uiState.currentStreamDescription,
            currentFilename = currentFilename
        )
    }
    val currentStream = allStreams.getOrNull(currentIdx)
    val provider = currentStream?.addonName ?: uiState.currentStreamAddonName
    // Quality per stream is parsed once here instead of once per row per recomposition.
    val byQuality = remember(allStreams, provider) {
        allStreams.filter { !it.isExternal() && (provider == null || it.addonName == provider) }
            .groupBy { it.sourceQuality() }
    }
    val anyByQuality = remember(allStreams) {
        allStreams.filter { !it.isExternal() }.groupBy { it.sourceQuality() }
    }
    val qualities = remember(byQuality) { byQuality.keys.sortedDescending() }
    val currentQuality = remember(currentStream) { currentStream?.sourceQuality() }
    val requesters = remember(qualities.size) { List(qualities.size) { FocusRequester() } }

    LaunchedEffect(visible, qualities) {
        if (!visible || qualities.isEmpty()) return@LaunchedEffect
        val target = qualities.indexOf(currentQuality).coerceAtLeast(0)
        repeat(12) {
            withFrameNanos { }
            if (runCatching { requesters[target].requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onClose,
        modifier = modifier,
        captureKeys = false,
        overlayTint = Color.Black.copy(alpha = 0.45f),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 56.dp)
    ) {
        Column(
            modifier = Modifier
                .width(580.dp)
                .align(Alignment.BottomStart)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "Quality",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            if (provider != null) {
                Text(
                    text = provider,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
                )
            }
            if (qualities.isEmpty()) {
                Text(
                    text = if (uiState.isLoadingSourceStreams) "Loading qualities…"
                    else stringResource(R.string.sources_no_streams),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                ) {
                    itemsIndexed(items = qualities, key = { _, q -> q }) { i, q ->
                        val sameProvider = byQuality[q].orEmpty()
                        val pick = remember(sameProvider, anyByQuality, q, currentStream) {
                            sameProvider.firstOrNull { it !== currentStream }
                                ?: sameProvider.firstOrNull()
                                ?: anyByQuality[q]?.firstOrNull()
                        }
                        val active = q == currentQuality
                        val shownStream = if (active && currentStream != null) currentStream else pick
                        QualityOptionRow(
                            quality = q,
                            stream = shownStream,
                            provider = provider,
                            count = sameProvider.size,
                            active = active,
                            focusRequester = requesters[i],
                            onClick = {
                                if (pick != null && pick !== currentStream) onStreamSelected(pick) else onClose()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityOptionRow(
    quality: Int,
    stream: Stream?,
    provider: String?,
    count: Int,
    active: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val parsed = remember(stream) { stream?.parsedSource() }
    val fg = if (focused) Color.Black else Color.White
    val secondary = if (focused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (active) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White
        ),
        shape = CardDefaults.shape(PanelRowShape),
        border = sourcePanelBorder(14.dp),
        scale = CardDefaults.scale(focusedScale = 1.02f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(PillShape)
                    .background(
                        when {
                            !active -> Color.Transparent
                            focused -> Color.Black
                            else -> NuvioTheme.colors.Secondary
                        }
                    )
            )
            QualityTile(
                label = com.nuvio.tv.core.player.DeviceVideoCapabilities.badgeFor(quality),
                format = parsed?.format ?: "",
                quality = quality,
                onLight = focused
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = listOfNotNull(
                        if (quality > 0) "${quality}p" else "Auto",
                        parsed?.size ?: formatSourceSize(stream?.behaviorHints?.videoSize)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = fg
                )
                SourceSpecChips(specs = parsed?.specs.orEmpty(), selected = focused, maxChips = 7)
                Text(
                    text = listOfNotNull(provider, "$count link${if (count != 1) "s" else ""}")
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (active) NowPlayingPill(compact = false, onLight = focused)
        }
    }
}
