@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.nuvio.tv.ui.screens.player

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.boundsInWindow
import android.graphics.RectF
import com.nuvio.tv.ui.screens.detail.formatPlaybackTime
import com.nuvio.tv.ui.theme.NuvioMotion
import kotlinx.coroutines.delay

import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.accentBrush

import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.party.PartyAction
import com.nuvio.tv.core.party.PartyMedia
import com.nuvio.tv.core.party.PartyNotice
import com.nuvio.tv.core.party.PartySync
import com.nuvio.tv.ui.screens.party.ConfirmDialog
import com.nuvio.tv.ui.screens.party.PartyPanel
import com.nuvio.tv.ui.screens.party.PartyStatusPill
import com.nuvio.tv.core.player.PlayerWindowBackdrop
import com.nuvio.tv.ui.util.keepLtr
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.data.local.LibassRenderMode
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.domain.model.StreamBadge
import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.components.LoadingIndicator
import android.text.format.DateFormat
import com.nuvio.tv.ui.util.rememberRawSvgPainter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.media3.exoplayer.ExoPlayer
import com.nuvio.tv.libass.media.widget.AssSubtitleSurfaceView
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

private const val PLAYER_CONTROLS_FADE_DURATION_MS = 200

private fun PlayerUiState.returnFocusSeasonEpisode(completed: Boolean): Pair<Int?, Int?> {
    val next = nextEpisode?.takeIf {
        it.hasAired && !(it.released.isNullOrBlank() && it.available == false)
    }
    return if (completed && next != null) {
        next.season to next.episode
    } else {
        currentSeason to currentEpisode
    }
}

internal fun playerBackTreatsSkipAsAutoPlay(
    streamAutoPlayMode: StreamAutoPlayMode,
    preferBingeGroupForNextEpisode: Boolean
): Boolean = streamAutoPlayMode != StreamAutoPlayMode.MANUAL || preferBingeGroupForNextEpisode

