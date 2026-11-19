@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch as coroutineLaunch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.mutableStateMapOf
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.SourceChipStatus
import com.nuvio.tv.ui.theme.NuvioTheme
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import androidx.compose.ui.platform.LocalContext

private enum class SourceFormatFilter(val label: String) {
    ALL("All"), HD("1080p+"), FILE("MP4 / MKV"), HLS("HLS"), DASH("DASH")
}

private fun Stream.sourceQuality(): Int {
    if (qualityValue > 0) return qualityValue
    val text = listOfNotNull(name, title, description).joinToString(" ")
    if (Regex("4k|2160|uhd", RegexOption.IGNORE_CASE).containsMatchIn(text)) return 2160
    return Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

private fun Stream.sourceFormat(): String {
    val u = (getStreamUrl() ?: return "P2P").lowercase().substringBefore('?')
    return when {
        u.contains(".m3u8") || u.contains("/hls") -> "HLS"
        u.contains(".mpd") -> "DASH"
        u.contains(".mkv") -> "MKV"
        else -> "MP4"
    }
}

private fun qualityLabel(q: Int): String? = when {
    q >= 2160 -> "4K"
    q > 0 -> "${q}p"
    else -> null
}

private fun formatSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1) String.format("%.1f GB", gb) else String.format("%.0f MB", bytes / 1_048_576.0)
}

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
            visible, uiState, currentFilename, streamsFocusRequester, onClose, onReload,
            onAddonFilterSelected, onStreamSelected, onExpandStreams, modifier
        )
    }
}

