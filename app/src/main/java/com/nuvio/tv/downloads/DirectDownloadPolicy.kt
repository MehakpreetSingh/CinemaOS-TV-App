package com.nuvio.tv.downloads

import java.net.URI

internal object DirectDownloadPolicy {
    fun extension(url: String, filename: String?, streamType: String? = null): String? {
        if (streamType?.lowercase() in setOf("hls", "dash", "application/x-mpegurl", "application/vnd.apple.mpegurl", "application/dash+xml")) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrBlank()) return null
        val path = uri.path.orEmpty().lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".torrent")) return null
        return listOf(path, filename.orEmpty().lowercase()).firstNotNullOfOrNull { value ->
            listOf("mp4", "mkv", "webm").firstOrNull { value.endsWith(".$it") }
        }
    }

    fun isVideoHeader(bytes: ByteArray): Boolean {
        val ebml = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())
        return bytes.take(4).toByteArray().contentEquals(ebml) ||
            (bytes.size >= 8 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp")
    }
}