internal fun playerBackOpensCurrentEpisodeStreams(
    episodeChangedInPlace: Boolean,
    autoPlayEnabled: Boolean
): Boolean = episodeChangedInPlace && !autoPlayEnabled

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onBackPress: (currentVideoId: String?, currentSeason: Int?, currentEpisode: Int?, autoPlayEnabled: Boolean, playbackCompleted: Boolean) -> Unit,
    onPlaybackErrorBack: () -> Unit = { onBackPress(null, null, null, false, false) },
    onPlaybackEnded: ((nextVideoId: String?, nextSeason: Int?, nextEpisode: Int?, exitReason: PlayerExitReason?) -> Unit)? = null,
    onPlayRecommendation: (PostPlayRecommendation, manualSelection: Boolean) -> Unit = { _, _ -> },
    onOpenRecommendationDetails: (PostPlayRecommendation) -> Unit = {},
    onOpenPartyMedia: (PartyMedia) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerBadges = rememberPlayerBadges(viewModel, uiState)
    val partyState by viewModel.party.state.collectAsState()
    val partyPlayback by viewModel.party.playback.collectAsState()
    val partyAvailable by viewModel.party.available.collectAsState()
    val partyCanUse by viewModel.party.canUse.collectAsState()
    val partyProfile by viewModel.party.profile.collectAsState()
    var partyNotice by remember { mutableStateOf<PartyNotice?>(null) }
    LaunchedEffect(Unit) {
        viewModel.party.notices.collectLatest { next ->
            partyNotice = next
            kotlinx.coroutines.delay(3000)
            partyNotice = null
        }
    }
    val postPlayRecommendationState by viewModel.postPlayRecommendationUiState.collectAsState()
    val effectiveAutoplayEnabled by viewModel.effectiveAutoplayEnabled.collectAsState(initial = false)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val containerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val progressBarFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }
    val chaptersFocusRequester = remember { FocusRequester() }
    val streamsFocusRequester = remember { FocusRequester() }
    val sourceStreamsFocusRequester = remember { FocusRequester() }
    val skipIntroFocusRequester = remember { FocusRequester() }
    val streamInfoFocusRequester = remember { FocusRequester() }
    val postPlayRecommendationFocusRequester = remember { FocusRequester() }
    val postPlayRecommendationPlayerWindowFocusRequester = remember { FocusRequester() }
    var skipButtonActuallyVisible by remember { mutableStateOf(false) }
    var restoreStreamInfoFocus by remember { mutableStateOf(false) }
    var focusPlayAfterMoreBack by remember { mutableStateOf(false) }
    var hideControlsAfterStreamInfo by remember { mutableStateOf(false) }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    var subtitleDelayFocusTarget by remember { mutableStateOf(SubtitleDelayFocusTarget.SLIDER) }
    val subtitleDelayResetFocusRequester = remember { FocusRequester() }
    val subtitleDelaySyncLineFocusRequester = remember { FocusRequester() }
    var subtitleTimingConsumeNextConfirmKeyUp by remember { mutableStateOf(false) }
    var reportCodeVisible by remember { mutableStateOf(false) }
    var exitDispatched by remember { mutableStateOf(false) }
    var externalHandoffInProgress by remember { mutableStateOf(false) }
    val autoPlayEnabledForBack = playerBackTreatsSkipAsAutoPlay(
        streamAutoPlayMode = uiState.streamAutoPlayMode,
        preferBingeGroupForNextEpisode = uiState.streamAutoPlayPreferBingeGroupForNextEpisode
    )

    val exitPlayer: () -> Unit = exitPlayer@{
        if (exitDispatched) return@exitPlayer
        exitDispatched = true
        val timeline = viewModel.playbackTimeline.value
        viewModel.stopAndRelease()
        val completed = postPlayRecommendationState.isVisible || uiState.playbackEnded ||
            (!timeline.isLive &&
                timeline.duration > 0L &&
                (timeline.currentPosition.toFloat() / timeline.duration.toFloat()) >= WatchProgress.COMPLETED_THRESHOLD)
        val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed)
        onBackPress(
            uiState.currentVideoId,
            focusSeason,
            focusEpisode,
            autoPlayEnabledForBack,
            completed
        )
    }
    // Once the first video has played, switching links keeps the video screen and shows only the small spinner.
    // The full-screen loader belongs to the stream screen; here only torrents keep their progress overlay.
    val hasStartedPlayback = !uiState.isTorrentStream
    val exitPlayerFromError: () -> Unit = {
        // No separate stream page: stay in the player and offer the other links in its Sources panel.
        viewModel.onEvent(PlayerEvent.OnShowSourcesPanel)
    }
    val dismissStreamInfoOverlay = {
        viewModel.onEvent(PlayerEvent.OnDismissStreamInfo)
    }
    val returnToPlayerFromPostPlay = {
        viewModel.returnToPlayerFromPostPlay()
    }

    val currentOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val currentOnBackPress by rememberUpdatedState(onBackPress)
    val currentOnPlayRecommendation by rememberUpdatedState(onPlayRecommendation)
    val currentOnOpenRecommendationDetails by rememberUpdatedState(onOpenRecommendationDetails)
    val nextEpisodeForEndPrompt = uiState.nextEpisode?.takeIf {
        it.hasAired && !(it.released.isNullOrBlank() && it.available == false)
    }
    val shouldConfirmNextEpisodeOnEnd =
        uiState.playbackEnded &&
            uiState.error == null &&
            (uiState.streamAutoPlayMode != StreamAutoPlayMode.MANUAL ||
                uiState.streamAutoPlayPreferBingeGroupForNextEpisode) &&
            !uiState.streamAutoPlayNextEpisodeEnabled &&
            nextEpisodeForEndPrompt != null
    val returnToDetailsFromEndPrompt = {
        viewModel.stopAndRelease()
        val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed = true)
        currentOnBackPress(
            uiState.currentVideoId,
            focusSeason,
            focusEpisode,
            true,
            true
        )
    }
    val continueToNextEpisodeFromEndPrompt = {
        val next = nextEpisodeForEndPrompt
        if (next != null) {
            viewModel.stopAndRelease()
            val cb = currentOnPlaybackEnded
            if (cb != null) {
                cb(next.videoId, next.season, next.episode, null)
            } else {
                val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed = true)
                currentOnBackPress(
                    uiState.currentVideoId,
                    focusSeason,
                    focusEpisode,
                    false,
                    true
                )
            }
        }
    }

    LaunchedEffect(uiState.playbackIssueReportStatus, uiState.playbackIssueReportId) {
        if (uiState.playbackIssueReportStatus == PlaybackIssueReportStatus.Sent &&
            !uiState.playbackIssueReportId.isNullOrBlank()
        ) {
            reportCodeVisible = true
            viewModel.scheduleHideControls()
            viewModel.onUserInteraction()
            delay(5000)
            reportCodeVisible = false
        } else if (uiState.playbackIssueReportStatus != PlaybackIssueReportStatus.Sent) {
            reportCodeVisible = false
        }
    }

    val handleBackPress = handleBackPress@{
        if (externalHandoffInProgress) return@handleBackPress
        if (postPlayRecommendationState.canReturnToPlayer && !uiState.playbackEnded) {
            returnToPlayerFromPostPlay()
            viewModel.hideControls()
        } else if (postPlayRecommendationState.isVisible || postPlayRecommendationState.isLoadingRecommendation) {
            exitPlayer()
        } else if (shouldConfirmNextEpisodeOnEnd) {
            returnToDetailsFromEndPrompt()
        } else if (uiState.error != null) {
            exitPlayerFromError()
        } else if (uiState.showAudioOverlay || uiState.showSubtitleOverlay) {
            viewModel.onEvent(PlayerEvent.OnDismissTransientOverlay)
        } else if (uiState.showStreamInfoOverlay) {
            dismissStreamInfoOverlay()
            if (hideControlsAfterStreamInfo) {
                viewModel.hideControls()
            } else if (uiState.showControls) {
                restoreStreamInfoFocus = true
                viewModel.onEvent(PlayerEvent.OnShowMoreDialog)
            }
            hideControlsAfterStreamInfo = false
        } else if (uiState.showPauseOverlay) {
            viewModel.onEvent(PlayerEvent.OnDismissPauseOverlay)
        } else if (uiState.showMoreDialog) {
            focusPlayAfterMoreBack = true
            viewModel.onEvent(PlayerEvent.OnDismissMoreDialog)
        } else if (uiState.showSubtitleTimingDialog) {
            viewModel.onEvent(PlayerEvent.OnDismissSubtitleTimingDialog)
        } else if (uiState.showSubtitleDelayOverlay) {
            viewModel.onEvent(PlayerEvent.OnHideSubtitleDelayOverlay)
        } else if (uiState.showSubtitleStylePanel) {
            viewModel.onEvent(PlayerEvent.OnDismissSubtitleStylePanel)
        } else if (uiState.showPartyPanel) {
            viewModel.onEvent(PlayerEvent.OnDismissPartyPanel)
        } else if (uiState.showSourcesPanel) {
            if (uiState.currentStreamUrl.isNullOrBlank()) {
                exitPlayer()
            } else {
                viewModel.onEvent(PlayerEvent.OnDismissSourcesPanel)
            }
        } else if (uiState.showChaptersPanel) {
            viewModel.onEvent(PlayerEvent.OnDismissChaptersPanel)
        } else if (uiState.showEpisodesPanel) {
            if (uiState.showEpisodeStreams) {
                viewModel.onEvent(PlayerEvent.OnBackFromEpisodeStreams)
            } else {
                viewModel.onEvent(PlayerEvent.OnDismissEpisodesPanel)
            }
        } else if (uiState.postPlayMode is PostPlayMode.AutoPlay &&
            postPlayRecommendationState.recommendation == null
        ) {
            viewModel.onEvent(PlayerEvent.OnDismissNextEpisodeCard)
            if (skipButtonActuallyVisible) {
                runCatching { skipIntroFocusRequester.requestFocus() }
            } else {
                runCatching { containerFocusRequester.requestFocus() }
            }
        } else if (skipButtonActuallyVisible && !uiState.showControls) {
            viewModel.onEvent(PlayerEvent.OnDismissSkipIntro)
        } else if (uiState.postPlayMode is PostPlayMode.StillWatching) {
            viewModel.onEvent(PlayerEvent.OnDismissStillWatchingPrompt)
        } else if (uiState.showLoadingOverlay && hasStartedPlayback) {
            // Still loading the link: Back leaves the player straight away.
            exitPlayer()
        } else if (uiState.showControls) {
            viewModel.hideControls()
        } else {
            exitPlayer()
        }
    }

    BackHandler {
        handleBackPress()
    }

    LaunchedEffect(
        uiState.playbackEnded,
        uiState.error,
        uiState.pendingExitReason,
        shouldConfirmNextEpisodeOnEnd,
        postPlayRecommendationState.blocksNaturalCompletion
    ) {
        val explicitReason = uiState.pendingExitReason
        val shouldDispatchNatural = uiState.playbackEnded &&
            uiState.error == null &&
            uiState.postPlayMode?.blocksNaturalCompletion() != true &&
            !postPlayRecommendationState.blocksNaturalCompletion &&
            !shouldConfirmNextEpisodeOnEnd &&
            explicitReason == null
        when {
            explicitReason == PlayerExitReason.StillWatchingPrompt -> {
                viewModel.stopAndRelease()
                val cb = currentOnPlaybackEnded
                if (cb != null) {
                    cb(null, null, null, PlayerExitReason.StillWatchingPrompt)
                } else {
                    val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed = true)
                    currentOnBackPress(
                        uiState.currentVideoId,
                        focusSeason,
                        focusEpisode,
                        autoPlayEnabledForBack,
                        true
                    )
                }
                viewModel.consumePendingExitReason()
            }
            shouldDispatchNatural -> {
                viewModel.stopAndRelease()
                val next = uiState.nextEpisode?.takeIf {
                    it.hasAired && !(it.released.isNullOrBlank() && it.available == false)
                }
                val cb = currentOnPlaybackEnded
                if (cb != null) {
                    cb(next?.videoId, next?.season, next?.episode, null)
                } else {
                    val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed = true)
                    currentOnBackPress(
                        uiState.currentVideoId,
                        focusSeason,
                        focusEpisode,
                        autoPlayEnabledForBack,
                        true
                    )
                }
            }
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.pauseForLifecycle()
                }
                Lifecycle.Event.ON_STOP -> {
                    // This back stack entry also stops when the player is navigated away from or
                    // exited, where the normal release has to run; only act when the app itself
                    // left the foreground.
                    val hostActivity = context as? androidx.activity.ComponentActivity
                    if (
                        hostActivity != null &&
                        isAppBackgroundStop(
                            isFinishing = hostActivity.isFinishing,
                            isChangingConfigurations = hostActivity.isChangingConfigurations,
                            hostState = hostActivity.lifecycle.currentState
                        )
                    ) {
                        viewModel.releaseForBackground()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Re-create the MediaSession so media controls work in foreground.
                    // Don't auto-resume playback — let the user press play.
                    viewModel.resumeForLifecycle()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Bump UI thread priority to THREAD_PRIORITY_DISPLAY (-4) while the player is active.
    // The Linux scheduler favors the thread under CPU pressure (background addon prefetch,
    // Trakt sync, image decode), reducing dropped frames at scene cuts and during decoder
    // spin-up. Restored on dispose so non-player screens stay at default priority.
    DisposableEffect(Unit) {
        val tid = android.os.Process.myTid()
        val previousPriority = runCatching { android.os.Process.getThreadPriority(tid) }.getOrDefault(0)
        runCatching {
            android.os.Process.setThreadPriority(tid, android.os.Process.THREAD_PRIORITY_DISPLAY)
        }
        onDispose {
            runCatching {
                android.os.Process.setThreadPriority(tid, previousPriority)
            }
        }
    }

    // Frame rate matching lifecycle.
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(activity) {
        viewModel.attachHostActivity(activity)
        viewModel.startInitialPlaybackIfNeeded()
    }
    DisposableEffect(activity) {
        onDispose {
            viewModel.attachHostActivity(null)
        }
    }
    LaunchedEffect(uiState.frameRateMatchingMode) {
        if (activity != null &&
            uiState.frameRateMatchingMode == com.nuvio.tv.data.local.FrameRateMatchingMode.OFF
        ) {
            com.nuvio.tv.core.player.FrameRateUtils.restoreOriginalDisplayMode(activity)
        }
    }
    // Restore original display mode when leaving the player
    val currentFrameRateMatchingMode by rememberUpdatedState(uiState.frameRateMatchingMode)
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                if (currentFrameRateMatchingMode == com.nuvio.tv.data.local.FrameRateMatchingMode.START_STOP) {
                    com.nuvio.tv.core.player.FrameRateUtils.restoreOriginalDisplayMode(activity)
                } else {
                    com.nuvio.tv.core.player.FrameRateUtils.cleanupDisplayListener()
                    com.nuvio.tv.core.player.FrameRateUtils.clearOriginalDisplayMode()
                }
            }
        }
    }

    // Request focus for key events when controls visibility or panel state changes
    LaunchedEffect(
        uiState.showControls,
        uiState.showEpisodesPanel,
        uiState.showSourcesPanel,
        uiState.showChaptersPanel,
        uiState.showPartyPanel,
        uiState.showSubtitleStylePanel,
        uiState.showSubtitleDelayOverlay,
        uiState.showSubtitleTimingDialog,
        uiState.showAudioOverlay,
        uiState.showSubtitleOverlay,
        uiState.showSpeedDialog,
        shouldConfirmNextEpisodeOnEnd,
        postPlayRecommendationState.isVisible,
    ) {
        if (shouldConfirmNextEpisodeOnEnd || postPlayRecommendationState.isVisible) return@LaunchedEffect
        if (uiState.error != null) return@LaunchedEffect
        if (uiState.showPartyPanel) return@LaunchedEffect
        if (uiState.showControls && !uiState.showEpisodesPanel && !uiState.showSourcesPanel && !uiState.showChaptersPanel &&
            !uiState.showAudioOverlay && !uiState.showSubtitleOverlay &&
            !uiState.showSubtitleStylePanel && !uiState.showSubtitleDelayOverlay &&
            !uiState.showSubtitleTimingDialog &&
            !uiState.showSpeedDialog
        ) {
            // Wait for AnimatedVisibility animation to complete before focusing play/pause button
            kotlinx.coroutines.delay(250)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus requester may not be ready yet
            }
        } else if (!uiState.showControls) {
            // When controls are hidden, let skip intro button take focus if visible
            val skipVisible = uiState.activeSkipInterval != null && !uiState.skipIntervalDismissed
            val nextEpisodeVisible = uiState.postPlayMode is PostPlayMode.AutoPlay
            if (!skipVisible && !nextEpisodeVisible) {
                try {
                    containerFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Focus requester may not be ready yet
                }
            }
            // If skip or next episode card is visible, their own LaunchedEffect will request focus
        }
    }

    // While a link loads, keep the control overlay up (with the small spinner) so Sources / Back stay reachable.
    val loadingKeepsControls = uiState.showLoadingOverlay && hasStartedPlayback && uiState.error == null
    LaunchedEffect(loadingKeepsControls, uiState.showControls) {
        if (loadingKeepsControls && !uiState.showControls) {
            viewModel.onEvent(PlayerEvent.OnToggleControls)
        }
    }

    // Initial focus on container - the LaunchedEffect above will handle focusing controls
    LaunchedEffect(Unit) {
        if (uiState.error == null) {
            containerFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(uiState.showSubtitleDelayOverlay) {
        subtitleDelayFocusTarget = SubtitleDelayFocusTarget.SLIDER
    }
    LaunchedEffect(uiState.showSubtitleTimingDialog) {
        if (!uiState.showSubtitleTimingDialog) {
            subtitleTimingConsumeNextConfirmKeyUp = false
        }
    }
    LaunchedEffect(uiState.showStreamInfoOverlay, uiState.showControls, uiState.showMoreDialog) {
        if (!uiState.showStreamInfoOverlay && uiState.showControls && uiState.showMoreDialog && restoreStreamInfoFocus) {
            delay(250)
            runCatching { streamInfoFocusRequester.requestFocus() }
            restoreStreamInfoFocus = false
        }
    }
    val moreDialogOpen by rememberUpdatedState(uiState.showMoreDialog)
    val controlsVisibleForMoreBack by rememberUpdatedState(uiState.showControls)
    val playerHasError by rememberUpdatedState(uiState.error != null)
    LaunchedEffect(focusPlayAfterMoreBack) {
        if (!focusPlayAfterMoreBack) return@LaunchedEffect
        runCatching { playPauseFocusRequester.requestFocus() }
        delay(200)
        if (!moreDialogOpen && controlsVisibleForMoreBack && !playerHasError) {
            playPauseFocusRequester.requestFocusAfterFrames(frames = 0)
        }
        focusPlayAfterMoreBack = false
    }

    val transparentLetterbox = uiState.transparentLetterbox &&
        uiState.internalPlayerEngine != InternalPlayerEngine.MVP_PLAYER
    DisposableEffect(transparentLetterbox) {
        if (!transparentLetterbox) {
            return@DisposableEffect onDispose {}
        }
        PlayerWindowBackdrop.acquireTransparent()
        onDispose { PlayerWindowBackdrop.releaseTransparent() }
    }

    // Controls and menus drawn over HDR video get the HDR brightness when the user enables it;
    // the video surface and its subtitles are never inside this layer.
    val playerControlsDim = Modifier.playerControlsBrightness(uiState.playerControlsBrightness)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (transparentLetterbox) Modifier else Modifier.background(Color.Black))
            .focusRequester(containerFocusRequester)
            .focusable(enabled = uiState.error == null)
            .onPreviewKeyEvent { keyEvent ->
                // Consume the confirm KEY_UP that opened the subtitle timing dialog before
                // the newly focused "Sync" button can treat it as a second click. Preview
                // is required: after open, focus moves into the dialog so onKeyEvent on
                // this container no longer receives the release.
                if (subtitleTimingConsumeNextConfirmKeyUp &&
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    subtitleTimingConsumeNextConfirmKeyUp = false
                    return@onPreviewKeyEvent true
                }

                val postPlayHandlesBack = postPlayRecommendationState.isVisible ||
                    postPlayRecommendationState.isLoadingRecommendation ||
                    postPlayRecommendationState.isTrailerPlaying
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && !postPlayHandlesBack)
                ) {
                    return@onPreviewKeyEvent when (keyEvent.nativeKeyEvent.action) {
                        KeyEvent.ACTION_DOWN -> true
                        KeyEvent.ACTION_UP -> {
                            handleBackPress()
                            true
                        }
                        else -> true
                    }
                }

                if (keyEvent.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_CAPTIONS) {
                    return@onPreviewKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_UP) {
                    return@onPreviewKeyEvent true
                }

                if (uiState.showSubtitleDelayOverlay) {
                    viewModel.onEvent(PlayerEvent.OnHideSubtitleDelayOverlay)
                } else if (
                    !uiState.showEpisodesPanel &&
                    !uiState.showSourcesPanel && !uiState.showChaptersPanel &&
                    !uiState.showPartyPanel &&
                    !uiState.showAudioOverlay &&
                    !uiState.showSubtitleOverlay &&
                    !uiState.showSubtitleStylePanel &&
                    !uiState.showSubtitleTimingDialog &&
                    !uiState.showSpeedDialog
                ) {
                    viewModel.onEvent(PlayerEvent.OnShowSubtitleOverlay)
                }
                true
            }
            .onKeyEvent { keyEvent ->
                // KEY_UP confirm for Sync Line is consumed in onPreviewKeyEvent so it still
                // runs after focus moves into the timing dialog.
                if (uiState.showSubtitleDelayOverlay) {
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (subtitleDelayFocusTarget) {
                            SubtitleDelayFocusTarget.SLIDER -> {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        viewModel.onEvent(PlayerEvent.OnAdjustSubtitleDelay(-SUBTITLE_DELAY_STEP_MS))
                                        return@onKeyEvent true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        viewModel.onEvent(PlayerEvent.OnAdjustSubtitleDelay(SUBTITLE_DELAY_STEP_MS))
                                        return@onKeyEvent true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        subtitleDelayFocusTarget = SubtitleDelayFocusTarget.SYNC_LINE
                                        return@onKeyEvent true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        return@onKeyEvent true
                                    }
                                }
                            }
                            SubtitleDelayFocusTarget.RESET -> {
                                val towardSyncLine = if (isRtl) {
                                    KeyEvent.KEYCODE_DPAD_LEFT
                                } else {
                                    KeyEvent.KEYCODE_DPAD_RIGHT
                                }
                                val awayFromSyncLine = if (isRtl) {
                                    KeyEvent.KEYCODE_DPAD_RIGHT
                                } else {
                                    KeyEvent.KEYCODE_DPAD_LEFT
                                }
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        viewModel.onEvent(PlayerEvent.OnResetSubtitleDelay())
                                        subtitleDelayFocusTarget = SubtitleDelayFocusTarget.SLIDER
                                        return@onKeyEvent true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        subtitleDelayFocusTarget = SubtitleDelayFocusTarget.SLIDER
                                        return@onKeyEvent true
                                    }
                                    towardSyncLine -> {
                                        subtitleDelayFocusTarget = SubtitleDelayFocusTarget.SYNC_LINE
                                        return@onKeyEvent true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN,
                                    awayFromSyncLine -> {
                                        return@onKeyEvent true
                                    }
                                }
                            }
                            SubtitleDelayFocusTarget.SYNC_LINE -> {
                                val towardReset = if (isRtl) {
                                    KeyEvent.KEYCODE_DPAD_RIGHT
                                } else {
                                    KeyEvent.KEYCODE_DPAD_LEFT
                                }
                                val awayFromReset = if (isRtl) {
                                    KeyEvent.KEYCODE_DPAD_LEFT
                                } else {
                                    KeyEvent.KEYCODE_DPAD_RIGHT
                                }
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        subtitleDelayFocusTarget = SubtitleDelayFocusTarget.SLIDER
                                        // KEY_DOWN open: swallow the trailing KEY_UP so the
                                        // dialog's Sync control does not treat it as a click.
                                        subtitleTimingConsumeNextConfirmKeyUp = true
                                        viewModel.onEvent(PlayerEvent.OnShowSubtitleTimingDialog)
                                        return@onKeyEvent true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        subtitleDelayFocusTarget = SubtitleDelayFocusTarget.SLIDER
                                        return@onKeyEvent true
                                    }
                                    towardReset -> {
                                        subtitleDelayFocusTarget = SubtitleDelayFocusTarget.RESET
                                        return@onKeyEvent true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN,
                                    awayFromReset -> {
                                        return@onKeyEvent true
                                    }
                                }
                            }
                        }
                    }
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                        (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ) {
                        return@onKeyEvent true
                    }
                    if (keyEvent.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) {
                        // While open, consume all non-back keys to avoid accidental dismissal.
                        return@onKeyEvent true
                    }
                }

                // When a side panel or dialog is open, let it handle all keys
                val panelOrDialogOpen = uiState.showEpisodesPanel || uiState.showSourcesPanel || uiState.showPartyPanel ||
                        uiState.showChaptersPanel ||
                        uiState.showAudioOverlay || uiState.showSubtitleOverlay ||
                        uiState.showSubtitleStylePanel || uiState.showSpeedDialog ||
                        uiState.showSubtitleDelayOverlay || uiState.showSubtitleTimingDialog ||
                        uiState.showMoreDialog ||
                        shouldConfirmNextEpisodeOnEnd ||
                        uiState.postPlayMode is PostPlayMode.StillWatching ||
                        postPlayRecommendationState.isVisible ||
                        uiState.error != null
                if (panelOrDialogOpen) return@onKeyEvent false

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnCommitPreviewSeek)
                                return@onKeyEvent true
                            }
                        }
                    }
                    return@onKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (uiState.showPauseOverlay) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                // Resume directly from pause overlay in one click.
                                viewModel.onEvent(PlayerEvent.OnPlayPause)
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                            }
                            else -> {
                                viewModel.onEvent(PlayerEvent.OnDismissPauseOverlay)
                            }
                        }
                        return@onKeyEvent true
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnPlayPause)
                                true
                            } else {
                                // Let the focused button handle it
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val overlayButtonsCoexist = skipButtonActuallyVisible &&
                                uiState.postPlayMode is PostPlayMode.AutoPlay
                            if (!uiState.showControls && !overlayButtonsCoexist) {
                                val isLeft =
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                val deltaMs = PlayerScrubRates.deltaMsForKeyRepeat(
                                    repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                    forward = !isLeft
                                )
                                viewModel.onEvent(PlayerEvent.OnPreviewSeekBy(deltaMs))
                                true
                            } else {
                                // Let focus system handle navigation when controls are visible
                                // or both skip and next-episode buttons are on screen
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                                if (!uiState.showControls) {
                                    viewModel.onEvent(PlayerEvent.OnToggleControls)
                                } else {
                                    try {
                                        progressBarFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        val skipVisible = skipButtonActuallyVisible
                                        if (skipVisible) {
                                            try {
                                                skipIntroFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                            }
                                        } else if (uiState.postPlayMode is PostPlayMode.AutoPlay) {
                                            try {
                                                nextEpisodeFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                            }
                                        } else {
                                            viewModel.hideControls()
                                        }
                                    }
                                }
                                true
                            }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnToggleControls)
                                true
                            } else {
                                // Let focus system handle navigation when controls are visible
                                false
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            viewModel.onEvent(PlayerEvent.OnPlayPause)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (!uiState.isPlaying) {
                                viewModel.onEvent(PlayerEvent.OnPlayPause)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (uiState.isPlaying) {
                                viewModel.onEvent(PlayerEvent.OnPlayPause)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            viewModel.onEvent(PlayerEvent.OnPlayPause)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            if (uiState.chapters.isEmpty()) {
                                false
                            } else {
                                val forward = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_CHANNEL_UP
                                viewModel.onEvent(PlayerEvent.OnSkipChapter(forward))
                                true
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            viewModel.onEvent(
                                PlayerEvent.OnSeekBy(
                                    PlayerScrubRates.deltaMsForKeyRepeat(
                                        repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                        forward = true
                                    )
                                )
                            )
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            viewModel.onEvent(
                                PlayerEvent.OnSeekBy(
                                    PlayerScrubRates.deltaMsForKeyRepeat(
                                        repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                        forward = false
                                    )
                                )
                            )
                            true
                        }
                        KeyEvent.KEYCODE_INFO -> {
                            if (uiState.showStreamInfoOverlay) {
                                viewModel.onEvent(PlayerEvent.OnDismissStreamInfo)
                                if (hideControlsAfterStreamInfo) {
                                    viewModel.hideControls()
                                } else if (uiState.showControls) {
                                    restoreStreamInfoFocus = true
                                    viewModel.onEvent(PlayerEvent.OnShowMoreDialog)
                                }
                                hideControlsAfterStreamInfo = false
                            } else {
                                hideControlsAfterStreamInfo = !uiState.showControls
                                viewModel.onEvent(PlayerEvent.OnShowStreamInfo)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        val postPlayRecommendationPlayerWidth by animateFloatAsState(
            targetValue = if (postPlayRecommendationState.isVisible) 0.32f else 1f,
            animationSpec = tween(durationMillis = POST_PLAY_RECOMMENDATION_TRANSITION_MS),
            label = "postPlayRecommendationPlayerWidth"
        )
        val postPlayRecommendationPlayerPadding by animateDpAsState(
            targetValue = if (postPlayRecommendationState.isVisible) NuvioTheme.spacing.xxl else 0.dp,
            animationSpec = tween(durationMillis = POST_PLAY_RECOMMENDATION_TRANSITION_MS),
            label = "postPlayRecommendationPlayerPadding"
        )
        val postPlayRecommendationPlayerCornerRadius by animateDpAsState(
            targetValue = if (postPlayRecommendationState.isVisible) 12.dp else 0.dp,
            animationSpec = tween(durationMillis = POST_PLAY_RECOMMENDATION_TRANSITION_MS),
            label = "postPlayRecommendationPlayerCornerRadius"
        )
        val postPlayRecommendationPlayerBorderAlpha by animateFloatAsState(
            targetValue = if (postPlayRecommendationState.isVisible) 0.2f else 0f,
            animationSpec = tween(durationMillis = POST_PLAY_RECOMMENDATION_TRANSITION_MS),
            label = "postPlayRecommendationPlayerBorderAlpha"
        )
        val playerSurfaceShape = RoundedCornerShape(postPlayRecommendationPlayerCornerRadius)
        val playerSurfaceIsFullscreen = !postPlayRecommendationState.isVisible &&
            postPlayRecommendationPlayerWidth >= 0.999f
        var subtitleControlsClearancePx by remember { mutableFloatStateOf(0f) }
        var progressBarSubtitleClearancePx by remember { mutableFloatStateOf(0f) }
        var controlsTitleRightPx by remember { mutableFloatStateOf(0f) }
        var subtitleTextMetrics by remember { mutableStateOf<SubtitleTextMetrics?>(null) }
        var skipClearancePx by remember { mutableFloatStateOf(0f) }
        var postPlayClearancePx by remember { mutableFloatStateOf(0f) }
        var seekClearancePx by remember { mutableFloatStateOf(0f) }
        val subtitleClearanceGapPx = with(LocalDensity.current) { SUBTITLE_CONTROLS_GAP.toPx() }
        val postPlayOverlayMode = uiState.postPlayMode.takeIf {
            uiState.error == null &&
                !postPlayRecommendationState.isVisible &&
                postPlayRecommendationState.recommendation == null &&
                !shouldConfirmNextEpisodeOnEnd &&
                !uiState.showLoadingOverlay &&
                !uiState.showPauseOverlay &&
                !uiState.showStreamInfoOverlay &&
                !uiState.showEpisodesPanel &&
                !uiState.showSourcesPanel && !uiState.showChaptersPanel &&
                !uiState.showPartyPanel &&
                !uiState.showAudioOverlay &&
                !uiState.showSubtitleOverlay &&
                !uiState.showSubtitleStylePanel &&
                !uiState.showSubtitleDelayOverlay &&
                !uiState.showSubtitleTimingDialog &&
                !uiState.showSpeedDialog &&
                !uiState.showMoreDialog
        }
        val seekOverlayVisible = uiState.showSeekOverlay && !uiState.showControls && uiState.error == null &&
            !uiState.showLoadingOverlay && !uiState.showPauseOverlay &&
            !uiState.showSubtitleDelayOverlay && !uiState.showSubtitleTimingDialog &&
            !uiState.showMoreDialog &&
            !uiState.isLive
        // Controls stay available while a link loads/buffers (small spinner only), so Sources / Back work.
        val playerControlsVisible = uiState.showControls && uiState.error == null &&
            (!uiState.showLoadingOverlay || hasStartedPlayback) && !uiState.showPauseOverlay &&
            !uiState.showStreamInfoOverlay &&
            !uiState.showSubtitleStylePanel &&
            !uiState.showSubtitleDelayOverlay &&
            !uiState.showEpisodesPanel &&
            !uiState.showSourcesPanel && !uiState.showChaptersPanel &&
            !uiState.showPartyPanel &&
            !uiState.showAudioOverlay &&
            !uiState.showSubtitleOverlay &&
            !uiState.showSpeedDialog &&
            !postPlayRecommendationState.isVisible &&
            uiState.postPlayMode !is PostPlayMode.StillWatching
        val latestPlayerControlsVisible by rememberUpdatedState(playerControlsVisible)
        LaunchedEffect(playerControlsVisible) {
            if (!playerControlsVisible) {
                subtitleControlsClearancePx = 0f
                progressBarSubtitleClearancePx = 0f
                controlsTitleRightPx = 0f
            }
        }
        val fallbackSubtitleLiftPx = with(LocalDensity.current) { SUBTITLE_CONTROLS_LIFT.toPx() }
        val playerSurfaceModifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = postPlayRecommendationPlayerPadding, top = postPlayRecommendationPlayerPadding)
            .fillMaxWidth(postPlayRecommendationPlayerWidth)
            .aspectRatio(16f / 9f)
            .clip(playerSurfaceShape)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = postPlayRecommendationPlayerBorderAlpha)),
                playerSurfaceShape
            )
            .then(
                if (transparentLetterbox && playerSurfaceIsFullscreen) Modifier
                else Modifier.background(Color.Black)
            )
            .zIndex(
                if (postPlayRecommendationState.isVisible || postPlayRecommendationPlayerWidth < 0.999f) {
                    2.2f
                } else {
                    0f
                }
            )

        if (!exitDispatched &&
            !postPlayRecommendationState.isTrailerPlaying &&
            (!postPlayRecommendationState.isVisible || !postPlayRecommendationState.hasAutoPlayedTrailer)
        ) {
            LaunchedEffect(postPlayRecommendationState.isVisible) {
                val playerView = viewModel.controller.exoPlayerView
                val vis = if (postPlayRecommendationState.isVisible) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
                playerView?.subtitleView?.visibility = vis
                playerView?.setAssOverlayVisibility(vis)
            }

            Box(modifier = playerSurfaceModifier) {
                val previewConfiguredSubtitlePosition = uiState.showSubtitleOverlay || uiState.showSubtitleStylePanel
                val subtitleCueState by viewModel.controller.subtitleCueTracker.state.collectAsState()
                val prescannedSubtitleTexts by viewModel.controller.subtitleCueTracker.prescannedTexts.collectAsState()
                val renderedSubtitleStyle = uiState.effectiveSubtitleStyle
                val styledSubtitleTypeface = rememberSubtitleTypeface(renderedSubtitleStyle.fontId)
                val measuredSubtitleTextMetrics = remember(
                    subtitleTextMetrics,
                    renderedSubtitleStyle.usesStyledRenderer,
                    styledSubtitleTypeface
                ) {
                    subtitleTextMetrics?.let { metrics ->
                        if (renderedSubtitleStyle.usesStyledRenderer) metrics.forStyledRenderer(styledSubtitleTypeface) else metrics
                    }
                }
                val subtitleWidthKey = listOf(uiState.selectedSubtitleTrackIndex, uiState.selectedAddonSubtitle, measuredSubtitleTextMetrics)
                var widestSubtitleBlockPx by remember(subtitleWidthKey) { mutableStateOf<Float?>(null) }
                val subtitleMeasurePaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
                LaunchedEffect(subtitleWidthKey, prescannedSubtitleTexts) {
                    val metrics = measuredSubtitleTextMetrics ?: return@LaunchedEffect
                    val texts = prescannedSubtitleTexts
                    val widest = withContext(Dispatchers.Default) {
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                        texts.maxOfOrNull { measureSubtitleBlockPx(paint, it, metrics) }
                    } ?: return@LaunchedEffect
                    widestSubtitleBlockPx = maxOf(widestSubtitleBlockPx ?: 0f, widest)
                }
                LaunchedEffect(subtitleWidthKey, subtitleCueState) {
                    val metrics = measuredSubtitleTextMetrics ?: return@LaunchedEffect
                    if (!subtitleCueState.visible) return@LaunchedEffect
                    val width = if (subtitleCueState.hasBitmap) {
                        Float.MAX_VALUE
                    } else {
                        measureSubtitleBlockPx(subtitleMeasurePaint, subtitleCueState.text, metrics)
                    }
                    widestSubtitleBlockPx = maxOf(widestSubtitleBlockPx ?: 0f, width)
                }
                val subtitleTitleMarginPx = with(LocalDensity.current) { SUBTITLE_TITLE_MARGIN.toPx() }
                val subtitleClearsControlsTitle = measuredSubtitleTextMetrics?.let {
                    subtitleClearsTitle(widestSubtitleBlockPx, it.centerXPx, controlsTitleRightPx, subtitleTitleMarginPx)
                } == true
                val effectiveControlsClearancePx = effectiveSubtitleControlsClearancePx(
                    fullClearancePx = subtitleControlsClearancePx,
                    progressBarClearancePx = progressBarSubtitleClearancePx,
                    clearsTitle = subtitleClearsControlsTitle
                )
                var heldControlsClearancePx by remember { mutableFloatStateOf(0f) }
                val controlsClearanceMeasured = playerControlsVisible && subtitleControlsClearancePx > 0f
                LaunchedEffect(controlsClearanceMeasured, effectiveControlsClearancePx) {
                    heldControlsClearancePx = if (controlsClearanceMeasured) {
                        maxOf(heldControlsClearancePx, effectiveControlsClearancePx)
                    } else {
                        0f
                    }
                }
                val targetSubtitleLiftPx = computeSubtitleLiftPx(
                    previewConfiguredPosition = previewConfiguredSubtitlePosition,
                    controlsVisible = controlsClearanceMeasured,
                    controlsClearancePx = maxOf(heldControlsClearancePx, effectiveControlsClearancePx),
                    fallbackControlsLiftPx = fallbackSubtitleLiftPx,
                    skipVisible = skipButtonActuallyVisible,
                    skipClearancePx = skipClearancePx,
                    postPlayVisible = postPlayOverlayMode != null,
                    postPlayClearancePx = postPlayClearancePx,
                    seekVisible = seekOverlayVisible,
                    seekClearancePx = seekClearancePx
                )
                var subtitleLiftPx by remember { mutableFloatStateOf(0f) }
                var pendingDropCueVersion by remember { mutableStateOf<Long?>(null) }
                var subtitleDropTimedOut by remember { mutableStateOf(false) }
                var controlsHideAnimationFinished by remember { mutableStateOf(false) }
                LaunchedEffect(playerControlsVisible) {
                    controlsHideAnimationFinished = false
                    if (!playerControlsVisible) {
                        delay(PLAYER_CONTROLS_FADE_DURATION_MS.toLong())
                        controlsHideAnimationFinished = true
                    }
                }
                val applySubtitleLiftImmediately = shouldApplySubtitleLiftImmediately(
                    isPlaying = uiState.isPlaying,
                    previewConfiguredPosition = previewConfiguredSubtitlePosition,
                    controlsVisible = playerControlsVisible || !controlsHideAnimationFinished
                )
                LaunchedEffect(
                    targetSubtitleLiftPx,
                    subtitleCueState,
                    applySubtitleLiftImmediately,
                    subtitleDropTimedOut
                ) {
                    val decision = nextSubtitleLift(
                        targetLiftPx = targetSubtitleLiftPx,
                        appliedLiftPx = subtitleLiftPx,
                        cueVersion = subtitleCueState.version,
                        cueVisible = subtitleCueState.visible,
                        applyImmediately = applySubtitleLiftImmediately,
                        pendingDropCueVersion = pendingDropCueVersion,
                        dropTimedOut = subtitleDropTimedOut
                    )
                    subtitleLiftPx = decision.appliedLiftPx
                    pendingDropCueVersion = decision.pendingDropCueVersion
                    if (decision.pendingDropCueVersion == null) subtitleDropTimedOut = false
                }
                LaunchedEffect(pendingDropCueVersion) {
                    if (pendingDropCueVersion != null) {
                        delay(SUBTITLE_LIFT_DROP_TIMEOUT_MS)
                        subtitleDropTimedOut = true
                    }
                }
                if (uiState.internalPlayerEngine == InternalPlayerEngine.MVP_PLAYER) {
                    MpvPlayerSurface(
                        viewModel = viewModel,
                        isPlaying = uiState.isPlaying,
                        isBuffering = uiState.isBuffering,
                        aspectMode = uiState.aspectMode,
                        subtitleStyle = uiState.effectiveSubtitleStyle,
                        subtitleLiftPx = subtitleLiftPx,
                        subtitleTrackKey = subtitleWidthKey.take(2),
                        onSubtitleTextMetricsChanged = { subtitleTextMetrics = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    viewModel.exoPlayer?.let { player ->
                        ExoPlayerSurface(
                            player = player,
                            controller = viewModel.controller,
                            isPlaying = uiState.isPlaying,
                            isBuffering = uiState.isBuffering,
                            aspectMode = uiState.aspectMode,
                            tunnelingEnabled = uiState.tunnelingEnabled,
                            tunneledSurfaceFill = uiState.tunneledSurfaceFill,
                            useLibass = uiState.useLibass,
                            libassRenderMode = uiState.libassRenderMode,
                            letterboxMaskEnabled = !(transparentLetterbox && playerSurfaceIsFullscreen),
                            subtitleStyle = uiState.effectiveSubtitleStyle,
                            subtitleLiftPx = subtitleLiftPx,
                            onBindSubtitleView = viewModel::bindExoSubtitleView,
                            onSubtitleTextMetricsChanged = { subtitleTextMetrics = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (!postPlayRecommendationState.isVisible) {
                    val styledSubtitleCues by viewModel.controller.styledSubtitles.cues.collectAsState()
                    val fallbackSubtitleTextSizePx = with(LocalDensity.current) {
                        (SUBTITLE_BASE_TEXT_SP * renderedSubtitleStyle.size / 100f).sp.toPx()
                    }
                    StyledSubtitleOverlay(
                        cues = styledSubtitleCues,
                        style = renderedSubtitleStyle,
                        textSizePx = subtitleTextMetrics?.textSizePx ?: fallbackSubtitleTextSizePx,
                        liftPx = subtitleLiftPx,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (postPlayRecommendationState.canReturnToPlayer) {
                    PostPlayRecommendationPlayerWindow(
                        focusRequester = postPlayRecommendationPlayerWindowFocusRequester,
                        downFocusRequester = postPlayRecommendationFocusRequester,
                        onBack = handleBackPress,
                        onClick = {
                            returnToPlayerFromPostPlay()
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnToggleControls)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (!exitDispatched) {
            PostPlayRecommendationOverlay(
                state = postPlayRecommendationState,
                currentTitle = uiState.contentName ?: uiState.title,
                showManualPlayOption = effectiveAutoplayEnabled,
                playFocusRequester = postPlayRecommendationFocusRequester,
                playerWindowFocusRequester = postPlayRecommendationPlayerWindowFocusRequester,
                onBack = handleBackPress,
                onForceExit = { exitPlayer() },
                onStopTrailer = viewModel::onPostPlayTrailerEnded,
                onPlay = { recommendation ->
                    if (!exitDispatched) {
                        exitDispatched = true
                        viewModel.stopAndRelease()
                        currentOnPlayRecommendation(recommendation, false)
                    }
                },
                onPlayManually = { recommendation ->
                    if (!exitDispatched) {
                        exitDispatched = true
                        viewModel.stopAndRelease()
                        currentOnPlayRecommendation(recommendation, true)
                    }
                },
                onOpenDetails = { recommendation ->
                    if (!exitDispatched) {
                        exitDispatched = true
                        viewModel.stopAndRelease()
                        currentOnOpenRecommendationDetails(recommendation)
                    }
                },
                onPlayTrailer = viewModel::playPostPlayTrailer,
                onTrailerEnded = viewModel::onPostPlayTrailerEnded,
                onPreviousRecommendation = viewModel::showPreviousPostPlayRecommendation,
                onNextRecommendation = viewModel::showNextPostPlayRecommendation,
                mdbListRatingOrder = postPlayRecommendationState.mdbListRatingOrder,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            )
        }

        LoadingOverlay(
