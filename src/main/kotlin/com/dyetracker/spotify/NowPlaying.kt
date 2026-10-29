package com.dyetracker.spotify

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
 * What the now-playing HUD widget shows (PBI 76): the snapshot of the last poll, the progress
 * moved on between polls, and the times under the bar. Pure, so it is unit-tested without a client.
 */

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val PERCENT_MAX = 100f
private const val HOURS_FORMAT = "%d:%02d:%02d"
private const val MINUTES_FORMAT = "%d:%02d"

/**
 * The last poll's track: the [title], [subtitle] and [url] of [nowPlayingLine], whether it
 * [isPlaying], where it was ([progressMs]) of how long ([durationMs]), and when the mod got it
 * ([receivedAtMs], the local clock).
 */
data class NowPlayingSnapshot(
    val title: String,
    val subtitle: String,
    val url: String,
    val isPlaying: Boolean,
    val progressMs: Long,
    val durationMs: Long,
    val receivedAtMs: Long,
)

/** The snapshot of [cp] received at [receivedAtMs], or null when nothing is playing (no item). */
fun snapshotOf(cp: CurrentlyPlaying, receivedAtMs: Long): NowPlayingSnapshot? {
    val line = nowPlayingLine(cp) ?: return null
    return NowPlayingSnapshot(
        title = line.title,
        subtitle = line.subtitle,
        url = line.url,
        isPlaying = cp.isPlaying,
        progressMs = cp.progressMs ?: 0,
        durationMs = cp.item?.durationMs ?: 0,
        receivedAtMs = receivedAtMs,
    )
}

/**
 * Where the track is at [nowMs]: while playing, the polled progress plus the time since the poll
 * arrived, capped at the duration; while paused, the polled progress. Never below 0. Measured from
 * the local receive time, not Spotify's `timestamp`, so a skewed clock can't move the bar.
 */
fun interpolatedProgressMs(s: NowPlayingSnapshot, nowMs: Long): Long {
    val progress = if (s.isPlaying) {
        min(s.durationMs, s.progressMs + max(0, nowMs - s.receivedAtMs))
    } else {
        s.progressMs
    }
    return max(0, progress)
}

/** The bar's fill at [nowMs], `0..100`; 0 when the duration is unknown. */
fun progressPercent(s: NowPlayingSnapshot, nowMs: Long): Float {
    if (s.durationMs <= 0) return 0f
    return (interpolatedProgressMs(s, nowMs).toFloat() / s.durationMs * PERCENT_MAX).coerceIn(0f, PERCENT_MAX)
}

/** [ms] as `m:ss` under an hour and `h:mm:ss` from one hour; a negative time is `0:00`. */
fun formatTime(ms: Long): String {
    val totalSeconds = max(0, ms) / SpotifyConstants.MS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, HOURS_FORMAT, hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, MINUTES_FORMAT, minutes, seconds)
    }
}
