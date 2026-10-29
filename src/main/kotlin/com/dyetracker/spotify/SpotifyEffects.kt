package com.dyetracker.spotify

/*
 * The widget's playback effects (PBI 76, task 76-7): the border pulses toward white for
 * [SpotifyConstants.PULSE_MS] when the track changes, and a brighter head sits at the progress
 * fill's leading edge. Only the border and the bar take them; the art and the Spotify icon never
 * do. Pure, so it is unit-tested without a client.
 */

/** Separates the title and subtitle in a [trackKey] for a track with no url. */
private const val KEY_SEPARATOR = "|"

/**
 * How strong the pulse is [msSinceTrackChange] after the track changed: `(1 - p)²` with
 * `p = ms / PULSE_MS` clamped `0..1`, so 1 at the change, easing out to 0 at
 * [SpotifyConstants.PULSE_MS] and after. A negative time is a full pulse.
 */
fun pulseAmount(msSinceTrackChange: Long): Float {
    val p = (msSinceTrackChange.toFloat() / SpotifyConstants.PULSE_MS).coerceIn(0f, 1f)
    val remaining = 1f - p
    return remaining * remaining
}

/** [border] moved toward white by up to [SpotifyConstants.PULSE_MAX_MIX], by [pulseAmount]. */
fun pulsedBorder(border: Int, msSinceTrackChange: Long): Int =
    lerpColor(border, OPAQUE_WHITE, SpotifyConstants.PULSE_MAX_MIX * pulseAmount(msSinceTrackChange))

/** The progress bar's head: [accent] moved [SpotifyConstants.GLOW_HEAD_MIX] of the way to white. */
fun glowHeadColor(accent: Int): Int = lerpColor(accent, OPAQUE_WHITE, SpotifyConstants.GLOW_HEAD_MIX)

/** What identifies [s]'s track: its url, or `title|subtitle` when the url is blank; null for nothing playing. */
fun trackKey(s: NowPlayingSnapshot?): String? = when {
    s == null -> null
    s.url.isBlank() -> "${s.title}$KEY_SEPARATOR${s.subtitle}"
    else -> s.url
}

/**
 * Whether going from [prev] to [next] is a new track, so the border pulses: [next] is playing
 * something and its [trackKey] differs from [prev]'s. The first song after nothing playing (a
 * start or a login) is a change; a song ending into nothing playing is not.
 */
fun trackChanged(prev: NowPlayingSnapshot?, next: NowPlayingSnapshot?): Boolean {
    val nextKey = trackKey(next) ?: return false
    return nextKey != trackKey(prev)
}
