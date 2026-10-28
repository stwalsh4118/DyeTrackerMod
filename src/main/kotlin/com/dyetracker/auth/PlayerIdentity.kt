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
    fun hudUsername(): String =
        effectiveUsername(ConfigManager.config.linkedUsername, Minecraft.getInstance().user.name)
}
