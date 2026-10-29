package com.dyetracker.menu

import com.dyetracker.api.HistoryDyeGain
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util

/**
 * Draws the in-game menu's History tab below the profile strip: the period [pills] (the selected
 * period's highlighted), then either a [message] centred in the list area (loading, a failure, or
 * [historyStateMessage]'s hint) or the [gains] as rows scrolling in the tab's own viewport. A row
 * has the dye's icon box (as the Dyes tab draws it), `name · work` ([historyRowText]) and the
 * chance right-aligned. Rows aren't clickable and have no hover state.
 */
internal object HistoryTabView {

    /** What the History list area says instead of its rows: [text] in [argb]. */
    data class Message(val text: String, val argb: Int)

    /**
     * The period [pills] (hover-styled under ([mouseX], [mouseY])), then [message] in [geometry]'s
     * viewport when there is one, else [gains] laid out by [geometry] scrolled up by [scroll],
     * skipping rows outside the viewport.
     */
    fun draw(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        font: Font,
        pills: List<MenuPill>,
        message: Message?,
        gains: List<HistoryDyeGain>,
        geometry: HistoryGeometry,
        scroll: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hoveredPill = pillAt(pills, mouseX.toDouble(), mouseY.toDouble())
        for (pill in pills) screen.drawPill(context, pill, hovered = pill == hoveredPill)
        val viewport = geometry.viewport
        if (message != null) {
            // A tiny screen leaves no list area; centring in it would put the message on the pills.
            if (viewport.height > 0) screen.drawMessage(context, message.text, message.argb, viewport)
            return
        }
        val renderCtx = RenderContext(context, Util.getMillis(), false)
        screen.drawScrolled(context, viewport, geometry.contentHeight, scroll) {
            for ((index, gain) in gains.withIndex()) {
                val rowY = MenuLayout.historyRowY(viewport.y, index) - scroll
                if (rowY >= viewport.bottom) break // rows run downwards, so every later one is below too
                if (rowY + MenuLayout.HISTORY_ROW_HEIGHT <= viewport.y) continue
                drawRow(screen, context, renderCtx, font, gain, viewport.x, rowY, viewport.right)
            }
        }
    }

    /**
     * One dye's gain with its top at [y] between [left] and [right]: the icon box, then the left
     * text fitted to end before the chance, which is right-aligned at [right]; both centred on the box.
     */
    private fun drawRow(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        renderCtx: RenderContext,
        font: Font,
        gain: HistoryDyeGain,
        left: Int,
        y: Int,
        right: Int,
    ) {
        screen.drawIconBox(context, renderCtx, gain.dyeId, left, y)
        val (leftText, chanceText) = historyRowText(gain)
        val textY = y + (MenuLayout.ICON_BOX - font.lineHeight) / 2
        val chanceX = right - font.width(chanceText)
        UiDraw.drawText(context, font, chanceText, chanceX, textY, UiTheme.Colors.TEXT_SUBTLE)
        val textX = left + MenuLayout.ICON_BOX + MenuLayout.TEXT_GAP
        val text = MenuCards.fitText(leftText, chanceX - MenuLayout.TEXT_GAP - textX) { font.width(it) }
        UiDraw.drawText(context, font, text, textX, textY, UiTheme.Colors.TEXT_PRIMARY)
    }
}
