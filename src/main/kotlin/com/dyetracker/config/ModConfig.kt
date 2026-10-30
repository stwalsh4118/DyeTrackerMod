package com.dyetracker.config

import com.dyetracker.data.DyeRotation
import com.dyetracker.dyeprogress.DyeProgressWidgetConfig
import com.dyetracker.overlay.GifOverlayConfig
import com.dyetracker.rotation.RotationWidgetConfig
import com.dyetracker.spotify.SpotifyWidgetConfig
import com.dyetracker.ui.theme.ThemePresets
import kotlinx.serialization.Serializable

/**
 * Configuration data class for the DyeTracker mod.
 *
 * This class defines all configurable options for the mod.
 * The configuration is stored as JSON in the config directory.
 */
@Serializable
data class ModConfig(
    /**
     * The URL of the DyeTracker API server.
     */
    val apiUrl: String = DEFAULT_API_URL,

    /**
     * Authentication token for API requests.
     * Leave empty if not authenticated.
     */
    val authToken: String = DEFAULT_AUTH_TOKEN,

    /**
     * The verified Minecraft UUID (without dashes).
     * Set after successful account linking.
     */
    val linkedUuid: String = "",

    /**
     * The verified Minecraft username.
     * Set after successful account linking.
     */
    val linkedUsername: String = "",

    /**
     * Persisted GIF/image HUD overlays added by the player. See PBI-28.
     */
    val gifs: List<GifOverlayConfig> = emptyList(),

    /**
     * Last-known Vincent dye rotation, captured from the in-game "Dyes" screen. Persisted so the
     * rotation HUD widget can render immediately on startup before the player reopens Vincent.
     * Null until the first capture. See PBI-31.
     */
    val dyeRotation: DyeRotation? = null,

    /**
     * Placement (position/scale/visibility) of the singleton dye-rotation HUD widget. Null until
     * first seeded; the rotation feature seeds a default on startup. See PBI-31.
     */
    val rotationWidget: RotationWidgetConfig? = null,

    /**
     * Persisted single-dye progress HUD widgets added by the player (one per chosen dye +
     * profile). Multi-instance, like [gifs]. Defaults to empty so existing config files without
     * this field load unchanged. See PBI-34.
     */
    val dyeProgressWidgets: List<DyeProgressWidgetConfig> = emptyList(),

    /**
     * Whether DVD-bounce mode is on. The only bounce state ever persisted — per-widget drift
     * positions are ephemeral and never written to disk, so toggling off or restarting restores
     * every widget to its saved placement. Defaults to false so existing config files load
     * unchanged. See PBI-38.
     */
    val bounceEnabled: Boolean = false,

    /**
     * Set once the one-time menu hint has been printed in chat on joining a world or server, so
     * it never prints again. Defaults to false so existing config files load unchanged and their
     * players get the hint once. See PBI-67.
     */
    val menuHintShown: Boolean = false,

    /**
     * Discord Rich Presence on/off (PBI 77). Defaults to false so existing config files load unchanged.
     */
    val discordEnabled: Boolean = false,

    /**
     * Dye shown as the hunt on the Discord card (PBI 77); blank = none. Defaults to blank so existing config files load unchanged.
     */
    val discordHuntDyeId: String = "",

    /**
     * Whether the one-time Discord setup hint was shown (PBI 77). Defaults to false so existing config files load unchanged.
     */
    val discordHintShown: Boolean = false,

    /**
     * Placement (position/scale/visibility) of the singleton Spotify now-playing HUD widget. Null
     * until first seeded; the Spotify feature seeds a default on startup, and existing config
     * files without this field load unchanged. See PBI-76.
     */
    val spotifyWidget: SpotifyWidgetConfig? = null,

    /**
     * Whether "Tint all widgets" is on: the rotation and dye-progress widgets take the album
     * colors of the playing Spotify track. Defaults to false so existing config files load
     * unchanged. See PBI-76.
     */
    val spotifyTintAll: Boolean = false,

    /**
     * The active theme key (PBI 81): a preset id from `ThemePresets.PRESET_KEYS`, `dye:<id>` for a
     * dye theme, or `custom` for the Custom slot. Unknown values resolve to Default. Defaults to
     * Default so existing config files load unchanged.
     */
    val themeKey: String = ThemePresets.DEFAULT,

    /**
     * The base theme of the saved Custom slot (PBI 81): any non-custom theme key. Unknown or
     * `custom` resolves to Default.
     */
    val themeCustomBase: String = ThemePresets.DEFAULT,

    /**
     * The Custom slot's color overrides (PBI 81): `ThemeOverrides.KEYS` → `RRGGBB`. Invalid entries
     * are ignored.
     */
    val themeCustomOverrides: Map<String, String> = emptyMap(),
) {
    companion object {
        const val DEFAULT_API_URL = "https://dye-tracker-api.seanwalsh4118-7a3.workers.dev"
        const val DEFAULT_AUTH_TOKEN = ""
    }

    /**
     * Check if an account is currently linked.
     */
    fun isLinked(): Boolean = linkedUuid.isNotEmpty() && authToken.isNotEmpty()
}
