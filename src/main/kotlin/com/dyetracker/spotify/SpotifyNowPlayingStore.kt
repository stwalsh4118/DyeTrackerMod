package com.dyetracker.spotify

/**
 * What the HUD widget shows: the poller's [PollRecord], written whole by [SpotifyPoller] on the
 * Spotify API thread each tick and read by the HUD provider on the render thread.
 */
object SpotifyNowPlayingStore {
    @Volatile
    var record: PollRecord = PollRecord.EMPTY
}
