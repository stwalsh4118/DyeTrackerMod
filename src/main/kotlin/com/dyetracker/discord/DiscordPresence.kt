package com.dyetracker.discord

import com.dyetracker.DyeTrackerMod
import com.dyetracker.api.ApiClient
import com.dyetracker.config.ConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Discord Rich Presence worker. One loop on a single-thread IO dispatcher reconciles the
 * wanted card with Discord every [PRESENCE_TICK_MS] (or sooner after [wake]); all Discord IPC runs
 * there, never on the client thread. The blocking stats fetches run on their own IO scope, so a slow
 * API never holds up the worker or a clear. Failures are logged and never reach the game.
 *
 * With [DISCORD_APPLICATION_ID] blank, [start] logs once and never connects.
 */
object DiscordPresence {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val client = DiscordIpcClient(defaultIpcConnector(), DISCORD_APPLICATION_ID)
    private val pid = ProcessHandle.current().pid()
    private val sync = PresenceSync(client, pid)
    /** Where the stats fetches run: off the worker, so a hanging GET can't delay a clear. */
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stats = PresenceStatsCache(
        // A finished load wakes the worker, so the card follows it without waiting out a tick.
        launch = { block -> statsScope.async { block() }.also { it.invokeOnCompletion { wake() } } },
        load = { username ->
            loadPresenceStats(username, ApiClient::fetchProfiles, ApiClient::fetchDyeProgress, ApiClient::fetchCollection)
        },
    )

    @Volatile
    private var job: Job? = null

    /** The username the last card handed to [sync] was for, or `null` after a clear. Worker only. */
    private var sentForUsername: String? = null

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
     * Cancel the loop and any stats fetch, clear the card and close the Discord connection (on
     * client stop). The clear runs on the worker and is waited for at most
     * [SHUTDOWN_CLEAR_TIMEOUT_MS]; past that the connection is closed from here and the game goes on
     * quitting. Final: the client is shut down, so it never connects again.
     */
    fun stop() {
        job?.cancel()
        job = null
        statsScope.cancel()
        if (!runBounded(scope, SHUTDOWN_CLEAR_TIMEOUT_MS) { client.clearAndClose(pid) }) {
            DyeTrackerMod.debug("Discord presence clear did not finish in {}ms; closing", SHUTDOWN_CLEAR_TIMEOUT_MS)
        }
        // Covers a clear that timed out, and keeps a late tick from reconnecting.
        client.shutdown()
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

    /**
     * Reads only [ConfigManager] and one copy of [SkyBlockTracker.snapshot], never Minecraft state.
     * Decides first whether a card is wanted and clears when not, without touching the stats. A
     * wanted card is built from the stats known now and the hunt dye (a due fetch starts in the
     * background); while a username's first fetch is still running the card is held back, so the
     * stats card isn't stuck behind the send throttle after a bare one, or cleared when the card up
     * is another player's (see [cardDecision]).
     */
    private fun tick() {
        val skyBlock = SkyBlockTracker.snapshot
        val wanted = ConfigManager.isDiscordEnabled() && skyBlock.active
        // Only a wanted card touches the stats, so nothing is fetched while none is.
        val known = if (wanted) stats.get(skyBlock.username) else null
        when (cardDecision(wanted, stats.firstLoadPending, sentForUsername, skyBlock.username)) {
            CardDecision.SKIP -> return
            CardDecision.CLEAR -> {
                sync.reconcile(null)
                sentForUsername = null
            }
            CardDecision.SEND -> {
                sync.reconcile(buildPresenceCard(known, skyBlock.startedAtMs, ConfigManager.discordHuntDyeId()))
                sentForUsername = skyBlock.username
            }
        }
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
