package com.dyetracker.discord

import com.dyetracker.DyeTrackerMod

/** Logged once at the first failed connect of an outage. */
internal const val LOG_NOT_REACHABLE = "Discord not reachable; will keep retrying quietly"

/** Logged when a connect succeeds after failed ones. */
internal const val LOG_CONNECTED = "Discord presence connected"

/**
 * Reconciles the card the mod wants with what it last sent Discord: connects when a card is
 * wanted (backing off after failed connects), sends only when the card changed, pings an idle
 * connection every [IPC_HEALTH_CHECK_MS] so a Discord quit or restart is noticed, and clears the
 * card and disconnects when none is wanted. Pure over [client] and [clock], so it runs against a
 * fake Discord in tests; only the presence worker calls it.
 *
 * @param pid this game's process id, which Discord ties the card to.
 * @param backoff when the next connect may be tried after failed ones.
 * @param clock the current time in ms.
 * @param log where the two info lines of an outage go ([LOG_NOT_REACHABLE], [LOG_CONNECTED]); never chat.
 */
class PresenceSync(
    private val client: DiscordIpcClient,
    private val pid: Long,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { DyeTrackerMod.info(it) },
) {

    /** The card Discord last accepted, or `null` when none is known to be showing. */
    var lastSent: PresenceCard? = null
        private set

    /** When Discord last answered a connect, send or ping. */
    private var lastContactMs = 0L

    /**
     * Moves Discord towards [wanted]. `null` clears a sent card (once), closes the connection and
     * resets the backoff, and never connects just to clear. A card first pings a connection that
     * has been idle for [IPC_HEALTH_CHECK_MS] (a failed ping forgets [lastSent]); then, when not
     * connected, connects if the backoff allows (a failed connect just returns); then sends the
     * card when it differs from [lastSent]. A failed send forgets [lastSent], so the next call
     * reconnects and sends it again.
     */
    fun reconcile(wanted: PresenceCard?) {
        if (wanted == null) {
            if (client.isConnected) {
                if (lastSent != null) client.setActivity(pid, null)
                client.close()
            }
            lastSent = null
            backoff.onSuccess()
            return
        }
        val now = clock()
        if (client.isConnected && wanted == lastSent && now - lastContactMs >= IPC_HEALTH_CHECK_MS) {
            if (client.ping()) lastContactMs = now else lastSent = null
        }
        if (!client.isConnected && !connect(now)) return
        if (wanted == lastSent) return
        lastSent = if (client.setActivity(pid, wanted.toActivityJson())) {
            lastContactMs = now
            wanted
        } else {
            null
        }
    }

    /** Connects when the backoff allows, logging only the first failure of an outage and the recovery. */
    private fun connect(now: Long): Boolean {
        if (!backoff.canAttempt(now)) return false
        if (!client.connect()) {
            if (backoff.failures == 0) log(LOG_NOT_REACHABLE)
            backoff.onFailure(now)
            return false
        }
        if (backoff.failures > 0) log(LOG_CONNECTED)
        backoff.onSuccess()
        // A fresh connection shows no card yet, whatever was sent on the last one.
        lastSent = null
        lastContactMs = now
        return true
    }
}
