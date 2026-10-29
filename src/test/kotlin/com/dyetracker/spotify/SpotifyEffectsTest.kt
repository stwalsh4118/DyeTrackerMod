package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit coverage for the widget's playback effects (task 76-7): the border pulse's strength over
 * time and its color, the progress bar's glow head color, and which snapshot change counts as a
 * new track (the pulse's trigger).
 */
class SpotifyEffectsTest {

    private fun snapshot(title: String = "Song", subtitle: String = "Artist", url: String = URL_A) =
        NowPlayingSnapshot(
            title = title,
            subtitle = subtitle,
            url = url,
            isPlaying = true,
            progressMs = 0,
            durationMs = 1_000,
            receivedAtMs = 0,
        )

    // pulse strength

    @Test
    fun `the pulse is full at the track change`() {
        assertEquals(1f, pulseAmount(0))
    }

    @Test
    fun `the pulse is a quarter halfway through`() {
        assertEquals(0.25f, pulseAmount(300))
    }

    @Test
    fun `the pulse is gone at its end`() {
        assertEquals(0f, pulseAmount(600))
    }

    @Test
    fun `the pulse stays gone long after the change`() {
        assertEquals(0f, pulseAmount(5000))
    }

    @Test
    fun `a change in the future is a full pulse`() {
        assertEquals(1f, pulseAmount(-10))
    }

    @Test
    fun `no track change since startup is no pulse`() {
        // Before any song, trackChangedAtMs is 0, so the elapsed time is the whole clock.
        assertEquals(0f, pulseAmount(System.currentTimeMillis()))
    }

    // pulse color

    @Test
    fun `a finished pulse leaves the border as it is`() {
        val border = 0xFF_1E_D7_60.toInt()
        assertEquals(border, pulsedBorder(border, 600))
    }

    @Test
    fun `a full pulse moves the border its maximum mix toward white`() {
        val black = 0xFF_00_00_00.toInt()
        assertEquals(lerpColor(black, WHITE, 0.6f), pulsedBorder(black, 0))
    }

    @Test
    fun `a pulse halfway through moves the border a quarter of its maximum mix`() {
        val black = 0xFF_00_00_00.toInt()
        assertEquals(lerpColor(black, WHITE, 0.6f * 0.25f), pulsedBorder(black, 300))
    }

    // glow head

    @Test
    fun `the glow head is the accent halfway to white`() {
        val accent = 0xFF_00_00_00.toInt()
        assertEquals(lerpColor(accent, WHITE, 0.5f), glowHeadColor(accent))
    }

    @Test
    fun `the glow head on a white accent stays white`() {
        assertEquals(WHITE, glowHeadColor(WHITE))
    }

    // track key

    @Test
    fun `a track is keyed by its url`() {
        assertEquals(URL_A, trackKey(snapshot(url = URL_A)))
    }

    @Test
    fun `a track with no url is keyed by its title and subtitle`() {
        assertEquals("Song|Artist", trackKey(snapshot(url = "")))
    }

    @Test
    fun `a blank url falls back to title and subtitle`() {
        assertEquals("Song|Artist", trackKey(snapshot(url = "   ")))
    }

    @Test
    fun `nothing playing has no key`() {
        assertNull(trackKey(null))
    }

    // track change

    @Test
    fun `a different url is a track change`() {
        assertTrue(trackChanged(snapshot(url = URL_A), snapshot(url = URL_B)))
    }

    @Test
    fun `the same url is not a track change, even as the progress moves`() {
        assertFalse(trackChanged(snapshot(url = URL_A), snapshot(url = URL_A).copy(progressMs = 500, isPlaying = false)))
    }

    @Test
    fun `the first song after nothing playing is a track change`() {
        assertTrue(trackChanged(null, snapshot()))
    }

    @Test
    fun `a song ending into nothing playing is not a track change`() {
        assertFalse(trackChanged(snapshot(), null))
    }

    @Test
    fun `nothing playing twice is not a track change`() {
        assertFalse(trackChanged(null, null))
    }

    @Test
    fun `without urls a new title is a track change`() {
        assertTrue(trackChanged(snapshot(title = "One", url = ""), snapshot(title = "Two", url = "")))
    }

    @Test
    fun `without urls the same title and subtitle is not a track change`() {
        assertFalse(trackChanged(snapshot(url = ""), snapshot(url = "")))
    }

    private companion object {
        const val WHITE = 0xFF_FF_FF_FF.toInt()
        const val URL_A = "https://open.spotify.com/track/a"
        const val URL_B = "https://open.spotify.com/track/b"
    }
}
