package com.dyetracker.discord

/**
 * Reconciles the card the mod wants with what it last sent Discord: connects when a card is
 * wanted, sends only when the card changed, and clears the card and disconnects when none is.
 * Pure over [client], so it runs against a fake Discord in tests; only the presence worker calls it.
 *
 * @param pid this game's process id, which Discord ties the card to.
 */
class PresenceSync(private val client: DiscordIpcClient, private val pid: Long) {

    /** The card Discord last accepted, or `null` when none is known to be showing. */
    var lastSent: PresenceCard? = null
        private set

    /**
     * Moves Discord towards [wanted]. `null` clears a sent card (once) and closes the connection.
     * A card connects if needed (a failed connect just returns; the next call retries) and is sent
     * when it differs from [lastSent]; a failed send forgets [lastSent] so it is sent again.
     */
    fun reconcile(wanted: PresenceCard?) {
        if (wanted == null) {
            if (client.isConnected) {
                if (lastSent != null) client.setActivity(pid, null)
                client.close()
            }
            lastSent = null
            return
        }
        if (!client.isConnected) {
            if (!client.connect()) return
            // A fresh connection shows no card yet, whatever was sent on the last one.
            lastSent = null
        }
        if (wanted == lastSent) return
        lastSent = if (client.setActivity(pid, wanted.toActivityJson())) wanted else null
    }
}
