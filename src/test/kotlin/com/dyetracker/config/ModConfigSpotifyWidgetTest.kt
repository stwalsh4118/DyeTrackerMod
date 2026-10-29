package com.dyetracker.config

import com.dyetracker.spotify.SpotifyConstants
import com.dyetracker.spotify.SpotifyWidgetConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure-JVM serialization tests for the PBI 76 `spotifyWidget` placement: config files that predate
 * it load with no placement (back-compat), and a placement round-trips through JSON so it survives
 * a restart. Mirrors [ConfigManager]'s JSON settings; no Minecraft runtime.
 */
class ModConfigSpotifyWidgetTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `config files predating the Spotify widget load with no placement`() {
        val legacy = """{ "apiUrl": "https://example.test", "authToken": "", "bounceEnabled": true }"""
        val config = json.decodeFromString<ModConfig>(legacy)
        assertNull(config.spotifyWidget, "missing field defaults to null, leaving existing configs unchanged")
    }

    @Test
    fun `a Spotify widget placement round-trips through JSON`() {
        val placement = SpotifyWidgetConfig(x = 0.25f, y = 0.75f, scale = 1.5f, visible = false)
        val reloaded = json.decodeFromString<ModConfig>(json.encodeToString(ModConfig(spotifyWidget = placement)))
        assertEquals(placement, reloaded.spotifyWidget)
    }

    @Test
    fun `the default placement is the visible singleton at the top right`() {
        assertEquals(
            SpotifyWidgetConfig(id = SpotifyConstants.WIDGET_ID, x = 0.85f, y = 0.1f, scale = 1.0f, visible = true),
            SpotifyWidgetConfig(),
        )
    }
}
