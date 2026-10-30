package com.dyetracker.ui.theme

import com.dyetracker.config.ConfigManager
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * The Theme screen's HUD opacity slider (PBI 81), a vanilla slider over `0..1` for the HUD panel
 * alpha `0..255`, labeled `HUD opacity: 78%` ([hudOpacityLabel]). It starts at the live
 * [ThemeState.hudPanelAlpha], so a rebuilt screen keeps an unsaved drag. Moving it sets only
 * [ThemeState.hudPanelAlpha], so the HUD panels follow live with no disk write per step; releasing
 * the mouse or an arrow key step saves it ([saveHudOpacity]), as does closing the screen. It never
 * touches the theme.
 */
class HudOpacitySlider(x: Int, y: Int, width: Int, height: Int) : AbstractSliderButton(
    x,
    y,
    width,
    height,
    Component.literal(hudOpacityLabel(ThemeState.hudPanelAlpha)),
    alphaToSliderValue(ThemeState.hudPanelAlpha),
) {

    override fun updateMessage() {
        message = Component.literal(hudOpacityLabel(sliderValueToAlpha(value)))
    }

    override fun applyValue() {
        ThemeState.hudPanelAlpha = sliderValueToAlpha(value)
    }

    /** The drag (or click) ended: save the alpha. */
    override fun onRelease(event: MouseButtonEvent) {
        super.onRelease(event)
        saveHudOpacity()
    }

    /** An arrow key moves the slider a step with no release to follow, so save it here. */
    override fun keyPressed(event: KeyEvent): Boolean {
        val handled = super.keyPressed(event)
        if (handled) saveHudOpacity()
        return handled
    }
}

/**
 * Save the live [ThemeState.hudPanelAlpha] ([ConfigManager.setHudPanelAlpha]) when it differs from
 * the saved one, so the config file is written once per change and never for nothing.
 */
fun saveHudOpacity() {
    val live = ThemeState.hudPanelAlpha
    if (live != ConfigManager.hudPanelAlpha()) ConfigManager.setHudPanelAlpha(live)
}
