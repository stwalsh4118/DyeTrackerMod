package com.dyetracker.ui.edit

import com.dyetracker.DyeTrackerMod
import com.dyetracker.client.currentScreen
import com.dyetracker.client.showScreen
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

/**
 * Registers the `G` keybind that opens the generalized [WidgetEditScreen] (HUD edit mode for
 * every registered widget — GIF overlays today, more later). The binding lives in a custom
 * `dyetracker:main` category so vanilla's key-binding screen groups our shortcuts together.
 * Players may rebind freely; the `G` default is only the cold-start value, honored only when
 * the binding hasn't been overridden in `options.txt`.
 *
 * The translation key string is retained from PBI 28 (`key.dyetracker.edit_overlays`) so any
 * existing user rebind survives the toolkit migration; only its display label was generalized.
 */
object EditModeKeybind {

    private const val TRANSLATION_KEY = "key.dyetracker.edit_overlays"
    private const val CATEGORY_PATH = "main"
    private const val DEFAULT_KEY = GLFW.GLFW_KEY_G

    private lateinit var editKey: KeyMapping

    /** Register the keybind + the tick listener that opens edit mode on a press. */
    fun register() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(DyeTrackerMod.MOD_ID, CATEGORY_PATH))
        editKey = KeyMapping(
            TRANSLATION_KEY,
            InputConstants.Type.KEYSYM,
            DEFAULT_KEY,
            category,
        )
        KeyMappingHelper.registerKeyMapping(editKey)

        // Drain the press-counter every tick so a single press only opens the screen once
        // even if multiple presses landed within one tick.
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (editKey.consumeClick()) {
                openEditScreen(client)
            }
        }
        DyeTrackerMod.info("HUD edit-mode keybind registered (default G)")
    }

    /** Open edit mode if no other screen is active. Safe to call from any thread. */
    fun openEditScreen(client: Minecraft) {
        if (client.currentScreen() != null) return
        client.showScreen(WidgetEditScreen())
    }
}
