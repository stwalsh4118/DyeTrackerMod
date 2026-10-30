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
 * the mouse, or the key that moved it, saves the slider's own value ([saveSliderValue]), so holding
 * an arrow key saves once. Closing the screen saves too. It never touches the theme.
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

    /** A key press moved the slider and its release hasn't saved it yet. */
    private var keyMoveUnsaved = false

    override fun applyValue() {
        ThemeState.hudPanelAlpha = sliderValueToAlpha(value)
    }

    /** The drag (or click) ended: save the slider's value. */
    override fun onRelease(event: MouseButtonEvent) {
        super.onRelease(event)
        saveSliderValue()
    }

    /** An arrow key moves the slider a step, once per auto-repeat while held; [keyReleased] saves. */
    override fun keyPressed(event: KeyEvent): Boolean {
        val handled = super.keyPressed(event)
        if (handled) keyMoveUnsaved = true
        return handled
    }

    /** The key that moved the slider was released: save its value, once per key gesture. */
    override fun keyReleased(event: KeyEvent): Boolean {
        if (keyMoveUnsaved) {
            keyMoveUnsaved = false
            saveSliderValue()
        }
        return super.keyReleased(event)
    }

    /**
     * Set [ThemeState.hudPanelAlpha] from the slider's own [value] ([applyValue]), since a config
     * update mid-drag can reset it to the saved alpha, then save it ([saveHudOpacity]).
     */
    private fun saveSliderValue() {
        applyValue()
        saveHudOpacity()
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
