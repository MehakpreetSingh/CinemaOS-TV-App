@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.settings.SettingsDialogActionButton
import com.nuvio.tv.ui.screens.settings.SettingsDialogActionRow
import com.nuvio.tv.ui.theme.NuvioTheme

/** Asked when Back is pressed on Home, where there is nowhere further back to go. Focus starts on Stay. */
@Composable
fun ExitConfirmDialog(onExit: () -> Unit, onDismiss: () -> Unit) {
    val stayFocus = remember { FocusRequester() }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.exit_dialog_title),
        width = 460.dp,
    ) {
        Text(
            text = stringResource(R.string.exit_dialog_message),
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextSecondary,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsDialogActionRow {
            SettingsDialogActionButton(
                text = stringResource(R.string.exit_dialog_stay),
                onClick = onDismiss,
                modifier = Modifier.focusRequester(stayFocus),
            )
            SettingsDialogActionButton(text = stringResource(R.string.exit_dialog_confirm), onClick = onExit, primary = true)
        }
    }
    LaunchedEffect(Unit) { runCatching { stayFocus.requestFocus() } }
}
