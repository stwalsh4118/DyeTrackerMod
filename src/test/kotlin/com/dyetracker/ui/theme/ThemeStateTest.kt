package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for [ThemeState] (task 81-1): applying a config switches the themeable
 * [UiTheme.Colors] tokens, fixed tokens never move, and a [HudTint] still wins over the theme.
 */
class ThemeStateTest {

    @AfterTest
    fun reset() {
        ThemeState.active = ResolvedTheme.DEFAULT
        HudTint.colors = null
    }

    @Test
    fun `applying Neutral switches the themeable tokens`() {
        ThemeState.apply(ModConfig(themeKey = "neutral"))
        assertEquals(0xFFE0E0E0.toInt(), UiTheme.Colors.ACCENT_PRIMARY)
        assertEquals(0xF0080808.toInt(), UiTheme.Colors.MENU_BACKGROUND)
    }

    @Test
    fun `fixed tokens do not change with the theme`() {
        ThemeState.apply(ModConfig(themeKey = "neutral"))
        assertEquals(0xFF43B581.toInt(), UiTheme.Colors.BOOST_2X)
        assertEquals(0xFFFF5555.toInt(), UiTheme.Colors.STATUS_ERROR)
        assertEquals(0xFFFFD700.toInt(), UiTheme.Colors.TIER_GOLD)
    }

    @Test
    fun `applying Default after Neutral restores today's tokens`() {
        ThemeState.apply(ModConfig(themeKey = "neutral"))
        ThemeState.apply(ModConfig(themeKey = "default"))
        assertEquals(0xFFE225F4.toInt(), UiTheme.Colors.ACCENT_PRIMARY)
    }

    @Test
    fun `a HUD tint still wins over the theme`() {
        ThemeState.apply(ModConfig(themeKey = "neutral"))
        HudTint.colors = TintColors(TINT_BACKGROUND, TINT_BORDER)
        assertEquals(TINT_BACKGROUND, HudTint.panelBackground())
        assertEquals(TINT_BORDER, HudTint.panelBorder())
    }

    private companion object {
        const val TINT_BACKGROUND = 0xC8_20_40_60.toInt()
        const val TINT_BORDER = 0xFF_A0_B0_C0.toInt()
    }
}
