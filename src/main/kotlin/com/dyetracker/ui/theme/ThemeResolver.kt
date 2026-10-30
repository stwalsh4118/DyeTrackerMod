package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig

/**
 * The theme [config] picks (PBI 81): for a preset other than Default, its palette derived
 * ([ResolvedTheme.derive] of [ThemePresets.paletteFor]); the dye's [dyePalette] for a dye key whose
 * dye has a color; the Custom slot for [ThemePresets.CUSTOM] (see [resolveCustom]); Default for
 * [ThemePresets.DEFAULT], a blank key, a dye key with an unknown dye or any key it doesn't know.
 * Default is always [ResolvedTheme.DEFAULT], never derived.
 */
fun resolveTheme(config: ModConfig): ResolvedTheme =
    if (config.themeKey == ThemePresets.CUSTOM) resolveCustom(config) else resolveKey(config.themeKey)

/** The theme of the non-custom key [key], as [resolveTheme] describes. */
private fun resolveKey(key: String): ResolvedTheme {
    if (key == ThemePresets.DEFAULT) return ResolvedTheme.DEFAULT
    return ThemePresets.paletteFor(key)?.let(ResolvedTheme::derive) ?: ResolvedTheme.DEFAULT
}

/**
 * The Custom slot's theme: its base theme with, for each valid override, that color's token group
 * ([OVERRIDE_TOKENS]) replaced by what [ResolvedTheme.derive] gives for [effectivePalette]. With no
 * valid override it is exactly the base theme, so a Default base stays pinned.
 */
private fun resolveCustom(config: ModConfig): ResolvedTheme {
    val base = resolveKey(customBaseKey(config))
    val overridden = validOverrides(config).keys
    if (overridden.isEmpty()) return base
    val edited = ResolvedTheme.derive(effectivePalette(config))
    return overridden.fold(base) { theme, key -> OVERRIDE_TOKENS.getValue(key)(theme, edited) }
}

/**
 * For each [ThemeOverrides.KEYS] key, the tokens that read that base color: `(theme, edited)` gives
 * `theme` with those tokens taken from `edited`.
 */
private val OVERRIDE_TOKENS: Map<String, (ResolvedTheme, ResolvedTheme) -> ResolvedTheme> = mapOf(
    ThemeOverrides.ACCENT_PRIMARY to { t, e ->
        t.copy(
            progressFill = e.progressFill,
            accentPrimary = e.accentPrimary,
            badgeModText = e.badgeModText,
            badgeModBg = e.badgeModBg,
            badgeModBorder = e.badgeModBorder,
        )
    },
    ThemeOverrides.ACCENT_SECONDARY to { t, e ->
        t.copy(accentSecondary = e.accentSecondary, toggleOnBg = e.toggleOnBg, toggleOnBorder = e.toggleOnBorder)
    },
    ThemeOverrides.PANEL_BACKGROUND to { t, e ->
        t.copy(
            panelBackground = e.panelBackground,
            menuBackground = e.menuBackground,
            menuSurface = e.menuSurface,
            cardBackground = e.cardBackground,
            textMuted = e.textMuted,
            badgeMutedText = e.badgeMutedText,
        )
    },
    ThemeOverrides.PANEL_BORDER to { t, e -> t.copy(panelBorder = e.panelBorder, cardBorder = e.cardBorder) },
    ThemeOverrides.TEXT_PRIMARY to { t, e ->
        t.copy(textPrimary = e.textPrimary, focusBorder = e.focusBorder, pillSelectedBg = e.pillSelectedBg)
    },
    ThemeOverrides.TEXT_SECONDARY to { t, e ->
        t.copy(
            textSecondary = e.textSecondary,
            statusOk = e.statusOk,
            textSubtle = e.textSubtle,
            textMuted = e.textMuted,
            badgeMutedText = e.badgeMutedText,
        )
    },
)

/** [key] when [ThemePresets.paletteFor] knows it, else [ThemePresets.DEFAULT]; `custom` is not known. */
internal fun knownThemeKeyOrDefault(key: String): String =
    if (ThemePresets.paletteFor(key) != null) key else ThemePresets.DEFAULT

/** The base colors of [key] ([ThemePresets.paletteFor]), or Default's when it is not known; `custom` is not known. */
fun paletteOrDefault(key: String): ThemePalette = ThemePresets.paletteFor(key) ?: ThemePresets.DEFAULT_PALETTE

/** The Custom slot's base theme key: [ModConfig.themeCustomBase], or Default when it is `custom`, blank or unknown. */
fun customBaseKey(config: ModConfig): String = knownThemeKeyOrDefault(config.themeCustomBase)

/**
 * The Custom slot's usable overrides: the [ModConfig.themeCustomOverrides] entries whose key is in
 * [ThemeOverrides.KEYS] and whose value [parseHexRgb] reads, as RGB values.
 */
fun validOverrides(config: ModConfig): Map<String, Int> =
    config.themeCustomOverrides
        .filterKeys { it in ThemeOverrides.KEYS }
        .mapNotNull { (key, value) -> parseHexRgb(value)?.let { key to it } }
        .toMap()

/**
 * The Custom slot's base colors: the base theme's palette ([customBaseKey]) with each valid
 * override in place of its field; a [ThemeOverrides.PANEL_BORDER] override is opaque.
 */
fun effectivePalette(config: ModConfig): ThemePalette {
    val base = paletteOrDefault(customBaseKey(config))
    return validOverrides(config).entries.fold(base) { palette, (key, rgb) -> palette.withOverride(key, rgb) }
}
