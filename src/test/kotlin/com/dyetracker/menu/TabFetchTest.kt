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
 * request, shown profile and close, and the should-fetch and busy decisions.
 */
class TabFetchTest {

    /** Results the fetch posted to the "render thread", run by the test when it chooses. */
    private val posted = mutableListOf<() -> Unit>()

    /** The profile the strip shows; a result for another one is dropped. */
    private var shown: String? = P1

    /** Unconfined: the blocking call runs at once in [TabFetch.fetch], only its post waits. */
    private val fetch = TabFetch<String>(
        scope = CoroutineScope(Dispatchers.Unconfined),
        post = { posted += it },
        shownProfileId = { shown },
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
        fetch.fetch(P1, { ApiClient.ApiResult.Success("owned") })

        assertEquals(FetchState.Loading(P1), fetch.state)
        assertTrue(fetch.busy)
    }

    @Test
    fun `a posted success is Loaded with its data and calls onApplied`() {
        var applied = 0
        fetch.fetch(P1, { ApiClient.ApiResult.Success("owned") }) { applied++ }
        runPosted()

        assertEquals(FetchState.Loaded(P1, "owned"), fetch.state)
        assertFalse(fetch.busy)
        assertEquals(1, applied)
    }

    @Test
    fun `a posted error is Failed with its message`() {
        fetch.fetch(P1, { ApiClient.ApiResult.Error("HTTP 500") })
        runPosted()

        assertEquals(FetchState.Failed(P1, "HTTP 500"), fetch.state)
    }

    @Test
    fun `a call that throws is Failed with the unexpected error text`() {
        fetch.fetch(P1, { throw IllegalStateException("boom") })
        runPosted()

        assertEquals(FetchState.Failed(P1, "Unexpected error: boom"), fetch.state)
    }

    @Test
    fun `a throwable without a message names its class`() {
        fetch.fetch(P1, { throw IllegalStateException() })
        runPosted()

        assertEquals(FetchState.Failed(P1, "Unexpected error: IllegalStateException"), fetch.state)
    }

    @Test
    fun `a result superseded by a newer fetch is dropped`() {
        var applied = 0
        fetch.fetch(P1, { ApiClient.ApiResult.Success("old") }) { applied++ }
        fetch.fetch(P1, { ApiClient.ApiResult.Success("new") }) { applied++ }
        val (older, newer) = posted.toList()
        posted.clear()

        older()
        assertEquals(FetchState.Loading(P1), fetch.state)
        newer()
        assertEquals(FetchState.Loaded(P1, "new"), fetch.state)
        assertEquals(1, applied)
    }

    @Test
    fun `a result for a profile the strip no longer shows is dropped`() {
        var applied = 0
        fetch.fetch(P1, { ApiClient.ApiResult.Success("owned") }) { applied++ }
        shown = P2
        runPosted()

        assertEquals(FetchState.Loading(P1), fetch.state)
        assertEquals(0, applied)
    }

    @Test
    fun `a result posted after close is dropped`() {
        var applied = 0
        fetch.fetch(P1, { ApiClient.ApiResult.Success("owned") }) { applied++ }
        fetch.close()
        runPosted()

        assertEquals(FetchState.Loading(P1), fetch.state)
        assertEquals(0, applied)
    }

    @Test
    fun `accepts only the latest request for the shown profile while open`() {
        fetch.fetch(P1, { ApiClient.ApiResult.Success("a") })
        fetch.fetch(P1, { ApiClient.ApiResult.Success("b") })

        assertTrue(fetch.accepts(request = 2, profileId = P1))
        assertFalse(fetch.accepts(request = 1, profileId = P1))
        assertFalse(fetch.accepts(request = 2, profileId = P2))
        fetch.close()
        assertFalse(fetch.accepts(request = 2, profileId = P1))
    }

    @Test
    fun `needsFetch is false for the profile loading or loaded and true for another`() {
        fetch.fetch(P1, { ApiClient.ApiResult.Success("owned") })
        assertFalse(fetch.needsFetch(P1))
        assertTrue(fetch.needsFetch(P2))

        runPosted()
        assertFalse(fetch.needsFetch(P1))
        assertTrue(fetch.needsFetch(P2))
    }

    @Test
    fun `needsFetch is true again after a failure for that profile`() {
        fetch.fetch(P1, { ApiClient.ApiResult.Error("HTTP 500") })
        runPosted()

        assertTrue(fetch.needsFetch(P1))
    }

    @Test
    fun `stateFor gives the state only for its profile`() {
        fetch.fetch(P1, { ApiClient.ApiResult.Success("owned") })
        runPosted()

        assertEquals(FetchState.Loaded(P1, "owned"), fetch.stateFor(P1))
        assertNull(fetch.stateFor(P2))
    }

    @Test
    fun `loadedFor gives the data only once loaded for its profile`() {
        fetch.fetch(P1, { ApiClient.ApiResult.Success("owned") })
        assertNull(fetch.loadedFor(P1))

        runPosted()
        assertEquals("owned", fetch.loadedFor(P1))
        assertNull(fetch.loadedFor(P2))
    }

    @Test
    fun `loadedFor is null after a failure`() {
        fetch.fetch(P1, { ApiClient.ApiResult.Error("HTTP 500") })
        runPosted()

        assertNull(fetch.loadedFor(P1))
    }

    private companion object {
        const val P1 = "p-mango"
        const val P2 = "p-banana"
    }
}
