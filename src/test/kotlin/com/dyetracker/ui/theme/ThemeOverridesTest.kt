package com.dyetracker.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for the Custom slot's hex helpers (task 81-4): [parseHexRgb] takes six hex digits
 * with an optional `#` and surrounding spaces and nothing else; [formatHexRgb] writes six uppercase
 * digits. Also pins [ThemeOverrides.KEYS] and [ThemeOverrides.LABELS].
 */
class ThemeOverridesTest {

    @Test
    fun `parseHexRgb accepts six hex digits with an optional hash and spaces`() {
        for (input in listOf("e225f4", "#E225F4", " #e225f4 ")) {
            assertEquals(0xE225F4, parseHexRgb(input), "input: '$input'")
        }
    }

    @Test
    fun `parseHexRgb rejects anything that is not exactly six hex digits`() {
        for (input in listOf("", "#", "e225f", "e225f4a", "zz25f4", "80e225f4")) {
            assertNull(parseHexRgb(input), "input: '$input'")
        }
    }

    @Test
    fun `parseHexRgb drops only one leading hash`() {
        assertNull(parseHexRgb("##e225f4"))
    }

    @Test
    fun `formatHexRgb writes six uppercase digits without a hash`() {
        assertEquals("0A0B0C", formatHexRgb(0x0a0b0c))
    }

    @Test
    fun `formatHexRgb and parseHexRgb round-trip`() {
        assertEquals(0xE225F4, parseHexRgb(formatHexRgb(0xE225F4)))
    }

    @Test
    fun `the override keys are the six base colors, in order, each labeled`() {
        assertEquals(
            listOf("accentPrimary", "accentSecondary", "panelBackground", "panelBorder", "textPrimary", "textSecondary"),
            ThemeOverrides.KEYS,
        )
        assertEquals(
            listOf("Accent", "Accent 2", "Panel", "Border", "Text", "Text 2"),
            ThemeOverrides.KEYS.map { ThemeOverrides.LABELS.getValue(it) },
        )
    }
}
