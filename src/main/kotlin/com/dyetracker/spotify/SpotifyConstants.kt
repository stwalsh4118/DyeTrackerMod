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

    /** Converts a token's `expires_in` seconds to the stored `expiresAtMs`. */
    const val MS_PER_SECOND = 1000L

    /** Refresh the access token this long before it expires. */
    const val REFRESH_MARGIN_MS = 60_000L

    /** A 429's wait when Spotify sends no usable `Retry-After` header. */
    const val DEFAULT_RETRY_AFTER_SECONDS = 5L

    /** [SpotifyResult.Failure.status] for a failure with no HTTP response (network only). */
    const val STATUS_NO_RESPONSE = 0
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_FORBIDDEN = 403
    const val HTTP_TOO_MANY_REQUESTS = 429

    /** How often the HUD poller asks Spotify what's playing. */
    const val POLL_INTERVAL_MS = 5_000L

    /** Stable id of the singleton now-playing widget (its placement and edit-entry key). */
    const val WIDGET_ID = "spotify_now_playing"

    /** Texture ids of the bundled Spotify icons, for `ImageTextureManager` and `SpriteWidget`. */
    const val ICON_GREEN_ID = "spotify/icon_green"
    const val ICON_WHITE_ID = "spotify/icon_white"

    /** Classpath directory (under `resources/`) holding the bundled Spotify icon PNGs. */
    const val ICON_RESOURCE_DIR = "assets/dyetracker/spotify"

    /** Width of the widget's text column (title, subtitle, bar + times) in GUI pixels. */
    const val TEXT_WIDTH_PX = 120

    /** Draws the 32 px icon PNG at 12 GUI px. */
    const val ICON_SCALE = 0.375f

    /** Vertical gap between the widget's lines (title, subtitle, bar), in GUI pixels. */
    const val LINE_GAP_PX = 3

    /** Nothing playing for this long hides the widget until something plays again. */
    const val IDLE_HIDE_MS = 30_000L

    /** A paused widget's panel, border, text and bar alpha are multiplied by this. */
    const val PAUSED_ALPHA_FACTOR = 0.5f

    /** `currently_playing_type` while Spotify plays an ad. */
    const val AD_TYPE = "ad"

    /** The widget's title while an ad plays. */
    const val AD_TITLE = "Advertisement"

    /** Prefix of a paused widget's time text. */
    const val PAUSED_LABEL = "Paused"

    /** The widget's text in edit mode while it would otherwise be hidden, so it can be placed. */
    const val EDIT_PLACEHOLDER = "Spotify"

    const val HTTP_OK = 200

    /** Texture id of the current album art (or episode/show image), for `ImageTextureManager`. */
    const val ART_TEXTURE_ID = "spotify/art"

    /** The art fetched is the smallest image at least this wide (Spotify's 64 px size). */
    const val ART_MIN_WIDTH_PX = 64

    /** An image body past this many bytes is abandoned. */
    const val ART_MAX_BYTES = 2 * 1024 * 1024

    /** Corner radius on a [ART_MIN_WIDTH_PX]-wide image; scaled to the image's real width. */
    const val ART_CORNER_RADIUS_PX = 6

    /** The art's drawn width in GUI pixels. */
    const val ART_DRAW_PX = 32

    /** The only hosts art is fetched from (Spotify's image CDNs), over https. */
    val ART_ALLOWED_HOST_SUFFIXES = listOf(".scdn.co", ".spotifycdn.com")
}
