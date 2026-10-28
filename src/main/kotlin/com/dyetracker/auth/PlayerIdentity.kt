package com.dyetracker.auth

import com.dyetracker.config.ConfigManager
import net.minecraft.client.Minecraft

/**
 * Whose account the HUD dye widgets show: the linked account when there is one, else the
 * player's in-game name, so the widgets also work for a player who never linked (PBI 67).
 */
object PlayerIdentity {

    /** [linkedUsername] when it isn't blank, else [inGameName] trimmed; `""` when neither is set. */
    fun effectiveUsername(linkedUsername: String, inGameName: String?): String =
        linkedUsername.ifBlank { inGameName?.trim().orEmpty() }

    /** The name the HUD widget poller fetches under: the linked name, else the in-game name. */
    fun hudUsername(): String {
        // `user` is declared non-null, but the poller's first tick can run inside Minecraft's
        // constructor, before `user` is assigned.
        @Suppress("UNNECESSARY_SAFE_CALL")
        val inGameName = Minecraft.getInstance().user?.name
        return effectiveUsername(ConfigManager.config.linkedUsername, inGameName)
    }
}
