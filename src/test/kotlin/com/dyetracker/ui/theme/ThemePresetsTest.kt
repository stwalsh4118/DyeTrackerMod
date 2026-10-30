package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Unit coverage for the hand-made presets (task 81-6): Midnight, Forest, Sunset and Ice each resolve
 * to their derived palette, with pinned spot checks, are named, listed in order, and work as a
 * Custom slot base. The preset `midnight` and the dye theme `dye:midnight` stay apart.
 */
class ThemePresetsTest {

    private val palettes = mapOf(
        "midnight" to ThemePalette(0x7AA2FF, 0xC4A7FF, 0x0B1222, 0xFF2B3A5E.toInt(), 0xF5F7FF, 0xAEB8D0),
        "forest" to ThemePalette(0x5FD38D, 0xE8C468, 0x0D1A13, 0xFF2E4D3A.toInt(), 0xF1F7F2, 0xA7BDAE),
        "sunset" to ThemePalette(0xFF7A59, 0xFFC15E, 0x1C1014, 0xFF5C2F3C.toInt(), 0xFFF5F0, 0xCCA9A2),
        "ice" to ThemePalette(0x7FE6F2, 0xB9C6FF, 0x0E1A20, 0xFF3A5662.toInt(), 0xF4FBFF, 0xA6BEC7),
    )

    @Test
    fun `each new preset resolves to its derived palette`() {
        for ((key, palette) in palettes) {
            assertEquals(ResolvedTheme.derive(palette), resolveTheme(ModConfig(themeKey = key)), key)
        }
    }

    @Test
    fun `paletteFor gives each new preset's palette`() {
        for ((key, palette) in palettes) {
            assertEquals(palette, ThemePresets.paletteFor(key), key)
        }
    }

    @Test
    fun `Midnight pins its menu and muted text colors`() {
        val theme = resolveTheme(ModConfig(themeKey = "midnight"))
        assertEquals(0xF0060911.toInt(), theme.menuBackground, "menuBackground")
        assertEquals(0xFF070C16.toInt(), theme.menuSurface, "menuSurface")
        assertEquals(0xFF6D768A.toInt(), theme.textMuted, "textMuted")
    }

    @Test
    fun `Forest pins its menu background and muted text`() {
        val theme = resolveTheme(ModConfig(themeKey = "forest"))
        assertEquals(0xF0070D0A.toInt(), theme.menuBackground, "menuBackground")
        assertEquals(0xFF697C70.toInt(), theme.textMuted, "textMuted")
    }

    @Test
    fun `Sunset pins its menu background and muted text`() {
        val theme = resolveTheme(ModConfig(themeKey = "sunset"))
        assertEquals(0xF00E080A.toInt(), theme.menuBackground, "menuBackground")
        assertEquals(0xFF866C69.toInt(), theme.textMuted, "textMuted")
    }

    @Test
    fun `Ice pins its menu background and muted text`() {
        val theme = resolveTheme(ModConfig(themeKey = "ice"))
        assertEquals(0xF0070D10.toInt(), theme.menuBackground, "menuBackground")
        assertEquals(0xFF697C84.toInt(), theme.textMuted, "textMuted")
    }

    @Test
    fun `displayName names each new preset`() {
        assertEquals("Midnight", ThemePresets.displayName("midnight"))
        assertEquals("Forest", ThemePresets.displayName("forest"))
        assertEquals("Sunset", ThemePresets.displayName("sunset"))
        assertEquals("Ice", ThemePresets.displayName("ice"))
    }

    @Test
    fun `the preset midnight and the dye midnight are different themes`() {
        val preset = resolveTheme(ModConfig(themeKey = "midnight"))
        val dye = resolveTheme(ModConfig(themeKey = "dye:midnight"))

        assertEquals(ResolvedTheme.derive(palettes.getValue("midnight")), preset)
        assertEquals(ResolvedTheme.derive(dyePalette(DyeThemeColors.COLORS.getValue("midnight"))), dye)
        assertNotEquals(preset, dye)
        assertEquals("Midnight", ThemePresets.displayName("midnight"))
        assertEquals("Midnight dye", ThemePresets.displayName("dye:midnight"))
    }

    @Test
    fun `preset keys are default, neutral, midnight, forest, sunset, ice`() {
        assertEquals(listOf("default", "neutral", "midnight", "forest", "sunset", "ice"), ThemePresets.PRESET_KEYS)
    }

    @Test
    fun `each new preset works as a Custom slot base`() {
        for ((key, palette) in palettes) {
            val config = ModConfig(themeKey = "custom", themeCustomBase = key)
            assertEquals(key, customBaseKey(config), key)
            assertEquals(palette, effectivePalette(config), key)
            assertEquals(ResolvedTheme.derive(palette), resolveTheme(config), key)
        }
    }
}
