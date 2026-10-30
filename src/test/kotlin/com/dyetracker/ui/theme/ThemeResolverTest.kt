package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for [resolveTheme] and [ThemePresets.displayName] (task 81-1): each preset key
 * resolves to its theme, and blank or unknown keys fall back to Default. Dye keys (task 81-2)
 * resolve to their dye's palette, and [ThemePresets.dyeIdOf] and [ThemePresets.paletteFor] read them.
 */
class ThemeResolverTest {

    @Test
    fun `default resolves to DEFAULT`() {
        assertEquals(ResolvedTheme.DEFAULT, resolveTheme(ModConfig(themeKey = "default")))
    }

    @Test
    fun `neutral resolves to the derived Neutral palette`() {
        assertEquals(
            ResolvedTheme.derive(ThemePresets.NEUTRAL_PALETTE),
            resolveTheme(ModConfig(themeKey = "neutral")),
        )
    }

    @Test
    fun `a blank key resolves to DEFAULT`() {
        assertEquals(ResolvedTheme.DEFAULT, resolveTheme(ModConfig(themeKey = "")))
    }

    @Test
    fun `an unknown key resolves to DEFAULT`() {
        assertEquals(ResolvedTheme.DEFAULT, resolveTheme(ModConfig(themeKey = "bogus")))
    }

    @Test
    fun `displayName names each preset and falls back to Default`() {
        assertEquals("Default", ThemePresets.displayName("default"))
        assertEquals("Neutral", ThemePresets.displayName("neutral"))
        assertEquals("Default", ThemePresets.displayName("bogus"))
    }

    @Test
    fun `a dye key resolves to the derived dye palette`() {
        assertEquals(
            ResolvedTheme.derive(dyePalette(0xF6ADC6)),
            resolveTheme(ModConfig(themeKey = "dye:nadeshiko")),
        )
    }

    @Test
    fun `a dye key with an unknown or empty id resolves to DEFAULT`() {
        assertEquals(ResolvedTheme.DEFAULT, resolveTheme(ModConfig(themeKey = "dye:bogus")))
        assertEquals(ResolvedTheme.DEFAULT, resolveTheme(ModConfig(themeKey = "dye:")))
    }

    @Test
    fun `dyeKey and dyeIdOf round-trip a dye id`() {
        assertEquals("dye:jade", ThemePresets.dyeKey("jade"))
        assertEquals("jade", ThemePresets.dyeIdOf("dye:jade"))
    }

    @Test
    fun `dyeIdOf is null without the prefix or for an unknown dye`() {
        assertNull(ThemePresets.dyeIdOf("jade"))
        assertNull(ThemePresets.dyeIdOf("dye:bogus"))
        assertNull(ThemePresets.dyeIdOf("dye:"))
    }

    @Test
    fun `displayName names a dye theme after the dye`() {
        assertEquals("Nadeshiko dye", ThemePresets.displayName("dye:nadeshiko"))
        assertEquals("Wild Strawberry dye", ThemePresets.displayName("dye:wild_strawberry"))
        assertEquals("Default", ThemePresets.displayName("dye:bogus"))
    }

    @Test
    fun `paletteFor gives each theme's palette, or null for an unknown key`() {
        assertEquals(ThemePresets.DEFAULT_PALETTE, ThemePresets.paletteFor("default"))
        assertEquals(ThemePresets.NEUTRAL_PALETTE, ThemePresets.paletteFor("neutral"))
        assertEquals(dyePalette(0xF6ADC6), ThemePresets.paletteFor("dye:nadeshiko"))
        assertNull(ThemePresets.paletteFor("dye:bogus"))
        assertNull(ThemePresets.paletteFor("bogus"))
    }
}
