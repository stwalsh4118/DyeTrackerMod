package com.dyetracker.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Wire and file models for the Spotify feature (PBI 76). Defaults everywhere so a partial or
 * newer Spotify response still decodes.
 */

/** The token endpoint's response (authorization-code exchange). */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String = "",
)

/** `GET /me/player/currently-playing`. */
@Serializable
data class CurrentlyPlaying(
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("progress_ms") val progressMs: Long? = null,
    @SerialName("currently_playing_type") val type: String = "unknown",
    val item: PlayingItem? = null,
)

/** A track or a podcast episode; a track has [artists] + [album], an episode has [show] + [images]. */
@Serializable
data class PlayingItem(
    val name: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0,
    val type: String = "track",
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null,
    val show: SpotifyShow? = null,
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("external_urls") val externalUrls: ExternalUrls = ExternalUrls(),
)

@Serializable
data class SpotifyArtist(val name: String = "")

@Serializable
data class SpotifyAlbum(val name: String = "", val images: List<SpotifyImage> = emptyList())

@Serializable
data class SpotifyShow(val name: String = "", val images: List<SpotifyImage> = emptyList())

@Serializable
data class SpotifyImage(val url: String, val width: Int? = null, val height: Int? = null)

@Serializable
data class ExternalUrls(val spotify: String = "")

/** JSON for Spotify responses and the token file. */
val SpotifyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** The token file, `config/dyetracker/spotify.json`. Empty strings mean "not set". */
@Serializable
data class SpotifyAuthFile(
    val clientId: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtMs: Long = 0,
)
