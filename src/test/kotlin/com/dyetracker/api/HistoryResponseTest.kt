package com.dyetracker.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JSON-decoding coverage for the public history route's response (75-1) and its per-dye series
 * route's (75-2). Mirrors the `ApiClient` Json config (`ignoreUnknownKeys = true`), so the
 * response's `counters` are dropped.
 */
class HistoryResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the sample with a null chance and drops the counters`() {
        val body = """
            {"uuid":"u1","profileId":"p1","period":"24h","linked":true,
             "from":1761700000000,"to":1761786000000,"snapshotCount":14,
             "dyes":[{"dyeId":"bone","name":"Bone Dye","color":"#e3dac9","work":"1,000 skeletons","chance":0.0027},
                     {"dyeId":"livid","name":"Livid Dye","color":"#c0c0c0","work":"3 completions","chance":null}],
             "counters":[{"key":"slayer.zombie.xp","gained":1500}]}
        """.trimIndent()

        val result = json.decodeFromString<HistoryResponse>(body)

        assertEquals("u1", result.uuid)
        assertEquals("p1", result.profileId)
        assertEquals("24h", result.period)
        assertTrue(result.linked)
        assertEquals(1_761_700_000_000L, result.from)
        assertEquals(1_761_786_000_000L, result.to)
        assertEquals(14, result.snapshotCount)
        assertEquals(
            listOf(
                HistoryDyeGain("bone", "Bone Dye", "#e3dac9", "1,000 skeletons", 0.0027),
                HistoryDyeGain("livid", "Livid Dye", "#c0c0c0", "3 completions", null),
            ),
            result.dyes,
        )
    }

    @Test
    fun `decodes null from and to`() {
        val body = """
            {"uuid":"u1","profileId":"p1","period":"7d","linked":true,"from":null,"to":null,"snapshotCount":0,"dyes":[],"counters":[]}
        """.trimIndent()

        val result = json.decodeFromString<HistoryResponse>(body)

        assertNull(result.from)
        assertNull(result.to)
        assertEquals(0, result.snapshotCount)
        assertEquals(emptyList(), result.dyes)
    }

    @Test
    fun `missing keys fall back to their defaults`() {
        val result = json.decodeFromString<HistoryResponse>("""{"dyes":[{}]}""")

        assertEquals("", result.uuid)
        assertEquals("", result.profileId)
        assertEquals("", result.period)
        assertFalse(result.linked)
        assertNull(result.from)
        assertNull(result.to)
        assertEquals(0, result.snapshotCount)
        assertEquals(HistoryDyeGain(dyeId = "", name = "", color = "", work = "", chance = null), result.dyes.single())
    }

    @Test
    fun `an empty object decodes to an unlinked response without dyes`() {
        assertEquals(HistoryResponse(), json.decodeFromString<HistoryResponse>("{}"))
    }

    @Test
    fun `decodes the dye series sample with a null work bucket`() {
        val body = """
            {"uuid":"u1","profileId":"p1","period":"24h","dyeId":"bone","linked":true,
             "buckets":[{"start":1761700000000,"end":1761703600000,"work":120,"cumulativeChance":0.0004},
                        {"start":1761703600000,"end":1761707200000,"work":null,"cumulativeChance":null}]}
        """.trimIndent()

        val result = json.decodeFromString<HistoryDyeSeriesResponse>(body)

        assertEquals("u1", result.uuid)
        assertEquals("p1", result.profileId)
        assertEquals("24h", result.period)
        assertEquals("bone", result.dyeId)
        assertTrue(result.linked)
        assertEquals(
            listOf(
                HistoryBucket(1_761_700_000_000L, 1_761_703_600_000L, 120.0, 0.0004),
                HistoryBucket(1_761_703_600_000L, 1_761_707_200_000L, null, null),
            ),
            result.buckets,
        )
    }

    @Test
    fun `an empty object decodes to an unlinked series without buckets`() {
        val result = json.decodeFromString<HistoryDyeSeriesResponse>("{}")

        assertEquals(HistoryDyeSeriesResponse(), result)
        assertFalse(result.linked)
        assertEquals(emptyList(), result.buckets)
    }

    @Test
    fun `a bucket missing its keys falls back to its defaults`() {
        val result = json.decodeFromString<HistoryDyeSeriesResponse>("""{"buckets":[{}]}""")

        assertEquals(HistoryBucket(start = 0, end = 0, work = null, cumulativeChance = null), result.buckets.single())
    }
}
