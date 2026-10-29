package com.dyetracker.spotify

import com.dyetracker.ui.theme.TintColors

/**
 * The tint "Tint all widgets" gives the other HUD widgets (task 76-8): the album's background and
 * border while [enabled] and a track is playing or paused, else null (the neutral panel). The
 * paused dim is the Spotify widget's own; the other widgets are never dimmed.
 */
fun tintFor(enabled: Boolean, state: SpotifyWidgetState, colors: SpotifyColors): TintColors? {
    if (!enabled) return null
    return when (state) {
        is SpotifyWidgetState.Playing, is SpotifyWidgetState.Paused -> TintColors(colors.background, colors.border)
        else -> null
    }
}
