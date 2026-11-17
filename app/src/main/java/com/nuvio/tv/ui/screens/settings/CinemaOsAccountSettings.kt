@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.nuvio.tv.core.qr.QrCodeGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.core.cinemaos.account.CinemaOsAccountManager
import com.nuvio.tv.core.cinemaos.account.CinemaOsSyncService
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CinemaOsAccountUiState(
    val code: CinemaOsAccountManager.DeviceCode? = null,
    val secondsLeft: Int = 0,
    val error: String? = null
)

@HiltViewModel
class CinemaOsAccountViewModel @Inject constructor(
    private val manager: CinemaOsAccountManager,
    private val sync: CinemaOsSyncService
) : ViewModel() {
    val session = manager.session
    private val _ui = MutableStateFlow(CinemaOsAccountUiState())
    val ui: StateFlow<CinemaOsAccountUiState> = _ui.asStateFlow()
    private var job: Job? = null

    /** Runs the QR device flow: shows a code, polls, auto-refreshes on expiry. Stops when signed in. */
    fun startFlow() {
        if (job?.isActive == true || manager.isSignedIn) return
        job = viewModelScope.launch {
            while (isActive && !manager.isSignedIn) {
                val code = manager.startDeviceLogin().getOrNull()
                if (code == null) {
                    _ui.value = CinemaOsAccountUiState(error = "Cannot reach cinemaos.tech. Retrying...")
                    delay(5_000)
                    continue
                }
                val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
                var interval = code.intervalSec
                _ui.value = CinemaOsAccountUiState(code = code, secondsLeft = code.expiresInSec)
                var elapsedSincePoll = 0
                var done = false
                while (isActive && !done) {
                    val left = ((deadline - System.currentTimeMillis()) / 1000).toInt()
                    if (left <= 0) break
                    _ui.update { it.copy(secondsLeft = left) }
                    delay(1_000)
                    elapsedSincePoll++
                    if (elapsedSincePoll < interval) continue
                    elapsedSincePoll = 0
                    val r = manager.pollDeviceLogin(code.deviceCode).getOrNull() ?: continue
                    when (r) {
                        is CinemaOsAccountManager.PollResult.Approved -> {
                            manager.completeSignIn(r.customToken)
                                .onSuccess { sync.pullNow(); _ui.value = CinemaOsAccountUiState() }
                                .onFailure { _ui.value = CinemaOsAccountUiState(error = "Sign-in failed. Try again.") }
                            done = true
                        }
                        CinemaOsAccountManager.PollResult.SlowDown -> interval += 5
                        CinemaOsAccountManager.PollResult.Expired -> break
                        CinemaOsAccountManager.PollResult.Denied -> {
                            _ui.value = CinemaOsAccountUiState(error = "Sign-in was denied on the website.")
                            delay(3_000)
                            break
                        }
                        CinemaOsAccountManager.PollResult.Pending -> Unit
                    }
                }
                if (done) return@launch
            }
        }
    }

    fun stopFlow() {
        job?.cancel()
        job = null
    }

    fun signOut() {
        manager.signOut()
        _ui.value = CinemaOsAccountUiState()
        startFlow()
    }

    override fun onCleared() {
        job?.cancel()
    }
}

@Composable
internal fun CinemaOsAccountSettingsContent(initialFocusRequester: FocusRequester?) {
    val viewModel: CinemaOsAccountViewModel = hiltViewModel()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val signedIn = session != null
    DisposableEffect(signedIn) {
        if (!signedIn) viewModel.startFlow()
        onDispose { viewModel.stopFlow() }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = "CinemaOS Account",
            subtitle = "Sync Continue Watching and your Library with cinemaos.tech"
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            val current = session
            if (current != null) {
                SettingsActionRow(
                    title = "Signed in",
                    subtitle = current.email,
                    onClick = {},
                    modifier = if (initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier
                )
                SettingsActionRow(
                    title = "Sign out",
                    subtitle = "Watch history stays on this device",
                    onClick = { viewModel.signOut() }
                )
            } else {
                val code = ui.code
                if (code != null) {
                    val qr = remember(code.verificationUrlComplete) {
                        QrCodeGenerator.generate(code.verificationUrlComplete, size = 512).asImageBitmap()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(bitmap = qr, contentDescription = "Sign-in QR code", modifier = Modifier.size(220.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                            Text(text = code.userCode, style = MaterialTheme.typography.displaySmall)
                            Text(
                                text = "Scan with your phone or open cinemaos.tech/tv-login and enter the code",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioTheme.colors.TextSecondary
                            )
                            Text(
                                text = "Code refreshes in ${ui.secondsLeft / 60}:${"%02d".format(ui.secondsLeft % 60)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = NuvioTheme.colors.TextSecondary
                            )
                        }
                    }
                } else if (ui.error == null) {
                    Text(text = "Getting a sign-in code...", style = MaterialTheme.typography.bodyMedium)
                }
                SettingsActionRow(
                    title = "New code",
                    subtitle = null,
                    onClick = { viewModel.stopFlow(); viewModel.startFlow() },
                    modifier = if (initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier
                )
            }
            ui.error?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.Error)
            }
        }
    }
}
