package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit coverage for [SpotifyPoller]'s pure parts (task 76-3): it polls only while connected and
 * the widget is visible, and how a poll result changes the snapshot.
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
    fun `nothing playing clears the snapshot`() {
        assertNull(nextSnapshot(PREVIOUS, SpotifyResult.Success(null), NOW))
    }

    @Test
    fun `a playing track becomes the snapshot, received now`() {
        val cp = CurrentlyPlaying(isPlaying = true, progressMs = 7_000, item = PlayingItem(name = "New", durationMs = 9_000))
        assertEquals(
            NowPlayingSnapshot("New", "", "", isPlaying = true, progressMs = 7_000, durationMs = 9_000, receivedAtMs = NOW),
            nextSnapshot(PREVIOUS, SpotifyResult.Success(cp), NOW),
        )
    }

    @Test
    fun `a failure keeps the previous snapshot`() {
        assertSame(PREVIOUS, nextSnapshot(PREVIOUS, SpotifyResult.Failure(500, "HTTP 500"), NOW))
    }

    private companion object {
        const val NOW = 50_000L
        val PREVIOUS = NowPlayingSnapshot("Old", "Artist", "", isPlaying = true, progressMs = 1, durationMs = 2, receivedAtMs = 3)
    }
}
