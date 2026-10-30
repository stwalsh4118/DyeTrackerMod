package com.dyetracker.config

import com.dyetracker.ui.theme.ResolvedTheme
import com.dyetracker.ui.theme.resolveTheme
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for the PBI 81 `themeKey`: it defaults to `default`, config files that predate it
 * load that way, and any value round-trips through JSON unchanged so the pick survives a restart.
 * Mirrors [ConfigManager]'s JSON settings; no Minecraft runtime.
 */
class ModConfigThemeTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `default config uses the default theme`() {
        assertEquals("default", ModConfig().themeKey)
    }

    @Test
    fun `config files predating the key load the default theme`() {
        val legacy = """{ "apiUrl": "https://example.test", "authToken": "", "discordEnabled": true }"""
        assertEquals("default", json.decodeFromString<ModConfig>(legacy).themeKey)
    }

    @Test
    fun `theme key round-trips through JSON so it survives a restart`() {
        val saved = json.encodeToString(ModConfig(themeKey = "neutral"))
        assertTrue(saved.contains("themeKey"), "key is written to disk")
        assertEquals("neutral", json.decodeFromString<ModConfig>(saved).themeKey)
    }

    @Test
    fun `an unknown theme key round-trips unchanged and resolves to Default`() {
        val saved = json.encodeToString(ModConfig(themeKey = "dye:not-a-dye"))
        val reloaded = json.decodeFromString<ModConfig>(saved)
        assertEquals("dye:not-a-dye", reloaded.themeKey)
        assertEquals(ResolvedTheme.DEFAULT, resolveTheme(reloaded))
    }
}
