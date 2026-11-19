package com.nuvio.tv.ui.screens.player

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.downloads.OfflineDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerDownloadAction(uiState: PlayerUiState, currentFilename: String?, headers: Map<String, String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enqueueing by remember { mutableStateOf(false) }
    if (uiState.currentStreamUrl?.let { it.startsWith("https://") || it.startsWith("http://") } != true) return
    val index = findCurrentStreamIndex(uiState.sourceAllStreams, uiState.currentStreamInfoHash, uiState.currentStreamFileIdx,
        uiState.currentStreamAddonName, uiState.currentStreamUrl, uiState.currentStreamName, uiState.currentStreamDescription, currentFilename)
    val source = uiState.sourceAllStreams.getOrNull(index)
    Button(enabled = !enqueueing, modifier = modifier, onClick = {
        enqueueing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val episode = if (uiState.currentSeason != null && uiState.currentEpisode != null)
                        "S${uiState.currentSeason} E${uiState.currentEpisode}" else null
                    OfflineDownloads.enqueue(context,
                        listOfNotNull(uiState.title, episode, uiState.currentEpisodeTitle).joinToString(" · "),
                        uiState.currentStreamUrl.orEmpty(), source?.behaviorHints?.filename ?: currentFilename,
                        source?.behaviorHints?.proxyHeaders?.request.orEmpty() + headers, source?.behaviorHints?.streamType)
                }
            }
            enqueueing = false
            Toast.makeText(context, result.fold(
                onSuccess = { "Download started. Open Settings → Downloads to check progress or play offline." },
                onFailure = { it.message ?: "Could not start download" }), Toast.LENGTH_LONG).show()
        }
    }) { Text(if (enqueueing) "Starting download…" else "Download playing source") }
}
