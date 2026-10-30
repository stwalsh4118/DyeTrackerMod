package com.dyetracker.ui.theme

/**
 * The six base colors a theme is built from (PBI 81). [ResolvedTheme.derive] turns them into every
 * themeable token. All are `0xRRGGBB` RGB except [panelBorder], which is full ARGB so a theme can
 * give the border its own alpha.
 */
data class ThemePalette(
    val accentPrimary: Int,
    val accentSecondary: Int,
    val panelBackground: Int,
    val panelBorder: Int,
    val textPrimary: Int,
    val textSecondary: Int,
)
