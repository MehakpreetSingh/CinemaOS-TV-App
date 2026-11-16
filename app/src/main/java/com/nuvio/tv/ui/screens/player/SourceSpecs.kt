package com.nuvio.tv.ui.screens.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Stream
import java.util.Locale

/** One spec chip shown for a source: optional vector icon plus text. */
internal data class SourceSpec(val label: String, @DrawableRes val icon: Int? = null)

private val RX_WEBDL = Regex("web[-. ]?dl|\\bweb\\b|\\bamzn\\b|\\bnf\\b|\\bdsnp\\b", RegexOption.IGNORE_CASE)
private val RX_WEBRIP = Regex("web[-. ]?rip", RegexOption.IGNORE_CASE)
private val RX_BLURAY = Regex("blu[-. ]?ray|bdrip|brrip", RegexOption.IGNORE_CASE)
private val RX_REMUX = Regex("remux", RegexOption.IGNORE_CASE)
private val RX_ATMOS = Regex("atmos", RegexOption.IGNORE_CASE)
private val RX_DDP = Regex("\\bddp|dd\\+|e[-. ]?ac[-. ]?3|dolby\\s*digital\\s*plus", RegexOption.IGNORE_CASE)
private val RX_DD = Regex("\\bdd[-. ]?[257]|\\bac[-. ]?3\\b|dolby\\s*digital", RegexOption.IGNORE_CASE)
private val RX_DTSX = Regex("dts[-: .]?x", RegexOption.IGNORE_CASE)
private val RX_DTSHD = Regex("dts[-: .]?hd", RegexOption.IGNORE_CASE)
private val RX_DTSMA = Regex("dts[-: .]?hd[-: .]?ma|\\bma\\b", RegexOption.IGNORE_CASE)
private val RX_DTS = Regex("\\bdts\\b", RegexOption.IGNORE_CASE)
private val RX_TRUEHD = Regex("true[-. ]?hd", RegexOption.IGNORE_CASE)
private val RX_DV = Regex("dolby[-. ]?vision|\\bdovi\\b|\\bdv\\b", RegexOption.IGNORE_CASE)
private val RX_HDR10P = Regex("hdr10\\+|hdr10plus", RegexOption.IGNORE_CASE)
private val RX_HDR10 = Regex("hdr10", RegexOption.IGNORE_CASE)
private val RX_HDR = Regex("hdr(?!ip)", RegexOption.IGNORE_CASE)
private val RX_SDR = Regex("\\bsdr\\b", RegexOption.IGNORE_CASE)
private val RX_HEVC = Regex("hevc|x[. ]?265|h[. ]?265", RegexOption.IGNORE_CASE)
private val RX_AV1 = Regex("\\bav1\\b", RegexOption.IGNORE_CASE)
private val RX_H264 = Regex("x[. ]?264|h[. ]?264|\\bavc\\b", RegexOption.IGNORE_CASE)
private val RX_10BIT = Regex("10[-. ]?bit|hi10", RegexOption.IGNORE_CASE)
private val RX_CH = Regex("(?<![\\d.])([2578])[. ]([01])(?!\\d)", RegexOption.IGNORE_CASE)
private val RX_DDP_CH = Regex("(?:ddp|dd\\+|dd|e-?ac-?3|ac-?3|aac|dts)[-. ]?([2578])(?![\\d])", RegexOption.IGNORE_CASE)
private val RX_SIZE = Regex("(\\d+(?:[.,]\\d+)?)\\s?(gb|mb)\\b", RegexOption.IGNORE_CASE)

internal fun formatSourceSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)
}

