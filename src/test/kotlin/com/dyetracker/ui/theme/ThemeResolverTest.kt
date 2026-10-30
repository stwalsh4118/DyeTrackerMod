package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for [resolveTheme] and [ThemePresets.displayName] (task 81-1): each preset key
 * resolves to its theme, and blank or unknown keys fall back to Default.
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
    fun `preset keys are default then neutral`() {
        assertEquals(listOf("default", "neutral"), ThemePresets.PRESET_KEYS)
    }

    @Test
    fun `displayName names each preset and falls back to Default`() {
        assertEquals("Default", ThemePresets.displayName("default"))
        assertEquals("Neutral", ThemePresets.displayName("neutral"))
        assertEquals("Default", ThemePresets.displayName("bogus"))
    }
}
