package com.dyetracker.menu

import com.dyetracker.dyeprogress.DyeProgressWidgetView
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Rect
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Draws the in-game menu's Collection tab once its dyes and collection are loaded, like the
 * website's Collection page: the `N of M Dyes` summary and its percent over an emerald progress
 * bar, the [MenuCollection.EMPTY_HINT] when nothing is owned, the filter pills (category chips
 * with owned/total, then All / Owned / Unowned), then a card per filtered dye in the
 * [DyeTrackerMenuScreen]'s grid, or [MenuCollection.emptyFilterMessage] when the filters leave
 * none, then the drop timeline ([MenuTimeline]) of every owned dye with a date, scrolling with the
 * grid. An owned card has an emerald border and a `✓` / `xN` badge; a card not owned is dimmed.
 * Cards aren't clickable and have no hover state.
 */
internal object CollectionTabView {

    private val OWNED_BADGE_COLORS = DyeTrackerMenuScreen.BadgeColors(
        UiTheme.Colors.OWNED_TEXT,
        UiTheme.Colors.OWNED_BG,
        UiTheme.Colors.OWNED_BORDER,
    )

    /**
     * The summary of all [cells], the [pills] (hover-styled under ([mouseX], [mouseY]); an
     * unselected chip of a category whose dyes are all owned in emerald text), and the [visible]
     * cards laid out by [geometry] in [columns] columns, then the drop timeline of [cells] below
     * them, both scrolled up by [scroll]. With no [visible] cards, [ownedFilter]'s empty message
     * goes in the grid's band above the timeline, or centred in the grid area when there is no
     * timeline.
     */
    fun draw(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        font: Font,
        cells: List<CollectionCell>,
        visible: List<CollectionCell>,
        pills: List<MenuPill>,
        ownedFilter: OwnedFilter,
        geometry: CollectionGeometry,
        columns: Int,
        scroll: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val viewport = geometry.viewport
        val renderCtx = RenderContext(context, Util.getMillis(), false)
        drawSummary(context, renderCtx, font, MenuCollection.summary(cells), geometry)
        geometry.hintY?.let { hintY ->
            val hint = MenuCards.fitText(MenuCollection.EMPTY_HINT, viewport.width) { font.width(it) }
            UiDraw.drawText(
                context,
                font,
                hint,
                viewport.x,
                hintY,
                UiTheme.Colors.TEXT_SUBTLE,
                availableWidth = viewport.width,
                alignment = HorizontalAlignment.CENTER,
            )
        }
        drawPills(screen, context, cells, pills, mouseX, mouseY)
        val emptyMessage = MenuCollection.emptyFilterMessage(ownedFilter)
        val timelineY = geometry.timelineY
        if (visible.isEmpty() && timelineY == null) {
            // A tiny screen leaves no grid area; centring in it would put the message on the pills.
            if (viewport.height > 0) screen.drawMessage(context, emptyMessage, UiTheme.Colors.TEXT_SUBTLE, viewport)
            return
        }
        val drawBelow = {
            val gridTop = viewport.y - scroll
            if (visible.isEmpty()) {
                val band = Rect(viewport.x, gridTop, viewport.width, geometry.gridHeight)
                screen.drawMessage(context, emptyMessage, UiTheme.Colors.TEXT_SUBTLE, band)
            }
            if (timelineY != null) {
                drawTimeline(screen, context, font, MenuTimeline.timeline(cells), timelineY - scroll, viewport)
            }
        }
        screen.drawCardGrid(
            context,
            visible.size,
            columns,
            viewport.x,
            viewport,
            geometry.contentHeight,
            scroll,
            drawBelow,
        ) { index, x, y ->
            drawCard(screen, context, renderCtx, font, visible[index], x, y)
        }
    }

    /**
     * The drop timeline from [headingY] (already scrolled) inside the clipped [viewport]: the
     * heading, then a row per [rows] entry, skipping rows outside the viewport.
     */
    private fun drawTimeline(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        font: Font,
        rows: List<TimelineRow>,
        headingY: Int,
        viewport: Rect,
    ) {
        UiDraw.drawText(context, font, MenuTimeline.HEADING, viewport.x, headingY, UiTheme.Colors.TEXT_PRIMARY)
        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        for ((index, row) in rows.withIndex()) {
            val rowY = MenuLayout.timelineRowY(headingY, font.lineHeight, index)
            if (rowY >= viewport.bottom) break // rows run downwards, so every later row is below too
            if (rowY + MenuLayout.TIMELINE_ROW_HEIGHT <= viewport.y) continue
            drawTimelineRow(screen, context, font, MenuTimeline.timelineText(row, nowMs, zone), row, rowY, viewport)
        }
    }

