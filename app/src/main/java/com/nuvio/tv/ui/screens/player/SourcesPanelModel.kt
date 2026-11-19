package com.nuvio.tv.ui.screens.player

import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.nuvio.tv.core.player.autoPlayProviderRank
import com.nuvio.tv.domain.model.Stream
import java.util.Locale

/**
 * Display models for the in-player sources panel.
 *
 * Everything the rows show (quality, container format, spec chips, size) is parsed from free-form
 * names/titles/filenames with ~30 regexes per stream. That work is done once per distinct stream
 * content, off the main thread, and cached, so opening the panel only composes ready-made rows.
 */

internal enum class SourceFormatFilter(val label: String) {
    ALL("All"), HD("1080p+"), FILE("MP4 / MKV"), HLS("HLS"), DASH("DASH")
}

private val RX_QUALITY_4K = Regex("4k|2160|uhd", RegexOption.IGNORE_CASE)
private val RX_QUALITY_P = Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE)

internal fun Stream.sourceQuality(): Int {
    if (qualityValue > 0) return qualityValue
    val text = listOfNotNull(name, title, description).joinToString(" ")
    if (RX_QUALITY_4K.containsMatchIn(text)) return 2160
    return RX_QUALITY_P.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

internal fun Stream.sourceFormat(): String {
    val u = (getStreamUrl() ?: return "P2P").lowercase(Locale.US).substringBefore('?')
    return when {
        u.contains(".m3u8") || u.contains("/hls") -> "HLS"
        u.contains(".mpd") -> "DASH"
        u.contains(".mkv") -> "MKV"
        else -> "MP4"
    }
}

internal fun sourceQualityLabel(q: Int): String = when {
    q >= 2160 -> "4K"
    q > 0 -> "${q}p"
    else -> "Auto"
}

/** Identity of a link that survives badge/debrid re-copies of the same stream. */
internal fun Stream.sourceContentKey(): String {
    getStreamUrl()?.let { return "$addonName|u:$it" }
    infoHash?.takeIf { it.isNotBlank() }?.let { return "$addonName|h:${it.lowercase(Locale.US)}:${fileIdx ?: ""}" }
    return "$addonName|n:$name|$title|${description?.hashCode() ?: 0}"
}

@Immutable
internal class ParsedSource(
    val quality: Int,
    val format: String,
    val specs: List<SourceSpec>,
    val size: String?,
    /** Best-effort size in bytes (0 when unknown); used to rank links of the same quality. */
    val sizeBytes: Long = 0L
)

private val SIZE_SUFFIXES = arrayOf(" GB", " MB")

/** Everything sourceSpecs()/sourceQuality()/sourceFormat() read, and nothing else (badges excluded). */
private fun Stream.parseKey(): String = buildString {
    append(addonName).append('\u0001')
    append(url).append('\u0001')
    append(externalUrl).append('\u0001')
    append(name).append('\u0001')
    append(title).append('\u0001')
    append(description).append('\u0001')
    append(quality).append('\u0001')
    append(qualityValue).append('\u0001')
    append(behaviorHints?.filename).append('\u0001')
    append(behaviorHints?.bingeGroup).append('\u0001')
    append(behaviorHints?.videoSize)
}

private object SourceParseCache {
    // android.util.LruCache is synchronized, so the background builder and the UI can share it.
    val entries = LruCache<String, ParsedSource>(2048)
}

private fun sizeLabelToBytes(label: String?): Long {
    if (label == null) return 0L
    val n = label.substringBefore(' ').toDoubleOrNull() ?: return 0L
    return when {
        label.endsWith(" GB") -> (n * 1_073_741_824.0).toLong()
        label.endsWith(" MB") -> (n * 1_048_576.0).toLong()
        else -> 0L
    }
}

internal fun Stream.parsedSource(): ParsedSource {
    val key = parseKey()
    SourceParseCache.entries.get(key)?.let { return it }
    val allSpecs = sourceSpecs()
    val sizeSpec = allSpecs.firstOrNull { spec -> spec.icon == null && SIZE_SUFFIXES.any { spec.label.endsWith(it) } }
    val parsed = ParsedSource(
        quality = sourceQuality(),
        format = sourceFormat(),
        specs = if (sizeSpec == null) allSpecs else allSpecs - sizeSpec,
        size = sizeSpec?.label,
        sizeBytes = behaviorHints?.videoSize?.takeIf { it > 0 } ?: sizeLabelToBytes(sizeSpec?.label)
    )
    SourceParseCache.entries.put(key, parsed)
    return parsed
}

/** One server row, fully prepared for display. */
@Immutable
internal class SourceRowModel(
    /** Unique LazyColumn key. */
    val key: String,
    /** Matches [sourceContentKey] of the stream it was built from. */
    val contentKey: String,
    val stream: Stream,
    val provider: String,
    val quality: Int,
    val qualityLabel: String,
    val format: String,
    val title: String,
    val specs: List<SourceSpec>,
    /** "4.2 GB · Plugin name", or null. */
    val detail: String?,
    val sizeBytes: Long = 0L
)

@Immutable
internal class SourceProviderModel(
    val name: String,
    val rows: List<SourceRowModel>,
    val bestQuality: Int,
    val formats: String,
    val rank: Int
)

/** Builds every row from the raw list. Heavy (regex parsing on cache miss): call off the main thread. */
internal fun buildSourceRows(streams: List<Stream>): List<SourceRowModel> {
    val out = ArrayList<SourceRowModel>(streams.size)
    val seen = HashSet<String>(streams.size * 2)
    for (s in streams) {
        if (s.isExternal()) continue
        val contentKey = s.sourceContentKey()
        val hasLink = s.getStreamUrl() != null
        var key = contentKey
        if (hasLink) {
            // Every distinct link of a provider is its own row; repeats of the same link collapse.
            if (!seen.add(contentKey)) continue
        } else {
            var n = 1
            while (!seen.add(key)) key = "$contentKey#${n++}"
        }
        val parsed = s.parsedSource()
        val plugin = s.pluginSourceName?.takeIf { it.isNotBlank() && it != s.addonName }
        out += SourceRowModel(
            key = key,
            contentKey = contentKey,
            stream = s,
            provider = s.addonName,
            quality = parsed.quality,
            qualityLabel = sourceQualityLabel(parsed.quality),
            format = parsed.format,
            title = s.getDisplayNameOrNull() ?: s.name ?: s.addonName,
            specs = parsed.specs,
            detail = listOfNotNull(parsed.size, plugin).joinToString(" · ").ifEmpty { null },
            sizeBytes = parsed.sizeBytes
        )
    }
    return out
}

/** Cheap: works on pre-parsed ints/strings only, so it is fine to run during composition. */
internal fun groupSourceRows(
    rows: List<SourceRowModel>,
    filter: SourceFormatFilter,
    currentProvider: String?
): List<SourceProviderModel> {
    val kept = if (filter == SourceFormatFilter.ALL) rows else rows.filter { r ->
        when (filter) {
            SourceFormatFilter.ALL -> true
            SourceFormatFilter.HD -> r.quality >= 1080
            SourceFormatFilter.FILE -> r.format == "MP4" || r.format == "MKV"
            SourceFormatFilter.HLS -> r.format == "HLS"
            SourceFormatFilter.DASH -> r.format == "DASH"
        }
    }
    return kept.groupBy { it.provider }
        .map { (provider, list) ->
            val sorted = list.sortedByDescending { it.quality }
            SourceProviderModel(
                name = provider,
                rows = sorted,
                bestQuality = sorted.first().quality,
                formats = sorted.asSequence().map { it.format }.distinct().joinToString(", "),
                rank = autoPlayProviderRank(provider)
            )
        }
        .sortedWith(
            compareByDescending<SourceProviderModel> { it.name == currentProvider }
                .thenByDescending { it.rank }
                .thenByDescending { it.bestQuality }
        )
}

/** Quality bucket used by the player's Quality picker: 2160 / 1440 / 1080 / 720 / 480 / 360 (SD) / 0 (Auto). */
internal fun qualityBucket(q: Int): Int = when {
    q >= 2000 -> 2160
    q >= 1300 -> 1440
    q >= 1000 -> 1080
    q >= 700 -> 720
    q >= 400 -> 480
    q > 0 -> 360
    else -> 0
}

/** Short tile label for a [qualityBucket]. */
internal fun qualityBucketShortLabel(bucket: Int): String = when (bucket) {
    2160 -> "4K"
    360 -> "SD"
    0 -> "Auto"
    else -> "${bucket}p"
}

/** Title line for a [qualityBucket]. */
internal fun qualityBucketTitle(bucket: Int): String = when (bucket) {
    2160 -> "Ultra HD 4K"
    1440 -> "QHD 1440p"
    1080 -> "Full HD 1080p"
    720 -> "HD 720p"
    480 -> "SD 480p"
    360 -> "SD"
    else -> "Auto"
}

/** One entry of the Quality picker: every loaded source of one quality bucket. */
@Immutable
internal class QualityOptionModel(
    val bucket: Int,
    /** Number of distinct sources (links) in this bucket, across all providers. */
    val sourceCount: Int,
    val providerCount: Int,
    /** The source selecting this entry switches to. */
    val best: SourceRowModel,
    /** The playing source when it belongs to this bucket, else null. */
    val current: SourceRowModel?
)

/**
 * Groups ALL loaded sources by quality bucket (best first). The pick for each bucket is, in order:
 * same provider as the playing source, larger size, richer badges (spec chips). Cheap and pure, but
 * run it off the main thread together with [buildSourceRows].
 */
internal fun buildQualityOptions(
    rows: List<SourceRowModel>,
    currentContentKey: String?,
    currentProvider: String?
): List<QualityOptionModel> {
    val pickOrder = compareByDescending<SourceRowModel> { it.provider == currentProvider }
        .thenByDescending { it.sizeBytes }
        .thenByDescending { it.specs.size }
        .thenByDescending { it.quality }
    return rows.groupBy { qualityBucket(it.quality) }
        .map { (bucket, list) ->
            QualityOptionModel(
                bucket = bucket,
                sourceCount = list.size,
                providerCount = list.asSequence().map { it.provider }.distinct().count(),
                best = list.sortedWith(pickOrder).first(),
                current = currentContentKey?.let { key -> list.firstOrNull { it.contentKey == key } }
            )
        }
        .sortedByDescending { it.bucket }
}
