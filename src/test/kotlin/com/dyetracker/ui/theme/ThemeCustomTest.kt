package com.dyetracker.ui.theme

import com.dyetracker.config.ModConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit coverage for the Custom slot (task 81-4): [resolveTheme] for `custom` replaces only the
 * overridden colors' tokens (so the pinned Default keeps the rest), [customBaseKey] and
 * [validOverrides] drop what they don't know, [startCustomEdit] / [resetCustom] keep one slot, and
 * [overrideBoxText] fills the Theme screen's boxes from the active theme.
 */
class ThemeCustomTest {

    private fun custom(base: String, overrides: Map<String, String>) = ModConfig(
        themeKey = ThemePresets.CUSTOM,
        themeCustomBase = base,
        themeCustomOverrides = overrides,
    )

    @Test
    fun `custom is a theme key named Custom but not a preset`() {
        assertEquals("custom", ThemePresets.CUSTOM)
        assertEquals("Custom", ThemePresets.displayName("custom"))
        assertFalse("custom" in ThemePresets.PRESET_KEYS)
    }

    @Test
    fun `an accent override on Default replaces the accent tokens and keeps Default's pins`() {
        val theme = resolveTheme(custom("default", mapOf("accentPrimary" to "123456")))

        assertEquals(0xFF123456.toInt(), theme.accentPrimary)
        assertEquals(0x1A123456, theme.badgeModBg)
        assertEquals(0xFF123456.toInt(), theme.progressFill)
        assertEquals(0xF0000000.toInt(), theme.menuBackground)
        assertEquals(0xFF94A3B8.toInt(), theme.textSubtle)
    }

    @Test
    fun `a panel override on Default replaces the panel tokens`() {
        val theme = resolveTheme(custom("default", mapOf("panelBackground" to "202020")))

        assertEquals(0xF0101010.toInt(), theme.menuBackground)
        assertEquals(0xFF151515.toInt(), theme.menuSurface)
        assertEquals(0xFF202020.toInt(), theme.cardBackground)
        assertEquals(0xC8202020.toInt(), theme.panelBackground)
        assertEquals(0xFF7F7F7F.toInt(), theme.textMuted)
        assertEquals(ResolvedTheme.DEFAULT.accentPrimary, theme.accentPrimary)
    }

    @Test
    fun `a border override on Default is opaque and replaces both borders`() {
        val theme = resolveTheme(custom("default", mapOf("panelBorder" to "AA0000")))

        assertEquals(0xFFAA0000.toInt(), theme.panelBorder)
        assertEquals(0x4DAA0000, theme.cardBorder)
        assertEquals(ResolvedTheme.DEFAULT.copy(panelBorder = theme.panelBorder, cardBorder = theme.cardBorder), theme)
    }

    @Test
    fun `an override on a derived base equals deriving the edited palette`() {
        assertEquals(
            ResolvedTheme.derive(ThemePresets.NEUTRAL_PALETTE.copy(textPrimary = 0xFFEEDD)),
            resolveTheme(custom("neutral", mapOf("textPrimary" to "FFEEDD"))),
        )
    }

    @Test
    fun `each override on a derived base equals deriving the palette with that color changed`() {
        val rgb = 0x123456
        for (key in ThemeOverrides.KEYS) {
            val opaque = if (key == ThemeOverrides.PANEL_BORDER) 0xFF000000.toInt() or rgb else rgb
            val palette = when (key) {
                ThemeOverrides.ACCENT_PRIMARY -> ThemePresets.NEUTRAL_PALETTE.copy(accentPrimary = opaque)
                ThemeOverrides.ACCENT_SECONDARY -> ThemePresets.NEUTRAL_PALETTE.copy(accentSecondary = opaque)
                ThemeOverrides.PANEL_BACKGROUND -> ThemePresets.NEUTRAL_PALETTE.copy(panelBackground = opaque)
                ThemeOverrides.PANEL_BORDER -> ThemePresets.NEUTRAL_PALETTE.copy(panelBorder = opaque)
                ThemeOverrides.TEXT_PRIMARY -> ThemePresets.NEUTRAL_PALETTE.copy(textPrimary = opaque)
                else -> ThemePresets.NEUTRAL_PALETTE.copy(textSecondary = opaque)
            }
            assertEquals(
                ResolvedTheme.derive(palette),
                resolveTheme(custom("neutral", mapOf(key to formatHexRgb(rgb)))),
                "key: $key",
            )
        }
    }

    @Test
    fun `a second accent override on Default replaces only the second accent tokens`() {
        val theme = resolveTheme(custom("default", mapOf("accentSecondary" to "123456")))

        assertEquals(
            ResolvedTheme.DEFAULT.copy(accentSecondary = 0xFF123456.toInt(), toggleOnBg = 0x1A123456, toggleOnBorder = 0x33123456),
            theme,
        )
    }

    @Test
    fun `a secondary text override on Default replaces only the secondary text tokens`() {
        val theme = resolveTheme(custom("default", mapOf("textSecondary" to "808080")))
        val edited = ResolvedTheme.derive(ThemePresets.DEFAULT_PALETTE.copy(textSecondary = 0x808080))

        assertEquals(
            ResolvedTheme.DEFAULT.copy(
                textSecondary = 0xFF808080.toInt(),
                statusOk = 0xFF808080.toInt(),
                textSubtle = 0xFF808080.toInt(),
                textMuted = edited.textMuted,
                badgeMutedText = edited.textMuted,
            ),
            theme,
        )
    }

