@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.updater.ui

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.updater.UpdateUiState
import com.nuvio.tv.updater.model.AppUpdate

private val GlassBg = Color(0xFF0C0D10)

/** Premium dark-glass OTA update modal: notes, Update now / Later, progress, installing. */
@Composable
internal fun UpdateModal(
    state: UpdateUiState,
    update: AppUpdate,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val primaryFocus = remember { FocusRequester() }
    var started by remember { mutableStateOf(false) }
    val busy = started && state.errorMessage == null
    val installing = state.downloadedApkPath != null && started

    LaunchedEffect(update.tag) { primaryFocus.requestFocusAfterFrames() }
    // Once the download finishes after "Update now", hand straight over to the installer.
    LaunchedEffect(state.downloadedApkPath, started) {
        if (started && state.downloadedApkPath != null) onInstall()
    }
    LaunchedEffect(state.errorMessage) { if (state.errorMessage != null) started = false }

    val version = update.tag.let { if (it.startsWith("v")) it else "v$it" }
    val sizeLabel = update.assetSizeBytes?.let { Formatter.formatShortFileSize(context, it) }
    val notes = remember(update.notes) {
        update.notes.lines()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .take(6)
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(620.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(GlassBg.copy(alpha = 0.96f), Color(0xFF14161B).copy(alpha = 0.96f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 36.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo_wordmark),
                    contentDescription = null,
                    modifier = Modifier.height(34.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Update available · $version",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (sizeLabel != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = sizeLabel, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                }
                Spacer(Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "What's new",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    if (notes.isEmpty()) {
                        Text("Performance and stability improvements", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                    } else notes.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp)
                                    .size(5.dp)
                                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
                            )
                            Text(
                                text = line,
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))

                when {
                    installing -> StatusBlock("Installing…", null)
                    busy && state.isDownloading -> {
                        val p = state.downloadProgress
                        StatusBlock(
                            if (p != null) "Downloading ${(p * 100).toInt().coerceIn(0, 100)}%" else "Preparing download…",
                            p
                        )
                    }
                    busy -> StatusBlock("Preparing download…", null)
                    else -> {
                        if (state.errorMessage != null) {
                            Text(
                                text = state.errorMessage,
                                color = Color(0xFFFF8A80),
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Button(
                                onClick = {
                                    started = true
                                    if (state.downloadedApkPath == null) onDownload()
                                },
                                modifier = Modifier.focusRequester(primaryFocus),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    contentColor = Color.Black,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.55f)), shape = RoundedCornerShape(50))
                                ),
                                scale = ButtonDefaults.scale(focusedScale = 1.06f),
                                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    if (state.errorMessage != null) "Retry" else "Update now",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                )
                            }
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.20f),
                                    contentColor = Color.White.copy(alpha = 0.85f),
                                    focusedContentColor = Color.White
                                ),
                                border = ButtonDefaults.border(
                                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), shape = RoundedCornerShape(50)),
                                    focusedBorder = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.55f)), shape = RoundedCornerShape(50))
                                ),
                                scale = ButtonDefaults.scale(focusedScale = 1.06f),
                                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text("Later", fontWeight = FontWeight.Medium, fontSize = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBlock(label: String, progress: Float?) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
        }
    }
}
