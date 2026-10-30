package com.dyetracker.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the HUD opacity helpers (task 81-5): alpha and percent convert both ways with
 * rounding, out-of-range values clamp, and the slider's value and label follow the alpha.
 */
class HudOpacityTest {

    @Test
    fun `alpha converts to a rounded percent`() {
        assertEquals(listOf(0, 50, 78, 100), listOf(0, 128, 200, 255).map { alphaToPercent(it) })
    }

    @Test
    fun `percent converts to a rounded alpha`() {
        assertEquals(listOf(0, 128, 199, 255), listOf(0, 50, 78, 100).map { percentToAlpha(it) })
    }

    @Test
    fun `alpha clamps to 0 to 255`() {
        assertEquals(0, clampHudAlpha(-5))
        assertEquals(255, clampHudAlpha(999))
        assertEquals(HUD_PANEL_ALPHA_DEFAULT, clampHudAlpha(HUD_PANEL_ALPHA_DEFAULT))
    }

    @Test
    fun `out-of-range alpha and percent clamp before converting`() {
        assertEquals(100, alphaToPercent(999))
        assertEquals(0, alphaToPercent(-5))
        assertEquals(255, percentToAlpha(150))
        assertEquals(0, percentToAlpha(-10))
    }

    @Test
    fun `the default is today's panel alpha`() {
        assertEquals(0xC8, HUD_PANEL_ALPHA_DEFAULT)
    }

    @Test
    fun `slider value converts to an alpha, clamped`() {
        assertEquals(0, sliderValueToAlpha(0.0))
        assertEquals(255, sliderValueToAlpha(1.0))
        assertEquals(HUD_PANEL_ALPHA_DEFAULT, sliderValueToAlpha(HUD_PANEL_ALPHA_DEFAULT / 255.0))
        assertEquals(0, sliderValueToAlpha(-0.5))
        assertEquals(255, sliderValueToAlpha(1.5))
    }

    @Test
    fun `the label shows the alpha as a percent`() {
        assertEquals("HUD opacity: 78%", hudOpacityLabel(HUD_PANEL_ALPHA_DEFAULT))
        assertEquals("HUD opacity: 0%", hudOpacityLabel(0))
        assertEquals("HUD opacity: 100%", hudOpacityLabel(255))
    }
}