    @Test
    fun `a primary text override on Default replaces only the primary text tokens`() {
        val theme = resolveTheme(custom("default", mapOf("textPrimary" to "FFEEDD")))
        val text = 0xFFFFEEDD.toInt()

        assertEquals(ResolvedTheme.DEFAULT.copy(textPrimary = text, focusBorder = text, pillSelectedBg = text), theme)
    }

    @Test
    fun `custom with no valid overrides equals its base theme`() {
        val overrides = mapOf("accentPrimary" to "nope", "bogus" to "FFFFFF")

        assertEquals(ResolvedTheme.DEFAULT, resolveTheme(custom("default", overrides)))
        assertEquals(resolveTheme(ModConfig(themeKey = "dye:jade")), resolveTheme(custom("dye:jade", overrides)))
    }

    @Test
    fun `a custom, blank or unknown base is Default`() {
        for (base in listOf("custom", "bogus", "")) {
            val config = custom(base, mapOf("accentPrimary" to "123456"))
            assertEquals("default", customBaseKey(config), "base: '$base'")
            assertEquals(ThemePresets.DEFAULT_PALETTE.copy(accentPrimary = 0x123456), effectivePalette(config))
        }
    }

    @Test
    fun `validOverrides keeps known keys with valid hex only`() {
        val config = custom("default", mapOf("accentPrimary" to "#123456", "textPrimary" to "zz", "bogus" to "FFFFFF"))

        assertEquals(mapOf("accentPrimary" to 0x123456), validOverrides(config))
    }

    @Test
    fun `effectivePalette applies each override over the base palette`() {
        val config = custom("neutral", mapOf("panelBorder" to "112233", "accentSecondary" to "445566"))

        assertEquals(
            ThemePresets.NEUTRAL_PALETTE.copy(panelBorder = 0xFF112233.toInt(), accentSecondary = 0x445566),
            effectivePalette(config),
        )
    }

    @Test
    fun `editing from a preset starts a new slot on that preset`() {
        val old = ModConfig(themeKey = "neutral", themeCustomBase = "dye:jade", themeCustomOverrides = mapOf("textPrimary" to "000000"))

        val edited = startCustomEdit(old, "accentPrimary", 0xFF7A59)

        assertEquals("custom", edited.themeKey)
        assertEquals("neutral", edited.themeCustomBase)
        assertEquals(mapOf("accentPrimary" to "FF7A59"), edited.themeCustomOverrides)
    }

    @Test
    fun `editing from custom adds the override and keeps the base`() {
        val edited = startCustomEdit(custom("neutral", mapOf("textPrimary" to "FFEEDD")), "accentPrimary", 0x0a0b0c)

        assertEquals("custom", edited.themeKey)
        assertEquals("neutral", edited.themeCustomBase)
        assertEquals(mapOf("textPrimary" to "FFEEDD", "accentPrimary" to "0A0B0C"), edited.themeCustomOverrides)
    }

    @Test
    fun `editing from a dye theme bases the slot on that dye`() {
        val edited = startCustomEdit(ModConfig(themeKey = "dye:jade"), "accentPrimary", 0x123456)

        assertEquals("dye:jade", edited.themeCustomBase)
        assertEquals("custom", edited.themeKey)
    }

    @Test
    fun `editing from an unknown key bases the slot on Default`() {
        assertEquals("default", startCustomEdit(ModConfig(themeKey = "bogus"), "accentPrimary", 0x123456).themeCustomBase)
    }

    @Test
    fun `resetCustom clears the overrides and switches to the base`() {
        val reset = resetCustom(custom("neutral", mapOf("accentPrimary" to "123456")))

        assertEquals("neutral", reset.themeKey)
        assertEquals(emptyMap(), reset.themeCustomOverrides)
    }

    @Test
    fun `resetCustom with an unknown base switches to Default`() {
        assertEquals("default", resetCustom(custom("bogus", mapOf("accentPrimary" to "123456"))).themeKey)
    }

    @Test
    fun `the boxes show the active preset's colors`() {
        assertEquals(
            mapOf(
                "accentPrimary" to "E225F4",
                "accentSecondary" to "00F0FF",
                "panelBackground" to "101010",
                "panelBorder" to "555555",
                "textPrimary" to "FFFFFF",
                "textSecondary" to "BFBFBF",
            ),
            overrideBoxText(ModConfig(themeKey = "default")),
        )
    }

    @Test
    fun `an unknown key shows Default's colors in the boxes`() {
        assertEquals(overrideBoxText(ModConfig(themeKey = "default")), overrideBoxText(ModConfig(themeKey = "bogus")))
    }

    @Test
    fun `the boxes show a dye theme's colors`() {
        val jade = ThemePresets.paletteFor("dye:jade")!!

        assertEquals(formatHexRgb(jade.accentPrimary), overrideBoxText(ModConfig(themeKey = "dye:jade")).getValue("accentPrimary"))
    }

    @Test
    fun `the boxes show the Custom slot's colors while Custom is active`() {
        val text = overrideBoxText(custom("neutral", mapOf("accentPrimary" to "ff7a59")))

        assertEquals("FF7A59", text.getValue("accentPrimary"))
        assertEquals("A6A6A6", text.getValue("accentSecondary"))
    }

    @Test
    fun `the boxes show the preset, not the saved slot, while a preset is active`() {
        val config = ModConfig(themeKey = "neutral", themeCustomBase = "neutral", themeCustomOverrides = mapOf("accentPrimary" to "FF7A59"))

        assertEquals("E0E0E0", overrideBoxText(config).getValue("accentPrimary"))
    }
}
