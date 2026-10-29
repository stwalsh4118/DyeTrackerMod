package com.dyetracker.spotify

import kotlin.math.max

/*
 * What the now-playing widget shows in every situation (PBI 76, task 76-4): the state from the
 * session and the last poll, the poll delay (a 429 backs off), the paused dim, and the widget's
 * time and message lines. Pure, so it is unit-tested without a client.
 */

private const val ALPHA_SHIFT = 24
private const val ALPHA_MAX = 0xFF
private const val RGB_MASK = 0x00_FF_FF_FF
private const val TIME_SEPARATOR = " / "
private const val PAUSED_SEPARATOR = " · "

private const val NOT_SET_UP_LINE = "Spotify: not set up"
private const val NOT_SET_UP_HINT = "/dyetracker spotify clientid <id>"
private const val LOGGED_OUT_LINE = "Spotify: logged out"
private const val LOGGED_OUT_HINT = "/dyetracker spotify login"

/** Failures that keep showing a snapshot: a rate limit and a network failure. */
private val BLIP_STATUSES = setOf(SpotifyConstants.HTTP_TOO_MANY_REQUESTS, SpotifyConstants.STATUS_NO_RESPONSE)

/** One widget state per situation; [widgetState] picks it. */
sealed interface SpotifyWidgetState {
    data object NotSetUp : SpotifyWidgetState
    data object LoggedOut : SpotifyWidgetState
    data class Error(val text: String) : SpotifyWidgetState
    data class Playing(val s: NowPlayingSnapshot) : SpotifyWidgetState
    data class Paused(val s: NowPlayingSnapshot) : SpotifyWidgetState
    data object Ad : SpotifyWidgetState
    data object Hidden : SpotifyWidgetState
}

/**
 * What the poller last saw: the [lastResult] of the last poll (null before the first), the
 * [snapshot] the widget shows, and [lastActiveAtMs], the last time a track, an episode or an ad
 * was playing (0 = never). Whether an ad is playing is read from [lastResult]'s
 * `currently_playing_type`.
 */
data class PollRecord(
    val lastResult: SpotifyResult<CurrentlyPlaying?>?,
    val snapshot: NowPlayingSnapshot?,
    val lastActiveAtMs: Long,
) {
    companion object {
        /** Nothing polled yet (and what a logout or a missing Client ID clears to). */
        val EMPTY = PollRecord(lastResult = null, snapshot = null, lastActiveAtMs = 0)
    }
}

/** True for a successful poll of an ad. */
internal fun isAd(cp: CurrentlyPlaying?): Boolean = cp?.type == SpotifyConstants.AD_TYPE

/**
 * The widget's state at [nowMs], in order: a session that isn't set up or is logged out; the last
 * poll's failure (a 401 is logged out; a 429 or a network failure keeps showing a snapshot, so a
 * blip doesn't hide the song); an ad; the snapshot, playing or paused. When nothing is playing the
 * last snapshot stays for [SpotifyConstants.IDLE_HIDE_MS], then the widget hides.
 */
fun widgetState(status: SessionStatus, rec: PollRecord, nowMs: Long): SpotifyWidgetState {
    when (status) {
        SessionStatus.NOT_SET_UP -> return SpotifyWidgetState.NotSetUp
        SessionStatus.LOGGED_OUT -> return SpotifyWidgetState.LoggedOut
        SessionStatus.CONNECTED -> Unit
    }
    val snapshot = rec.snapshot
    val last = rec.lastResult
    if (last is SpotifyResult.Failure) {
        return when {
            last.status == SpotifyConstants.HTTP_UNAUTHORIZED -> SpotifyWidgetState.LoggedOut
            snapshot != null && last.status in BLIP_STATUSES -> snapshotState(snapshot)
            else -> SpotifyWidgetState.Error(failureText(last))
        }
    }
    val playing = (last as? SpotifyResult.Success)?.data
    if (isAd(playing)) return SpotifyWidgetState.Ad
    if (snapshot == null) return SpotifyWidgetState.Hidden
    val nothingPlaying = playing?.item == null
    if (nothingPlaying && nowMs - rec.lastActiveAtMs >= SpotifyConstants.IDLE_HIDE_MS) return SpotifyWidgetState.Hidden
    return snapshotState(snapshot)
}

private fun snapshotState(s: NowPlayingSnapshot): SpotifyWidgetState =
    if (s.isPlaying) SpotifyWidgetState.Playing(s) else SpotifyWidgetState.Paused(s)

/**
 * How long the poller waits after [result]: a 429 waits its `Retry-After` (or the default), never
 * less than [SpotifyConstants.POLL_INTERVAL_MS]; anything else waits the interval.
 */
fun pollDelayMs(result: SpotifyResult<*>?): Long {
    if (result !is SpotifyResult.Failure || result.status != SpotifyConstants.HTTP_TOO_MANY_REQUESTS) {
        return SpotifyConstants.POLL_INTERVAL_MS
    }
    val retryAfterSeconds = result.retryAfterSeconds ?: SpotifyConstants.DEFAULT_RETRY_AFTER_SECONDS
    return max(SpotifyConstants.POLL_INTERVAL_MS, retryAfterSeconds * SpotifyConstants.MS_PER_SECOND)
}

/** [argb] with its alpha multiplied by [factor] (`(alpha * factor).toInt()`); RGB unchanged. */
fun dim(argb: Int, factor: Float): Int {
    val alpha = ((argb ushr ALPHA_SHIFT) * factor).toInt().coerceIn(0, ALPHA_MAX)
    return (alpha shl ALPHA_SHIFT) or (argb and RGB_MASK)
}

/** The time under the bar at [nowMs], `m:ss / m:ss`; prefixed `Paused · ` when [paused]. */
fun timeText(s: NowPlayingSnapshot, nowMs: Long, paused: Boolean): String {
    val time = "${formatTime(interpolatedProgressMs(s, nowMs))}$TIME_SEPARATOR${formatTime(s.durationMs)}"
    return if (paused) "${SpotifyConstants.PAUSED_LABEL}$PAUSED_SEPARATOR$time" else time
}

/** The message panel's lines for a not-set-up, logged-out or error [state]; empty for the rest. */
fun messageLines(state: SpotifyWidgetState): List<String> = when (state) {
    SpotifyWidgetState.NotSetUp -> listOf(NOT_SET_UP_LINE, NOT_SET_UP_HINT)
    SpotifyWidgetState.LoggedOut -> listOf(LOGGED_OUT_LINE, LOGGED_OUT_HINT)
    is SpotifyWidgetState.Error -> listOf(state.text)
    else -> emptyList()
}