    /**
     * One timeline row at [rowY] across [viewport]: a square dot and the dye name in its accent,
     * then the small muted [text] (date and relative time), each fitted to what width is left.
     */
    private fun drawTimelineRow(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        font: Font,
        text: String,
        row: TimelineRow,
        rowY: Int,
        viewport: Rect,
    ) {
        val rowHeight = MenuLayout.TIMELINE_ROW_HEIGHT
        val dot = MenuLayout.TIMELINE_DOT
        val accent = DyeProgressWidgetView.parseAccentColor(row.color)
        UiDraw.fillRect(context, viewport.x, rowY + (rowHeight - dot) / 2, dot, dot, accent)

        val nameX = viewport.x + dot + MenuLayout.TEXT_GAP
        val name = MenuCards.fitText(row.name, viewport.right - nameX) { font.width(it) }
        UiDraw.drawText(context, font, name, nameX, rowY + (rowHeight - font.lineHeight) / 2, accent)

        val textX = nameX + font.width(name) + MenuLayout.TEXT_GAP
        val fitted = MenuCards.fitText(text, viewport.right - textX) {
            screen.scaledWidth(it, MenuLayout.SMALL_TEXT_SCALE)
        }
        val smallLineHeight = (font.lineHeight * MenuLayout.SMALL_TEXT_SCALE).roundToInt()
        UiDraw.drawText(
            context,
            font,
            fitted,
            textX,
            rowY + (rowHeight - smallLineHeight) / 2,
            UiTheme.Colors.TEXT_MUTED,
            scale = MenuLayout.SMALL_TEXT_SCALE,
        )
    }

    /**
     * [pills], the one under ([mouseX], [mouseY]) hover-styled. A selected pill takes the selected
     * style; an unselected chip whose category of [cells] is all owned has emerald text.
     */
    private fun drawPills(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        cells: List<CollectionCell>,
        pills: List<MenuPill>,
        mouseX: Int,
        mouseY: Int,
    ) {
        val complete = MenuCollection.categoryCounts(cells).filter { it.owned == it.total }.map { it.category }.toSet()
        val hovered = pillAt(pills, mouseX.toDouble(), mouseY.toDouble())
        for (pill in pills) {
            val style = when {
                pill.selected -> DyeTrackerMenuScreen.PillStyle.SELECTED
                pill.key in complete -> DyeTrackerMenuScreen.PillStyle.COMPLETE
                else -> DyeTrackerMenuScreen.PillStyle.UNSELECTED
            }
            screen.drawPill(context, pill, hovered = pill == hovered, style)
        }
    }

    /** `N of M Dyes` then its percent on one line, and below it the full-width progress bar. */
    private fun drawSummary(
        context: GuiGraphicsExtractor,
        renderCtx: RenderContext,
        font: Font,
        summary: MenuCollection.CollectionSummary,
        geometry: CollectionGeometry,
    ) {
        val left = geometry.viewport.x
        val summaryText = MenuCollection.summaryText(summary)
        UiDraw.drawText(context, font, summaryText, left, geometry.summaryY, UiTheme.Colors.TEXT_PRIMARY)
        UiDraw.drawText(
            context,
            font,
            MenuCollection.percentText(summary),
            left + font.width(summaryText) + MenuLayout.STRIP_ITEM_GAP,
            geometry.summaryY,
            UiTheme.Colors.TEXT_SUBTLE,
        )
        ProgressBarWidget(
            value = MenuCollection.percent(summary).toFloat(),
            width = geometry.viewport.width,
            height = UiTheme.Sizing.PROGRESS_BAR_HEIGHT,
            fillColor = UiTheme.Colors.COLLECTION_ACCENT,
        ).draw(renderCtx, left, geometry.barY)
    }

    /**
     * One card: the icon box with the sprite, the name in the dye's accent color and the category
     * under it. Owned: an emerald border and the [MenuCollection.ownedBadge] top-right. Not
     * owned: the usual border, then the whole card dimmed.
     */
    private fun drawCard(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        renderCtx: RenderContext,
        font: Font,
        cell: CollectionCell,
        x: Int,
        y: Int,
    ) {
        val entry = cell.entry
        val padding = MenuLayout.CARD_PADDING
        UiDraw.filledBox(
            context,
            x,
            y,
            MenuLayout.CARD_WIDTH,
            MenuLayout.CARD_HEIGHT,
            UiTheme.Colors.CARD_BACKGROUND,
            if (cell.owned) UiTheme.Colors.OWNED_BORDER else UiTheme.Colors.CARD_BORDER,
        )
        screen.drawIconBox(context, renderCtx, entry.dyeId, x + padding, y + padding)
        val textRight = x + MenuLayout.CARD_WIDTH - padding
        val nameRight = if (cell.owned) {
            val badgeLeft = screen.drawBadge(
                context,
                MenuCollection.ownedBadge(cell.count),
                OWNED_BADGE_COLORS,
                textRight,
                y + padding,
            )
            badgeLeft - MenuLayout.TEXT_GAP
        } else {
            textRight
        }

        val textX = x + padding + MenuLayout.ICON_BOX + MenuLayout.TEXT_GAP
        val nameY = y + padding + MenuLayout.NAME_TOP_OFFSET
        val name = MenuCards.fitText(entry.name, nameRight - textX) { font.width(it) }
        UiDraw.drawText(context, font, name, textX, nameY, DyeProgressWidgetView.parseAccentColor(entry.color))
        val category = MenuCards.fitText(MenuCards.formatCategory(entry.category), textRight - textX) {
            screen.scaledWidth(it, MenuLayout.SMALL_TEXT_SCALE)
        }
        UiDraw.drawText(
            context,
            font,
            category,
            textX,
            nameY + font.lineHeight,
            UiTheme.Colors.TEXT_MUTED,
            scale = MenuLayout.SMALL_TEXT_SCALE,
        )

        if (!cell.owned) {
            UiDraw.fillRect(context, x, y, MenuLayout.CARD_WIDTH, MenuLayout.CARD_HEIGHT, UiTheme.Colors.UNOWNED_DIM)
        }
    }
}
