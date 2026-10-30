package com.dyetracker.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit coverage for [themeButtons] (task 81-3): one button per preset then the dye button, the
 * active theme's marked and selected, an unknown key falling back to Default.
 */
class ThemeButtonsTest {

    @Test
    fun `Default active marks the Default button`() {
        val buttons = themeButtons("default")

        assertEquals(listOf("✔ Default", "Neutral", "Dye ▸"), buttons.map { it.label })
        assertEquals(listOf("default"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `Neutral active marks the Neutral button`() {
        val buttons = themeButtons("neutral")

        assertEquals(listOf("Default", "✔ Neutral", "Dye ▸"), buttons.map { it.label })
        assertEquals(listOf("neutral"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `a dye theme active marks the dye button with the dye's name`() {
        val buttons = themeButtons("dye:jade")

        assertEquals(ThemeButton(DYE_BUTTON_KEY, "✔ Jade ▸", selected = true), buttons.last())
        assertTrue(buttons.dropLast(1).none { it.selected }, "buttons: $buttons")
    }

    @Test
    fun `an unknown key marks Default, as it resolves to Default`() {
        val buttons = themeButtons("bogus")

        assertEquals(listOf("✔ Default", "Neutral", "Dye ▸"), buttons.map { it.label })
        assertEquals(listOf("default"), buttons.filter { it.selected }.map { it.key })
    }

    @Test
    fun `a dye key with a dye that has no color marks Default`() {
        val buttons = themeButtons("dye:no_such_dye")

        assertEquals(listOf("default"), buttons.filter { it.selected }.map { it.key })
        assertEquals("Dye ▸", buttons.last().label)
    }
}
