package com.dyetracker.menu

import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.ProfileSummary
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit coverage for the menu's in-memory cache, the "updated … ago" text, the sync-first check
 * and the re-arm after a failed sync, and its wiring on the sync's future (task 67-5).
 */
class MenuCacheTest {

    private val mango = ProfileSummary(id = "p-mango", cuteName = "Mango")
    private val banana = ProfileSummary(id = "p-banana", cuteName = "Banana", selected = true)

    private val mangoDyes = listOf(
        DyeProgressEntry(dyeId = "matcha", name = "Matcha Dye", color = "#7cb342", category = "Slayer", progress = 42.5),
    )
    private val bananaDyes = listOf(
        DyeProgressEntry(dyeId = "lava", name = "Lava Dye", color = "#ff5722", category = "Mining", progress = 100.0),
    )

    private val mangoResult = MenuLoadResult.Loaded("Steve", listOf(mango, banana), mango, mangoDyes)
    private val bananaResult = MenuLoadResult.Loaded("Steve", listOf(mango, banana), banana, bananaDyes)

    @Test
    fun `lastFor returns what put stored, with its fetch time`() {
        val cache = MenuCache()
        cache.put(mangoResult, nowMs = 1_000L)

        assertEquals(CachedMenu(mangoResult, fetchedAtMs = 1_000L), cache.lastFor("Steve"))
    }

    @Test
    fun `lastFor is null for a player never put`() {
        val cache = MenuCache()
        cache.put(mangoResult, nowMs = 1_000L)

        assertNull(cache.lastFor("Alex"))
    }

    @Test
    fun `usernames are case-insensitive for lastFor and get`() {
        val cache = MenuCache()
        cache.put(mangoResult, nowMs = 1_000L)

        assertEquals(mangoResult, cache.lastFor("sTEVE")?.result)
        assertEquals(mangoResult, cache.get("STEVE", mango.id)?.result)
    }

    @Test
    fun `put for profile B after A makes lastFor return B while get still returns A`() {
        val cache = MenuCache()
        cache.put(mangoResult, nowMs = 1_000L)
        cache.put(bananaResult, nowMs = 2_000L)

        assertEquals(CachedMenu(bananaResult, fetchedAtMs = 2_000L), cache.lastFor("Steve"))
        assertEquals(CachedMenu(mangoResult, fetchedAtMs = 1_000L), cache.get("Steve", mango.id))
    }

    @Test
    fun `put again for the same profile replaces its entry`() {
        val cache = MenuCache()
        cache.put(mangoResult, nowMs = 1_000L)
        val refreshed = mangoResult.copy(dyes = bananaDyes)
        cache.put(refreshed, nowMs = 5_000L)

        assertEquals(CachedMenu(refreshed, fetchedAtMs = 5_000L), cache.get("Steve", mango.id))
    }

    @Test
    fun `get is null for a profile never put`() {
        val cache = MenuCache()
        cache.put(mangoResult, nowMs = 1_000L)

        assertNull(cache.get("Steve", banana.id))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = MenuCache()
        cache.put(mangoResult, nowMs = 1_000L)
        cache.put(bananaResult, nowMs = 2_000L)

        cache.clear()

        assertNull(cache.lastFor("Steve"))
        assertNull(cache.get("Steve", mango.id))
        assertNull(cache.get("Steve", banana.id))
    }

    private fun ageAt(ageMs: Long): String = formatAge(nowMs = FETCHED_AT_MS + ageMs, fetchedAtMs = FETCHED_AT_MS)

    @Test
    fun `formatAge is just now under five seconds`() {
        assertEquals("updated just now", ageAt(0L))
        assertEquals("updated just now", ageAt(4_999L))
    }

    @Test
    fun `formatAge counts seconds from five seconds to under a minute`() {
        assertEquals("updated 5s ago", ageAt(5_000L))
        assertEquals("updated 59s ago", ageAt(59_999L))
    }

    @Test
    fun `formatAge counts minutes from one minute to under an hour`() {
        assertEquals("updated 1m ago", ageAt(60_000L))
        assertEquals("updated 59m ago", ageAt(3_599_999L))
    }

