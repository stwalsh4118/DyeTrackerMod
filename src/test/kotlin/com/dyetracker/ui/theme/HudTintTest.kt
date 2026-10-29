package com.dyetracker.ui.theme

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for [HudTint] (task 76-8): no tint gives the neutral panel tokens, a tint gives
 * its colors, and clearing it goes back to the tokens.
 */
class HudTintTest {

    @AfterTest
    fun resetTint() {
        HudTint.colors = null
    }

    @Test
    fun `no tint gives the neutral panel tokens`() {
        HudTint.colors = null
        assertEquals(UiTheme.Colors.PANEL_BACKGROUND, HudTint.panelBackground())
        assertEquals(UiTheme.Colors.PANEL_BORDER, HudTint.panelBorder())
    }

    @Test
    fun `a tint gives its colors`() {
        HudTint.colors = TintColors(BACKGROUND, BORDER)
        assertEquals(BACKGROUND, HudTint.panelBackground())
        assertEquals(BORDER, HudTint.panelBorder())
    }

    @Test
    fun `clearing the tint goes back to the neutral tokens`() {
        HudTint.colors = TintColors(BACKGROUND, BORDER)
        HudTint.colors = null
        assertEquals(UiTheme.Colors.PANEL_BACKGROUND, HudTint.panelBackground())
        assertEquals(UiTheme.Colors.PANEL_BORDER, HudTint.panelBorder())
    }

    private companion object {
        const val BACKGROUND = 0xC8_20_40_60.toInt()
        const val BORDER = 0xFF_A0_B0_C0.toInt()
    }
}
