package com.nuvio.tv.core.player

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.view.Display
import com.nuvio.tv.domain.model.Stream

/**
 * What this TV can actually play: display max resolution and the hardware/software decoders
 * for HEVC / AV1 (incl. 10-bit HDR profiles) and Dolby Vision. Detected lazily, once.
 */
object DeviceVideoCapabilities {
    data class Caps(
        val maxDisplayHeight: Int,
        val hevc: Boolean,
        val hevc4k: Boolean,
        val hevc10bit: Boolean,
        val av1: Boolean,
        val av1_4k: Boolean,
        val dolbyVision: Boolean,
        val h264_4k: Boolean
    )

    /** Conservative fallback used when the application context cannot be resolved. */
    private val FALLBACK = Caps(1080, hevc = true, hevc4k = false, hevc10bit = false, av1 = false, av1_4k = false, dolbyVision = false, h264_4k = false)

    @Volatile
    private var cached: Caps? = null

    fun get(): Caps = cached ?: synchronized(this) { cached ?: detect().also { cached = it } }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun appContext(): Context? = runCatching {
        val at = Class.forName("android.app.ActivityThread")
        at.getMethod("currentApplication").invoke(null) as? Application
    }.getOrNull()

    private fun detect(): Caps {
        val ctx = appContext() ?: return FALLBACK
        return runCatching {
            Caps(
                maxDisplayHeight = detectDisplayHeight(ctx),
                hevc = hasDecoder("video/hevc"),
                hevc4k = hasDecoder("video/hevc", 3840, 2160),
                hevc10bit = hasHevcMain10(),
                av1 = hasDecoder("video/av01"),
                av1_4k = hasDecoder("video/av01", 3840, 2160),
                dolbyVision = hasDecoder("video/dolby-vision"),
                h264_4k = hasDecoder("video/avc", 3840, 2160)
            )
        }.getOrDefault(FALLBACK)
    }

    private fun detectDisplayHeight(ctx: Context): Int {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return 1080
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return 1080
        var best = 0
        val modes = display.supportedModes
        for (m in modes) {
            val w = maxOf(m.physicalWidth, m.physicalHeight)
            val h = minOf(m.physicalWidth, m.physicalHeight)
            val tier = when {
                w >= 3800 || h >= 2100 -> 2160
                w >= 2500 || h >= 1400 -> 1440
                w >= 1900 || h >= 1000 -> 1080
                else -> 720
            }
            if (tier > best) best = tier
        }
        return if (best == 0) 1080 else best
    }

    private fun decoders(mime: String): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }

    private fun hasDecoder(mime: String, width: Int = 0, height: Int = 0): Boolean = decoders(mime).any { info ->
        if (width == 0) true else runCatching {
            info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(width, height) == true
        }.getOrDefault(false)
    }

    private fun hasHevcMain10(): Boolean = decoders("video/hevc").any { info ->
        runCatching {
            info.getCapabilitiesForType("video/hevc").profileLevels.any {
                it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
            }
        }.getOrDefault(false)
    }

    private val HEVC = Regex("hevc|x265|h\\.?265", RegexOption.IGNORE_CASE)
    private val AV1 = Regex("\\bav1\\b", RegexOption.IGNORE_CASE)
    private val DV = Regex("dolby\\s*vision|\\bdovi\\b|\\bdv\\b", RegexOption.IGNORE_CASE)
    private val HDR = Regex("hdr", RegexOption.IGNORE_CASE)
    private val TEN_BIT = Regex("10[\\s-]?bit|main\\s*10", RegexOption.IGNORE_CASE)
    private val UHD = Regex("4k|2160|uhd", RegexOption.IGNORE_CASE)
    private val P = Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE)

    /** Nominal height declared by the stream (0 when unknown). */
    fun declaredHeight(stream: Stream): Int {
        if (stream.qualityValue > 0) return stream.qualityValue
        val text = listOfNotNull(stream.name, stream.title, stream.description).joinToString(" ")
        if (UHD.containsMatchIn(text)) return 2160
        P.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return if (Regex("\\bhd\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) 720 else 0
    }

    /**
     * Quality this device can really play for [stream]: the declared height clamped to the display and
     * to the decoder that the stream's codec needs. Returns 0 when its codec cannot be decoded at all.
     */
    fun playableHeight(stream: Stream): Int {
        val caps = get()
        val text = listOfNotNull(stream.name, stream.title, stream.description).joinToString(" ")
        val declared = declaredHeight(stream)
        if (declared <= 0) return 1
        val isAv1 = AV1.containsMatchIn(text)
        val isHevc = HEVC.containsMatchIn(text)
        val isDv = DV.containsMatchIn(text)
        val isHdr = HDR.containsMatchIn(text)
        val tenBit = TEN_BIT.containsMatchIn(text)
        val needsHevc = isHevc || isDv || isHdr || (!isAv1 && declared >= 2160)

        var cap = caps.maxDisplayHeight
        when {
            isAv1 -> {
                if (!caps.av1) return 0
                if (declared >= 2160 && !caps.av1_4k) cap = minOf(cap, 1080)
            }
            needsHevc -> {
                if (!caps.hevc) return 0
                if ((isHdr || tenBit) && !caps.hevc10bit) return 0
                if (declared >= 2160 && !caps.hevc4k) cap = minOf(cap, 1080)
            }
            else -> if (declared >= 2160 && !caps.h264_4k) cap = minOf(cap, 1080)
        }
        return minOf(declared, cap)
    }

    /** Short label: 4K / FHD / HD / SD for a height. */
    fun badgeFor(height: Int): String = when {
        height >= 2000 -> "4K"
        height >= 1000 -> "FHD"
        height >= 700 -> "HD"
        height > 0 -> "SD"
        else -> "AUTO"
    }
}