    @Test
    fun `formatAge counts hours from one hour`() {
        assertEquals("updated 1h ago", ageAt(3_600_000L))
    }

    @Test
    fun `formatAge counts a negative age as zero`() {
        assertEquals("updated just now", ageAt(-120_000L))
    }

    @Test
    fun `shouldSyncFirst is true only when linked, pending and holding data`() {
        assertTrue(shouldSyncFirst(isLinked = true, syncPending = true, hasData = true))

        assertFalse(shouldSyncFirst(isLinked = false, syncPending = true, hasData = true))
        assertFalse(shouldSyncFirst(isLinked = true, syncPending = false, hasData = true))
        assertFalse(shouldSyncFirst(isLinked = true, syncPending = true, hasData = false))
        assertFalse(shouldSyncFirst(isLinked = false, syncPending = false, hasData = true))
        assertFalse(shouldSyncFirst(isLinked = false, syncPending = true, hasData = false))
        assertFalse(shouldSyncFirst(isLinked = true, syncPending = false, hasData = false))
        assertFalse(shouldSyncFirst(isLinked = false, syncPending = false, hasData = false))
    }

    @Test
    fun `shouldRearmSync re-arms after a failed sync or one with no result`() {
        assertTrue(shouldRearmSync(succeeded = false))
        assertTrue(shouldRearmSync(succeeded = null))
    }

    @Test
    fun `shouldRearmSync leaves a successful sync alone`() {
        assertFalse(shouldRearmSync(succeeded = true))
    }

    /** Counts [rearm] calls. */
    private class RearmCounter {
        var calls = 0
        val rearm: () -> Unit = { calls++ }
    }

    private val isSuccess: (Boolean) -> Boolean = { it }

    @Test
    fun `attachRearm re-arms once when the sync fails`() {
        val sync = CompletableFuture<Boolean>()
        val counter = RearmCounter()
        attachRearm(sync, isSuccess, counter.rearm)

        sync.complete(false)

        assertEquals(1, counter.calls)
    }

    @Test
    fun `attachRearm does not re-arm when the sync succeeds`() {
        val sync = CompletableFuture<Boolean>()
        val counter = RearmCounter()
        attachRearm(sync, isSuccess, counter.rearm)

        sync.complete(true)

        assertEquals(0, counter.calls)
    }

    @Test
    fun `attachRearm re-arms when the sync completes exceptionally`() {
        val sync = CompletableFuture<Boolean>()
        val counter = RearmCounter()
        attachRearm(sync, isSuccess, counter.rearm)

        sync.completeExceptionally(IllegalStateException("network down"))

        assertEquals(1, counter.calls)
    }

    @Test
    fun `attachRearm still re-arms after the returned wait stage is cancelled`() {
        val sync = CompletableFuture<Boolean>()
        val counter = RearmCounter()
        val wait = attachRearm(sync, isSuccess, counter.rearm)

        wait.cancel(false) // what await() does when the waiting coroutine is cancelled
        sync.complete(false)

        assertEquals(1, counter.calls)
    }

    @Test
    fun `cancelling the returned wait stage does not cancel the sync`() {
        val sync = CompletableFuture<Boolean>()
        val wait = attachRearm(sync, isSuccess, RearmCounter().rearm)

        wait.cancel(false)

        assertFalse(sync.isDone)
        assertTrue(sync.complete(true))
    }

    @Test
    fun `the returned wait stage completes normally once the sync fails`() {
        val sync = CompletableFuture<Boolean>()
        val wait = attachRearm(sync, isSuccess, RearmCounter().rearm)

        sync.completeExceptionally(IllegalStateException("network down"))

        assertTrue(wait.isDone)
        assertFalse(wait.isCompletedExceptionally)
    }

    private companion object {
        /** An arbitrary fetch time, far from zero so a negative age doesn't go below the epoch. */
        const val FETCHED_AT_MS = 1_700_000_000_000L
    }
}
