package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The CinemaOS TV prototype is laid out on a 1920x1080 stage. Android TVs report different dp
 * widths depending on density (960dp at xhdpi, 1920dp at mdpi), so every prototype pixel value is
 * scaled by screenWidthDp / 1920 to keep the same proportions on any TV.
 */
@Composable
@ReadOnlyComposable
fun cinemaScale(): Float = LocalConfiguration.current.screenWidthDp / 1920f

/** Prototype pixels -> dp. */
@Composable
@ReadOnlyComposable
fun cdp(prototypePx: Number): Dp = (prototypePx.toFloat() * cinemaScale()).dp

/** Prototype pixels -> sp. */
@Composable
@ReadOnlyComposable
fun csp(prototypePx: Number): TextUnit = (prototypePx.toFloat() * cinemaScale()).sp
