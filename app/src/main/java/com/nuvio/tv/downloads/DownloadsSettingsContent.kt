@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.downloads

import android.app.DownloadManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DownloadsSettingsContent(initialFocusRequester: FocusRequester?, onPlay: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloads by remember { mutableStateOf(emptyList<OfflineDownload>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        while (true) {
            runCatching { withContext(Dispatchers.IO) { OfflineDownloads.list(context) } }
                .onSuccess { downloads = it; error = null }
                .onFailure { error = it.message ?: "Could not load downloads" }
            loaded = true
            delay(1_500)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary)
        Text("Stored on this device. Start a download from the player’s Sources panel using an MP4, MKV, WebM, HLS or DASH source. Offline HLS/DASH uses ExoPlayer.",
            style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary)
        error?.let { Text(it, color = NuvioTheme.colors.TextSecondary) }
        if (loaded && downloads.isEmpty()) {
            Text("No downloads yet", color = NuvioTheme.colors.TextSecondary)
            Button(onClick = { scope.launch {
                runCatching { withContext(Dispatchers.IO) { OfflineDownloads.list(context) } }
                    .onSuccess { downloads = it; error = null }
                    .onFailure { error = it.message ?: "Could not load downloads" }
            } }, modifier = if (initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier) {
                Text("Refresh")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(downloads, key = { _, item -> item.id }) { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextPrimary)
                    val state = when {
                        item.playable -> "Ready to watch offline"
                        item.state == DownloadManager.STATUS_SUCCESSFUL -> "The response is not a supported video file"
                        item.state == DownloadManager.STATUS_FAILED -> item.error ?: "Download failed or file unavailable. Choose another source."
                        item.state == DownloadManager.STATUS_PAUSED -> "Waiting for network or storage"
                        item.state == DownloadManager.STATUS_PENDING -> "Queued"
                        else -> "Downloading"
                    }
                    val mb = item.downloaded / (1024L * 1024L)
                    Text("$state · $mb MB", color = NuvioTheme.colors.TextSecondary)
                    if (item.total > 0 && !item.playable && item.state != DownloadManager.STATUS_FAILED) {
                        LinearProgressIndicator(progress = { (item.downloaded.toFloat() / item.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (item.playable) Button(
                            onClick = { onPlay(Uri.fromFile(item.file).toString(), item.title) },
                            modifier = if (index == 0 && initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier,
                        ) { Text("Play offline") }
                        Button(onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) {
                                    OfflineDownloads.remove(context, item.id)
                                    OfflineDownloads.list(context)
                                } }.onSuccess { downloads = it }.onFailure {
                                    Toast.makeText(context, it.message ?: "Could not remove download", Toast.LENGTH_LONG).show()
                                }
                            }
                        }, modifier = if (index == 0 && !item.playable && initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier) {
                            Text(if (item.playable || item.state == DownloadManager.STATUS_FAILED || item.state == DownloadManager.STATUS_SUCCESSFUL) "Delete" else "Cancel download")
                        }
                    }
                }
            }
        }
    }
}
