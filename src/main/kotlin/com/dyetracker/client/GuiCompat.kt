package com.dyetracker.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Screen and HUD-visibility access across the 26.x nodes. Minecraft 26.2 moved this state off
 * [Minecraft] onto its `gui` field (`Gui`): `setScreen` → `gui.setScreen`, the `screen` field →
 * `gui.screen()`, and F1's `options.hideGui` → `gui.hud.isHidden()`.
 *
 * Call these instead of the vanilla members so the version switch lives here only. The
 * `//?` lines are Stonecutter version comments; see
 * docs/delivery/53/research/53-1-26.2-toolchain-guide.md.
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
