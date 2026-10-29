package com.dyetracker.menu

import com.dyetracker.api.HistoryBucket
import com.dyetracker.api.HistoryDyeGain
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Rect
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import java.time.ZoneId

/**
 * Draws the in-game menu's History tab below the profile strip: the period [pills] (the selected
 * period's highlighted), then either a [message] centred in the list area (loading, a failure, or
 * [historyStateMessage]'s hint) or the selected dye's chart panel over the [gains] as rows
 * scrolling in the tab's own viewport. A row has the dye's icon box (as the Dyes tab draws it),
 * `name · work` ([historyRowText]) and the chance right-aligned; the selected row sits on a card.
 * The chart shows the selected dye's work per time slice as bars in its color, or what it says
 * instead; the hovered slice's [barTooltip] goes at the right end of its title line, like the
 * Dyes tab's pin hint.
 */
internal object HistoryTabView {

    /** What the History list area or chart says instead of its rows or bars: [text] in [argb]. */
    data class Message(val text: String, val argb: Int)

    /**
     * The selected dye's chart: its [title] ([chartTitle]), then [message] when there is one
     * (loading, a failure, [chartMessage]), else the [buckets] as bars in [argb].
     */
    data class Chart(val title: String, val message: Message?, val buckets: List<HistoryBucket>, val argb: Int)

    /**
     * The period [pills] (hover-styled under ([mouseX], [mouseY])), then [message] in [geometry]'s
     * viewport when there is one, else the [chart] in [geometry]'s chart rect and [gains] laid out by
     * [geometry] scrolled up by [scroll], skipping rows outside the viewport; the row for
     * [selectedDyeId] is highlighted.
     */
    fun draw(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        font: Font,
        pills: List<MenuPill>,
        message: Message?,
        chart: Chart?,
        gains: List<HistoryDyeGain>,
        selectedDyeId: String?,
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
        val chartRect = geometry.chart
        if (chart != null && chartRect != null) drawChart(screen, context, font, chart, chartRect, mouseX, mouseY)
        val renderCtx = RenderContext(context, Util.getMillis(), false)
        screen.drawScrolled(context, viewport, geometry.contentHeight, scroll) {
            for ((index, gain) in gains.withIndex()) {
                val rowY = MenuLayout.historyRowY(viewport.y, index) - scroll
                if (rowY >= viewport.bottom) break // rows run downwards, so every later one is below too
                if (rowY + MenuLayout.HISTORY_ROW_HEIGHT <= viewport.y) continue
                val selected = gain.dyeId == selectedDyeId
                drawRow(screen, context, renderCtx, font, gain, viewport.x, rowY, viewport.right, selected)
            }
        }
    }

    /**
     * [chart] as a card in [rect]: the title on its first line, then its message centred in the bar
     * area ([MenuLayout.historyBarArea]) or its bars ([barHeights], [barRects]); a `null` bucket
     * draws nothing. The slice under ([mouseX], [mouseY]) in the bar area puts its [barTooltip] at
     * the right end of the title line, the title fitted before it.
     */
    private fun drawChart(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        font: Font,
        chart: Chart,
        rect: Rect,
        mouseX: Int,
        mouseY: Int,
    ) {
        UiDraw.filledBox(
            context,
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            UiTheme.Colors.CARD_BACKGROUND,
            UiTheme.Colors.CARD_BORDER,
        )
        val area = MenuLayout.historyBarArea(rect, font.lineHeight)
        var tooltip: String? = null
        if (chart.message != null) {
            screen.drawMessage(context, chart.message.text, chart.message.argb, area)
        } else {
            val heights = barHeights(chart.buckets.map { it.work }, area.height)
            for (bar in barRects(heights, area.x, area.bottom, area.width, MenuLayout.HISTORY_BAR_GAP)) {
                if (bar != null) UiDraw.fillRect(context, bar.x, bar.y, bar.width, bar.height, chart.argb)
            }
            if (area.contains(mouseX, mouseY)) {
                tooltip = barSlotAt(mouseX.toDouble(), area.x, area.width, chart.buckets.size)
                    ?.let { barTooltip(chart.buckets[it], ZoneId.systemDefault()) }
            }
        }
        val titleY = rect.y + MenuLayout.CARD_PADDING
        var titleWidth = area.width
        if (tooltip != null) {
            UiDraw.drawText(
                context,
                font,
                tooltip,
                area.x,
                titleY,
                UiTheme.Colors.ACCENT_SECONDARY,
                availableWidth = area.width,
                alignment = HorizontalAlignment.END,
            )
            titleWidth = area.width - font.width(tooltip) - MenuLayout.TEXT_GAP
        }
        val title = MenuCards.fitText(chart.title, titleWidth) { font.width(it) }
        UiDraw.drawText(context, font, title, area.x, titleY, UiTheme.Colors.TEXT_PRIMARY)
    }

    /**
     * One dye's gain with its top at [y] between [left] and [right]: on a card when [selected], the
     * icon box, then the left text fitted to end before the chance, which is right-aligned at
     * [right]; both centred on the box.
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
        selected: Boolean,
    ) {
        if (selected) {
            UiDraw.filledBox(
                context,
                left,
                y,
                right - left,
                MenuLayout.ICON_BOX,
                UiTheme.Colors.CARD_BACKGROUND,
                UiTheme.Colors.PILL_HOVER_BORDER,
            )
        }
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
