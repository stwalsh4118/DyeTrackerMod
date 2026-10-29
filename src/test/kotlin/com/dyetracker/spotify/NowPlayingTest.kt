package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for the HUD's now-playing logic (task 76-3): the progress interpolated from the
 * local receive time and capped at the duration, the bar's percent, the `m:ss` / `h:mm:ss` times,
 * and the snapshot built from Spotify's response.
 */
class NowPlayingTest {

    private fun snapshot(
        isPlaying: Boolean = true,
        progressMs: Long = 1_000,
        durationMs: Long = 200_000,
        receivedAtMs: Long = 10_000,
    ) = NowPlayingSnapshot(
        title = "Song",
        subtitle = "Artist",
        url = "",
        isPlaying = isPlaying,
        progressMs = progressMs,
        durationMs = durationMs,
        receivedAtMs = receivedAtMs,
    )

    @Test
    fun `playing progress moves on by the time since it was received`() {
        assertEquals(3_500, interpolatedProgressMs(snapshot(progressMs = 1_000, receivedAtMs = 10_000), 12_500))
    }

    @Test
    fun `playing progress is capped at the duration`() {
        val s = snapshot(progressMs = 199_000, durationMs = 200_000, receivedAtMs = 10_000)
        assertEquals(200_000, interpolatedProgressMs(s, 15_000))
    }

    @Test
    fun `paused progress stays where it was`() {
        assertEquals(1_000, interpolatedProgressMs(snapshot(isPlaying = false, progressMs = 1_000), 12_500))
    }

    @Test
    fun `a clock behind the receive time leaves the progress unchanged`() {
        assertEquals(1_000, interpolatedProgressMs(snapshot(progressMs = 1_000, receivedAtMs = 10_000), 9_000))
    }

    @Test
    fun `progress is never below zero`() {
        assertEquals(0, interpolatedProgressMs(snapshot(isPlaying = false, progressMs = -50), 12_500))
    }

    @Test
    fun `progress percent is the share of the duration`() {
        val s = snapshot(isPlaying = false, progressMs = 50_000, durationMs = 200_000)
        assertEquals(25f, progressPercent(s, 12_500))
    }

    @Test
    fun `progress percent is zero with no duration`() {
        assertEquals(0f, progressPercent(snapshot(isPlaying = false, progressMs = 50_000, durationMs = 0), 12_500))
    }

    @Test
    fun `progress percent is clamped to 100`() {
        val s = snapshot(isPlaying = false, progressMs = 300_000, durationMs = 200_000)
        assertEquals(100f, progressPercent(s, 12_500))
    }

    @Test
    fun `format time reads m colon ss under an hour`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("1:01", formatTime(61_000))
        assertEquals("59:59", formatTime(3_599_000))
    }

    @Test
    fun `format time reads h colon mm colon ss from one hour`() {
        assertEquals("1:00:00", formatTime(3_600_000))
    }

    @Test
    fun `format time of a negative time is zero`() {
        assertEquals("0:00", formatTime(-5))
    }

    @Test
    fun `no item is no snapshot`() {
        assertNull(snapshotOf(CurrentlyPlaying(isPlaying = true, progressMs = 5, item = null), 10_000))
    }

    @Test
    fun `a track's fields are copied into the snapshot`() {
        val cp = CurrentlyPlaying(
            isPlaying = true,
            progressMs = 42_000,
            type = "track",
            item = PlayingItem(
                name = "Song",
                durationMs = 180_000,
                artists = listOf(SpotifyArtist("A1"), SpotifyArtist("A2")),
                externalUrls = ExternalUrls(TRACK_URL),
            ),
        )
        assertEquals(
            NowPlayingSnapshot(
                title = "Song",
                subtitle = "A1, A2",
                url = TRACK_URL,
                isPlaying = true,
                progressMs = 42_000,
                durationMs = 180_000,
                receivedAtMs = 10_000,
            ),
            snapshotOf(cp, 10_000),
        )
    }

    @Test
    fun `a missing progress_ms is zero`() {
        val cp = CurrentlyPlaying(isPlaying = false, progressMs = null, item = PlayingItem(name = "Song"))
        assertEquals(0, snapshotOf(cp, 10_000)?.progressMs)
    }

    @Test
    fun `an episode's snapshot is the episode name over its show`() {
        val cp = CurrentlyPlaying(
            isPlaying = true,
            type = "episode",
            item = PlayingItem(name = "Ep 12", type = "episode", show = SpotifyShow(name = "The Pod")),
        )
        val s = snapshotOf(cp, 10_000)
        assertEquals("Ep 12", s?.title)
        assertEquals("The Pod", s?.subtitle)
    }

    private companion object {
        const val TRACK_URL = "https://open.spotify.com/track/1"
    }
}
