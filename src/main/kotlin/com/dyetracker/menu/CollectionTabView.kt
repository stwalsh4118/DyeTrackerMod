package com.dyetracker.menu

import com.dyetracker.dyeprogress.DyeProgressWidgetView
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util

/**
 * Draws the in-game menu's Collection tab once its dyes and collection are loaded, like the
 * website's Collection page: the `N of M Dyes` summary and its percent over an emerald progress
 * bar, the [MenuCollection.EMPTY_HINT] when nothing is owned, then a card per dye in the
 * [DyeTrackerMenuScreen]'s grid. An owned card has an emerald border and a `✓` / `xN` badge; a
 * card not owned is dimmed. Cards aren't clickable and have no hover state.
 */
internal object CollectionTabView {

    private val OWNED_BADGE_COLORS = DyeTrackerMenuScreen.BadgeColors(
        UiTheme.Colors.OWNED_TEXT,
        UiTheme.Colors.OWNED_BG,
        UiTheme.Colors.OWNED_BORDER,
    )

    /** [cells] laid out by [geometry] in [columns] columns, the grid scrolled up by [scroll]. */
    fun draw(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        font: Font,
        cells: List<CollectionCell>,
        geometry: CollectionGeometry,
        columns: Int,
        scroll: Int,
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
        screen.drawCardGrid(context, cells.size, columns, viewport.x, viewport, geometry.contentHeight, scroll) { index, x, y ->
            drawCard(screen, context, renderCtx, font, cells[index], x, y)
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
