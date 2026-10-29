package com.dyetracker.discord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unit tests for [ReconnectBackoff]: the wait after each failed connect. */
class ReconnectBackoffTest {

    /** The wait the next failure at [now] sets, read back through [ReconnectBackoff.canAttempt]. */
    private fun ReconnectBackoff.waitAfterFailureAt(now: Long): Long {
        onFailure(now)
        var wait = 1L
        while (!canAttempt(now + wait)) wait++
        return wait
    }

    @Test
    fun `a fresh backoff can attempt at once`() {
        val backoff = ReconnectBackoff()
        assertTrue(backoff.canAttempt(0))
        assertEquals(0, backoff.failures)
    }

    @Test
    fun `the first failure waits the initial 5 s`() {
        val backoff = ReconnectBackoff()
        backoff.onFailure(0)
        assertFalse(backoff.canAttempt(4_999))
        assertTrue(backoff.canAttempt(5_000))
        assertEquals(1, backoff.failures)
    }

    @Test
    fun `waits double per failure and cap at 60 s`() {
        val backoff = ReconnectBackoff()
        val waits = List(6) { backoff.waitAfterFailureAt(it * 100_000L) }
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L), waits)
        assertEquals(6, backoff.failures)
    }

    @Test
    fun `onSuccess resets the wait and the failure count`() {
        val backoff = ReconnectBackoff()
        backoff.onFailure(0)
        backoff.onFailure(5_000)
        backoff.onSuccess()

        assertTrue(backoff.canAttempt(5_001), "no failure is pending")
        assertEquals(0, backoff.failures)
        assertEquals(5_000L, backoff.waitAfterFailureAt(10_000))
    }

    @Test
    fun `custom limits are honoured`() {
        val backoff = ReconnectBackoff(initialMs = 100, maxMs = 250)
        val waits = List(4) { backoff.waitAfterFailureAt(it * 1_000L) }
        assertEquals(listOf(100L, 200L, 250L, 250L), waits)
    }
}
