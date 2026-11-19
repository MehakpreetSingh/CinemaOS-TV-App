package com.nuvio.tv.downloads

import androidx.media3.datasource.DataSpec
import android.net.Uri
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class AdaptiveStreamDownloadsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun read(factory: androidx.media3.datasource.DataSource.Factory, url: String): Pair<String, String> {
        val source = factory.createDataSource()
        try {
            source.open(DataSpec(Uri.parse(url)))
            val output = java.io.ByteArrayOutputStream()
            val b = ByteArray(4096)
            while (true) { val n = source.read(b, 0, b.size); if (n < 0) break; output.write(b, 0, n) }
            return source.uri.toString() to output.toString("UTF-8")
        } finally { source.close() }
    }
    @Test fun hlsSavesSegmentsAndKeysAndReadsOfflineWithoutNetwork(): Unit = runBlocking {
        val server = MockWebServer()
        val requests = java.util.Collections.synchronizedList(mutableListOf<RecordedRequest>())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(r: RecordedRequest): MockResponse {
                requests += r
                return MockResponse().setBody(when(r.path) {
                    "/master.m3u8" -> return MockResponse().setResponseCode(302).setHeader("Location", "/cdn/master.m3u8")
                    "/cdn/master.m3u8" -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=200\nhigh.m3u8\n"
                    "/cdn/high.m3u8" -> "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\",IV=0x00000000000000000000000000000001\n#EXTINF:6,\nsegment.ts\n#EXT-X-ENDLIST\n"
                    "/cdn/key" -> "0123456789abcdef"
                    "/cdn/segment.ts" -> "offline segment"
                    else -> return MockResponse().setResponseCode(404)
                })
            }
        }
        server.start()
        val url = server.url("/master.m3u8").toString()
        val destination = File(context.cacheDir, "test-${System.nanoTime()}.m3u8")
        try {
            val partial = AdaptiveStreamDownloads.download(context, url, mapOf("Authorization" to "Bearer fixture"), "hls", destination) { _, _ -> }
            assertTrue(partial.renameTo(destination))
            assertTrue(requests.all { it.getHeader("Authorization") == "Bearer fixture" })
            assertFalse(requests.any { it.path.orEmpty().endsWith("/low.m3u8") })
            server.shutdown()
            val factory = requireNotNull(AdaptiveStreamDownloads.playbackFactory(context, destination.toURI().toString()))
            assertEquals(url.replace("/master.m3u8", "/cdn/master.m3u8"), read(factory, destination.toURI().toString()).first)
            assertEquals("offline segment", read(factory, url.replace("master.m3u8", "cdn/segment.ts")).second)
            assertEquals("0123456789abcdef", read(factory, url.replace("master.m3u8", "cdn/key")).second)
            assertEquals(1, AdaptiveStreamDownloads.streamKeys(destination.toURI().toString()).size)
            try { read(factory, url.replace("master.m3u8", "missing.ts")); fail("Missing cache must fail") } catch (_: java.io.IOException) { }
        } finally { runCatching { server.shutdown() }; AdaptiveStreamDownloads.remove(destination); destination.delete() }
    }
    @Test fun dashDownloadsVideoAndAudioAndRetainsOfflineManifest(): Unit = runBlocking {
        val server = MockWebServer()
        val mpd = """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT6S" minBufferTime="PT1S">
            <Period duration="PT6S"><AdaptationSet mimeType="video/mp4"><Representation id="v" bandwidth="1000" codecs="avc1.42c01e" width="320" height="180">
            <SegmentTemplate timescale="1" duration="6" startNumber="1" initialization="v-init.mp4" media="v-${'$'}Number${'$'}.m4s"/></Representation></AdaptationSet>
            <AdaptationSet mimeType="audio/mp4" lang="en"><Representation id="a" bandwidth="128" codecs="mp4a.40.2" audioSamplingRate="48000">
            <SegmentTemplate timescale="1" duration="6" startNumber="1" initialization="a-init.mp4" media="a-${'$'}Number${'$'}.m4s"/></Representation></AdaptationSet></Period></MPD>""".trimIndent()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(r: RecordedRequest) = MockResponse().setBody(if (r.path == "/movie.mpd") mpd else "segment ${r.path}")
        }
        server.start()
        val url = server.url("/movie.mpd").toString()
        val destination = File(context.cacheDir, "test-${System.nanoTime()}.mpd")
        try {
            val partial = AdaptiveStreamDownloads.download(context, url, emptyMap(), "dash", destination) { _, _ -> }
            assertTrue(partial.renameTo(destination)); server.shutdown()
            val factory = requireNotNull(AdaptiveStreamDownloads.playbackFactory(context, destination.toURI().toString()))
            assertEquals(mpd, read(factory, destination.toURI().toString()).second)
            assertEquals("segment /v-1.m4s", read(factory, url.replace("movie.mpd", "v-1.m4s")).second)
            assertEquals("segment /a-1.m4s", read(factory, url.replace("movie.mpd", "a-1.m4s")).second)
        } finally { runCatching { server.shutdown() }; AdaptiveStreamDownloads.remove(destination); destination.delete() }
    }
    @Test fun livePlaylistIsRejectedWithoutPublishingCompletedMarker(): Unit = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nlive.ts\n")); server.start()
        val destination = File(context.cacheDir, "test-${System.nanoTime()}.m3u8")
        try {
            try { AdaptiveStreamDownloads.download(context, server.url("/live.m3u8").toString(), emptyMap(), "hls", destination) { _, _ -> }; fail("Live download must reject") }
            catch (_: IllegalArgumentException) { }
            assertFalse(destination.exists())
        } finally { server.shutdown(); AdaptiveStreamDownloads.remove(destination) }
    }
    @Test fun extensionlessManifestsAreDetectedWithoutRelyingOnFilename() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/dash+xml; charset=utf-8").setBody("<MPD/>"))
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-TARGETDURATION:6\n"))
            server.start()
            assertEquals("dash", AdaptiveStreamDownloads.detect(server.url("/watch?id=1").toString(), emptyMap()))
            assertEquals("hls", AdaptiveStreamDownloads.detect(server.url("/watch?id=2").toString(), emptyMap()))
        }
    }
    @Test fun drmDashIsRejectedBeforeDownloadingMedia(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" xmlns:cenc="urn:mpeg:cenc:2013" type="static" mediaPresentationDuration="PT6S" minBufferTime="PT1S">
                <Period><AdaptationSet mimeType="video/mp4"><ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc" cenc:default_KID="12345678-1234-1234-1234-123456789abc"/>
                <Representation id="v" bandwidth="1000" codecs="avc1.42c01e"><SegmentTemplate timescale="1" duration="6" initialization="init.mp4" media="v-${'$'}Number${'$'}.m4s"/></Representation></AdaptationSet></Period></MPD>"""))
            server.start()
            val destination = File(context.cacheDir, "test-${System.nanoTime()}.mpd")
            try {
                try { AdaptiveStreamDownloads.download(context, server.url("/drm.mpd").toString(), emptyMap(), "dash", destination) { _, _ -> }; fail("DRM must reject") }
                catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty().contains("DRM")) }
                assertFalse(destination.exists())
                assertEquals(1, server.requestCount)
            } finally { AdaptiveStreamDownloads.remove(destination) }
        }
    }

}
