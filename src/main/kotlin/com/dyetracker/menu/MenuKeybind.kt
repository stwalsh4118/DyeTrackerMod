package com.dyetracker.menu

import com.dyetracker.DyeTrackerMod
import com.dyetracker.client.DyeTrackerKeys
import com.dyetracker.client.currentScreen
import com.dyetracker.client.showScreen
import com.dyetracker.config.ConfigManager
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Registers the "Open DyeTracker Menu" keybind, unbound by default so it never clashes with
 * another key; players bind it in Controls → DyeTracker. Also prints a one-time chat hint on
 * the first join, so players find the menu (`/dyetracker menu`) and the key.
 */
object MenuKeybind {

    private const val TRANSLATION_KEY = "key.dyetracker.open_menu"
    private const val HINT_PREFIX = "[DyeTracker]"
    private const val HINT_TEXT = " Open your dyes with /dyetracker menu, or bind a key in Controls → DyeTracker."

    private lateinit var menuKey: KeyMapping

    /** Register the keybind, the tick listener that opens the menu on a press, and the join hint. */
    fun register() {
        menuKey = KeyMapping(
            TRANSLATION_KEY,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.value,
            DyeTrackerKeys.CATEGORY,
        )
        KeyMappingHelper.registerKeyMapping(menuKey)

        // Drain the press-counter every tick so a single press only opens the menu once.
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (menuKey.consumeClick()) {
                if (client.currentScreen() == null) client.showScreen(DyeTrackerMenuScreen())
            }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, client ->
            if (shouldShowMenuHint(ConfigManager.config)) client.execute { showMenuHint(client) }
        }
        DyeTrackerMod.info("Menu keybind registered (unbound by default)")
    }

    /** Print the menu hint to [client]'s player once; it only counts as shown if there was a player. */
    private fun showMenuHint(client: Minecraft) {
        if (!shouldShowMenuHint(ConfigManager.config)) return
        val player = client.player ?: return
        player.sendSystemMessage(
            Component.literal(HINT_PREFIX).withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(HINT_TEXT).withStyle(ChatFormatting.GRAY)),
        )
        ConfigManager.update { it.copy(menuHintShown = true) }
    }
}
