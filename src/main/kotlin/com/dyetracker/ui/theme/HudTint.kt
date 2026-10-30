package com.dyetracker.ui.theme

/** A panel fill and border (ARGB) that replace the neutral panel tokens while a tint is on. */
data class TintColors(val background: Int, val border: Int)

/**
 * The runtime panel colors for HUD widgets that opt in to a tint. A feature sets [colors] each
 * frame before the widgets are built (widget trees are rebuilt every frame, so they pick it up on
 * the same frame); null gives the neutral [UiTheme.Colors.PANEL_BACKGROUND] /
 * [UiTheme.Colors.PANEL_BORDER]. The fill takes the HUD opacity ([ThemeState.hudPanelAlpha]),
 * tinted or not; the border keeps its own alpha. Written and read on the render thread; volatile so
 * a read from any other thread sees a whole value. Feature-agnostic: it knows nothing about where a
 * tint comes from.
 */
object HudTint {

    @Volatile
    var colors: TintColors? = null

    /** The panel fill: the tint's background, or the neutral token, at the HUD opacity. */
    fun panelBackground(): Int =
        themeWithAlpha(ThemeState.hudPanelAlpha, colors?.background ?: UiTheme.Colors.PANEL_BACKGROUND)

    /** The panel border: the tint's border, or the neutral token. */
    fun panelBorder(): Int = colors?.border ?: UiTheme.Colors.PANEL_BORDER
}
