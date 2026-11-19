package com.nuvio.tv.downloads

import org.junit.Assert.*
import org.junit.Test

class DirectDownloadPolicyTest {
    @Test fun acceptsDirectFilesAndOpaqueUrlsWithFileMetadata() {
        assertEquals("mp4", DirectDownloadPolicy.extension("https://cdn.test/film.MP4?token=abc", null))
        assertEquals("mkv", DirectDownloadPolicy.extension("https://cdn.test/download?id=1", "Film.mkv"))
        assertEquals("webm", DirectDownloadPolicy.extension("http://cdn.test/film.webm", null))
    }
    @Test fun rejectsPlaylistsTorrentsAndLocalOrUnknownSources() {
        for (url in listOf("https://cdn.test/master.m3u8", "https://cdn.test/stream.mpd", "https://cdn.test/file.torrent", "file:///video.mp4", "magnet:?xt=test", "https://cdn.test/page")) {
            assertNull(url, DirectDownloadPolicy.extension(url, null))
        }
        assertNull(DirectDownloadPolicy.extension("https://cdn.test/master.m3u8", "film.mp4"))
        assertNull(DirectDownloadPolicy.extension("https://cdn.test/stream", "film.mp4", "hls"))
        assertNull(DirectDownloadPolicy.extension("https://cdn.test/stream", "film.mp4", "dash"))
    }
    @Test fun aSuccessfulHttpResponseMustStillContainVideo() {
        assertTrue(DirectDownloadPolicy.isVideoHeader(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())))
        assertTrue(DirectDownloadPolicy.isVideoHeader(byteArrayOf(0,0,0,24)+"ftypisom".toByteArray()))
        assertFalse(DirectDownloadPolicy.isVideoHeader("#EXTM3U\n".toByteArray()))
        assertFalse(DirectDownloadPolicy.isVideoHeader("<html>Login</html>".toByteArray()))
        assertFalse(DirectDownloadPolicy.isVideoHeader(byteArrayOf()))
    }
}
