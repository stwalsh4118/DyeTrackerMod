package com.dyetracker.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [runBounded], what [DiscordPresence.stop] clears the card with: game shutdown never waits longer than its bound. */
@Timeout(TEST_TIMEOUT_SECONDS)
class BoundedShutdownTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun elapsedMs(block: () -> Unit): Long {
        val started = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
    }

    @Test
    fun `a quick block runs on the worker and finishes in time`() {
        var ranOn = ""
        assertTrue(runBounded(scope, BOUND_MS) { ranOn = Thread.currentThread().name })
        assertTrue(ranOn.isNotEmpty())
    }

    @Test
    fun `a stuck block is abandoned at the bound`() {
        val release = CountDownLatch(1)
        var finished = true
        val elapsed = elapsedMs { finished = runBounded(scope, BOUND_MS) { release.await() } }
        release.countDown()

        assertFalse(finished)
        assertTrue(elapsed < BOUND_MS + SLACK_MS, "returned after ${elapsed}ms")
    }

    @Test
    fun `a block queued behind a busy worker is abandoned and never runs`() {
        val release = CountDownLatch(1)
        val busy = scope.launch { release.await() }
        val ran = AtomicBoolean(false)

        assertFalse(runBounded(scope, BOUND_MS) { ran.set(true) })
        release.countDown()
        while (busy.isActive) Thread.sleep(POLL_MS)
        Thread.sleep(POLL_MS)

        assertFalse(ran.get(), "the queued clear was cancelled")
    }

    @Test
    fun `a block that throws counts as finished and never throws out`() {
        assertTrue(runBounded(scope, BOUND_MS) { error("Discord broke") })
    }

    private companion object {
        const val BOUND_MS = 200L
        const val SLACK_MS = 1_000L
        const val POLL_MS = 20L
    }
}