/** Parses name/title/description/filename keywords into ordered spec chips. */
internal fun Stream.sourceSpecs(): List<SourceSpec> {
    val urlName = listOfNotNull(url, externalUrl).firstOrNull()?.substringBefore('?')?.substringAfterLast('/')
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    val t = listOfNotNull(
        name, title, description, behaviorHints?.filename, behaviorHints?.bingeGroup, urlName, quality
    ).joinToString(" ")
        .replace('_', ' ')
        .replace(Regex("(?i)\\bdolby\\s+vision\\b"), "Dolby Vision")
    val out = ArrayList<SourceSpec>()

    when {
        RX_REMUX.containsMatchIn(t) -> out += SourceSpec("REMUX")
        RX_WEBDL.containsMatchIn(t) -> out += SourceSpec("WEB-DL", R.drawable.ic_spec_cloud)
        RX_WEBRIP.containsMatchIn(t) -> out += SourceSpec("WEBRip", R.drawable.ic_spec_cloud)
        RX_BLURAY.containsMatchIn(t) -> out += SourceSpec("BluRay")
    }
    if (RX_REMUX.containsMatchIn(t) && RX_BLURAY.containsMatchIn(t)) out += SourceSpec("BluRay")

    val ch = RX_DDP_CH.find(t)?.groupValues?.get(1)?.let { n -> if (n == "7") "7.1" else if (n == "5") "5.1" else "$n.0" }
        ?: RX_CH.find(t)?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }
    val ddp = RX_DDP.containsMatchIn(t)
    var audioAdded = false
    if (RX_ATMOS.containsMatchIn(t)) {
        val base = if (ddp) "Dolby Atmos (DD+)" else "Dolby Atmos"
        out += SourceSpec(if (ch != null) "$base $ch" else base, R.drawable.ic_spec_atmos)
        audioAdded = true
    } else if (ddp) {
        out += SourceSpec(if (ch != null) "DD+ $ch" else "DD+", R.drawable.ic_spec_dolby)
        audioAdded = true
    } else if (RX_DD.containsMatchIn(t)) {
        out += SourceSpec(if (ch != null) "DD $ch" else "DD", R.drawable.ic_spec_dolby)
        audioAdded = true
    }
    when {
        RX_DTSX.containsMatchIn(t) -> { out += SourceSpec("DTS:X", R.drawable.ic_spec_dts); audioAdded = true }
        RX_DTSHD.containsMatchIn(t) -> {
            out += SourceSpec(if (RX_DTSMA.containsMatchIn(t)) "DTS-HD MA" else "DTS-HD", R.drawable.ic_spec_dts); audioAdded = true
        }
        RX_DTS.containsMatchIn(t) -> { out += SourceSpec("DTS", R.drawable.ic_spec_dts); audioAdded = true }
    }
    if (RX_TRUEHD.containsMatchIn(t)) { out += SourceSpec("TrueHD", R.drawable.ic_spec_dolby); audioAdded = true }
    if (!audioAdded && ch != null) out += SourceSpec(ch)

    when {
        RX_DV.containsMatchIn(t) -> out += SourceSpec("Dolby Vision", R.drawable.ic_spec_dolby_vision)
    }
    when {
        RX_HDR10P.containsMatchIn(t) -> out += SourceSpec("HDR10+", R.drawable.ic_spec_hdr)
        RX_HDR10.containsMatchIn(t) -> out += SourceSpec("HDR10", R.drawable.ic_spec_hdr)
        RX_HDR.containsMatchIn(t) -> out += SourceSpec("HDR", R.drawable.ic_spec_hdr)
        RX_SDR.containsMatchIn(t) -> out += SourceSpec("SDR")
    }
    when {
        RX_AV1.containsMatchIn(t) -> out += SourceSpec("AV1")
        RX_HEVC.containsMatchIn(t) -> out += SourceSpec("HEVC")
        RX_H264.containsMatchIn(t) -> out += SourceSpec("H.264")
    }
    if (RX_10BIT.containsMatchIn(t)) out += SourceSpec("10-bit")

    val size = formatSourceSize(behaviorHints?.videoSize)
        ?: RX_SIZE.find(t)?.let {
            val n = it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let null
            val unit = it.groupValues[2].uppercase(Locale.US)
            if (unit == "GB") String.format(Locale.US, "%.1f GB", n) else String.format(Locale.US, "%.0f MB", n)
        }
    if (size != null) out += SourceSpec(size)
    return out.distinctBy { it.label }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceSpecChips(
    specs: List<SourceSpec>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    maxChips: Int = 8
) {
    if (specs.isEmpty()) return
    val fg = if (selected) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    val bg = if (selected) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.1f)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        specs.take(maxChips).forEach { spec ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(bg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                if (spec.icon != null) {
                    Icon(
                        painter = painterResource(spec.icon),
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(text = spec.label, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
            }
        }
    }
}
