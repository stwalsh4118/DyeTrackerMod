package com.dyetracker.discord

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Discord Rich Presence worker. One loop on a single-thread IO dispatcher reconciles the
 * wanted card with Discord every [PRESENCE_TICK_MS] (or sooner after [wake]); all Discord IPC runs
 * there, never on the client thread. Failures are logged and never reach the game.
 *
 * With [DISCORD_APPLICATION_ID] blank, [start] logs once and never connects.
 */
object DiscordPresence {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val client = DiscordIpcClient(defaultIpcConnector(), DISCORD_APPLICATION_ID)
    private val pid = ProcessHandle.current().pid()
    private val sync = PresenceSync(client, pid)

    @Volatile
    private var job: Job? = null

    @Volatile
    private var blankIdLogged = false

    /** Start the presence loop. Idempotent: a second call while running is a no-op. */
    fun start() {
        if (job?.isActive == true) return
        if (DISCORD_APPLICATION_ID.isBlank()) {
            if (!blankIdLogged) {
                blankIdLogged = true
                DyeTrackerMod.warn("Discord application id not set; presence disabled")
            }
            return
        }
        job = scope.launch {
            while (isActive) {
                safeTick()
                // Sleep one tick, or less when [wake] asks for an early one.
                withTimeoutOrNull(PRESENCE_TICK_MS) { wakeups.receive() }
            }
        }
        DyeTrackerMod.info("Discord presence started ({}ms tick)", PRESENCE_TICK_MS)
    }

    /**
     * Cancel the loop, clear the card and close the Discord connection (on client stop). The clear
     * runs on the worker and is waited for at most [SHUTDOWN_CLEAR_TIMEOUT_MS]; past that the
     * connection is closed from here and the game goes on quitting.
     */
    fun stop() {
        job?.cancel()
        job = null
        if (!runBounded(scope, SHUTDOWN_CLEAR_TIMEOUT_MS) { client.clearAndClose(pid) }) {
            DyeTrackerMod.debug("Discord presence clear did not finish in {}ms; closing", SHUTDOWN_CLEAR_TIMEOUT_MS)
        }
        // Covers a clear that timed out; a no-op after one that finished.
        client.close()
        client.shutdownWatchdog()
    }

    /** Run a tick soon instead of waiting out the interval, e.g. right after `/dyetracker discord on|off`. */
    fun wake() {
        wakeups.trySend(Unit)
    }

    private fun safeTick() {
        try {
            tick()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            DyeTrackerMod.warn("Discord presence tick failed: {}", e.message ?: e.javaClass.simpleName)
        }
    }

    private fun tick() {
        val wanted = if (ConfigManager.isDiscordEnabled()) TRACER_CARD else null
        sync.reconcile(wanted)
    }
}

/**
 * Runs [block] on [scope] and waits at most [timeoutMs] for it, blocking the caller; returns
 * whether it finished in time. At the timeout the block is cancelled: one still queued never runs,
 * one already running is left to finish on its own. Never throws: a block that fails counts as
 * finished.
 */
internal fun runBounded(scope: CoroutineScope, timeoutMs: Long, block: () -> Unit): Boolean = runBlocking {
    val work = scope.async { runCatching(block) }
    val finished = withTimeoutOrNull(timeoutMs) { work.await() } != null
    if (!finished) work.cancel()
    finished
}
