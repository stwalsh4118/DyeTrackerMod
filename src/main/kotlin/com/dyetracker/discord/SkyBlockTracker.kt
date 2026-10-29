package com.dyetracker.discord

import com.dyetracker.DyeTrackerMod
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot

/**
 * Tracks whether the player is on Hypixel SkyBlock, for the Discord card. Every
 * [SKYBLOCK_POLL_TICKS] client ticks it reads the server address and the sidebar title on the
 * client thread, feeds [isSkyBlock] to one [SkyBlockSession] and publishes the result in [active]
 * and [startedAtMs], the only state the presence worker reads. A disconnect ends the session at once.
 */
object SkyBlockTracker {

    private val session = SkyBlockSession()

    private var tickCounter = 0

    /** Whether the player counts as on SkyBlock (debounced, see [SkyBlockSession]). */
    @Volatile
    var active: Boolean = false
        private set

    /** When the current SkyBlock session began (epoch ms), or `null` while not on SkyBlock. */
    @Volatile
    var startedAtMs: Long? = null
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
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            session.leaveNow()
            publish()
        }
        DyeTrackerMod.info("SkyBlockTracker registered")
    }

    private fun poll(client: Minecraft) {
        val address = client.getCurrentServer()?.ip
        val sidebarTitle = client.level?.getScoreboard()?.getDisplayObjective(DisplaySlot.SIDEBAR)?.getDisplayName()?.getString()
        session.update(isSkyBlock(address, sidebarTitle), System.currentTimeMillis())
        publish()
    }

    /** Copies the session into the published fields; start time first, so a reader that sees a session begin sees its start. */
    private fun publish() {
        startedAtMs = session.startedAtMs
        active = session.active
    }
}
