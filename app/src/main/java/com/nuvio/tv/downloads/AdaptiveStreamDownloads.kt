package com.nuvio.tv.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.dash.offline.DashDownloader
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.offline.Downloader
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject

/** Non-evicting per-download cache. A completed marker is published only after every segment exists. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object AdaptiveStreamDownloads {
    private const val MAGIC = "CinemaOS adaptive offline 1"
    private val caches = mutableMapOf<String, SimpleCache>()
    fun type(url: String, hint: String? = null): String? {
        val path = Uri.parse(url).path.orEmpty().lowercase()
        return when {
            path.endsWith(".m3u8") || hint?.lowercase() in setOf("hls", "application/x-mpegurl", "application/vnd.apple.mpegurl") -> "hls"
            path.endsWith(".mpd") || hint?.lowercase() in setOf("dash", "application/dash+xml") -> "dash"
            else -> null
        }
    }
    fun detect(url: String, headers: Map<String, String>, hint: String? = null): String? {
        type(url, hint)?.let { return it }
        val path = Uri.parse(url).path.orEmpty().lowercase()
        if (listOf(".mp4", ".mkv", ".webm").any(path::endsWith)) return null
        val source = DefaultHttpDataSource.Factory()
            .setUserAgent(headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                ?: com.nuvio.tv.ui.screens.player.PlayerMediaSourceFactory.DEFAULT_USER_AGENT)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(15000)
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("Range", true) }).createDataSource()
        return try {
            source.open(DataSpec(Uri.parse(url)))
            val mime = source.responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.firstOrNull()?.substringBefore(';')
            type(url, mime)?.let { return it }
            val prefix = ByteArray(1024)
            var size = 0
            while (size < prefix.size) {
                val n = source.read(prefix, size, prefix.size - size)
                if (n < 0) break
                size += n
            }
            val text = String(prefix, 0, size, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            when {
                text.startsWith("#EXTM3U") -> "hls"
                Regex("<(?:(?:[A-Za-z0-9_]+):)?MPD(?:\\s|>)").containsMatchIn(text) -> "dash"
                else -> null
            }
        } finally { source.close() }
    }
    private fun directory(marker: File) = File(marker.parentFile, marker.name.removeSuffix(".part") + ".segments")
    @Synchronized private fun cache(context: Context, marker: File): SimpleCache {
        val dir = directory(marker)
        return caches.getOrPut(dir.absolutePath) {
            SimpleCache(dir, NoOpCacheEvictor(), StandaloneDatabaseProvider(context.applicationContext))
        }
    }
    fun markerType(url: String): String? = marker(url)?.second?.optString("kind")
    fun streamKeys(url: String): List<StreamKey> {
        val keys = marker(url)?.second?.optJSONArray("keys") ?: return emptyList()
        return (0 until keys.length()).map { i ->
            val key = keys.getJSONArray(i)
            StreamKey(key.getInt(0), key.getInt(1), key.getInt(2))
        }
    }
    fun isMarker(url: String): Boolean = marker(url) != null
    private fun marker(url: String): Pair<File, JSONObject>? = runCatching {
        val uri = Uri.parse(url)
        if (uri.scheme != "file") return null
        val file = File(requireNotNull(uri.path))
        if (!file.isFile || file.length() > 16384) return null
        val json = JSONObject(file.readText())
        if (json.optString("format") != MAGIC) return null
        file to json
    }.getOrNull()
    fun bytes(context: Context, marker: File): Long = cache(context, marker).cacheSpace
    @Synchronized fun remove(marker: File) {
        val dir = directory(marker)
        caches.remove(dir.absolutePath)?.release()
        dir.deleteRecursively()
    }

    suspend fun download(context: Context, url: String, headers: Map<String, String>, kind: String,
        marker: File, onProgress: (Long, Long?) -> Unit): File = runInterruptible {
        marker.parentFile?.mkdirs()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                ?: com.nuvio.tv.ui.screens.player.PlayerMediaSourceFactory.DEFAULT_USER_AGENT)
            .setConnectTimeoutMs(30000).setReadTimeoutMs(30000)
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("Range", true) && !it.equals("If-Range", true) })
        fun load(uri: Uri): Pair<Uri, ByteArray> {
            val source = http.createDataSource()
            try {
                source.open(DataSpec(uri))
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (true) {
                    if (Thread.interrupted()) throw InterruptedException()
                    val n = source.read(buffer, 0, buffer.size)
                    if (n < 0) break
                    check(output.size() + n <= 16 * 1024 * 1024) { "Manifest is too large" }
                    output.write(buffer, 0, n)
                }
                return (source.uri ?: uri) to output.toByteArray()
            } finally { source.close() }
        }
        val uri = Uri.parse(url)
        val (manifestUri, manifestBytes) = load(uri)
        val keys = mutableListOf<StreamKey>()
        if (kind == "hls") {
            fun checkMedia(uri: Uri, bytes: ByteArray, master: HlsMultivariantPlaylist = HlsMultivariantPlaylist.EMPTY) {
                val playlist = HlsPlaylistParser(master, null).parse(uri, bytes.inputStream()) as? HlsMediaPlaylist
                    ?: throw IOException("Expected an HLS media playlist")
                require(playlist.hasEndTag) { "Live HLS streams cannot be saved as a complete offline movie" }
                require(playlist.protectionSchemes == null) { "This stream requires a DRM offline license" }
            }
            when (val playlist = HlsPlaylistParser().parse(manifestUri, manifestBytes.inputStream())) {
                is HlsMediaPlaylist -> checkMedia(manifestUri, manifestBytes)
                is HlsMultivariantPlaylist -> {
                    require(playlist.sessionKeyDrmInitData.isEmpty()) { "This stream requires a DRM offline license" }
                    val best = playlist.variants.indices.maxByOrNull { playlist.variants[it].format.let { f -> f.width.toLong().coerceAtLeast(0) * f.height.coerceAtLeast(0) * 100000000L + f.bitrate.coerceAtLeast(0) } }
                        ?: throw IOException("HLS has no video variants")
                    keys += StreamKey(0, best)
                    load(playlist.variants[best].url).let { (resolved, bytes) -> checkMedia(resolved, bytes, playlist) }
                    playlist.audios.forEachIndexed { i, rendition -> rendition.url?.let {
                        load(it).let { (resolved, bytes) -> checkMedia(resolved, bytes, playlist) }; keys += StreamKey(1, i)
                    } }
                    playlist.subtitles.forEachIndexed { i, rendition -> rendition.url?.let {
                        load(it).let { (resolved, bytes) -> checkMedia(resolved, bytes, playlist) }; keys += StreamKey(2, i)
                    } }
                }
            }
        } else {
            val manifest = DashManifestParser().parse(manifestUri, manifestBytes.inputStream())
            require(!manifest.dynamic) { "Live DASH streams cannot be saved as a complete offline movie" }
            for (p in 0 until manifest.periodCount) {
                manifest.getPeriod(p).adaptationSets.forEachIndexed { a, set ->
                    require(set.representations.all { it.format.drmInitData == null }) { "This stream requires a DRM offline license" }
                    set.representations.indices.maxByOrNull { set.representations[it].format.bitrate }?.let {
                        keys += StreamKey(p, a, it)
                    }
                }
            }
            require(keys.isNotEmpty()) { "DASH has no downloadable tracks" }
        }
        val cache = cache(context, marker)
        val factory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(http)
        val item = MediaItem.Builder().setUri(uri).setStreamKeys(keys).build()
        val downloader: Downloader = if (kind == "hls") HlsDownloader(item, factory) else DashDownloader(item, factory)
        try {
            downloader.download { total, downloaded, _ -> onProgress(downloaded, total.takeIf { it >= 0 }) }
            if (Thread.interrupted()) throw InterruptedException()
            val partial = File(marker.parentFile, marker.name + ".part")
            val storedKeys = org.json.JSONArray()
            keys.forEach { storedKeys.put(org.json.JSONArray().put(it.periodIndex).put(it.groupIndex).put(it.streamIndex)) }
            partial.writeText(JSONObject().put("format", MAGIC).put("url", url).put("kind", kind).put("keys", storedKeys).toString())
            partial
        } finally { downloader.cancel() }
    }

    /** No upstream: missing cache entries fail instead of silently fetching an expiring online source. */
    fun playbackFactory(context: Context, url: String): DataSource.Factory? {
        val (file, json) = marker(url) ?: return null
        val original = Uri.parse(json.getString("url"))
        val factory = CacheDataSource.Factory().setCache(cache(context, file))
            .setUpstreamDataSourceFactory(null).setCacheWriteDataSinkFactory(null)
        return DataSource.Factory {
            val delegate = factory.createDataSource()
            object : DataSource by delegate {
                override fun open(dataSpec: DataSpec): Long = delegate.open(
                    if (dataSpec.uri.toString() == url) dataSpec.buildUpon().setUri(original).build() else dataSpec
                )
            }
        }
    }
}
