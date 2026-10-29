package com.dyetracker.spotify

import com.dyetracker.ui.theme.TintColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for "Tint all widgets" (task 76-8): the other widgets take the album's background
 * and border only while the option is on and a track is playing or paused, undimmed when paused.
 */
class SpotifyTintTest {

    @Test
    fun `enabled and playing tints with the album colors`() {
        assertEquals(TintColors(BACKGROUND, BORDER), tintFor(enabled = true, SpotifyWidgetState.Playing(PLAYING), COLORS))
    }

    @Test
    fun `enabled and paused tints with the album colors, undimmed`() {
        assertEquals(TintColors(BACKGROUND, BORDER), tintFor(enabled = true, SpotifyWidgetState.Paused(PAUSED), COLORS))
    }

    @Test
    fun `enabled and hidden does not tint`() {
        assertNull(tintFor(enabled = true, SpotifyWidgetState.Hidden, COLORS))
    }

    @Test
    fun `enabled and logged out does not tint`() {
        assertNull(tintFor(enabled = true, SpotifyWidgetState.LoggedOut, COLORS))
    }

    @Test
    fun `enabled and not set up does not tint`() {
        assertNull(tintFor(enabled = true, SpotifyWidgetState.NotSetUp, COLORS))
    }

    @Test
    fun `enabled and an ad does not tint`() {
        assertNull(tintFor(enabled = true, SpotifyWidgetState.Ad, COLORS))
    }

    @Test
    fun `enabled and an error does not tint`() {
        assertNull(tintFor(enabled = true, SpotifyWidgetState.Error("Can't reach Spotify"), COLORS))
    }

    @Test
    fun `disabled and playing does not tint`() {
        assertNull(tintFor(enabled = false, SpotifyWidgetState.Playing(PLAYING), COLORS))
    }

    @Test
    fun `the tint feedback says on or off`() {
        assertEquals("Tint all widgets: on.", tintAllText(true))
        assertEquals("Tint all widgets: off.", tintAllText(false))
    }

    private companion object {
        const val BACKGROUND = 0xC8_20_40_60.toInt()
        const val BORDER = 0xFF_A0_B0_C0.toInt()
        const val ACCENT = 0xFF_11_22_33.toInt()
        const val TEXT = 0xFF_FF_FF_FF.toInt()
        val COLORS = SpotifyColors(BACKGROUND, BORDER, ACCENT, TEXT, SpotifyIcon.WHITE)
        val PLAYING = NowPlayingSnapshot("Song", "Artist", "", isPlaying = true, progressMs = 1_000, durationMs = 9_000, receivedAtMs = 0)
        val PAUSED = PLAYING.copy(isPlaying = false)
    }
}
