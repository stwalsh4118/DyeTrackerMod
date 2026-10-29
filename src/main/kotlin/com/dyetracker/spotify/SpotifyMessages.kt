package com.dyetracker.spotify

/*
 * Player-facing Spotify text (PBI 76): failure lines and the status line. Pure, so it is
 * unit-tested without a client.
 */

/** The chat line for a failed Spotify call. */
fun failureText(f: SpotifyResult.Failure): String = when (f.status) {
    SpotifyConstants.HTTP_UNAUTHORIZED -> "Not logged in: /dyetracker spotify login"
    SpotifyConstants.HTTP_FORBIDDEN -> "Spotify refused: add your account under User Management in your Spotify app"
    SpotifyConstants.HTTP_TOO_MANY_REQUESTS ->
        "Spotify rate limit, retrying in ${f.retryAfterSeconds ?: SpotifyConstants.DEFAULT_RETRY_AFTER_SECONDS}s"
    SpotifyConstants.STATUS_NO_RESPONSE -> "Can't reach Spotify"
    else -> "Spotify error ${f.status}: ${f.message}"
}

/** The one-line Spotify status, with the command that moves the player forward. */
fun statusText(status: SessionStatus): String = when (status) {
    SessionStatus.NOT_SET_UP -> "Spotify: not set up (/dyetracker spotify clientid <id>)"
    SessionStatus.LOGGED_OUT -> "Spotify: logged out (/dyetracker spotify login)"
    SessionStatus.CONNECTED -> "Spotify: connected"
}
