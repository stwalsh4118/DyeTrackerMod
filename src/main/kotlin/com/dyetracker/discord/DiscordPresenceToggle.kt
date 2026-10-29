package com.dyetracker.discord

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Turn Discord Rich Presence on (PBI 77), as `/dyetracker discord on` and the menu's Settings tab
 * do: persist the flag and wake the presence worker (all Discord IPC runs there, never on this
 * client thread). The first time ever, also hand the one-time setup hint to [sendHint] and mark it
 * shown ([shouldShowDiscordHint]).
 */
fun enableDiscordPresence(sendHint: (Component) -> Unit) {
    ConfigManager.setDiscordEnabled(true)
    DiscordPresence.wake()
    if (!shouldShowDiscordHint(ConfigManager.config)) return
    sendHint(
        Component.literal(DyeTrackerMod.CHAT_PREFIX).withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal(DISCORD_HINT_TEXT).withStyle(ChatFormatting.GRAY)),
    )
    ConfigManager.update { it.copy(discordHintShown = true) }
}

/** Turn Discord Rich Presence off (PBI 77): persist the flag and wake the presence worker, which clears the card. */
fun disableDiscordPresence() {
    ConfigManager.setDiscordEnabled(false)
    DiscordPresence.wake()
}
