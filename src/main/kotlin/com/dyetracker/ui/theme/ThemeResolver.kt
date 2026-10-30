package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig

/**
 * The theme [config] picks (PBI 81): Neutral for [ThemePresets.NEUTRAL]; Default for
 * [ThemePresets.DEFAULT], a blank key or any key it doesn't know.
 */
fun resolveTheme(config: ModConfig): ResolvedTheme = when (config.themeKey) {
    ThemePresets.NEUTRAL -> ResolvedTheme.derive(ThemePresets.NEUTRAL_PALETTE)
    else -> ResolvedTheme.DEFAULT
}
