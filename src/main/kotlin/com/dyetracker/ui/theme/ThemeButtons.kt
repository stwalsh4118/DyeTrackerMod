package com.dyetracker.ui.theme

import com.dyetracker.dyeprogress.DyeCatalog
import com.dyetracker.dyeprogress.DyeOption

/** [ThemeButton.key] of the Theme screen's dye button, which opens the dye picker. */
const val DYE_BUTTON_KEY = "dye"

/** Before the active theme's button label. */
private const val SELECTED_PREFIX = "✔ "

/** The dye button's name; the dye's name replaces it while a dye theme is active. */
private const val DYE_BUTTON_NAME = "Dye"

/** Ends the dye button's label: it opens another screen. */
private const val OPENS_SCREEN_SUFFIX = " ▸"

/**
 * One button on the Theme screen (PBI 81): the theme [key] it applies (a preset key, or
 * [DYE_BUTTON_KEY]), its [label], and whether it is the active theme's ([selected]).
 */
data class ThemeButton(val key: String, val label: String, val selected: Boolean)

/**
 * The Theme screen's buttons with [activeKey] the theme in use: one per [ThemePresets.PRESET_KEYS]
 * ([ThemePresets.displayName]), then the dye button, `Dye ▸`. The active theme's button is
 * selected and its label starts with `✔ `; while a dye theme is active the dye button names the
 * dye instead (`✔ Jade ▸`). Any other key resolves to Default, so Default is marked then.
 */
fun themeButtons(activeKey: String): List<ThemeButton> {
    val activeDyeId = ThemePresets.dyeIdOf(activeKey)
    val activePreset = when {
        activeDyeId != null -> null
        activeKey in ThemePresets.PRESET_KEYS -> activeKey
        else -> ThemePresets.DEFAULT
    }
    val presets = ThemePresets.PRESET_KEYS.map { key ->
        val selected = key == activePreset
        ThemeButton(key, marked(ThemePresets.displayName(key), selected), selected)
    }
    val dyeSelected = activeDyeId != null
    val dyeName = activeDyeId?.let { DyeCatalog.displayNameOr(it) } ?: DYE_BUTTON_NAME
    return presets + ThemeButton(DYE_BUTTON_KEY, marked(dyeName, dyeSelected) + OPENS_SCREEN_SUFFIX, dyeSelected)
}

/** [label], led by [SELECTED_PREFIX] when [selected]. */
private fun marked(label: String, selected: Boolean): String = if (selected) SELECTED_PREFIX + label else label

/** The dyes the Theme screen's dye picker offers: the [DyeCatalog] dyes with a theme color in [DyeThemeColors.COLORS]. */
fun themeDyeOptions(): List<DyeOption> = DyeCatalog.all.filter { it.dyeId in DyeThemeColors.COLORS }
