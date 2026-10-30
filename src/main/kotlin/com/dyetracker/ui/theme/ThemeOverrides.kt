package com.dyetracker.ui.theme

/**
 * The base colors the Custom slot can override (PBI 81): their keys in
 * [com.dyetracker.config.ModConfig.themeCustomOverrides], each named after its [ThemePalette]
 * field, and the labels the Theme screen shows for them.
 */
object ThemeOverrides {
    const val ACCENT_PRIMARY = "accentPrimary"
    const val ACCENT_SECONDARY = "accentSecondary"
    const val PANEL_BACKGROUND = "panelBackground"
    const val PANEL_BORDER = "panelBorder"
    const val TEXT_PRIMARY = "textPrimary"
    const val TEXT_SECONDARY = "textSecondary"

    /** Every override key, in the order the Theme screen lays out their boxes. */
    val KEYS = listOf(ACCENT_PRIMARY, ACCENT_SECONDARY, PANEL_BACKGROUND, PANEL_BORDER, TEXT_PRIMARY, TEXT_SECONDARY)

    /** The Theme screen's label for each key in [KEYS]. */
    val LABELS = mapOf(
        ACCENT_PRIMARY to "Accent",
        ACCENT_SECONDARY to "Accent 2",
        PANEL_BACKGROUND to "Panel",
        PANEL_BORDER to "Border",
        TEXT_PRIMARY to "Text",
        TEXT_SECONDARY to "Text 2",
    )
}

/** The one `#` [parseHexRgb] allows before the digits. */
private const val HEX_PREFIX = "#"

/** Hex digits in an `RRGGBB` color. */
private const val HEX_RGB_DIGITS = 6

private const val HEX_RADIX = 16

/** Exactly [HEX_RGB_DIGITS] hex digits, either case. */
private val HEX_RGB = Regex("[0-9a-fA-F]{$HEX_RGB_DIGITS}")

/** The alpha of a border override: a typed border is opaque. */
private const val OVERRIDE_BORDER_ALPHA = 0xFF

/** How to read and replace the [ThemePalette] field behind one override key. */
private class PaletteField(val get: (ThemePalette) -> Int, val set: (ThemePalette, Int) -> ThemePalette)

/** The palette field behind each [ThemeOverrides.KEYS] key. */
private val PALETTE_FIELDS = mapOf(
    ThemeOverrides.ACCENT_PRIMARY to PaletteField({ it.accentPrimary }, { p, rgb -> p.copy(accentPrimary = rgb) }),
    ThemeOverrides.ACCENT_SECONDARY to PaletteField({ it.accentSecondary }, { p, rgb -> p.copy(accentSecondary = rgb) }),
    ThemeOverrides.PANEL_BACKGROUND to PaletteField({ it.panelBackground }, { p, rgb -> p.copy(panelBackground = rgb) }),
    ThemeOverrides.PANEL_BORDER to PaletteField(
        { it.panelBorder },
        { p, rgb -> p.copy(panelBorder = themeWithAlpha(OVERRIDE_BORDER_ALPHA, rgb)) },
    ),
    ThemeOverrides.TEXT_PRIMARY to PaletteField({ it.textPrimary }, { p, rgb -> p.copy(textPrimary = rgb) }),
    ThemeOverrides.TEXT_SECONDARY to PaletteField({ it.textSecondary }, { p, rgb -> p.copy(textSecondary = rgb) }),
)

/**
 * The `RRGGBB` color in [input]: surrounding spaces and one leading `#` are dropped, then it must
 * be exactly six hex digits, either case. Anything else is `null`.
 */
fun parseHexRgb(input: String): Int? {
    val digits = input.trim().removePrefix(HEX_PREFIX).trim()
    return if (HEX_RGB.matches(digits)) digits.toInt(HEX_RADIX) else null
}

/** The RGB channels of [rgb] as six uppercase hex digits, no `#`; any alpha is dropped. */
fun formatHexRgb(rgb: Int): String = "%0${HEX_RGB_DIGITS}X".format(rgb and RGB_MASK)

/** This palette's color for the override [key] (a [ThemeOverrides.KEYS] key). */
internal fun ThemePalette.overrideColor(key: String): Int = PALETTE_FIELDS.getValue(key).get(this)

/**
 * This palette with the override [key] (a [ThemeOverrides.KEYS] key) set to [rgb]; a
 * [ThemeOverrides.PANEL_BORDER] override is made opaque.
 */
internal fun ThemePalette.withOverride(key: String, rgb: Int): ThemePalette = PALETTE_FIELDS.getValue(key).set(this, rgb)
