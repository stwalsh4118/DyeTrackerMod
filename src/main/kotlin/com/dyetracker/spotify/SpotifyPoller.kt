package com.dyetracker.spotify

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Whether the poller asks Spotify this tick: only while connected and the widget is shown. */
fun shouldPoll(connected: Boolean, widgetVisible: Boolean): Boolean = connected && widgetVisible

/**
 * The snapshot after a poll that returned [result] at [nowMs]: nothing playing clears it, a track
 * replaces it, and a failure keeps [current] (the widget doesn't flicker on one bad poll).
 */
internal fun nextSnapshot(
    current: NowPlayingSnapshot?,
    result: SpotifyResult<CurrentlyPlaying?>,
    nowMs: Long,
): NowPlayingSnapshot? = when (result) {
    is SpotifyResult.Success -> result.data?.let { snapshotOf(it, nowMs) }
    is SpotifyResult.Failure -> current
}

/**
 * Keeps [SpotifyNowPlayingStore] current for the HUD widget (PBI 76). Every
 * [SpotifyConstants.POLL_INTERVAL_MS] it asks Spotify what's playing, but only while the player
 * is connected and the widget is visible; otherwise it clears the snapshot and makes no call.
 * Runs on [SpotifyThreads.apiDispatcher], the one Spotify API thread. Failures are logged at
 * debug and never reach gameplay.
 */
object SpotifyPoller {

    private val scope = CoroutineScope(SupervisorJob() + SpotifyThreads.apiDispatcher)

    @Volatile
    private var job: Job? = null

    /** Start the poll loop. Idempotent: a second call while running is a no-op. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                safeTick()
                delay(SpotifyConstants.POLL_INTERVAL_MS)
            }
        }
        DyeTrackerMod.info("Spotify poller started ({}ms interval)", SpotifyConstants.POLL_INTERVAL_MS)
    }

    /** Cancel the poll loop (on client stop). */
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun safeTick() {
        try {
            tick()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The class name only: an exception message may carry a response body.
            DyeTrackerMod.warn("Spotify poll failed: {}", e.javaClass.simpleName)
        }
    }

    private fun tick() {
        val session = SpotifySessions.default
        val visible = ConfigManager.spotifyPlacements.all().firstOrNull()?.visible ?: SpotifyWidgetConfig().visible
        if (!shouldPoll(session.status() == SessionStatus.CONNECTED, visible)) {
            SpotifyNowPlayingStore.snapshot = null
            return
        }
        val result = session.currentlyPlaying()
        if (result is SpotifyResult.Failure) {
            DyeTrackerMod.debug("Spotify poll: {}", failureText(result))
        }
        SpotifyNowPlayingStore.snapshot =
            nextSnapshot(SpotifyNowPlayingStore.snapshot, result, System.currentTimeMillis())
    }
}
