package com.dyetracker.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit coverage for the Collection and Achievements tabs' session cache (68-8). */
class MenuTabCacheTest {

    @Test
    fun `get returns what put stored, with its fetch time`() {
        val cache = SessionCache<String>()
        cache.put("Steve", P1, "mango-owned", nowMs = 1_000L)

        assertEquals(Cached("mango-owned", fetchedAtMs = 1_000L), cache.get("Steve", P1))
    }

    @Test
    fun `usernames are case-insensitive`() {
        val cache = SessionCache<String>()
        cache.put("sTeVe", P1, "mango-owned", nowMs = 1_000L)

        assertEquals("mango-owned", cache.get("STEVE", P1)?.value)
        assertEquals("mango-owned", cache.get("steve", P1)?.value)
    }

    @Test
    fun `two profiles of one player are kept apart`() {
        val cache = SessionCache<String>()
        cache.put("Steve", P1, "mango-owned", nowMs = 1_000L)
        cache.put("Steve", P2, "banana-owned", nowMs = 2_000L)

        assertEquals(Cached("mango-owned", fetchedAtMs = 1_000L), cache.get("Steve", P1))
        assertEquals(Cached("banana-owned", fetchedAtMs = 2_000L), cache.get("Steve", P2))
    }

    @Test
    fun `two players on the same profile id are kept apart`() {
        val cache = SessionCache<String>()
        cache.put("Steve", P1, "steve-owned", nowMs = 1_000L)

        assertNull(cache.get("Alex", P1))
    }

    @Test
    fun `put again for the same player and profile replaces the entry`() {
        val cache = SessionCache<String>()
        cache.put("Steve", P1, "old", nowMs = 1_000L)
        cache.put("STEVE", P1, "new", nowMs = 5_000L)

        assertEquals(Cached("new", fetchedAtMs = 5_000L), cache.get("Steve", P1))
    }

    @Test
    fun `get is null for an unknown player or profile`() {
        val cache = SessionCache<String>()
        assertNull(cache.get("Steve", P1))

        cache.put("Steve", P1, "mango-owned", nowMs = 1_000L)
        assertNull(cache.get("Steve", P2))
        assertNull(cache.get("Alex", P1))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = SessionCache<String>()
        cache.put("Steve", P1, "mango-owned", nowMs = 1_000L)
        cache.put("Steve", P2, "banana-owned", nowMs = 2_000L)

        cache.clear()

        assertNull(cache.get("Steve", P1))
        assertNull(cache.get("Steve", P2))
    }

    @Test
    fun `two caches keep their values apart`() {
        val collections = SessionCache<String>()
        val achievements = SessionCache<String>()
        collections.put("Steve", P1, "owned", nowMs = 1_000L)
        achievements.put("Steve", P1, "unlocked", nowMs = 2_000L)
        achievements.put("Steve", P2, "banana-unlocked", nowMs = 3_000L)

        assertEquals(Cached("owned", fetchedAtMs = 1_000L), collections.get("Steve", P1))
        assertEquals(Cached("unlocked", fetchedAtMs = 2_000L), achievements.get("Steve", P1))
        assertNull(collections.get("Steve", P2))

        collections.clear()
        assertEquals("unlocked", achievements.get("Steve", P1)?.value)
    }

    private companion object {
        const val P1 = "p-mango"
        const val P2 = "p-banana"
    }
}
