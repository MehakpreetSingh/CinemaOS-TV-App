package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackUiPolicyTest {

    @Test
    fun `vod hls is not live without player flag or channel type`() {
        assertFalse(
            LivePlaybackUiPolicy.isLivePlayback(
                playerReportsLive = false,
                contentType = "movie"
            )
        )
        assertFalse(
            LivePlaybackUiPolicy.isLivePlayback(
                playerReportsLive = false,
                contentType = "series"
            )
        )
        assertFalse(
            LivePlaybackUiPolicy.isLivePlayback(
                playerReportsLive = false,
                contentType = "tv"
            )
        )
    }

    @Test
    fun `channel catalog type is live even before player reports`() {
        assertTrue(
            LivePlaybackUiPolicy.isLivePlayback(
                playerReportsLive = false,
                contentType = "channel"
            )
        )
    }

    @Test
    fun `player live window flag marks live hls regardless of movie type`() {
        assertTrue(
            LivePlaybackUiPolicy.isLivePlayback(
                playerReportsLive = true,
                contentType = "movie"
            )
        )
    }

    @Test
    fun `live latch sticks after the player reported live once`() {
        val latched = LivePlaybackUiPolicy.nextLiveLatch(
            playerReportsLive = true,
            previouslyLatched = false
        )
        assertTrue(latched)
        assertTrue(
            LivePlaybackUiPolicy.isLivePlayback(
                playerReportsLive = false,
                contentType = "movie",
                latchedLive = latched
            )
        )
    }

    @Test
    fun `watch clock accumulates only while playing live`() {
        val clock = LivePlaybackWatchClock()
        assertEquals(
            0L,
            clock.watchedDurationMs(isLive = true, isPlaying = true, nowElapsedMs = 1_000L)
        )
        assertEquals(
            6_000L,
            clock.watchedDurationMs(isLive = true, isPlaying = true, nowElapsedMs = 7_000L)
        )
        assertEquals(
            6_000L,
            clock.watchedDurationMs(isLive = true, isPlaying = false, nowElapsedMs = 7_000L)
        )
        assertEquals(
            6_000L,
            clock.watchedDurationMs(isLive = true, isPlaying = false, nowElapsedMs = 20_000L)
        )
        assertEquals(
            6_000L,
            clock.watchedDurationMs(isLive = true, isPlaying = true, nowElapsedMs = 22_000L)
        )
        assertEquals(
            8_000L,
            clock.watchedDurationMs(isLive = true, isPlaying = true, nowElapsedMs = 24_000L)
        )
    }

    @Test
    fun `watch clock resets when leaving live`() {
        val clock = LivePlaybackWatchClock()
        clock.watchedDurationMs(isLive = true, isPlaying = true, nowElapsedMs = 0L)
        clock.watchedDurationMs(isLive = true, isPlaying = false, nowElapsedMs = 4_000L)
        assertEquals(
            0L,
            clock.watchedDurationMs(isLive = false, isPlaying = true, nowElapsedMs = 10_000L)
        )
    }

    private fun growth(vararg readings: Pair<Long, Boolean>, mediaKey: String = "a"): LivePlaybackUiPolicy.DurationGrowth =
        readings.fold(LivePlaybackUiPolicy.DurationGrowth()) { state, (durationMs, fromRequestedMedia) ->
            LivePlaybackUiPolicy.nextDurationGrowth(state, mediaKey, durationMs, fromRequestedMedia)
        }

    @Test
    fun `fixed vod duration does not count as growing`() {
        assertFalse(growth(1_320_000L to true, 1_320_000L to true, 1_325_000L to true).grew)
    }

    @Test
    fun `growing mpv duration marks live`() {
        assertTrue(growth(8_000L to true, 18_000L to true, 32_000L to true).grew)
    }

    @Test
    fun `readings before the requested media are skipped`() {
        // The previous file's 20 min is reported before MPV switches to a 45 min episode
        assertFalse(growth(1_200_000L to false, 2_700_000L to true, 2_700_000L to true).grew)
    }

    @Test
    fun `a new media key starts over`() {
        val live = growth(8_000L to true, 46_000L to true)
        assertTrue(live.grew)
        val next = LivePlaybackUiPolicy.nextDurationGrowth(live, "b", 2_700_000L, true)
        assertFalse(next.grew)
        assertEquals(2_700_000L, next.firstDurationMs)
    }

    @Test
    fun `known live sports item is live before the player reports`() {
        assertTrue(
            LivePlaybackUiPolicy.isLivePlayback(
                playerReportsLive = false,
                contentType = "tv",
                knownLiveItem = true
            )
        )
    }

    @Test
    fun `exo unbounded unseekable ready stream counts as live`() {
        val unset = androidx.media3.common.C.TIME_UNSET
        val ready = androidx.media3.common.Player.STATE_READY
        val buffering = androidx.media3.common.Player.STATE_BUFFERING
        assertTrue(LivePlaybackUiPolicy.exoReportsLive(true, buffering, 10_000L, true))
        assertTrue(LivePlaybackUiPolicy.exoReportsLive(false, ready, unset, false))
        assertFalse(LivePlaybackUiPolicy.exoReportsLive(false, buffering, unset, false))
        assertFalse(LivePlaybackUiPolicy.exoReportsLive(false, ready, 3_600_000L, true))
        assertFalse(LivePlaybackUiPolicy.exoReportsLive(false, ready, unset, true))
    }

    @Test
    fun `sports replay items are never known live`() {
        val sports = com.nuvio.tv.domain.model.SportsAddon
        sports.rememberCatalogItems(sports.CATALOG_LIVE, listOf("nuvio_sport_test_live_event"))
        sports.rememberCatalogItems(sports.CATALOG_NETWORKS, listOf("nuvio_sport_test_channel"))
        sports.rememberCatalogItems("nuvio_sports_replays_football_recent", listOf("nuvio_sport_test_replay"))
        assertTrue(sports.isLiveSportsItem("nuvio_sport_test_live_event"))
        assertTrue(sports.isLiveSportsItem("nuvio_sport_test_channel"))
        assertFalse(sports.isLiveSportsItem("nuvio_sport_test_replay"))
        assertFalse(sports.isLiveSportsItem("nuvio_sport_test_unknown"))
        assertFalse(sports.isLiveSportsItem("tt1234567"))
    }
}
