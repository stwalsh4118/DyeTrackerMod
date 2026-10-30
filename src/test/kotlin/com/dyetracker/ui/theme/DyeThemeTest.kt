package com.dyetracker.ui.theme

import com.dyetracker.spotify.contrastRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit coverage for [dyePalette] (task 81-2): the dye is lightened only until it reads on the dark
 * panel, the secondary is a lighter shade of it, the border is it at low alpha, and every catalog
 * dye gives a readable accent.
 */
class DyeThemeTest {

    private val panel = 0x101010

    @Test
    fun `Nadeshiko is already readable, so its accent is the dye itself`() {
        val palette = dyePalette(0xF6ADC6)
        assertEquals(0xF6ADC6, palette.accentPrimary)
        assertEquals(0xF9CADA, palette.accentSecondary)
        assertEquals(0x66F6ADC6, palette.panelBorder)
        assertEquals(panel, palette.panelBackground)
    }

    @Test
    fun `the text colors are white and light gray`() {
        val palette = dyePalette(0xF6ADC6)
        assertEquals(0xFFFFFF, palette.textPrimary)
        assertEquals(0xBFBFBF, palette.textSecondary)
    }

    @Test
    fun `Pure Black is lightened to a readable gray`() {
        val palette = dyePalette(0x000000)
        assertEquals(0x666666, palette.accentPrimary)
        assertEquals(0x9C9C9C, palette.accentSecondary)
    }

    @Test
    fun `Bingo Blue is lightened until it reads`() {
        val palette = dyePalette(0x002FA7)
        assertEquals(0x4063BD, palette.accentPrimary)
        assertEquals(0x839AD4, palette.accentSecondary)
    }

    @Test
    fun `Dark Purple is lightened until it reads`() {
        val palette = dyePalette(0x301934)
        assertEquals(0x6E5E71, palette.accentPrimary)
        assertEquals(0xA196A3, palette.accentSecondary)
    }

    @Test
    fun `every dye gives an accent that reads on the panel`() {
        val unreadable = DyeThemeColors.COLORS.filter { (_, rgb) ->
            contrastRatio(dyePalette(rgb).accentPrimary, panel) < 3.0
        }.keys
        assertTrue(unreadable.isEmpty(), "Accents under 3.0:1 on the panel: $unreadable")
    }
}
