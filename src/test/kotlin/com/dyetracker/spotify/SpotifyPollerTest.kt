package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit coverage for [SpotifyPoller]'s pure parts (task 76-3): it polls only while connected and
 * the widget is visible, how a poll result changes the record (task 76-4), and which art it
 * shows (none once logged out, task 76-6).
 */
class SpotifyPollerTest {

    @Test
    fun `connected and visible polls`() {
        assertTrue(shouldPoll(connected = true, widgetVisible = true))
    }

    @Test
    fun `connected and hidden does not poll`() {
        assertFalse(shouldPoll(connected = true, widgetVisible = false))
    }

    @Test
    fun `not connected and visible does not poll`() {
        assertFalse(shouldPoll(connected = false, widgetVisible = true))
    }

    @Test
    fun `not connected and hidden does not poll`() {
        assertFalse(shouldPoll(connected = false, widgetVisible = false))
    }

    @Test
    fun `a playing track becomes the snapshot, received now, and marks the record active`() {
        val cp = CurrentlyPlaying(isPlaying = true, progressMs = 7_000, item = PlayingItem(name = "New", durationMs = 9_000))
        assertEquals(
            PollRecord(
                SpotifyResult.Success(cp),
                NowPlayingSnapshot("New", "", "", isPlaying = true, progressMs = 7_000, durationMs = 9_000, receivedAtMs = NOW),
                lastActiveAtMs = NOW,
            ),
            nextRecord(ACTIVE, SpotifyResult.Success(cp), NOW),
        )
    }

    @Test
    fun `a paused track marks the record active`() {
        val cp = CurrentlyPlaying(isPlaying = false, progressMs = 7_000, item = PlayingItem(name = "New", durationMs = 9_000))
        assertEquals(NOW, nextRecord(STALE, SpotifyResult.Success(cp), NOW).lastActiveAtMs)
    }

    @Test
    fun `an ad marks the record active`() {
        val ad = CurrentlyPlaying(isPlaying = true, type = SpotifyConstants.AD_TYPE)
        val next = nextRecord(STALE, SpotifyResult.Success(ad), NOW)
        assertEquals(PollRecord(SpotifyResult.Success(ad), snapshot = null, lastActiveAtMs = NOW), next)
    }

    @Test
    fun `nothing playing within 30 s keeps the snapshot and the last active time`() {
        val next = nextRecord(ACTIVE, SpotifyResult.Success(null), NOW)
        assertEquals(PollRecord(SpotifyResult.Success(null), PREVIOUS, ACTIVE.lastActiveAtMs), next)
    }

    @Test
    fun `nothing playing 30 s after the last song clears the snapshot`() {
        val next = nextRecord(STALE, SpotifyResult.Success(null), NOW)
        assertEquals(PollRecord(SpotifyResult.Success(null), null, STALE.lastActiveAtMs), next)
    }

    @Test
    fun `an item-less response counts as nothing playing`() {
        val next = nextRecord(STALE, SpotifyResult.Success(CurrentlyPlaying(isPlaying = false)), NOW)
        assertNull(next.snapshot)
        assertEquals(STALE.lastActiveAtMs, next.lastActiveAtMs)
    }

    @Test
    fun `a failure keeps the previous snapshot and active time`() {
        val failure = SpotifyResult.Failure(500, "HTTP 500")
        val next = nextRecord(ACTIVE, failure, NOW)
        assertSame(PREVIOUS, next.snapshot)
        assertEquals(PollRecord(failure, PREVIOUS, ACTIVE.lastActiveAtMs), next)
    }

    @Test
    fun `not connected clears the record without a poll`() {
        assertEquals(PollRecord.EMPTY, recordWithoutPoll(ACTIVE, connected = false))
    }

    @Test
    fun `connected but hidden leaves the record alone`() {
        assertSame(ACTIVE, recordWithoutPoll(ACTIVE, connected = true))
    }

    @Test
    fun `connected shows the snapshot's art`() {
        assertEquals(ART_URL, artUrlFor(WITH_ART, connected = true))
    }

    @Test
    fun `not connected shows no art even with a snapshot`() {
        assertNull(artUrlFor(WITH_ART, connected = false))
    }

    @Test
    fun `connected with no snapshot shows no art`() {
        assertNull(artUrlFor(PollRecord.EMPTY, connected = true))
    }

    private companion object {
        const val NOW = 50_000L
        const val ART_URL = "https://i.scdn.co/image/a"
        val PREVIOUS = NowPlayingSnapshot("Old", "Artist", "", isPlaying = true, progressMs = 1, durationMs = 2, receivedAtMs = 3)

        /** Last active 10 s ago: inside the idle window. */
        val ACTIVE = PollRecord(SpotifyResult.Success(null), PREVIOUS, lastActiveAtMs = NOW - 10_000)

        /** A record whose snapshot has art. */
        val WITH_ART = PollRecord(SpotifyResult.Success(null), PREVIOUS.copy(artUrl = ART_URL), lastActiveAtMs = NOW)

        /** Last active exactly the idle limit ago: past the window. */
        val STALE = PollRecord(SpotifyResult.Success(null), PREVIOUS, lastActiveAtMs = NOW - SpotifyConstants.IDLE_HIDE_MS)
    }
}
