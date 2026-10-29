package com.dyetracker.discord

import com.dyetracker.DyeTrackerMod

/** Logged once at the first failed connect of an outage. */
internal const val LOG_NOT_REACHABLE = "Discord not reachable; will keep retrying quietly"

/** Logged when a connect succeeds after failed ones. */
internal const val LOG_CONNECTED = "Discord presence connected"

/** Logged, before Discord's reason, once per distinct card Discord rejects. */
internal const val LOG_REJECTED = "Discord rejected the presence card: "

/**
 * Reconciles the card the mod wants with what it last sent Discord: connects when a card is
 * wanted (backing off after failed connects), sends only when the card changed and at most once per
 * [MIN_UPDATE_INTERVAL_MS] on a connection, pings an idle connection every [IPC_HEALTH_CHECK_MS] so
 * a Discord quit or restart is noticed, and clears the card and disconnects when none is wanted.
 * Pure over [client] and [clock], so it runs against a fake Discord in tests; only the presence
 * worker calls it.
 *
 * @param pid this game's process id, which Discord ties the card to.
 * @param backoff when the next connect may be tried after failed ones.
 * @param clock the current time in ms.
 * @param log where the two info lines of an outage go ([LOG_NOT_REACHABLE], [LOG_CONNECTED]) and
 *   why Discord rejected a card ([LOG_REJECTED]); never chat.
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
     * When a card was last sent on this connection (accepted or not), or `null` when none was yet;
     * the throttle's clock. Separate from [lastContactMs]: a ping is contact but not a send.
     */
    private var lastSentAtMs: Long? = null

    /**
     * The card Discord last rejected, so its reason is logged once however often it is retried;
     * `null` after an accepted card, so a card rejected again then is logged again.
     */
    private var lastRejected: PresenceCard? = null

    /**
     * Moves Discord towards [wanted]. `null` clears a sent card (once), closes the connection and
     * resets the backoff, and never connects just to clear. A card first pings a connection that
     * has been idle for [IPC_HEALTH_CHECK_MS] (a failed ping forgets [lastSent]); then, when not
     * connected, connects if the backoff allows (a failed connect just returns); then sends the
     * card when it differs from [lastSent], unless a card went out on this connection less than
     * [MIN_UPDATE_INTERVAL_MS] ago (a later call sends the newest card then). The first send on a
     * fresh connection is immediate, and a clear is never throttled. A failed send forgets
     * [lastSent], so a later call sends it again (after a reconnect when the connection broke).
     * When Discord rejects a card, its reason is logged ([LOG_REJECTED]) once per distinct card.
     *
     * The ping only runs for a card Discord already has; a card still waiting on the throttle is
     * sent within [MIN_UPDATE_INTERVAL_MS], and that send checks the connection instead.
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
        if (wanted == lastSent || throttled(now)) return
        lastSentAtMs = now
        if (client.setActivity(pid, wanted.toActivityJson())) {
            lastContactMs = now
            lastSent = wanted
            lastRejected = null
        } else {
            lastSent = null
            client.lastError?.let { reason -> logRejected(wanted, reason) }
        }
    }

    /** Logs why Discord rejected [card], unless it is the card whose rejection was logged last. */
    private fun logRejected(card: PresenceCard, reason: String) {
        if (card == lastRejected) return
        lastRejected = card
        log("$LOG_REJECTED$reason")
    }

    /** Whether a card went out on this connection less than [MIN_UPDATE_INTERVAL_MS] before [now]. */
    private fun throttled(now: Long): Boolean {
        val sentAt = lastSentAtMs ?: return false
        return now - sentAt < MIN_UPDATE_INTERVAL_MS
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
        // A fresh connection shows no card yet, whatever was sent on the last one, and owes no wait.
        lastSent = null
        lastSentAtMs = null
        lastContactMs = now
        return true
    }
}
