package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.core.network.AppDns
import com.nuvio.tv.core.network.SecureDnsMode

/** "Secure DNS (DNS over HTTPS)" row + picker. The choice applies to every app request immediately. */
@Composable
internal fun SecureDnsSettingsRow() {
    val mode by AppDns.mode.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    SettingsActionRow(
        title = stringResource(R.string.network_secure_dns_title),
        subtitle = stringResource(R.string.network_secure_dns_subtitle),
        value = secureDnsModeLabel(mode),
        onClick = { showDialog = true }
    )

    if (showDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.network_secure_dns_title),
            subtitle = stringResource(R.string.network_secure_dns_subtitle),
            options = SecureDnsMode.entries.map { option ->
                SettingsPickerOption(
                    value = option,
                    title = secureDnsModeLabel(option),
                    description = secureDnsModeDescription(option)
                )
            },
            selectedValue = mode,
            onOptionSelected = { selected ->
                AppDns.setMode(selected)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            width = 480.dp,
            maxHeight = 360.dp
        )
    }
}

@Composable
private fun secureDnsModeLabel(mode: SecureDnsMode): String = when (mode) {
    SecureDnsMode.AUTOMATIC -> stringResource(R.string.network_secure_dns_automatic)
    SecureDnsMode.CLOUDFLARE -> stringResource(R.string.network_secure_dns_cloudflare)
    SecureDnsMode.GOOGLE -> stringResource(R.string.network_secure_dns_google)
    SecureDnsMode.OFF -> stringResource(R.string.network_secure_dns_off)
}

@Composable
private fun secureDnsModeDescription(mode: SecureDnsMode): String = when (mode) {
    SecureDnsMode.AUTOMATIC -> stringResource(R.string.network_secure_dns_automatic_desc)
    SecureDnsMode.CLOUDFLARE -> stringResource(R.string.network_secure_dns_cloudflare_desc)
    SecureDnsMode.GOOGLE -> stringResource(R.string.network_secure_dns_google_desc)
    SecureDnsMode.OFF -> stringResource(R.string.network_secure_dns_off_desc)
}
