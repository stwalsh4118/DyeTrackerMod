package com.dyetracker.menu

import com.dyetracker.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit coverage for a menu tab's per-profile fetch (68-5): its state, the stale-result drop by
 * request, shown profile and close, and the should-fetch and busy decisions; and clearing the
 * state when the newest result is dropped for a profile no longer shown (68-6); and the session
 * cache: a cached entry shown at once while a background fetch runs, a success written to the
 * cache, a failed background fetch keeping the cached data (68-8).
 */
class TabFetchTest {

    /** Results the fetch posted to the "render thread", run by the test when it chooses. */
    private val posted = mutableListOf<() -> Unit>()

    /** The profile the strip shows; a result for another one is dropped. */
    private var shown: String? = P1

    /** The session cache the fetch seeds from and writes to. */
    private val cache = SessionCache<String>()

    /** The clock a posted success is stamped with. */
    private var nowMs = NOW_MS

    /** Unconfined: the blocking call runs at once in [TabFetch.fetch], only its post waits. */
    private val fetch = TabFetch<String>(
        scope = CoroutineScope(Dispatchers.Unconfined),
        post = { posted += it },
        shownProfileId = { shown },
        cache = cache,
        now = { nowMs },
    )

    private fun runPosted() {
        val pending = posted.toList()
        posted.clear()
        pending.forEach { it() }
    }

    @Test
    fun `starts with no state, not busy, needing a fetch for any profile`() {
        assertNull(fetch.state)
        assertFalse(fetch.busy)
        assertTrue(fetch.needsFetch(P1))
    }

