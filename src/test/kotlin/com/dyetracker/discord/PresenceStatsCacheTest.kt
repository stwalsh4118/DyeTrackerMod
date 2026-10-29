package com.dyetracker.discord

import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PresenceStatsCache]: when the worker re-fetches the card's stats, what it keeps when a fetch
 * fails, and that it never waits for a fetch. Loads either finish at once ([cache]) or stay pending
 * until a test completes them ([manual]).
 */
class PresenceStatsCacheTest {

    private var now = 0L
    private val loads = mutableListOf<String>()
    private var next: PresenceStats? = stats("one")
    private val load = { username: String -> loads += username; next }

    /** Runs each load at once. */
    private val cache = PresenceStatsCache(launch = { block -> CompletableDeferred(block()) }, load = load, clock = { now })

    /** Each launched load, left pending until the test completes it. */
    private val launched = mutableListOf<CompletableDeferred<PresenceStats?>>()
    private val manual = PresenceStatsCache(
        launch = { _ -> CompletableDeferred<PresenceStats?>().also { launched += it } },
        load = load,
        clock = { now },
    )

    private fun stats(profileId: String) = PresenceStats("uuid", profileId, "Banana", emptyList(), 1, 42, 1)

    @Test
    fun `the first call loads`() {
        assertEquals(stats("one"), cache.get(STEVE))
        assertEquals(listOf(STEVE), loads)
        assertFalse(cache.firstLoadPending)
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

    @Test
    fun `get returns at once while a load is pending`() {
        assertNull(manual.get(STEVE))

        assertEquals(1, launched.size)
        assertFalse(launched.single().isCompleted, "nothing waited for the load")
        assertTrue(manual.firstLoadPending)
    }

    @Test
    fun `the next get picks up a finished load`() {
        manual.get(STEVE)
        launched.single().complete(stats("one"))

        assertEquals(stats("one"), manual.get(STEVE))
        assertFalse(manual.firstLoadPending)
    }

    @Test
    fun `a pending reload keeps showing the last good stats`() {
        manual.get(STEVE)
        launched.single().complete(stats("one"))
        manual.get(STEVE)
        now = STATS_REFRESH_MS

        assertEquals(stats("one"), manual.get(STEVE))
        assertEquals(2, launched.size)
        assertFalse(manual.firstLoadPending, "only the first load holds the card back")
    }

    @Test
    fun `no second load starts while one is pending`() {
        manual.get(STEVE)
        for (t in listOf(1_000L, STATS_REFRESH_MS, 2 * STATS_REFRESH_MS)) {
            now = t
            manual.get(STEVE)
        }

        assertEquals(1, launched.size)
    }

    @Test
    fun `a load for the old username is dropped`() {
        manual.get(STEVE)
        val steveLoad = launched.single()
        now = 1_000
        manual.get(ALEX)
        steveLoad.complete(stats("steve"))

        assertNull(manual.get(ALEX), "Steve's stats are never shown for Alex")
        assertTrue(manual.firstLoadPending, "Alex's own load is still running")
        assertEquals(2, launched.size)

        launched[1].complete(stats("alex"))
        assertEquals(stats("alex"), manual.get(ALEX))
    }

    @Test
    fun `a load that throws counts as failed`() {
        manual.get(STEVE)
        launched.single().completeExceptionally(IllegalStateException("boom"))

        assertNull(manual.get(STEVE))
        assertFalse(manual.firstLoadPending)
    }

    private companion object {
        const val STEVE = "Steve"
        const val ALEX = "Alex"
    }
}
