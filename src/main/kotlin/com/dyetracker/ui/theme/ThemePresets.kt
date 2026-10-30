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

    /** Key of the saved Custom slot: a base theme plus color overrides. Not a preset. */
    const val CUSTOM = "custom"

    /** The start of a dye theme's key; the dye id follows it (`dye:nadeshiko`). */
    const val DYE_PREFIX = "dye:"

    private const val DEFAULT_NAME = "Default"
    private const val NEUTRAL_NAME = "Neutral"
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

    /** The preset keys, in the order the command and the Theme screen list them. [CUSTOM] is not one. */
    val PRESET_KEYS: List<String> = listOf(DEFAULT, NEUTRAL)

    /**
     * The name shown for [key]: the preset's name, `Custom` for [CUSTOM], or `"<dye name> dye"` for a
     * dye theme. Anything else, including a dye key with an unknown dye, shows as Default.
     */
    fun displayName(key: String): String {
        val dyeId = dyeIdOf(key)
        return when {
            dyeId != null -> "${DyeCatalog.displayNameOr(dyeId)}$DYE_NAME_SUFFIX"
            key == NEUTRAL -> NEUTRAL_NAME
            key == CUSTOM -> CUSTOM_NAME
            else -> DEFAULT_NAME
        }
    }

    /** The theme key for the dye [dyeId]. */
    fun dyeKey(dyeId: String): String = DYE_PREFIX + dyeId

    /** The dye id in the dye theme key [key], or `null` when it is not one or the dye has no color. */
    fun dyeIdOf(key: String): String? =
        key.removePrefix(DYE_PREFIX).takeIf { key.startsWith(DYE_PREFIX) && it in DyeThemeColors.COLORS }

    /** The base colors of the theme [key]: a preset's palette, a dye's [dyePalette], or `null` for an unknown key. */
    fun paletteFor(key: String): ThemePalette? {
        val dyeId = dyeIdOf(key)
        return when {
            dyeId != null -> dyePalette(DyeThemeColors.COLORS.getValue(dyeId))
            key == DEFAULT -> DEFAULT_PALETTE
            key == NEUTRAL -> NEUTRAL_PALETTE
            else -> null
        }
    }
}
