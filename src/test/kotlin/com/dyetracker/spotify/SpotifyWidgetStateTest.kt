package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the widget's state rules (task 76-4): session status first, then the last
 * poll's failure, an ad, the snapshot, and the idle hide; the 429 poll backoff; the paused dim;
 * and the widget's time and message lines.
 */
class SpotifyWidgetStateTest {

    @Test
    fun `NOT_SET_UP beats a stale snapshot`() {
        assertEquals(SpotifyWidgetState.NotSetUp, widgetState(SessionStatus.NOT_SET_UP, playingRecord(), NOW))
    }

    @Test
    fun `LOGGED_OUT beats a stale snapshot`() {
        assertEquals(SpotifyWidgetState.LoggedOut, widgetState(SessionStatus.LOGGED_OUT, playingRecord(), NOW))
    }

    @Test
    fun `a 401 is logged out`() {
        assertEquals(SpotifyWidgetState.LoggedOut, connected(failureRecord(SpotifyResult.Failure(401, "Not logged in"))))
    }

    @Test
    fun `a 403 is the refused error`() {
        assertEquals(
            SpotifyWidgetState.Error("Spotify refused: add your account under User Management in your Spotify app"),
            connected(failureRecord(SpotifyResult.Failure(403, "Forbidden"))),
        )
    }

    @Test
    fun `a 403 is an error even with a snapshot`() {
        assertEquals(
            SpotifyWidgetState.Error("Spotify refused: add your account under User Management in your Spotify app"),
            connected(failureRecord(SpotifyResult.Failure(403, "Forbidden"), PLAYING)),
        )
    }

