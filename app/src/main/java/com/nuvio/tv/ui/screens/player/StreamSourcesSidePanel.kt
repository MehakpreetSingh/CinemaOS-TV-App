@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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

private sealed interface SourcePanelItem {
    data class Header(val provider: String, val servers: Int, val formats: String, val best: Int, val open: Boolean) : SourcePanelItem
    data class Row(val provider: String, val index: Int, val stream: Stream) : SourcePanelItem
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
 * CinemaOS in-player sources panel, laid out like the phone app's ServerBrowser: filter chips,
 * a providers/servers summary line, then one collapsible card per provider listing its servers.
 * Uses the same container, header, chips and card styling as the Subtitles/Audio panels.
 */
@Composable
internal fun StreamSourcesSidePanel(
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
    val expanded = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val closeRequester = remember { FocusRequester() }
    val chipRequesters = remember { List(SourceFormatFilter.values().size) { FocusRequester() } }
    val headerRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val listState = rememberLazyListState()

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
            .map { (prov, list) -> prov to list.sortedByDescending { it.sourceQuality() } }
            .sortedWith(
                compareByDescending<Pair<String, List<Stream>>> { it.first == currentProvider }
                    .thenByDescending { (_, l) -> l.maxOf { it.sourceQuality() } }
            )
    }

    // Open the provider that is playing.
    LaunchedEffect(currentProvider) {
        if (currentProvider != null && expanded[currentProvider] == null) expanded[currentProvider] = true
    }

    val items = buildList<SourcePanelItem> {
        groups.forEach { (prov, list) ->
            val open = expanded[prov] == true
            add(
                SourcePanelItem.Header(
                    provider = prov,
                    servers = list.size,
                    formats = list.map { it.sourceFormat() }.distinct().joinToString(", "),
                    best = list.maxOf { it.sourceQuality() },
                    open = open
                )
            )
            if (open) list.forEachIndexed { i, s -> add(SourcePanelItem.Row(prov, i, s)) }
        }
    }

    // Initial focus: the active server's row (its provider is expanded), else the first card.
    var focusDone by remember { mutableStateOf(false) }
    LaunchedEffect(groups.isNotEmpty(), currentProvider) {
        if (focusDone || groups.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val rowItem = items.indexOfFirst { it is SourcePanelItem.Row && it.stream === currentStream }
        val target = if (rowItem >= 0) rowItem else 0
        listState.scrollToItem(target)
        withFrameNanos { }
        val item = items.getOrNull(target)
        val requester = when (item) {
            is SourcePanelItem.Row -> rowRequesters["${item.provider}#${item.index}"]
            is SourcePanelItem.Header -> headerRequesters[item.provider]
            null -> null
        }
        if (requester != null && runCatching { requester.requestFocus() }.isSuccess) focusDone = true
    }
    // Nothing to land on yet: keep focus inside the panel.
    LaunchedEffect(groups.isEmpty()) {
        if (groups.isEmpty()) {
            withFrameNanos { }
            runCatching { chipRequesters[0].requestFocus() }
        }
    }

    val searching = uiState.isLoadingSourceStreams ||
        uiState.sourceChips.any { it.status == SourceChipStatus.LOADING }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(520.dp)
            .clip(RoundedCornerShape(topStart = NuvioTheme.spacing.lg, bottomStart = NuvioTheme.spacing.lg))
            .background(NuvioTheme.colors.BackgroundElevated)
    ) {
        Column(modifier = Modifier.padding(NuvioTheme.spacing.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sources_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary
                )
                DialogButton(
                    text = stringResource(R.string.sources_close),
                    onClick = onClose,
                    isPrimary = false,
                    modifier = Modifier
                        .focusRequester(closeRequester)
                        .focusProperties { down = chipRequesters[filter.ordinal] }
                )
            }

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

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
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

            // Filter chips: Left/Right moves focus and selects the filter; first one is refresh.
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                SourceFormatFilter.values().forEachIndexed { i, f ->
                    val selected = filter == f
                    androidx.tv.material3.Card(
                        onClick = { filter = f },
                        modifier = Modifier
                            .focusRequester(chipRequesters[i])
                            .focusProperties { up = closeRequester }
                            .onFocusChanged { if (it.isFocused) filter = f },
                        colors = androidx.tv.material3.CardDefaults.colors(
                            containerColor = if (selected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = if (selected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.08f)
                        ),
                        shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(999.dp)),
                        border = sourcePanelBorder(999.dp),
                        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
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
                    isLoading = searching,
                    modifier = Modifier.focusProperties { up = closeRequester }
                )
            }

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
            Text(
                text = "${groups.size} PROVIDERS · ${groups.sumOf { it.second.size }} SERVERS" +
                    if (searching) " · STILL SEARCHING…" else "",
                style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))

