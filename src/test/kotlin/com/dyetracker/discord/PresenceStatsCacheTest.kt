package com.dyetracker.discord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [PresenceStatsCache]: when the worker re-fetches the card's stats, and what it keeps when a fetch fails. */
class PresenceStatsCacheTest {

    private var now = 0L
    private val loads = mutableListOf<String>()
    private var next: PresenceStats? = stats("one")
    private val cache = PresenceStatsCache(load = { username -> loads += username; next }, clock = { now })

    private fun stats(profileId: String) = PresenceStats("uuid", profileId, "Banana", emptyList(), 1, 42, 1)

    @Test
    fun `the first call loads`() {
        assertEquals(stats("one"), cache.get(STEVE))
        assertEquals(listOf(STEVE), loads)
    }

    @Test
    fun `within the refresh interval the stats are reused`() {
        cache.get(STEVE)
        now = STATS_REFRESH_MS - 1
        next = stats("two")

        assertEquals(stats("one"), cache.get(STEVE))
        assertEquals(1, loads.size)
    }

    @Test
    fun `after the refresh interval the stats are reloaded`() {
        cache.get(STEVE)
        now = STATS_REFRESH_MS
        next = stats("two")

        assertEquals(stats("two"), cache.get(STEVE))
        assertEquals(2, loads.size)
    }

    @Test
    fun `a failed reload keeps the last good stats`() {
        cache.get(STEVE)
        now = STATS_REFRESH_MS
        next = null

        assertEquals(stats("one"), cache.get(STEVE))
        assertEquals(2, loads.size)
    }

    @Test
    fun `a failed first load is retried after the interval, not every tick`() {
        next = null
        assertNull(cache.get(STEVE))
        now = STATS_REFRESH_MS - 1
        assertNull(cache.get(STEVE))
        assertEquals(1, loads.size)

        now = STATS_REFRESH_MS
        next = stats("one")
        assertEquals(stats("one"), cache.get(STEVE))
        assertEquals(2, loads.size)
    }

    @Test
    fun `a new username reloads at once and never shows the old player's stats`() {
        cache.get(STEVE)
        now = 1_000
        next = null

        assertNull(cache.get(ALEX))
        assertEquals(listOf(STEVE, ALEX), loads)
    }

    private companion object {
        const val STEVE = "Steve"
        const val ALEX = "Alex"
    }
}
