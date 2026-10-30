package com.dyetracker.ui.theme

/**
 * Every themeable [UiTheme.Colors] token as one ARGB value (PBI 81). [ThemeState.active] holds the
 * one in use, and each themeable token reads its field. [DEFAULT] is today's look, pinned value by
 * value; other themes come from [derive].
 */
data class ResolvedTheme(
    val panelBackground: Int,
    val panelBorder: Int,
    val focusBorder: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val statusOk: Int,
    val progressFill: Int,
    val menuBackground: Int,
    val menuSurface: Int,
    val cardBackground: Int,
    val cardBorder: Int,
    val accentPrimary: Int,
    val accentSecondary: Int,
    val textSubtle: Int,
    val textMuted: Int,
    val badgeModText: Int,
    val badgeModBg: Int,
    val badgeModBorder: Int,
    val badgeMutedText: Int,
    val pillSelectedBg: Int,
    val toggleOnBg: Int,
    val toggleOnBorder: Int,
) {
    companion object {
        /** The Default theme: the token values from before themes, so Default looks exactly as it did. */
        val DEFAULT = ResolvedTheme(
            panelBackground = 0xC8_10_10_10.toInt(),
            panelBorder = 0xFF_55_55_55.toInt(),
            focusBorder = 0xFF_FF_FF_FF.toInt(),
            textPrimary = 0xFF_FF_FF_FF.toInt(),
            textSecondary = 0xFF_BF_BF_BF.toInt(),
            statusOk = 0xFF_BF_BF_BF.toInt(),
            progressFill = 0xFF_E2_25_F4.toInt(),
            menuBackground = 0xF0_00_00_00.toInt(),
            menuSurface = 0xFF_0A_0A_0A.toInt(),
            cardBackground = 0xFF_11_11_11.toInt(),
            cardBorder = 0x1A_FF_FF_FF,
            accentPrimary = 0xFF_E2_25_F4.toInt(),
            accentSecondary = 0xFF_00_F0_FF.toInt(),
            textSubtle = 0xFF_94_A3_B8.toInt(),
            textMuted = 0xFF_64_74_8B.toInt(),
            badgeModText = 0xFF_E2_25_F4.toInt(),
            badgeModBg = 0x1A_E2_25_F4,
            badgeModBorder = 0x4D_E2_25_F4,
            badgeMutedText = 0xFF_64_74_8B.toInt(),
            pillSelectedBg = 0xFF_FF_FF_FF.toInt(),
            toggleOnBg = 0x1A_00_F0_FF,
            toggleOnBorder = 0x33_00_F0_FF,
        )
    }
}

/** Opaque alpha, for solid text, accents and fills. */
private const val ALPHA_OPAQUE = 0xFF

/** The HUD panel fill's alpha (the same as Default's panel background). */
private const val ALPHA_PANEL = 0xC8

/** The menu backdrop's alpha: nearly solid, slightly see-through. */
private const val ALPHA_MENU_BACKGROUND = 0xF0

/** Faint washes: the `MOD` badge fill and the on-toggle fill (10%). */
private const val ALPHA_WASH = 0x1A

/** Toggle-on border (20%). */
private const val ALPHA_TOGGLE_BORDER = 0x33

/** The `MOD` badge border and the card border (30%). */
private const val ALPHA_SOFT_BORDER = 0x4D

/** How far muted text moves from secondary text toward the panel background. */
private const val MUTED_TEXT_MIX = 0.4f

/** How far the menu backdrop darkens the panel background toward black. */
private const val MENU_BACKGROUND_DARKEN = 0.5f

/** How far the menu surface darkens the panel background toward black. */
private const val MENU_SURFACE_DARKEN = 0.35f

/** Black, the color the menu backdrop and surface darken toward. */
private const val BLACK_RGB = 0x000000

/** Every themeable token from the six base colors of [p], by fixed formulas. */
fun ResolvedTheme.Companion.derive(p: ThemePalette): ResolvedTheme {
    val textPrimary = themeWithAlpha(ALPHA_OPAQUE, p.textPrimary)
    val textSecondary = themeWithAlpha(ALPHA_OPAQUE, p.textSecondary)
    val textMuted = themeWithAlpha(ALPHA_OPAQUE, themeMix(p.textSecondary, p.panelBackground, MUTED_TEXT_MIX))
    val accentPrimary = themeWithAlpha(ALPHA_OPAQUE, p.accentPrimary)
    return ResolvedTheme(
        panelBackground = themeWithAlpha(ALPHA_PANEL, p.panelBackground),
        panelBorder = p.panelBorder,
        focusBorder = textPrimary,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        statusOk = textSecondary,
        progressFill = accentPrimary,
        menuBackground = themeWithAlpha(
            ALPHA_MENU_BACKGROUND,
            themeMix(p.panelBackground, BLACK_RGB, MENU_BACKGROUND_DARKEN),
        ),
        menuSurface = themeWithAlpha(ALPHA_OPAQUE, themeMix(p.panelBackground, BLACK_RGB, MENU_SURFACE_DARKEN)),
        cardBackground = themeWithAlpha(ALPHA_OPAQUE, p.panelBackground),
        cardBorder = themeWithAlpha(ALPHA_SOFT_BORDER, p.panelBorder),
        accentPrimary = accentPrimary,
        accentSecondary = themeWithAlpha(ALPHA_OPAQUE, p.accentSecondary),
        textSubtle = textSecondary,
        textMuted = textMuted,
        badgeModText = accentPrimary,
        badgeModBg = themeWithAlpha(ALPHA_WASH, p.accentPrimary),
        badgeModBorder = themeWithAlpha(ALPHA_SOFT_BORDER, p.accentPrimary),
        badgeMutedText = textMuted,
        pillSelectedBg = textPrimary,
        toggleOnBg = themeWithAlpha(ALPHA_WASH, p.accentSecondary),
        toggleOnBorder = themeWithAlpha(ALPHA_TOGGLE_BORDER, p.accentSecondary),
    )
}