@Composable
private fun StreamSourcesPanelContent(
    visible: Boolean,
    uiState: PlayerUiState,
    currentFilename: String?,
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

    val groups = remember(allStreams, filter, currentProvider) {
        val kept = allStreams.filter { s ->
            !s.isExternal() && when (filter) {
                SourceFormatFilter.ALL -> true
                SourceFormatFilter.HD -> s.sourceQuality() >= 1080
                SourceFormatFilter.FILE -> s.sourceFormat().let { it == "MP4" || it == "MKV" }
                SourceFormatFilter.HLS -> s.sourceFormat() == "HLS"
                SourceFormatFilter.DASH -> s.sourceFormat() == "DASH"
            }
        }
        kept.groupBy { it.addonName }
            .map { (prov, list) ->
                val sorted = list.sortedByDescending { it.sourceQuality() }
                // Every distinct link is its own row (the quality panel keeps its own per-quality grouping).
                val rows = sorted.distinctBy { it.getStreamUrl() ?: System.identityHashCode(it) }
                prov to rows
            }
            .sortedWith(
                compareByDescending<Pair<String, List<Stream>>> { it.first == currentProvider }
                    .thenByDescending { it.first.let { n -> com.nuvio.tv.core.player.autoPlayProviderRank(n) } }
                    .thenByDescending { (_, l) -> l.maxOf { it.sourceQuality() } }
            )
    }

    // The right column follows the focused provider; default to the one that is playing.
    val activeProvider = groups.firstOrNull { it.first == selectedProvider }?.first
        ?: groups.firstOrNull { it.first == currentProvider }?.first
        ?: groups.firstOrNull()?.first
    val activeServers = groups.firstOrNull { it.first == activeProvider }?.second.orEmpty()
    val rightTargetIdx = lastServerIndex[activeProvider]?.takeIf { it in activeServers.indices }
        ?: activeServers.indexOfFirst { it === currentStream }.takeIf { it >= 0 }
        ?: 0
    val focusServers: () -> Unit = {
        panelScope.coroutineLaunch {
            serverListState.scrollToItem(rightTargetIdx)
            withFrameNanos { }
            runCatching { rightRequester.requestFocus() }
        }
    }

    // Initial focus: the playing provider's card (or the first one) once results are there.
    var focusDone by remember { mutableStateOf(false) }
    LaunchedEffect(visible, groups.isNotEmpty(), currentProvider) {
        if (!visible) { focusDone = false; return@LaunchedEffect }
        if (focusDone || groups.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val target = (groups.indexOfFirst { it.first == currentProvider }).coerceAtLeast(0)
        providerListState.scrollToItem(target)
        withFrameNanos { }
        repeat(6) {
            val req = providerRequesters[groups[target].first]
            if (req != null && runCatching { req.requestFocus() }.isSuccess) {
                focusDone = true
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(60)
        }
    }
    // Nothing to land on yet: keep focus inside the panel.
    LaunchedEffect(visible, groups.isEmpty()) {
        if (visible && groups.isEmpty()) {
            withFrameNanos { }
            runCatching { chipRequesters[0].requestFocus() }
        }
    }

    val searching = uiState.isLoadingSourceStreams ||
        uiState.sourceChips.any { it.status == SourceChipStatus.LOADING }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onClose,
        modifier = modifier,
        captureKeys = false,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp)
    ) {
        Column(
            modifier = Modifier
                .width(900.dp)
                .align(Alignment.TopStart)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(R.string.sources_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.xs)
            )

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
                color = NuvioTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
            )

            val downloadScope = rememberCoroutineScope()
            val downloadSource = currentStream
            if (downloadSource != null) {
                androidx.tv.material3.Button(
                    onClick = {
                        downloadScope.coroutineLaunch {
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    com.nuvio.tv.downloads.OfflineDownloads.enqueue(
                                        context = context,
                                        title = listOfNotNull(uiState.title, seasonEpisodeCode, episodeTitle).distinct().joinToString(" · "),
                                        url = uiState.currentStreamUrl ?: downloadSource.getStreamUrl().orEmpty(),
                                        filename = downloadSource.behaviorHints?.filename ?: currentFilename,
                                        headers = downloadSource.behaviorHints?.proxyHeaders?.request.orEmpty(),
                                        streamType = downloadSource.behaviorHints?.streamType,
                                    )
                                }
                            }
                            val message = result.fold(
                                onSuccess = { "Download started. Open Settings → Downloads to check progress or play offline." },
                                onFailure = { it.message ?: "Could not start download" },
                            )
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm),
                ) { Text("Download playing source") }
            }

            // Filter chips (RN FILTERS) + refresh.
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
            ) {
                SourceFormatFilter.values().forEachIndexed { i, f ->
                    val selected = filter == f
                    androidx.tv.material3.Card(
                        onClick = { filter = f },
                        modifier = Modifier
                            .focusRequester(chipRequesters[i])
                            .focusProperties {
                                down = providerRequesters[activeProvider]
                                    ?.takeIf { providerListState.layoutInfo.visibleItemsInfo.any { it.key == activeProvider } }
                                    ?: FocusRequester.Default
                            }
                            .onFocusChanged { if (it.isFocused) filter = f },
                        colors = androidx.tv.material3.CardDefaults.colors(
                            containerColor = if (selected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = if (selected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.24f)
                        ),
                        shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(999.dp)),
                        border = sourcePanelBorder(999.dp),
                        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.06f, pressedScale = 1f)
                    ) {
                        Text(
                            text = f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) NuvioTheme.colors.OnSecondary else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md, vertical = 6.dp)
                        )
                    }
                }
                com.nuvio.tv.ui.components.RefreshFilterChip(
                    onClick = onReload,
                    isLoading = searching
                )
            }

            Text(
                text = "${groups.size} providers \u00B7 ${groups.sumOf { it.second.size }} servers" +
                    if (searching) " \u00B7 searching\u2026" else "",
                style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.xs)
            )

            val emptyMessage: String? = when {
                uiState.sourceStreamsError != null && allStreams.isEmpty() -> uiState.sourceStreamsError
                groups.isEmpty() && !searching -> stringResource(R.string.sources_no_streams)
                else -> null
            }
            run {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left column: providers.
                    LazyColumn(
                        state = providerListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = NuvioTheme.spacing.xs, bottom = NuvioTheme.spacing.sm),
                        modifier = Modifier.width(340.dp).fillMaxHeight()
                    ) {
                        if (groups.isEmpty()) {
                            items(count = if (searching) 7 else 0) { SourcePanelSkeletonRow(64.dp) }
                        }
                        itemsIndexed(items = groups, key = { _, g -> g.first }) { provIdx, (prov, list) ->
                            val req = providerRequesters.getOrPut(prov) {
                                if (prov == groups.firstOrNull()?.first) streamsFocusRequester else FocusRequester()
                            }
                            val playing = prov == currentProvider
                            val shown = prov == activeProvider
                            val formats = list.map { it.sourceFormat() }.distinct().joinToString(", ")
                            androidx.tv.material3.Card(
                                onClick = { focusServers() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(req)
                                    // Up: previous provider (default search); only the first goes to the chips.
                                    .focusProperties {
                                        if (provIdx == 0) up = chipRequesters[filter.ordinal]
                                    }
                                    .onPreviewKeyEvent {
                                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
                                            focusServers(); true
                                        } else false
                                    }
                                    .onFocusChanged { if (it.isFocused) selectedProvider = prov },
                                colors = androidx.tv.material3.CardDefaults.colors(
                                    containerColor = if (shown) NuvioTheme.colors.Secondary else Color.Transparent,
                                    focusedContainerColor = if (shown) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.22f)
                                ),
                                shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                                border = sourcePanelBorder(NuvioTheme.radii.md),
                                scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.05f, pressedScale = 1f)
                            ) {
                                val primary = if (shown) NuvioTheme.colors.OnSecondary else Color.White
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = NuvioTheme.spacing.md, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                            .background(primary.copy(alpha = if (playing) 1f else 0.25f))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prov,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${list.size} server${if (list.size > 1) "s" else ""} · $formats",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (shown) NuvioTheme.colors.OnSecondary.copy(alpha = 0.72f)
                                            else NuvioTheme.colors.TextTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    qualityLabel(list.maxOf { it.sourceQuality() })?.let { SourcePanelBadge(it, shown) }
                                }
                            }
                        }
                    }

                    // Right column: the focused provider's servers.
                    LazyColumn(
                        state = serverListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = NuvioTheme.spacing.xs, bottom = NuvioTheme.spacing.sm),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        if (activeServers.isEmpty()) {
                            if (emptyMessage != null) {
                                item {
                                    Text(
                                        text = emptyMessage,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(NuvioTheme.spacing.md)
                                    )
                                }
                            } else {
                                items(count = 7) { SourcePanelSkeletonRow(72.dp) }
                            }
                        }
                        itemsIndexed(
                            items = activeServers,
                            key = { i, _ -> "$activeProvider#$i" }
                        ) { i, s ->
                            val active = s === currentStream
                            val isRightTarget = i == rightTargetIdx
                            val title = s.getDisplayNameOrNull() ?: s.name ?: s.addonName
                            val meta = s.pluginSourceName?.takeIf { it.isNotBlank() && it != s.addonName }.orEmpty()
                            val specs = remember(s) { s.sourceSpecs() }
                            val titleColor = if (active) NuvioTheme.colors.OnSecondary else Color.White
                            androidx.tv.material3.Card(
                                onClick = { onStreamSelected(s) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isRightTarget) Modifier.focusRequester(rightRequester) else Modifier)
                                    .focusProperties {
                                        left = providerRequesters[activeProvider] ?: FocusRequester.Default
                                        // Up: previous server (default search); only the first goes to the chips.
                                        if (i == 0) up = chipRequesters[filter.ordinal]
                                    }
                                    .onFocusChanged { if (it.isFocused) activeProvider?.let { p -> lastServerIndex[p] = i } },
                                colors = androidx.tv.material3.CardDefaults.colors(
                                    containerColor = if (active) NuvioTheme.colors.Secondary else Color.Transparent,
                                    focusedContainerColor = if (active) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.22f)
                                ),
                                shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                                border = sourcePanelBorder(NuvioTheme.radii.md),
                                scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.04f, pressedScale = 1f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = NuvioTheme.spacing.md, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SourcePanelBadge(qualityLabel(s.sourceQuality()) ?: "Auto", active)
                                    SourcePanelBadge(s.sourceFormat(), active)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = titleColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        SourceSpecChips(
                                            specs = specs,
                                            selected = active,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        if (meta.isNotBlank()) {
                                            Text(
                                                text = meta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (active) NuvioTheme.colors.OnSecondary.copy(alpha = 0.72f)
                                                else NuvioTheme.colors.TextTertiary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (active) {
                                        Text(text = "\u2713", style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.OnSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePanelSkeletonRow(height: androidx.compose.ui.unit.Dp) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "srcSkel")
    val a by t.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "srcSkelA"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(NuvioTheme.radii.md))
            .background(Color.White.copy(alpha = a))
    )
}

@Composable
private fun SourcePanelBadge(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .background(
                if (selected) NuvioTheme.colors.OnSecondary.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = NuvioTheme.spacing.sm, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) NuvioTheme.colors.OnSecondary.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun sourcePanelBorder(radius: androidx.compose.ui.unit.Dp) = androidx.tv.material3.CardDefaults.border(
    border = androidx.tv.material3.Border(
        border = androidx.compose.foundation.BorderStroke(NuvioTheme.spacing.xxs, Color.Transparent),
        shape = RoundedCornerShape(radius)
    ),
    focusedBorder = androidx.tv.material3.Border(
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
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
    val candidates = remember(allStreams, provider) {
        allStreams.filter { !it.isExternal() && (provider == null || it.addonName == provider) }
    }
    val qualities = remember(candidates) { candidates.map { it.sourceQuality() }.distinct().sortedDescending() }
    val currentQuality = currentStream?.sourceQuality()
    val requesters = remember(qualities.size) { List(qualities.size) { FocusRequester() } }

    LaunchedEffect(visible, qualities) {
        if (!visible || qualities.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val target = qualities.indexOf(currentQuality).coerceAtLeast(0)
        runCatching { requesters[target].requestFocus() }
    }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onClose,
        modifier = modifier,
        captureKeys = false,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp)
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .align(Alignment.BottomStart)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "Quality",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
            )
            if (qualities.isEmpty()) {
                Text(
                    text = if (uiState.isLoadingSourceStreams) "Loading qualities\u2026"
                    else stringResource(R.string.sources_no_streams),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(top = NuvioTheme.spacing.xs, bottom = NuvioTheme.spacing.sm),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                ) {
                    itemsIndexed(items = qualities, key = { _, q -> q }) { i, q ->
                        val active = q == currentQuality
                        val count = candidates.count { it.sourceQuality() == q }
                        val pick = remember(candidates, allStreams, q, currentStream) {
                            candidates.filter { it.sourceQuality() == q }
                                .let { l -> l.firstOrNull { it !== currentStream } ?: l.firstOrNull() }
                                ?: allStreams.filter { !it.isExternal() && it.sourceQuality() == q }.firstOrNull()
                        }
                        val shownStream = if (active && currentStream != null) currentStream else pick
                        val specs = remember(shownStream) { shownStream?.sourceSpecs().orEmpty() }
                        val fg = if (active) NuvioTheme.colors.OnSecondary else Color.White
                        androidx.tv.material3.Card(
                            onClick = {
                                if (pick != null && pick !== currentStream) onStreamSelected(pick) else onClose()
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(requesters[i]),
                            colors = androidx.tv.material3.CardDefaults.colors(
                                containerColor = if (active) NuvioTheme.colors.Secondary else Color.Transparent,
                                focusedContainerColor = if (active) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.08f)
                            ),
                            shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                            border = sourcePanelBorder(NuvioTheme.radii.md),
                            scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = NuvioTheme.spacing.md, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .background(
                                            if (active) NuvioTheme.colors.OnSecondary.copy(alpha = 0.16f)
                                            else Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = com.nuvio.tv.core.player.DeviceVideoCapabilities.badgeFor(q),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = fg
                                    )
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = listOfNotNull(
                                            if (q > 0) "${q}p" else "Auto",
                                            formatSize(shownStream?.behaviorHints?.videoSize)
                                        ).joinToString(" \u00B7 "),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = fg
                                    )
                                    SourceSpecChips(specs = specs, selected = active, maxChips = 6)
                                    Text(
                                        text = listOfNotNull(provider, "$count link${if (count > 1) "s" else ""}")
                                            .joinToString(" \u00B7 "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (active) NuvioTheme.colors.OnSecondary.copy(alpha = 0.72f)
                                        else NuvioTheme.colors.TextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (active) Text(text = "\u2713", style = MaterialTheme.typography.titleLarge, color = fg)
                            }
                        }
                    }
                }
            }
        }
    }
}
