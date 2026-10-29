package com.dyetracker.discord

import com.dyetracker.config.ModConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The PBI 77 `discordHintShown` config flag and [shouldShowDiscordHint]: the hint shows until the
 * flag is set, config files predating the flag load with it unset, and it survives a save/load.
 * Mirrors [com.dyetracker.config.ConfigManager]'s JSON settings; no Minecraft runtime.
 */
class DiscordHintTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `the hint shows by default`() {
        assertFalse(ModConfig().discordHintShown)
        assertTrue(shouldShowDiscordHint(ModConfig()))
    }

    @Test
    fun `the hint does not show once it was shown`() {
        assertFalse(shouldShowDiscordHint(ModConfig(discordHintShown = true)))
    }

    @Test
    fun `config files predating the flag load with the hint unshown`() {
        val legacy = """{ "apiUrl": "https://example.test", "discordEnabled": true, "discordHuntDyeId": "nadeshiko" }"""
        val config = json.decodeFromString<ModConfig>(legacy)

        assertFalse(config.discordHintShown)
        assertTrue(shouldShowDiscordHint(config))
    }

    @Test
    fun `the shown flag round-trips through JSON`() {
        val saved = json.encodeToString(ModConfig(discordHintShown = true))
        assertTrue(saved.contains("discordHintShown"), "the flag is written to disk")

        assertTrue(json.decodeFromString<ModConfig>(saved).discordHintShown)
    }
}
