package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig

/**
 * [config] after setting the override [overrideKey] (a [ThemeOverrides.KEYS] key) to [rgb] (PBI 81).
 * While Custom is active the override joins the slot's others. From any other theme it starts a new
 * slot, replacing the old one: based on that theme (Default when the key is unknown), holding only
 * this override, and made the active theme.
 */
fun startCustomEdit(config: ModConfig, overrideKey: String, rgb: Int): ModConfig {
    val hex = formatHexRgb(rgb)
    if (config.themeKey == ThemePresets.CUSTOM) {
        return config.copy(themeCustomOverrides = config.themeCustomOverrides + (overrideKey to hex))
    }
    return config.copy(
        themeKey = ThemePresets.CUSTOM,
        themeCustomBase = knownThemeKeyOrDefault(config.themeKey),
        themeCustomOverrides = mapOf(overrideKey to hex),
    )
}

/** [config] with the Custom slot's overrides cleared and its base theme ([customBaseKey]) active (PBI 81). */
fun resetCustom(config: ModConfig): ModConfig =
    config.copy(themeCustomOverrides = emptyMap(), themeKey = customBaseKey(config))

/**
 * What the Theme screen's override boxes show for [config] (PBI 81): for each [ThemeOverrides.KEYS]
 * key, [formatHexRgb] of that color in the active theme's palette, which is [effectivePalette] while
 * Custom is active and otherwise the theme's own (Default's for an unknown key).
 */
fun overrideBoxText(config: ModConfig): Map<String, String> {
    val palette = if (config.themeKey == ThemePresets.CUSTOM) {
        effectivePalette(config)
    } else {
        paletteOrDefault(config.themeKey)
    }
    return ThemeOverrides.KEYS.associateWith { formatHexRgb(palette.overrideColor(it)) }
}
