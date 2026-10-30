package com.dyetracker.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for [ResolvedTheme] (task 81-1): [ResolvedTheme.DEFAULT] pins today's token values
 * field by field, and [ResolvedTheme.derive] turns the Neutral palette into the expected colors.
 */
class ResolvedThemeTest {

    @Test
    fun `DEFAULT pins today's token values`() {
        val d = ResolvedTheme.DEFAULT
        assertEquals(0xC8101010.toInt(), d.panelBackground, "panelBackground")
        assertEquals(0xFF555555.toInt(), d.panelBorder, "panelBorder")
        assertEquals(0xFFFFFFFF.toInt(), d.focusBorder, "focusBorder")
        assertEquals(0xFFFFFFFF.toInt(), d.textPrimary, "textPrimary")
        assertEquals(0xFFBFBFBF.toInt(), d.textSecondary, "textSecondary")
        assertEquals(0xFFBFBFBF.toInt(), d.statusOk, "statusOk")
        assertEquals(0xFFE225F4.toInt(), d.progressFill, "progressFill")
        assertEquals(0xF0000000.toInt(), d.menuBackground, "menuBackground")
        assertEquals(0xFF0A0A0A.toInt(), d.menuSurface, "menuSurface")
        assertEquals(0xFF111111.toInt(), d.cardBackground, "cardBackground")
        assertEquals(0x1AFFFFFF, d.cardBorder, "cardBorder")
        assertEquals(0xFFE225F4.toInt(), d.accentPrimary, "accentPrimary")
        assertEquals(0xFF00F0FF.toInt(), d.accentSecondary, "accentSecondary")
        assertEquals(0xFF94A3B8.toInt(), d.textSubtle, "textSubtle")
        assertEquals(0xFF64748B.toInt(), d.textMuted, "textMuted")
        assertEquals(0xFFE225F4.toInt(), d.badgeModText, "badgeModText")
        assertEquals(0x1AE225F4, d.badgeModBg, "badgeModBg")
        assertEquals(0x4DE225F4, d.badgeModBorder, "badgeModBorder")
        assertEquals(0xFF64748B.toInt(), d.badgeMutedText, "badgeMutedText")
        assertEquals(0xFFFFFFFF.toInt(), d.pillSelectedBg, "pillSelectedBg")
        assertEquals(0x1A00F0FF, d.toggleOnBg, "toggleOnBg")
        assertEquals(0x3300F0FF, d.toggleOnBorder, "toggleOnBorder")
    }

    @Test
    fun `derive turns the Neutral palette into the expected colors`() {
        val n = ResolvedTheme.derive(ThemePresets.NEUTRAL_PALETTE)
        assertEquals(0xF0080808.toInt(), n.menuBackground, "menuBackground")
        assertEquals(0xFF0A0A0A.toInt(), n.menuSurface, "menuSurface")
        assertEquals(0x4D555555, n.cardBorder, "cardBorder")
        assertEquals(0xFF797979.toInt(), n.textMuted, "textMuted")
        assertEquals(0xFFE0E0E0.toInt(), n.accentPrimary, "accentPrimary")
        assertEquals(0x1AA6A6A6, n.toggleOnBg, "toggleOnBg")
        assertEquals(0xC8101010.toInt(), n.panelBackground, "panelBackground")
        assertEquals(0x4DE0E0E0, n.badgeModBorder, "badgeModBorder")
    }

    @Test
    fun `derive shares each base color across its fields`() {
        val n = ResolvedTheme.derive(ThemePresets.NEUTRAL_PALETTE)
        assertEquals(0xFF555555.toInt(), n.panelBorder, "panelBorder is the palette border as is")
        assertEquals(n.textPrimary, n.focusBorder, "focusBorder follows textPrimary")
        assertEquals(n.textPrimary, n.pillSelectedBg, "pillSelectedBg follows textPrimary")
        assertEquals(0xFFBFBFBF.toInt(), n.textSecondary, "textSecondary")
        assertEquals(n.textSecondary, n.statusOk, "statusOk follows textSecondary")
        assertEquals(n.textSecondary, n.textSubtle, "textSubtle follows textSecondary")
        assertEquals(n.textMuted, n.badgeMutedText, "badgeMutedText follows textMuted")
        assertEquals(n.accentPrimary, n.progressFill, "progressFill follows accentPrimary")
        assertEquals(n.accentPrimary, n.badgeModText, "badgeModText follows accentPrimary")
        assertEquals(0x1AE0E0E0, n.badgeModBg, "badgeModBg")
        assertEquals(0xFFA6A6A6.toInt(), n.accentSecondary, "accentSecondary")
        assertEquals(0x33A6A6A6, n.toggleOnBorder, "toggleOnBorder")
        assertEquals(0xFF101010.toInt(), n.cardBackground, "cardBackground")
    }

    @Test
    fun `themeWithAlpha replaces the alpha byte`() {
        assertEquals(0x4D123456, themeWithAlpha(0x4D, 0xFF123456.toInt()))
        assertEquals(0xFF123456.toInt(), themeWithAlpha(0xFF, 0x123456))
    }

    @Test
    fun `themeMix blends RGB and drops alpha`() {
        assertEquals(0x080808, themeMix(0x101010, 0x000000, 0.5f))
        assertEquals(0x101010, themeMix(0x101010, 0x000000, 0f))
        assertEquals(0x000000, themeMix(0x101010, 0x000000, 1f))
    }
}
