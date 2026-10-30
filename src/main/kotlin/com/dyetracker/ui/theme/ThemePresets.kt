package com.dyetracker.ui.theme

import com.dyetracker.dyeprogress.DyeCatalog

/**
 * The built-in themes (PBI 81): their keys, the base colors behind each, and their names. Besides
 * the presets, every dye in [DyeThemeColors.COLORS] is a theme, keyed [DYE_PREFIX] + its id.
 */
object ThemePresets {

    /** Key of the Default theme: the look from before themes. */
    const val DEFAULT = "default"

    /** Key of the Neutral theme: grays in place of the magenta and cyan accents. */
    const val NEUTRAL = "neutral"

    /** Key of the Midnight theme: periwinkle and lavender on deep navy. */
    const val MIDNIGHT = "midnight"

    /** Key of the Forest theme: green and gold on dark green. */
    const val FOREST = "forest"

    /** Key of the Sunset theme: coral and amber on dark plum. */
    const val SUNSET = "sunset"

    /** Key of the Ice theme: aqua and periwinkle on dark slate. */
    const val ICE = "ice"

    /** Key of the saved Custom slot: a base theme plus color overrides. Not a preset. */
    const val CUSTOM = "custom"

    /** The start of a dye theme's key; the dye id follows it (`dye:nadeshiko`). */
    const val DYE_PREFIX = "dye:"

    private const val DEFAULT_NAME = "Default"
    private const val NEUTRAL_NAME = "Neutral"
    private const val MIDNIGHT_NAME = "Midnight"
    private const val FOREST_NAME = "Forest"
    private const val SUNSET_NAME = "Sunset"
    private const val ICE_NAME = "Ice"
    private const val CUSTOM_NAME = "Custom"

    /** Follows the dye's name in a dye theme's name (`Nadeshiko dye`). */
    private const val DYE_NAME_SUFFIX = " dye"

    /** The panel fill shared by Default and Neutral. */
    private const val BASE_PANEL_BACKGROUND = 0x101010

    /** The panel border (ARGB) shared by Default and Neutral. */
    private const val BASE_PANEL_BORDER = 0xFF555555.toInt()

    /** The primary text color shared by Default and Neutral. */
    private const val BASE_TEXT_PRIMARY = 0xFFFFFF

    /** The secondary text color shared by Default and Neutral. */
    private const val BASE_TEXT_SECONDARY = 0xBFBFBF

    /**
     * The base colors behind Default, for editing and overriding it. Default itself is never
     * derived from these: it is [ResolvedTheme.DEFAULT], pinned value by value.
     */
    val DEFAULT_PALETTE = ThemePalette(
        accentPrimary = 0xE225F4,
        accentSecondary = 0x00F0FF,
        panelBackground = BASE_PANEL_BACKGROUND,
        panelBorder = BASE_PANEL_BORDER,
        textPrimary = BASE_TEXT_PRIMARY,
        textSecondary = BASE_TEXT_SECONDARY,
    )

    /** The base colors behind Neutral. */
    val NEUTRAL_PALETTE = ThemePalette(
        accentPrimary = 0xE0E0E0,
        accentSecondary = 0xA6A6A6,
        panelBackground = BASE_PANEL_BACKGROUND,
        panelBorder = BASE_PANEL_BORDER,
        textPrimary = BASE_TEXT_PRIMARY,
        textSecondary = BASE_TEXT_SECONDARY,
    )

    /** The base colors behind Midnight. */
    val MIDNIGHT_PALETTE = ThemePalette(
        accentPrimary = 0x7AA2FF,
        accentSecondary = 0xC4A7FF,
        panelBackground = 0x0B1222,
        panelBorder = 0xFF2B3A5E.toInt(),
        textPrimary = 0xF5F7FF,
        textSecondary = 0xAEB8D0,
    )

    /** The base colors behind Forest. */
    val FOREST_PALETTE = ThemePalette(
        accentPrimary = 0x5FD38D,
        accentSecondary = 0xE8C468,
        panelBackground = 0x0D1A13,
        panelBorder = 0xFF2E4D3A.toInt(),
        textPrimary = 0xF1F7F2,
        textSecondary = 0xA7BDAE,
    )

    /** The base colors behind Sunset. */
    val SUNSET_PALETTE = ThemePalette(
        accentPrimary = 0xFF7A59,
        accentSecondary = 0xFFC15E,
        panelBackground = 0x1C1014,
        panelBorder = 0xFF5C2F3C.toInt(),
        textPrimary = 0xFFF5F0,
        textSecondary = 0xCCA9A2,
    )

    /** The base colors behind Ice. */
    val ICE_PALETTE = ThemePalette(
        accentPrimary = 0x7FE6F2,
        accentSecondary = 0xB9C6FF,
        panelBackground = 0x0E1A20,
        panelBorder = 0xFF3A5662.toInt(),
        textPrimary = 0xF4FBFF,
        textSecondary = 0xA6BEC7,
    )

    /** The preset keys, in the order the command and the Theme screen list them. [CUSTOM] is not one. */
    val PRESET_KEYS: List<String> = listOf(DEFAULT, NEUTRAL, MIDNIGHT, FOREST, SUNSET, ICE)

    /** The name of each preset and of [CUSTOM], by key. */
    private val NAMES: Map<String, String> = mapOf(
        DEFAULT to DEFAULT_NAME,
        NEUTRAL to NEUTRAL_NAME,
        MIDNIGHT to MIDNIGHT_NAME,
        FOREST to FOREST_NAME,
        SUNSET to SUNSET_NAME,
        ICE to ICE_NAME,
        CUSTOM to CUSTOM_NAME,
    )

    /** The base colors of each preset, by key. */
    private val PALETTES: Map<String, ThemePalette> = mapOf(
        DEFAULT to DEFAULT_PALETTE,
        NEUTRAL to NEUTRAL_PALETTE,
        MIDNIGHT to MIDNIGHT_PALETTE,
        FOREST to FOREST_PALETTE,
        SUNSET to SUNSET_PALETTE,
        ICE to ICE_PALETTE,
    )

    /**
     * The name shown for [key]: the preset's name, `Custom` for [CUSTOM], or `"<dye name> dye"` for a
     * dye theme. Anything else, including a dye key with an unknown dye, shows as Default.
     */
    fun displayName(key: String): String {
        val dyeId = dyeIdOf(key)
        return if (dyeId != null) "${DyeCatalog.displayNameOr(dyeId)}$DYE_NAME_SUFFIX" else NAMES[key] ?: DEFAULT_NAME
    }

    /** The theme key for the dye [dyeId]. */
    fun dyeKey(dyeId: String): String = DYE_PREFIX + dyeId

    /** The dye id in the dye theme key [key], or `null` when it is not one or the dye has no color. */
    fun dyeIdOf(key: String): String? =
        key.removePrefix(DYE_PREFIX).takeIf { key.startsWith(DYE_PREFIX) && it in DyeThemeColors.COLORS }

    /** The base colors of the theme [key]: a preset's palette, a dye's [dyePalette], or `null` for an unknown key. */
    fun paletteFor(key: String): ThemePalette? {
        val dyeId = dyeIdOf(key)
        return if (dyeId != null) dyePalette(DyeThemeColors.COLORS.getValue(dyeId)) else PALETTES[key]
    }
}
