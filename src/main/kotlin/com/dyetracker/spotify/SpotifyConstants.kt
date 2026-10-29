package com.dyetracker.spotify

/**
 * Fixed values for the Spotify now-playing feature (PBI 76). The player brings their own Spotify
 * app (Client ID); nothing here is a secret.
 */
object SpotifyConstants {
    const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
    const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    const val API_BASE_URL = "https://api.spotify.com/v1"

    /** The only scope the feature asks for. */
    const val SCOPE = "user-read-currently-playing"

    /** Loopback IP literal for the login listener. Spotify rejects `localhost` redirect URIs. */
    const val LOOPBACK_HOST = "127.0.0.1"

    /** Fixed login listener port: Spotify's dashboard refuses a loopback redirect URI without one. */
    const val LOOPBACK_PORT = 43821
    const val CALLBACK_PATH = "/callback"

    /** The redirect URI the player registers on their Spotify app, exactly. */
    const val REGISTERED_REDIRECT_URI = "http://127.0.0.1:43821/callback"

    /** How long the login listener waits for the browser to come back. */
    const val LOGIN_TIMEOUT_MS = 300_000L
    const val HTTP_TIMEOUT_SECONDS = 10L

    const val VERIFIER_LENGTH = 64
    const val STATE_LENGTH = 32

    /** RFC 7636 unreserved characters, for the PKCE verifier and the state. */
    const val VERIFIER_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** A Spotify Client ID: 32 hex characters. */
    val CLIENT_ID_REGEX = Regex("^[0-9a-fA-F]{32}$")

    /** Token file, next to `config.json` in `config/dyetracker/`. */
    const val AUTH_FILE_NAME = "spotify.json"

    /** Label of the link back to Spotify (required by the Spotify Developer Policy). */
    const val LISTEN_ON_SPOTIFY = "LISTEN ON SPOTIFY"
}
