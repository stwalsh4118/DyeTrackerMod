package com.dyetracker.ui.theme

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for [HudTint] (task 76-8): no tint gives the neutral panel tokens, a tint gives
 * its colors, and clearing it goes back to the tokens. Task 81-5: the panel fill takes
 * [ThemeState.hudPanelAlpha], tinted or not; the border never does.
 */
class HudTintTest {

    @AfterTest
    fun resetTint() {
        HudTint.colors = null
        ThemeState.hudPanelAlpha = HUD_PANEL_ALPHA_DEFAULT
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

    @Test
    fun `the default opacity and no tint keep today's panel fill`() {
        assertEquals(0xC8101010.toInt(), HudTint.panelBackground())
    }

    @Test
    fun `the opacity sets the neutral panel fill's alpha`() {
        ThemeState.hudPanelAlpha = HALF_ALPHA
        assertEquals(0x80101010.toInt(), HudTint.panelBackground())
    }

    @Test
    fun `the opacity sets a tint background's alpha`() {
        HudTint.colors = TintColors(0xC8123456.toInt(), BORDER)
        ThemeState.hudPanelAlpha = HALF_ALPHA
        assertEquals(0x80123456.toInt(), HudTint.panelBackground())
    }

    @Test
    fun `the opacity leaves the border alone`() {
        ThemeState.hudPanelAlpha = HALF_ALPHA
        assertEquals(UiTheme.Colors.PANEL_BORDER, HudTint.panelBorder())
        HudTint.colors = TintColors(BACKGROUND, BORDER)
        assertEquals(BORDER, HudTint.panelBorder())
    }

    private companion object {
        const val HALF_ALPHA = 0x80
        const val BACKGROUND = 0xC8_20_40_60.toInt()
        const val BORDER = 0xFF_A0_B0_C0.toInt()
    }
}
