package com.dyetracker.spotify

import com.dyetracker.ui.core.WidgetPlacement
import kotlinx.serialization.Serializable

/**
 * Persisted placement for the single Spotify now-playing HUD widget (PBI 76). Implements
 * [WidgetPlacement] so the generic HUD host and edit screen can position/scale/hide it.
 *
 * There is exactly one now-playing widget, so [id] defaults to the stable
 * [SpotifyConstants.WIDGET_ID]. Coordinates are fractions of the scaled window (0..1) addressing
 * the widget's center, so placement survives resolution changes.
 */
@Serializable
data class SpotifyWidgetConfig(
    override val id: String = SpotifyConstants.WIDGET_ID,
    override val x: Float = DEFAULT_X,
    override val y: Float = DEFAULT_Y,
    override val scale: Float = DEFAULT_SCALE,
    override val visible: Boolean = true,
) : WidgetPlacement {
    companion object {
        /** Top right, clear of the chat and the hotbar. */
        const val DEFAULT_X = 0.85f
        const val DEFAULT_Y = 0.1f
        const val DEFAULT_SCALE = 1.0f
    }
}
