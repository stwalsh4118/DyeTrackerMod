package com.dyetracker.ui.theme

/** The built-in themes (PBI 81): their keys, the base colors behind each, and their names. */
object ThemePresets {

    /** Key of the Default theme: the look from before themes. */
    const val DEFAULT = "default"

    /** Key of the Neutral theme: grays in place of the magenta and cyan accents. */
    const val NEUTRAL = "neutral"

    private const val DEFAULT_NAME = "Default"
    private const val NEUTRAL_NAME = "Neutral"

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

    /** The preset keys, in the order the command and the Theme screen list them. */
    val PRESET_KEYS: List<String> = listOf(DEFAULT, NEUTRAL)

    /** The name shown for [key]; anything that is not a preset shows as Default. */
    fun displayName(key: String): String = when (key) {
        NEUTRAL -> NEUTRAL_NAME
        else -> DEFAULT_NAME
    }
}
