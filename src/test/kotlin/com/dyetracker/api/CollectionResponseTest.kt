package com.dyetracker.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JSON-decoding coverage for the collection endpoint's response (task 68-2). Mirrors the
 * `ApiClient` Json config (`ignoreUnknownKeys = true`), so the entries' `syncedAt` and
 * `metadata` are dropped.
 */
class CollectionResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a collection with an owned dye`() {
        val body = """
            {"uuid":"e92…","username":"_Writhe","dyes":[{"dyeId":"matcha","profileId":"p1","count":2,"obtainedAt":1761000000000,"syncedAt":"2026-10-27T00:00:00.000Z","metadata":null}]}
        """.trimIndent()

        val result = json.decodeFromString<CollectionResponse>(body)

        assertEquals("e92…", result.uuid)
        assertEquals("_Writhe", result.username)
        assertEquals(
            listOf(CollectionEntry(dyeId = "matcha", profileId = "p1", count = 2, obtainedAt = 1761000000000L)),
            result.dyes,
        )
    }

    @Test
    fun `decodes an empty collection`() {
        val result = json.decodeFromString<CollectionResponse>("""{"uuid":"x","username":"y","dyes":[]}""")

        assertEquals("x", result.uuid)
        assertEquals("y", result.username)
        assertTrue(result.dyes.isEmpty())
    }

    @Test
    fun `an entry with only a dyeId takes the defaults`() {
        val result = json.decodeFromString<CollectionResponse>("""{"dyes":[{"dyeId":"celeste","obtainedAt":null}]}""")

        assertEquals(listOf(CollectionEntry(dyeId = "celeste", profileId = "", count = 1, obtainedAt = null)), result.dyes)
    }
}