    @Test
    fun `a 429 with a snapshot keeps showing the song`() {
        assertEquals(
            SpotifyWidgetState.Playing(PLAYING),
            connected(failureRecord(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 7), PLAYING)),
        )
    }

    @Test
    fun `a 429 without a snapshot is the rate limit error`() {
        assertEquals(
            SpotifyWidgetState.Error("Spotify rate limit, retrying in 7s"),
            connected(failureRecord(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 7))),
        )
    }

    @Test
    fun `a network failure with a paused snapshot keeps showing it paused`() {
        assertEquals(
            SpotifyWidgetState.Paused(PAUSED),
            connected(failureRecord(SpotifyResult.Failure(0, "Connection refused"), PAUSED)),
        )
    }

    @Test
    fun `a network failure without a snapshot is the unreachable error`() {
        assertEquals(
            SpotifyWidgetState.Error("Can't reach Spotify"),
            connected(failureRecord(SpotifyResult.Failure(0, "Connection refused"))),
        )
    }

    @Test
    fun `an ad is Ad`() {
        val ad = CurrentlyPlaying(isPlaying = true, type = SpotifyConstants.AD_TYPE)
        assertEquals(
            SpotifyWidgetState.Ad,
            connected(PollRecord(SpotifyResult.Success(ad), snapshot = null, lastActiveAtMs = NOW)),
        )
    }

    @Test
    fun `an ad beats a kept snapshot`() {
        val ad = CurrentlyPlaying(isPlaying = true, type = SpotifyConstants.AD_TYPE)
        assertEquals(
            SpotifyWidgetState.Ad,
            connected(PollRecord(SpotifyResult.Success(ad), snapshot = PLAYING, lastActiveAtMs = NOW)),
        )
    }

    @Test
    fun `a playing snapshot is Playing`() {
        assertEquals(SpotifyWidgetState.Playing(PLAYING), connected(playingRecord()))
    }

    @Test
    fun `a paused snapshot is Paused`() {
        val rec = PollRecord(SpotifyResult.Success(PAUSED_CP), snapshot = PAUSED, lastActiveAtMs = NOW)
        assertEquals(SpotifyWidgetState.Paused(PAUSED), connected(rec))
    }

    @Test
    fun `nothing playing 10 s after a paused song keeps it paused`() {
        val rec = PollRecord(SpotifyResult.Success(null), snapshot = PAUSED, lastActiveAtMs = NOW - TEN_SECONDS_MS)
        assertEquals(SpotifyWidgetState.Paused(PAUSED), connected(rec))
    }

    @Test
    fun `nothing playing 10 s after a playing song keeps it playing`() {
        val rec = PollRecord(SpotifyResult.Success(null), snapshot = PLAYING, lastActiveAtMs = NOW - TEN_SECONDS_MS)
        assertEquals(SpotifyWidgetState.Playing(PLAYING), connected(rec))
    }

    @Test
    fun `nothing playing 30 s after the last song is Hidden`() {
        val rec = PollRecord(
            SpotifyResult.Success(null),
            snapshot = PLAYING,
            lastActiveAtMs = NOW - SpotifyConstants.IDLE_HIDE_MS,
        )
        assertEquals(SpotifyWidgetState.Hidden, connected(rec))
    }

    @Test
    fun `an item-less response counts as nothing playing`() {
        val rec = PollRecord(
            SpotifyResult.Success(CurrentlyPlaying(isPlaying = false)),
            snapshot = PLAYING,
            lastActiveAtMs = NOW - SpotifyConstants.IDLE_HIDE_MS,
        )
        assertEquals(SpotifyWidgetState.Hidden, connected(rec))
    }

    @Test
    fun `never active is Hidden`() {
        assertEquals(SpotifyWidgetState.Hidden, connected(PollRecord(SpotifyResult.Success(null), null, 0)))
    }

    @Test
    fun `never polled is Hidden`() {
        assertEquals(SpotifyWidgetState.Hidden, connected(PollRecord.EMPTY))
    }

    @Test
    fun `pollDelayMs waits out a 429's longer Retry-After`() {
        assertEquals(12_000L, pollDelayMs(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 12)))
    }

    @Test
    fun `pollDelayMs never polls faster than the interval on a 429`() {
        assertEquals(5_000L, pollDelayMs(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 2)))
    }

    @Test
    fun `pollDelayMs on a 429 with no Retry-After uses the default wait`() {
        assertEquals(5_000L, pollDelayMs(SpotifyResult.Failure(429, "Rate limited")))
    }

    @Test
    fun `pollDelayMs after a success is the interval`() {
        assertEquals(5_000L, pollDelayMs(SpotifyResult.Success(null)))
    }

    @Test
    fun `pollDelayMs after another failure is the interval`() {
        assertEquals(5_000L, pollDelayMs(SpotifyResult.Failure(500, "HTTP 500", retryAfterSeconds = 60)))
    }

    @Test
    fun `pollDelayMs with no poll is the interval`() {
        assertEquals(5_000L, pollDelayMs(null))
    }

    @Test
    fun `dim halves the panel alpha and keeps the RGB`() {
        assertEquals(0x64191414, dim(0xC8191414.toInt(), 0.5f))
    }

    @Test
    fun `dim halves an opaque white`() {
        assertEquals(0x7FFFFFFF, dim(0xFFFFFFFF.toInt(), 0.5f))
    }

    @Test
    fun `dim by 0 is fully transparent`() {
        assertEquals(0x00191414, dim(0xC8191414.toInt(), 0f))
    }

    @Test
    fun `timeText while playing is progress over duration`() {
        assertEquals("1:05 / 3:00", timeText(PLAYING, NOW + 5_000, paused = false))
    }

    @Test
    fun `timeText while paused is prefixed Paused`() {
        assertEquals("Paused · 1:00 / 3:00", timeText(PAUSED, NOW + 5_000, paused = true))
    }

    @Test
    fun `not set up message points at help`() {
        assertEquals(
            listOf("Spotify: not set up", "/dyetracker spotify help"),
            messageLines(SpotifyWidgetState.NotSetUp),
        )
    }

    @Test
    fun `logged out message says how to log in`() {
        assertEquals(
            listOf("Spotify: logged out", "/dyetracker spotify login"),
            messageLines(SpotifyWidgetState.LoggedOut),
        )
    }

    @Test
    fun `error message is the error text`() {
        assertEquals(listOf("Can't reach Spotify"), messageLines(SpotifyWidgetState.Error("Can't reach Spotify")))
    }

    @Test
    fun `non-message states have no message lines`() {
        assertEquals(emptyList(), messageLines(SpotifyWidgetState.Playing(PLAYING)))
        assertEquals(emptyList(), messageLines(SpotifyWidgetState.Hidden))
    }

    private fun connected(rec: PollRecord) = widgetState(SessionStatus.CONNECTED, rec, NOW)

    private fun playingRecord() = PollRecord(SpotifyResult.Success(PLAYING_CP), snapshot = PLAYING, lastActiveAtMs = NOW)

    private fun failureRecord(f: SpotifyResult.Failure, snapshot: NowPlayingSnapshot? = null) =
        PollRecord(f, snapshot, lastActiveAtMs = if (snapshot == null) 0 else NOW)

    private companion object {
        const val NOW = 100_000L
        const val TEN_SECONDS_MS = 10_000L
        val PLAYING_CP = CurrentlyPlaying(isPlaying = true, progressMs = 60_000, item = PlayingItem(name = "Song", durationMs = 180_000))
        val PAUSED_CP = PLAYING_CP.copy(isPlaying = false)
        val PLAYING = NowPlayingSnapshot("Song", "", "", isPlaying = true, progressMs = 60_000, durationMs = 180_000, receivedAtMs = NOW)
        val PAUSED = PLAYING.copy(isPlaying = false)
    }
}
