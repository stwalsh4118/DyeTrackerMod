package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig

/**
 * The theme in use (PBI 81). The themeable [UiTheme.Colors] tokens read [active] on every access,
 * and widget trees and screens are rebuilt every frame, so a change shows on the next frame.
 * [com.dyetracker.config.ConfigManager] calls [apply] each time the config changes. Volatile so a
 * read from any thread sees a whole value.
 */
object ThemeState {

    @Volatile
    var active: ResolvedTheme = ResolvedTheme.DEFAULT

    /** Make the theme [config] picks the active one. */
    fun apply(config: ModConfig) {
        active = resolveTheme(config)
    }
}
