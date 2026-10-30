package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig

/**
 * The theme in use (PBI 81). The themeable [UiTheme.Colors] tokens read [active] on every access,
 * and widget trees and screens are rebuilt every frame, so a change shows on the next frame; the
 * HUD widget panels read [hudPanelAlpha] the same way. [com.dyetracker.config.ConfigManager] calls
 * [apply] each time the config changes. Volatile so a read from any thread sees a whole value.
 */
object ThemeState {

    @Volatile
    var active: ResolvedTheme = ResolvedTheme.DEFAULT

    /**
     * The HUD widget panels' background alpha (`0..255`). The Theme screen's opacity slider sets it
     * live while dragging, before it is saved; [apply] sets it from the config.
     */
    @Volatile
    var hudPanelAlpha: Int = HUD_PANEL_ALPHA_DEFAULT

    /** Make the theme [config] picks the active one, with its HUD opacity ([clampHudAlpha]). */
    fun apply(config: ModConfig) {
        active = resolveTheme(config)
        hudPanelAlpha = clampHudAlpha(config.hudPanelAlpha)
    }
}
