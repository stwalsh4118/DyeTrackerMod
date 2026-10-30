package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig

/**
 * The theme [config] picks (PBI 81): Neutral for [ThemePresets.NEUTRAL]; the dye's [dyePalette] for
 * a dye key whose dye has a color; Default for [ThemePresets.DEFAULT], a blank key, a dye key with
 * an unknown dye or any key it doesn't know. Default is always [ResolvedTheme.DEFAULT], never derived.
 */
fun resolveTheme(config: ModConfig): ResolvedTheme {
    val dyeId = ThemePresets.dyeIdOf(config.themeKey)
    return when {
        dyeId != null -> ResolvedTheme.derive(dyePalette(DyeThemeColors.COLORS.getValue(dyeId)))
        config.themeKey == ThemePresets.NEUTRAL -> ResolvedTheme.derive(ThemePresets.NEUTRAL_PALETTE)
        else -> ResolvedTheme.DEFAULT
    }
}
