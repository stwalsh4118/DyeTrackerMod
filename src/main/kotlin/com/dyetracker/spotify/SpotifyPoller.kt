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
 * The record after a poll that returned [result] at [nowMs]. A track, an episode or an ad marks
 * the record active now and replaces the snapshot (an ad has no item, so no snapshot). Nothing
 * playing keeps the snapshot until [SpotifyConstants.IDLE_HIDE_MS] after the record was last
 * active, then clears it. A failure keeps the snapshot, so a 429 or a network blip keeps showing
 * the song (see [widgetState]).
 */
internal fun nextRecord(prev: PollRecord, result: SpotifyResult<CurrentlyPlaying?>, nowMs: Long): PollRecord =
    when (result) {
        is SpotifyResult.Failure -> prev.copy(lastResult = result)
        is SpotifyResult.Success -> {
            val cp = result.data
            if (cp != null && (cp.item != null || isAd(cp))) {
                PollRecord(result, snapshotOf(cp, nowMs), lastActiveAtMs = nowMs)
            } else {
                val idle = nowMs - prev.lastActiveAtMs >= SpotifyConstants.IDLE_HIDE_MS
                PollRecord(result, if (idle) null else prev.snapshot, prev.lastActiveAtMs)
            }
        }
    }

/**
 * The record on a tick that doesn't poll: logged out or not set up clears it (no stale song after
 * a logout); connected but hidden leaves it alone.
 */
internal fun recordWithoutPoll(prev: PollRecord, connected: Boolean): PollRecord =
    if (connected) prev else PollRecord.EMPTY

/** The art to show for [rec]: its snapshot's, only while [connected] (no art after a logout). */
internal fun artUrlFor(rec: PollRecord, connected: Boolean): String? =
    if (connected) rec.snapshot?.artUrl else null

/**
 * Keeps [SpotifyNowPlayingStore] current for the HUD widget (PBI 76). Every
 * [SpotifyConstants.POLL_INTERVAL_MS] (longer after a 429, see [pollDelayMs]) it asks Spotify
 * what's playing, but only while the player is connected and the widget is visible. Not connected
 * clears the record with no call; hidden leaves it alone. Runs on [SpotifyThreads.apiDispatcher],
 * the one Spotify API thread. Failures are logged at debug and never reach gameplay.
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
                delay(pollDelayMs(safeTick()))
            }
        }
        DyeTrackerMod.info("Spotify poller started ({}ms interval)", SpotifyConstants.POLL_INTERVAL_MS)
    }

    /** Cancel the poll loop (on client stop) and release the album art. */
    fun stop() {
        job?.cancel()
        job = null
        SpotifyArtFetcher.ensure(null)
    }

    /** [tick], with any exception logged; returns the tick's poll result, or null when none. */
    private fun safeTick(): SpotifyResult<CurrentlyPlaying?>? {
        try {
            return tick()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The class name only: an exception message may carry a response body.
            DyeTrackerMod.warn("Spotify poll failed: {}", e.javaClass.simpleName)
            return null
        }
    }

    /** One poll, written to the store as a whole record; returns its result, or null with no poll. */
    private fun tick(): SpotifyResult<CurrentlyPlaying?>? {
        val session = SpotifySessions.default
        val connected = session.status() == SessionStatus.CONNECTED
        val visible = ConfigManager.spotifyPlacements.all().firstOrNull()?.visible ?: SpotifyWidgetConfig().visible
        if (!shouldPoll(connected, visible)) {
            publish(recordWithoutPoll(SpotifyNowPlayingStore.record, connected), System.currentTimeMillis())
            return null
        }
        val result = session.currentlyPlaying()
        if (result is SpotifyResult.Failure) {
            DyeTrackerMod.debug("Spotify poll: {}", failureText(result))
        }
        val nowMs = System.currentTimeMillis()
        publish(nextRecord(SpotifyNowPlayingStore.record, result, nowMs), nowMs)
        return result
    }

    /**
     * Write [rec] to the store, stamp [nowMs] as the track change when it is a new track (the
     * border pulse, after the record: see [SpotifyNowPlayingStore.trackChangedAtMs]), then load
     * its snapshot's art. No snapshot, or a session no longer connected (the poll may have logged
     * it out), releases it.
     */
    private fun publish(rec: PollRecord, nowMs: Long) {
        val changed = trackChanged(SpotifyNowPlayingStore.record.snapshot, rec.snapshot)
        SpotifyNowPlayingStore.record = rec
        if (changed) SpotifyNowPlayingStore.trackChangedAtMs = nowMs
        val connected = SpotifySessions.default.status() == SessionStatus.CONNECTED
        SpotifyArtFetcher.ensure(artUrlFor(rec, connected))
    }
}

