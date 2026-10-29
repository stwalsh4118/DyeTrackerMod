package com.dyetracker.spotify

/**
 * What the HUD widget shows: the poller's [PollRecord], written whole by [SpotifyPoller] on the
 * Spotify API thread each tick and read by the HUD provider on the render thread.
 */
object SpotifyNowPlayingStore {
    @Volatile
    var record: PollRecord = PollRecord.EMPTY

    /**
     * When the snapshot last became a new track ([trackChanged]), for the border pulse (task
     * 76-7); 0 before the first song, so nothing pulses at startup. Written only by
     * [SpotifyPoller], after [record], so a frame that reads the record first never pairs an old
     * track with a new change time (at worst a new track starts its pulse a frame late).
     */
    @Volatile
    var trackChangedAtMs: Long = 0L
}
