package com.dyetracker.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JSON coverage for the `POST /api/v1/rotation` models. Mirrors the `ApiClient` Json config
 * (`ignoreUnknownKeys = true`, `encodeDefaults = true`): the request must encode to exactly the
 * four keys the endpoint reads, and the response must decode both a full body and `{}`.
 */
class RotationPushModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `rotation push request encodes to exactly the four endpoint keys`() {
        val request = RotationPushRequest(
            listOf("bone", "celeste", "flame"),
            mapOf("bone" to 2, "celeste" to 2, "flame" to 3),
            493,
            1761700000000,
        )

        val encoded: JsonObject = json.parseToJsonElement(json.encodeToString(request)).jsonObject

        val expected = buildJsonObject {
            putJsonArray("dyeIds") {
                add("bone")
                add("celeste")
                add("flame")
            }
            putJsonObject("boosts") {
                put("bone", 2)
                put("celeste", 2)
                put("flame", 3)
            }
            put("skyblockYear", 493)
            put("capturedAt", 1761700000000L)
        }
        assertEquals(setOf("dyeIds", "boosts", "skyblockYear", "capturedAt"), encoded.keys)
        assertEquals(expected, encoded)
    }

    @Test
    fun `rotation push response decodes a stored body`() {
        val result = json.decodeFromString<RotationPushResponse>("""{"stored":true,"skyblockYear":493}""")

        assertTrue(result.stored)
        assertEquals(493, result.skyblockYear)
    }

    @Test
    fun `rotation push response decodes an empty body to defaults`() {
        val result = json.decodeFromString<RotationPushResponse>("{}")

        assertFalse(result.stored)
        assertEquals(0, result.skyblockYear)
    }
}
