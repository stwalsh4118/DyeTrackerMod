package com.dyetracker.spotify

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.hud.HudWidgetRegistry
import com.dyetracker.ui.theme.HudTint

/**
 * Sets [HudTint] from the album colors each frame for "Tint all widgets" (task 76-8). Registered
 * as a [HudWidgetRegistry] provider that draws nothing: register it before every other provider,
 * so the widgets built later in the same frame see this frame's tint.
 *
 * The state ignores the Spotify widget's own visibility: the tint follows the music with the
 * widget hidden (the poller keeps polling while the option is on). The colors are the faded ones,
 * so the tint fades with the widget on a track change. Runs on the render thread every frame, so
 * it only reads volatile state.
 */
object SpotifyTintProvider {

    /** Register the provider. Call once at client init, before any other HUD provider. */
    fun register() {
        HudWidgetRegistry.register {
            val nowMs = System.currentTimeMillis()
            val state = widgetState(SpotifySessions.default.status(), SpotifyNowPlayingStore.record, nowMs)
            HudTint.colors = tintFor(ConfigManager.isSpotifyTintAll(), state, SpotifyColorStore.colorsAt(nowMs))
            emptyList()
        }
    }
}
