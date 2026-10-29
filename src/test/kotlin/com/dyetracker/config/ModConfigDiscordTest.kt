package com.dyetracker.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for the PBI 77 `discordEnabled` flag and `discordHuntDyeId`: they default to off
 * and blank, config files that predate them load that way, and they round-trip through JSON so the
 * choice survives a restart. Mirrors [ConfigManager]'s JSON settings; no Minecraft runtime.
 */
class ModConfigDiscordTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `default config has Discord presence off`() {
        assertFalse(ModConfig().discordEnabled, "presence is off by default")
    }

    @Test
    fun `config files predating the flag load with Discord presence off`() {
        val legacy = """{ "apiUrl": "https://example.test", "authToken": "", "bounceEnabled": true }"""
        val config = json.decodeFromString<ModConfig>(legacy)
        assertFalse(config.discordEnabled, "missing flag defaults to off, leaving existing configs unchanged")
    }

    @Test
    fun `Discord flag round-trips through JSON so it survives a restart`() {
        val saved = json.encodeToString(ModConfig(discordEnabled = true))
        assertTrue(saved.contains("discordEnabled"), "flag is written to disk")

        val reloaded = json.decodeFromString<ModConfig>(saved)
        assertTrue(reloaded.discordEnabled, "enabled state persists across a save/load cycle")
    }

    @Test
    fun `default config has no Discord hunt dye`() {
        assertEquals("", ModConfig().discordHuntDyeId)
    }

    @Test
    fun `config files predating the hunt dye load with none`() {
        val legacy = """{ "apiUrl": "https://example.test", "authToken": "", "discordEnabled": true }"""
        assertEquals("", json.decodeFromString<ModConfig>(legacy).discordHuntDyeId)
    }

    @Test
    fun `Discord hunt dye round-trips through JSON so it survives a restart`() {
        val saved = json.encodeToString(ModConfig(discordHuntDyeId = "nadeshiko"))
        assertEquals("nadeshiko", json.decodeFromString<ModConfig>(saved).discordHuntDyeId)
    }
}
