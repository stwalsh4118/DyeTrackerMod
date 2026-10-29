package com.dyetracker.spotify

/**
 * The track the HUD widget shows: written by [SpotifyPoller] on the Spotify API thread, read by
 * the HUD provider on the render thread. Null when nothing is playing or the poller is idle.
 */
object SpotifyNowPlayingStore {
    @Volatile
    var snapshot: NowPlayingSnapshot? = null
}
