package com.dyetracker.config

import com.dyetracker.menu.shouldShowMenuHint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for the PBI 67 `menuHintShown` config flag: it defaults to false for existing
 * config files that predate it, it round-trips through JSON so the one-time hint stays shown
 * after a restart, and [shouldShowMenuHint] follows it. Mirrors [ConfigManager]'s JSON settings;
 * no Minecraft runtime.
 */
class ModConfigMenuHintTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `default config has not shown the menu hint`() {
        assertFalse(ModConfig().menuHintShown, "the hint is unshown by default")
    }

    @Test
    fun `config files predating the flag load with the hint unshown`() {
        // A minimal pre-PBI-67 config: no menuHintShown key present.
        val legacy = """{ "apiUrl": "https://example.test", "authToken": "", "bounceEnabled": true }"""
        val config = json.decodeFromString<ModConfig>(legacy)
        assertFalse(config.menuHintShown, "missing flag defaults to unshown, so existing players get the hint once")
    }

    @Test
    fun `menu hint flag round-trips through JSON so it survives a restart`() {
        val saved = json.encodeToString(ModConfig(menuHintShown = true))
        assertTrue(saved.contains("menuHintShown"), "flag is written to disk")

        val reloaded = json.decodeFromString<ModConfig>(saved)
        assertTrue(reloaded.menuHintShown, "shown state persists across a save/load cycle")
    }

    @Test
    fun `shouldShowMenuHint is true until the hint has been shown`() {
        assertTrue(shouldShowMenuHint(ModConfig(menuHintShown = false)))
    }

    @Test
    fun `shouldShowMenuHint is false once the hint has been shown`() {
        assertFalse(shouldShowMenuHint(ModConfig(menuHintShown = true)))
    }
}
