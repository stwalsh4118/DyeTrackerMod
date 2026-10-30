package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit coverage for [themeButtons] (task 81-3): one button per preset then the dye button, the
 * active theme's marked and selected, an unknown key falling back to Default. Task 81-4 adds the
 * Custom button, there only while a Custom slot exists. Task 81-5 adds [selectedThemeKey]: which
 * theme the buttons mark for a config. Task 81-6 adds the Midnight, Forest, Sunset and Ice buttons.
 */
class ThemeButtonsTest {

    @Test
    fun `Default active marks the Default button`() {
        val buttons = themeButtons("default", false)

        assertEquals(listOf("✔ Default", "Neutral", "Midnight", "Forest", "Sunset", "Ice", "Dye ▸"), buttons.map { it.label })
        assertEquals(listOf("default"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `Neutral active marks the Neutral button`() {
        val buttons = themeButtons("neutral", false)

        assertEquals(listOf("Default", "✔ Neutral", "Midnight", "Forest", "Sunset", "Ice", "Dye ▸"), buttons.map { it.label })
        assertEquals(listOf("neutral"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `Sunset active shows the six presets then the dye button, Sunset marked`() {
        val buttons = themeButtons("sunset", false)

        assertEquals(
            listOf("Default", "Neutral", "Midnight", "Forest", "✔ Sunset", "Ice", "Dye ▸"),
            buttons.map { it.label },
        )
        assertEquals(listOf("sunset"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `a dye theme active marks the dye button with the dye's name`() {
        val buttons = themeButtons("dye:jade", false)

        assertEquals(ThemeButton(DYE_BUTTON_KEY, "✔ Jade ▸", selected = true), buttons.last())
        assertTrue(buttons.dropLast(1).none { it.selected }, "buttons: $buttons")
    }

    @Test
    fun `an unknown key marks Default, as it resolves to Default`() {
        val buttons = themeButtons("bogus", false)

        assertEquals(listOf("✔ Default", "Neutral", "Midnight", "Forest", "Sunset", "Ice", "Dye ▸"), buttons.map { it.label })
        assertEquals(listOf("default"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `a dye key with a dye that has no color marks Default`() {
        val buttons = themeButtons("dye:no_such_dye", false)

        assertEquals(listOf("default"), buttons.filter { it.selected }.map { it.key })
        assertEquals("Dye ▸", buttons.last().label)
    }

    @Test
    fun `no Custom button without a Custom slot`() {
        val buttons = themeButtons("default", false)

        assertFalse(buttons.any { it.key == "custom" }, "buttons: $buttons")
    }

    @Test
    fun `Custom active shows the Custom button last, marked and selected`() {
        val buttons = themeButtons("custom", true)

        assertEquals(listOf("Default", "Neutral", "Midnight", "Forest", "Sunset", "Ice", "Dye ▸", "✔ Custom"), buttons.map { it.label })
        assertEquals(listOf("custom"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `a Custom slot on another theme shows an unmarked Custom button`() {
        val buttons = themeButtons("neutral", true)

        assertEquals(ThemeButton("custom", "Custom", selected = false), buttons.last())
        assertEquals(listOf("neutral"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `the selected key is the theme key when it is not custom`() {
        assertEquals("neutral", selectedThemeKey(ModConfig(themeKey = "neutral")))
        assertEquals("dye:jade", selectedThemeKey(ModConfig(themeKey = "dye:jade")))
    }

    @Test
    fun `the selected key is custom while Custom is active with a slot`() {
        val config = ModConfig(
            themeKey = "custom",
            themeCustomBase = "neutral",
            themeCustomOverrides = mapOf("accentPrimary" to "FF7A59"),
        )
        assertEquals("custom", selectedThemeKey(config))
    }

    @Test
    fun `custom with no valid slot selects the slot's base, the theme on screen`() {
        val config = ModConfig(
            themeKey = "custom",
            themeCustomBase = "dye:jade",
            themeCustomOverrides = mapOf("accentPrimary" to "not-hex"),
        )
        assertEquals("dye:jade", selectedThemeKey(config))
    }

    @Test
    fun `custom with no slot on an unknown base selects Default`() {
        assertEquals("default", selectedThemeKey(ModConfig(themeKey = "custom", themeCustomBase = "bogus")))
    }
}
