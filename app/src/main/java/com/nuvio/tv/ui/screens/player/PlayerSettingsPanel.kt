package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.ui.screens.settings.PlaybackSettingsContent

/** Uses the same full-screen overlay and remote-focus controls as Audio and Sources. */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerSettingsPanel(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    brightness: Float = 1f,
    uiState: PlayerUiState,
    downloadHeaders: Map<String, String>,
    currentFilename: String?,
) {
    val first = remember { FocusRequester() }
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    PlayerOverlayScaffold(
        visible = true,
        onDismiss = onDismiss,
        captureKeys = true,
        contentPadding = PaddingValues(horizontal = 44.dp, vertical = 28.dp),
        modifier = Modifier.playerControlsBrightness(brightness),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(first)) { Text("Close settings") }
                // Live playback runs at the live edge: no playback speed.
                if (!uiState.isLive) speeds.forEach { speed ->
                    Button(onClick = { onSpeedSelected(speed) }) {
                        Text(if (speed == currentSpeed) "✓ ${speed}×" else "${speed}×")
                    }
                }
            }
            PlayerDownloadAction(uiState, currentFilename, downloadHeaders)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PlaybackSettingsContent()
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}