    @Test
    fun `a fetch is Loading and busy until its result is posted`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("owned") })

        assertEquals(FetchState.Loading(P1), fetch.state)
        assertTrue(fetch.busy)
    }

    @Test
    fun `a posted success is Loaded with its data and calls onApplied`() {
        var applied = 0
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("owned") }) { applied++ }
        runPosted()

        assertEquals(FetchState.Loaded(P1, "owned", fetchedAtMs = NOW_MS), fetch.state)
        assertFalse(fetch.busy)
        assertEquals(1, applied)
    }

    @Test
    fun `a posted error is Failed with its message`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Error("HTTP 500") })
        runPosted()

        assertEquals(FetchState.Failed(P1, "HTTP 500"), fetch.state)
    }

    @Test
    fun `a call that throws is Failed with the unexpected error text`() {
        fetch.fetch(USER, P1, { throw IllegalStateException("boom") })
        runPosted()

        assertEquals(FetchState.Failed(P1, "Unexpected error: boom"), fetch.state)
    }

    @Test
    fun `a throwable without a message names its class`() {
        fetch.fetch(USER, P1, { throw IllegalStateException() })
        runPosted()

        assertEquals(FetchState.Failed(P1, "Unexpected error: IllegalStateException"), fetch.state)
    }

    @Test
    fun `a result superseded by a newer fetch is dropped`() {
        var applied = 0
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("old") }) { applied++ }
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("new") }) { applied++ }
        val (older, newer) = posted.toList()
        posted.clear()

        older()
        assertEquals(FetchState.Loading(P1), fetch.state)
        newer()
        assertEquals(FetchState.Loaded(P1, "new", fetchedAtMs = NOW_MS), fetch.state)
        assertEquals(1, applied)
    }

    @Test
    fun `the newest result for a profile the strip no longer shows is dropped and clears the state`() {
        var applied = 0
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("owned") }) { applied++ }
        shown = P2
        runPosted()

        assertNull(fetch.state)
        assertFalse(fetch.busy)
        assertTrue(fetch.needsFetch(P1))
        assertEquals(0, applied)
    }

    @Test
    fun `switching back to a profile whose result was dropped fetches it again`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("stale") })
        shown = P2
        runPosted()
        shown = P1

        assertTrue(fetch.needsFetch(P1))
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("fresh") })
        runPosted()
        assertEquals(FetchState.Loaded(P1, "fresh", fetchedAtMs = NOW_MS), fetch.state)
    }

    @Test
    fun `a superseded result for a profile no longer shown leaves the newer fetch's state`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("old") })
        fetch.fetch(USER, P2, { ApiClient.ApiResult.Success("new") })
        shown = P2
        val (older, newer) = posted.toList()
        posted.clear()

        older()
        assertEquals(FetchState.Loading(P2), fetch.state)
        newer()
        assertEquals(FetchState.Loaded(P2, "new", fetchedAtMs = NOW_MS), fetch.state)
    }

    @Test
    fun `a posted result applies, clears or is dropped by request, shown profile and close`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("a") })
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("b") })

        assertEquals(PostedResult.APPLY, fetch.dispositionOf(request = 2, profileId = P1))
        assertEquals(PostedResult.CLEAR, fetch.dispositionOf(request = 2, profileId = P2))
        assertEquals(PostedResult.DROP, fetch.dispositionOf(request = 1, profileId = P1))
        assertEquals(PostedResult.DROP, fetch.dispositionOf(request = 1, profileId = P2))
        fetch.close()
        assertEquals(PostedResult.DROP, fetch.dispositionOf(request = 2, profileId = P1))
        assertEquals(PostedResult.DROP, fetch.dispositionOf(request = 2, profileId = P2))
    }

    @Test
    fun `a result posted after close is dropped`() {
        var applied = 0
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("owned") }) { applied++ }
        fetch.close()
        runPosted()

        assertEquals(FetchState.Loading(P1), fetch.state)
        assertEquals(0, applied)
    }

    @Test
    fun `needsFetch is false for the profile loading or loaded and true for another`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("owned") })
        assertFalse(fetch.needsFetch(P1))
        assertTrue(fetch.needsFetch(P2))

        runPosted()
        assertFalse(fetch.needsFetch(P1))
        assertTrue(fetch.needsFetch(P2))
    }

    @Test
    fun `needsFetch is true again after a failure for that profile`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Error("HTTP 500") })
        runPosted()

        assertTrue(fetch.needsFetch(P1))
    }

    @Test
    fun `stateFor gives the state only for its profile`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("owned") })
        runPosted()

        assertEquals(FetchState.Loaded(P1, "owned", fetchedAtMs = NOW_MS), fetch.stateFor(P1))
        assertNull(fetch.stateFor(P2))
    }

    @Test
    fun `loadedFor gives the data only once loaded for its profile`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("owned") })
        assertNull(fetch.loadedFor(P1))

        runPosted()
        assertEquals("owned", fetch.loadedFor(P1))
        assertNull(fetch.loadedFor(P2))
    }

    @Test
    fun `loadedFor is null after a failure`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Error("HTTP 500") })
        runPosted()

        assertNull(fetch.loadedFor(P1))
    }

    @Test
    fun `a posted success is written to the cache, stamped with the clock at posting`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("owned") })
        nowMs = NOW_MS + 500L
        runPosted()

        assertEquals(Cached("owned", fetchedAtMs = NOW_MS + 500L), cache.get(USER, P1))
        assertEquals(FetchState.Loaded(P1, "owned", fetchedAtMs = NOW_MS + 500L), fetch.state)
    }

    @Test
    fun `a posted error writes nothing to the cache`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Error("HTTP 500") })
        runPosted()

        assertNull(cache.get(USER, P1))
    }

    @Test
    fun `a cached entry shows at once, refreshing, while the fetch runs`() {
        cache.put(USER, P1, "cached", nowMs = 1_000L)
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("fresh") })

        assertEquals(FetchState.Loaded(P1, "cached", fetchedAtMs = 1_000L, RefreshStatus.REFRESHING), fetch.state)
        assertEquals("cached", fetch.loadedFor(P1))
        assertTrue(fetch.busy)
        assertFalse(fetch.needsFetch(P1))
    }

    @Test
    fun `the cache is matched by username case-insensitively`() {
        cache.put("sTEVE", P1, "cached", nowMs = 1_000L)
        fetch.fetch("steve", P1, { ApiClient.ApiResult.Success("fresh") })

        assertEquals(FetchState.Loaded(P1, "cached", fetchedAtMs = 1_000L, RefreshStatus.REFRESHING), fetch.state)
    }

    @Test
    fun `a cached entry for another profile or player is not shown`() {
        cache.put(USER, P2, "banana", nowMs = 1_000L)
        cache.put("Alex", P1, "alex", nowMs = 1_000L)
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("fresh") })

        assertEquals(FetchState.Loading(P1), fetch.state)
    }

    @Test
    fun `a background success replaces the cached data and the cache entry`() {
        var applied = 0
        cache.put(USER, P1, "cached", nowMs = 1_000L)
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("fresh") }) { applied++ }
        runPosted()

        assertEquals(FetchState.Loaded(P1, "fresh", fetchedAtMs = NOW_MS), fetch.state)
        assertEquals(Cached("fresh", fetchedAtMs = NOW_MS), cache.get(USER, P1))
        assertFalse(fetch.busy)
        assertEquals(1, applied)
    }

    @Test
    fun `a failed background fetch keeps the cached data and says the refresh failed`() {
        var applied = 0
        cache.put(USER, P1, "cached", nowMs = 1_000L)
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Error("HTTP 500") }) { applied++ }
        runPosted()

        assertEquals(FetchState.Loaded(P1, "cached", fetchedAtMs = 1_000L, RefreshStatus.FAILED), fetch.state)
        assertEquals(Cached("cached", fetchedAtMs = 1_000L), cache.get(USER, P1))
        assertFalse(fetch.busy)
        assertFalse(fetch.needsFetch(P1))
        assertEquals(1, applied)
    }

    @Test
    fun `a background fetch that throws keeps the cached data too`() {
        cache.put(USER, P1, "cached", nowMs = 1_000L)
        fetch.fetch(USER, P1, { throw IllegalStateException("boom") })
        runPosted()

        assertEquals(FetchState.Loaded(P1, "cached", fetchedAtMs = 1_000L, RefreshStatus.FAILED), fetch.state)
    }

    @Test
    fun `fetching again after a failed refresh refreshes behind the cached data`() {
        cache.put(USER, P1, "cached", nowMs = 1_000L)
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Error("HTTP 500") })
        runPosted()
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("fresh") })

        assertEquals(FetchState.Loaded(P1, "cached", fetchedAtMs = 1_000L, RefreshStatus.REFRESHING), fetch.state)
        runPosted()
        assertEquals(FetchState.Loaded(P1, "fresh", fetchedAtMs = NOW_MS), fetch.state)
    }

    @Test
    fun `fetching a loaded profile again refreshes behind the data it loaded`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("first") })
        runPosted()
        nowMs = NOW_MS + 1_000L
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("second") })

        assertEquals(FetchState.Loaded(P1, "first", fetchedAtMs = NOW_MS, RefreshStatus.REFRESHING), fetch.state)
        runPosted()
        assertEquals(FetchState.Loaded(P1, "second", fetchedAtMs = NOW_MS + 1_000L), fetch.state)
    }

    @Test
    fun `a dropped or cleared success writes nothing to the cache`() {
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("old") })
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("new") })
        val (older, _) = posted.toList()
        posted.clear()
        older()
        assertNull(cache.get(USER, P1))

        shown = P2
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("moved") })
        runPosted()
        assertNull(cache.get(USER, P1))

        shown = P1
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("closed") })
        fetch.close()
        runPosted()
        assertNull(cache.get(USER, P1))
    }

    @Test
    fun `a cleared background fetch leaves the cache entry for the next fetch`() {
        cache.put(USER, P1, "cached", nowMs = 1_000L)
        fetch.fetch(USER, P1, { ApiClient.ApiResult.Success("fresh") })
        shown = P2
        runPosted()

        assertNull(fetch.state)
        assertEquals(Cached("cached", fetchedAtMs = 1_000L), cache.get(USER, P1))
    }

    private companion object {
        const val USER = "Steve"
        const val NOW_MS = 10_000L
        const val P1 = "p-mango"
        const val P2 = "p-banana"
    }
}
