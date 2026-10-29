package com.dyetracker.spotify

/*
 * The now-playing line for `/dyetracker spotify now` (PBI 76). Pure, so it is unit-tested without
 * a client.
 */

private const val ARTIST_SEPARATOR = ", "

/** What's playing: the [title], who made it ([subtitle]) and the Spotify [url] to link back to. */
data class NowPlayingLine(val title: String, val subtitle: String, val url: String)

/**
 * The line for [cp], or null when nothing is playing (no response, or no item). The subtitle is
 * a track's artists, or an episode's show, or empty.
 */
fun nowPlayingLine(cp: CurrentlyPlaying?): NowPlayingLine? {
    val item = cp?.item ?: return null
    val subtitle = when {
        item.artists.isNotEmpty() -> item.artists.joinToString(ARTIST_SEPARATOR) { it.name }
        else -> item.show?.name.orEmpty()
    }
    return NowPlayingLine(item.name, subtitle, item.externalUrls.spotify)
}
