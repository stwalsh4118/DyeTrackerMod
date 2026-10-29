package com.dyetracker.discord

import com.dyetracker.events.InventoryUtils

/** Separates a server address from its port. */
private const val PORT_SEPARATOR = ':'

/** Separates the labels of a domain name; a trailing one marks a fully qualified name. */
private const val DOMAIN_LABEL_SEPARATOR = '.'

/**
 * Whether [address] is a Hypixel server: once trimmed, lowercased and stripped of a `:port` suffix
 * and a trailing `.`, it is [HYPIXEL_DOMAIN] or a subdomain of it. `null` or blank is not.
 */
fun isHypixelAddress(address: String?): Boolean {
    val host = address?.trim()?.lowercase()
        ?.substringBefore(PORT_SEPARATOR)
        ?.removeSuffix(DOMAIN_LABEL_SEPARATOR.toString())
    if (host.isNullOrEmpty()) return false
    return host == HYPIXEL_DOMAIN || host.endsWith("$DOMAIN_LABEL_SEPARATOR$HYPIXEL_DOMAIN")
}

/**
 * Whether the player is on Hypixel SkyBlock: connected to a Hypixel [serverAddress] with a sidebar
 * whose title, formatting stripped, contains [SKYBLOCK_SIDEBAR_MARKER] (any case). No sidebar is
 * not SkyBlock.
 */
fun isSkyBlock(serverAddress: String?, sidebarTitle: String?): Boolean {
    if (!isHypixelAddress(serverAddress) || sidebarTitle == null) return false
    return InventoryUtils.stripFormatting(sidebarTitle).contains(SKYBLOCK_SIDEBAR_MARKER, ignoreCase = true)
}

/**
 * One SkyBlock visit, fed a SkyBlock check now and then. Entering is immediate; leaving waits until
 * the player has been off SkyBlock for [leaveDebounceMs], so lobby hops and server swaps keep the
 * session and its start time. Not thread-safe: one caller (the client thread) feeds it.
 *
 * @param leaveDebounceMs how long off SkyBlock ends the session.
 */
class SkyBlockSession(private val leaveDebounceMs: Long = SKYBLOCK_LEAVE_DEBOUNCE_MS) {

    /** Whether the player counts as on SkyBlock. */
    val active: Boolean get() = startedAtMs != null

    /** When the session began (ms), or `null` while inactive. */
    var startedAtMs: Long? = null
        private set

    /** When the player was first seen off SkyBlock during this session, or `null` while on it. */
    private var offSinceMs: Long? = null

    /**
     * Feeds one check at [nowMs]. On SkyBlock starts a session (or cancels a pending leave, keeping
     * [startedAtMs]); off SkyBlock for [leaveDebounceMs] since the first such check ends it.
     */
    fun update(onSkyBlockNow: Boolean, nowMs: Long) {
        if (onSkyBlockNow) {
            if (startedAtMs == null) startedAtMs = nowMs
            offSinceMs = null
            return
        }
        if (startedAtMs == null) return
        val offSince = offSinceMs ?: nowMs.also { offSinceMs = it }
        if (nowMs - offSince >= leaveDebounceMs) leaveNow()
    }

    /** Ends the session at once (disconnect). */
    fun leaveNow() {
        startedAtMs = null
        offSinceMs = null
    }
}
