package com.dyetracker.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.Util
import java.net.URI

/*
 * Screen, HUD-visibility and key-mapping access across the 26.x nodes. Minecraft 26.2 moved
 * screen state off [Minecraft] onto its `gui` field (`Gui`): `setScreen` → `gui.setScreen`, the
 * `screen` field → `gui.screen()`, and F1's `options.hideGui` → `gui.hud.isHidden()`. Minecraft
 * 26.3 replaced GLFW with SDL, renamed the keyboard input type `KEYSYM` → `KEYBOARD`, and moved
 * opening a link off `Util.getPlatform().openUri` onto `Blaze3D.openUri`.
 *
 * Call these instead of the vanilla members so the version switch lives here only. The
 * `//?` lines are Stonecutter version comments; see
 * docs/delivery/53/research/53-1-26.2-toolchain-guide.md and
 * docs/delivery/78/research/78-1-26.3-toolchain-guide.md.
 */

/** The screen currently open, or null when in game. */
fun Minecraft.currentScreen(): Screen? {
    //? if >=26.2 {
    /*return gui.screen()
    *///?} else {
    return screen
    //?}
}

/** Open [screen], or close the current one when null. */
fun Minecraft.showScreen(screen: Screen?) {
    //? if >=26.2 {
    /*gui.setScreen(screen)
    *///?} else {
    setScreen(screen)
    //?}
}

/** True while the player has hidden the HUD with F1. */
fun Minecraft.isHudHidden(): Boolean {
    //? if >=26.2 {
    /*return gui.hud.isHidden()
    *///?} else {
    return options.hideGui
    //?}
}

/** The input type for a keyboard [net.minecraft.client.KeyMapping]. */
fun keyboardInputType(): InputConstants.Type {
    //? if >=26.3 {
    /*return InputConstants.Type.KEYBOARD
    *///?} else {
    return InputConstants.Type.KEYSYM
    //?}
}

/** Open [uri] in the system browser, as a chat link's confirm screen does. */
fun openUri(uri: URI) {
    //? if >=26.3 {
    /*com.mojang.blaze3d.Blaze3D.openUri(uri)
    *///?} else {
    Util.getPlatform().openUri(uri)
    //?}
}