            when {
                uiState.sourceStreamsError != null && allStreams.isEmpty() -> Text(
                    text = uiState.sourceStreamsError,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
                groups.isEmpty() && searching -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTheme.spacing.xl),
                    contentAlignment = Alignment.Center
                ) { LoadingIndicator() }
                groups.isEmpty() -> Text(
                    text = stringResource(R.string.sources_no_streams),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                else -> LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs),
                    contentPadding = PaddingValues(
                        start = NuvioTheme.spacing.sm, top = 6.dp, end = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.sm
                    ),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    itemsIndexed(items, key = { _, it ->
                        when (it) {
                            is SourcePanelItem.Header -> "h:${it.provider}"
                            is SourcePanelItem.Row -> "r:${it.provider}#${it.index}"
                        }
                    }) { _, item ->
                        when (item) {
                            is SourcePanelItem.Header -> {
                                val req = headerRequesters.getOrPut(item.provider) {
                                    if (item.provider == groups.firstOrNull()?.first) streamsFocusRequester else FocusRequester()
                                }
                                val playing = item.provider == currentProvider
                                androidx.tv.material3.Card(
                                    onClick = { expanded[item.provider] = !item.open },
                                    modifier = Modifier.fillMaxWidth().focusRequester(req),
                                    colors = androidx.tv.material3.CardDefaults.colors(
                                        containerColor = Color.White.copy(alpha = if (playing) 0.12f else 0.06f),
                                        focusedContainerColor = Color.White.copy(alpha = if (playing) 0.16f else 0.1f)
                                    ),
                                    shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                                    border = sourcePanelBorder(NuvioTheme.radii.md),
                                    scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = NuvioTheme.spacing.md, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = if (playing) 1f else 0.25f))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.provider,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${item.servers} server${if (item.servers > 1) "s" else ""} · ${item.formats}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NuvioTheme.colors.TextTertiary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        qualityLabel(item.best)?.let { SourcePanelBadge(it, false) }
                                        Text(
                                            text = if (item.open) "▴" else "▾",
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                            is SourcePanelItem.Row -> {
                                val req = rowRequesters.getOrPut("${item.provider}#${item.index}") { FocusRequester() }
                                val s = item.stream
                                val active = s === currentStream
                                val title = s.getDisplayNameOrNull() ?: s.name ?: s.addonName
                                val meta = listOfNotNull(
                                    s.pluginSourceName?.takeIf { it.isNotBlank() && it != s.addonName },
                                    formatSize(s.behaviorHints?.videoSize)
                                ).joinToString(" · ")
                                val titleColor = if (active) NuvioTheme.colors.OnSecondary else Color.White
                                androidx.tv.material3.Card(
                                    onClick = { onStreamSelected(s) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = NuvioTheme.spacing.md)
                                        .focusRequester(req),
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
                                            .padding(horizontal = NuvioTheme.spacing.md, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                            Text(text = "✓", color = NuvioTheme.colors.OnSecondary)
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
        border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
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
