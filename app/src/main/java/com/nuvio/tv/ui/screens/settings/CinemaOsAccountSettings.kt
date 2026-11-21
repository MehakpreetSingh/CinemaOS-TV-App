@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import com.nuvio.tv.ui.screens.profile.EditProfileOverlay
import com.nuvio.tv.ui.screens.profile.ProfileSelectionViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import com.nuvio.tv.ui.screens.account.AccountSignOutConfirmationDialog
import com.nuvio.tv.ui.theme.NuvioPrimitives
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
    val lastSyncedAt = sync.lastSyncedAt
    val isSyncing = sync.isSyncing
    fun syncNow() { sync.pullNow() }
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
internal fun CinemaOsAccountSettingsContent(
    initialFocusRequester: FocusRequester?,
    onNavigateToManageProfiles: () -> Unit = {}
) {
    val viewModel: CinemaOsAccountViewModel = hiltViewModel()
    val profileViewModel: ProfileSelectionViewModel = hiltViewModel()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by profileViewModel.activeProfileId.collectAsStateWithLifecycle()
    val avatarCatalog by profileViewModel.avatarCatalog.collectAsStateWithLifecycle()
    val profileBackgroundCatalog by profileViewModel.profileBackgroundCatalog.collectAsStateWithLifecycle()
    val hasProfileBackgroundAccess by profileViewModel.hasProfileBackgroundAccess.collectAsStateWithLifecycle()
    val isSavingProfile by profileViewModel.isSaving.collectAsStateWithLifecycle()
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
    var showProfileEditor by remember { mutableStateOf(false) }
    val editProfileRequester = initialFocusRequester ?: remember { FocusRequester() }
    var editorWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(showProfileEditor) {
        if (showProfileEditor) {
            editorWasShown = true
        } else if (editorWasShown) {
            // Give focus back to Edit profile once the editor dialog has closed.
            kotlinx.coroutines.delay(150)
            runCatching { editProfileRequester.requestFocus() }
        }
    }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val lastSynced by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val syncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val signedIn = session != null
    var showSignOutConfirmation by remember { mutableStateOf(false) }
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
            subtitle = "Your profile, and sync of Continue Watching and your Library with cinemaos.tech"
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            val current = session
            if (activeProfile != null) {
                CinemaOsProfileHeader(
                    profile = activeProfile,
                    avatarImageUrl = remember(activeProfile, avatarCatalog) {
                        activeProfile.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: profileViewModel.getAvatarImageUrl(activeProfile.avatarId)
                    },
                    email = current?.email,
                    signedIn = current != null
                )
                SettingsActionRow(
                    title = "Edit profile",
                    subtitle = "Change your name and avatar",
                    leadingIcon = Icons.Default.Edit,
                    onClick = { showProfileEditor = true },
                    modifier = Modifier.focusRequester(editProfileRequester)
                )
                SettingsActionRow(
                    title = "Manage profiles",
                    subtitle = "Add, switch or remove profiles",
                    leadingIcon = Icons.Default.People,
                    onClick = onNavigateToManageProfiles
                )
            }
            if (current != null) {
                SettingsActionRow(
                    title = if (syncing) "Syncing..." else "Sync now",
                    subtitle = if (lastSynced > 0L) {
                        "Last synced " + android.text.format.DateUtils.getRelativeTimeSpanString(
                            lastSynced, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                        )
                    } else "Last synced: never",
                    onClick = { if (!syncing) viewModel.syncNow() }
                )
                Button(
                    onClick = { showSignOutConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioPrimitives.red600.copy(alpha = 0.2f),
                        focusedContainerColor = NuvioPrimitives.red600.copy(alpha = 0.4f),
                        contentColor = Color(0xFFF44336),
                        focusedContentColor = Color(0xFFF44336)
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = NuvioTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                        Text(
                            text = "Sign out",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
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
                    modifier = if (initialFocusRequester != null && activeProfile == null) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
            ui.error?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.Error)
            }
        }
    }

    if (showSignOutConfirmation) {
        AccountSignOutConfirmationDialog(
            onConfirm = {
                viewModel.signOut()
                showSignOutConfirmation = false
            },
            onDismiss = { showSignOutConfirmation = false }
        )
    }
}

    val profileToEdit = activeProfile
    if (showProfileEditor && profileToEdit != null) {
        Dialog(
            onDismissRequest = { showProfileEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EditProfileOverlay(
                profile = profileToEdit,
                avatarCatalog = avatarCatalog,
                profileBackgroundCatalog = profileBackgroundCatalog,
                hasProfileBackgroundAccess = hasProfileBackgroundAccess,
                isSaving = isSavingProfile,
                avatarUrlResolver = { avatarId -> profileViewModel.getAvatarImageUrl(avatarId) },
                onDismiss = { showProfileEditor = false },
                onSaveProfile = { updated ->
                    profileViewModel.updateProfile(updated)
                    showProfileEditor = false
                }
            )
        }
    }
}

@Composable
private fun CinemaOsProfileHeader(
    profile: UserProfile,
    avatarImageUrl: String?,
    email: String?,
    signedIn: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = NuvioTheme.spacing.md,
                vertical = NuvioTheme.spacing.md
            ),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatarCircle(
            name = profile.name,
            colorHex = profile.avatarColorHex,
            size = 96.dp,
            avatarImageUrl = avatarImageUrl
        )
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xxs)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.headlineSmall,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = if (signedIn) (email ?: "Signed in to CinemaOS") else "Not signed in to CinemaOS",
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
