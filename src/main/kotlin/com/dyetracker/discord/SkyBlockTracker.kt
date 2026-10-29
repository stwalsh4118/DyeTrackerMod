package com.dyetracker.discord

import com.dyetracker.DyeTrackerMod
import com.dyetracker.auth.PlayerIdentity
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot

/**
 * Tracks whether the player is on Hypixel SkyBlock, for the Discord card. Every
 * [SKYBLOCK_POLL_TICKS] client ticks it reads the server address, the sidebar title and
 * [PlayerIdentity.hudUsername] on the client thread, feeds [countsAsSkyBlock] to one
 * [SkyBlockSession] and publishes the result as one [snapshot], the only state the presence worker
 * reads. A disconnect ends the session at once.
 * The session is only touched on the client thread: DISCONNECT can fire on the Netty event loop, so
 * its handler hands the work to the client, and a poll on a dropped connection counts as off SkyBlock.
 */
object SkyBlockTracker {

    private val session = SkyBlockSession()

    private var tickCounter = 0

    /**
     * The latest SkyBlock state (debounced, see [SkyBlockSession]). Replaced whole by each poll and
     * disconnect, so a reader that copies it once sees one consistent state.
     */
    @Volatile
    var snapshot: SkyBlockSnapshot = SkyBlockSnapshot(active = false, startedAtMs = null, username = "")
        private set

    /** Registers the SkyBlock poll on the client tick and the disconnect hook. */
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            tickCounter++
            if (tickCounter >= SKYBLOCK_POLL_TICKS) {
                tickCounter = 0
                poll(client)
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client ->
            client.execute {
                session.leaveNow()
                publish(snapshot.username)
            }
        }
        DyeTrackerMod.info("SkyBlockTracker registered")
    }

    private fun poll(client: Minecraft) {
        val connected = client.getConnection()?.getConnection()?.isConnected() == true
        val address = client.getCurrentServer()?.ip
        val sidebarTitle = client.level?.getScoreboard()?.getDisplayObjective(DisplaySlot.SIDEBAR)?.getDisplayName()?.getString()
        session.update(countsAsSkyBlock(connected, address, sidebarTitle), System.currentTimeMillis())
        publish(PlayerIdentity.hudUsername())
    }

    /** Publishes the session and [username] as a new [snapshot]. */
    private fun publish(username: String) {
        snapshot = SkyBlockSnapshot(active = session.active, startedAtMs = session.startedAtMs, username = username)
    }
}
