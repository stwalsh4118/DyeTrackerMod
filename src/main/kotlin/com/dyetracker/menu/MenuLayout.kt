package com.dyetracker.menu

import com.dyetracker.ui.core.Rect
import com.dyetracker.ui.theme.UiTheme
import kotlin.math.floor
import kotlin.math.max

/**
 * Where the Collection tab's parts sit below the profile strip: the summary line at [summaryY],
 * the progress bar at [barY], the empty hint at [hintY] (`null` when it doesn't show), the filter
 * [pills] (`key to rect`, as [layoutPills] places them), and the scrolling [viewport] with its
 * [contentHeight]: the card grid ([gridHeight] tall, its unscrolled top `viewport.y`), then the
 * drop timeline's heading at the unscrolled [timelineY] (`null` when there is no timeline).
 */
data class CollectionGeometry(
    val summaryY: Int,
    val barY: Int,
    val hintY: Int?,
    val pills: List<Pair<String, Rect>>,
    val viewport: Rect,
    val contentHeight: Int,
    val gridHeight: Int,
    val timelineY: Int?,
)

/** Where a Dyes card's category-line badge starts ([badgeLeft]) and how wide the category text may be ([categoryWidth]). */
data class CategoryLine(val badgeLeft: Int, val categoryWidth: Int)

/**
 * Where the Achievements tab's parts sit below the profile strip: the summary line at [summaryY],
 * the progress bar at [barY], the status [pills] (`key to rect`, as [layoutPills] places them),
 * and the scrolling [viewport] with its [contentHeight]: one section per [sectionTops] entry, its
 * header at that unscrolled y and its rows below it ([MenuLayout.achievementRowY]).
 */
data class AchievementsGeometry(
    val summaryY: Int,
    val barY: Int,
    val pills: List<Pair<String, Rect>>,
    val viewport: Rect,
    val contentHeight: Int,
    val sectionTops: List<Int>,
)

/**
 * Where the History tab's parts sit below the profile strip: the period [pills] (`key to rect`, as
 * [layoutPills] places them), the selected dye's [chart] panel (`null` without rows), and the
 * scrolling [viewport] with its [contentHeight]: one row per dye, each at [MenuLayout.historyRowY].
 */
data class HistoryGeometry(val pills: List<Pair<String, Rect>>, val chart: Rect?, val viewport: Rect, val contentHeight: Int)

/** Sizes (GUI pixels) and card-grid geometry for the in-game DyeTracker menu. */
object MenuLayout {
    /** Gap between the screen edges / header and the content. */
    const val OUTER_MARGIN = 16

    /** Height of the header bar (title + tab bar). */
    const val HEADER_HEIGHT = 28

    /** Width of one dye card. */
    const val CARD_WIDTH = 160

    /** Height of one dye card. */
    const val CARD_HEIGHT = 70

    /** Gap between neighbouring cards, both axes. */
    const val CARD_GAP = 8

    /** Inner padding of a card. */
    const val CARD_PADDING = 6

    /** Most card columns shown, however wide the screen. */
    const val MAX_COLUMNS = 6

    /** Horizontal gap between the header's title and its tab pills. */
    const val TAB_GAP = 16

    /** Side of the black icon box in a card's top-left corner. */
    const val ICON_BOX = 28

    /** Side of the dye sprite, centred in the icon box. */
    const val ICON_SPRITE = 24

    /** Text scale of a card's status badge label. */
    const val BADGE_SCALE = 0.75f

    /** Text scale of a card's small text (category, status label, value). */
    const val SMALL_TEXT_SCALE = 0.75f

    /** Padding between a badge's border and its scaled label. */
    const val BADGE_PADDING = 2

    /** Gap between neighbouring card texts (icon to name, name to badge, value line to bar). */
    const val TEXT_GAP = 4

    /** Offset of a card's name below the icon box's top edge. */
    const val NAME_TOP_OFFSET = 2

    /** Side of a bundled dye sprite PNG, the source size for the stretched blit. */
    const val SPRITE_SOURCE_SIZE = 300

    /** Pixels scrolled per mouse-wheel notch. */
    const val SCROLL_STEP = 20

    /** Width of the scrollbar track, drawn [SCROLLBAR_GAP] to the right of the card grid. */
    const val SCROLLBAR_WIDTH = 2

    /** Gap between the card grid's right edge and the scrollbar. */
    const val SCROLLBAR_GAP = 4

    /** Shortest the scrollbar thumb gets, however long the content. */
    const val SCROLLBAR_MIN_THUMB = 12

    /** Height of a menu pill (category filter, sort). */
    const val PILL_HEIGHT = 16

    /** Padding on each side of a pill's label. */
    const val PILL_PADDING_X = 8

    /** Gap between neighbouring pills, both axes. */
    const val PILL_GAP = 4

    /** Height of the profile strip (name, profile pill, totals) between the header and the control pills. */
    const val STRIP_HEIGHT = 20

    /** Gap between the profile strip and the control pills below it. */
    const val STRIP_GAP = 8

    /** Horizontal gap between the strip's name and its profile pill, and the least before its totals. */
    const val STRIP_ITEM_GAP = TEXT_GAP * 2

    /** How long the message after a card click (pinned, already pinned, can't pin) shows, in ms. */
    const val PIN_MESSAGE_MS = 4_000L

    /** How long the Unlink pill waits for its confirming click, in ms. */
    const val UNLINK_CONFIRM_MS = 3_000L

    /** Widest the Settings tab's panel gets, however wide the screen. */
    const val SETTINGS_WIDTH = 320

    /** Height of one drop timeline row (dot, dye name, date line). */
    const val TIMELINE_ROW_HEIGHT = 14

    /** Side of the square dot, in the dye's accent, at a drop timeline row's left. */
    const val TIMELINE_DOT = 6

    /** Height the Collection grid keeps for its empty-filter message when the drop timeline follows it. */
    const val EMPTY_GRID_HEIGHT = CARD_HEIGHT

    /** Height of one Settings row (label, detail line, control pill). */
    const val SETTINGS_ROW_HEIGHT = 28

    /** Height of a Settings text field (the Spotify Client ID), as tall as the pill beside it. */
    const val SETTINGS_FIELD_HEIGHT = PILL_HEIGHT

    /** Height of one Achievements row (tier pill, name, description, footer). */
    const val ACHIEVEMENT_ROW_HEIGHT = 30

    /** Gap between one Achievements section's last row and the next section's header. */
    const val ACHIEVEMENT_SECTION_GAP = CARD_GAP

    /** Height of the progress bar under a locked achievement's `Progress n / m` footer. */
    const val ACHIEVEMENT_PROGRESS_BAR_HEIGHT = 2

    /** Height of one History row: a dye's icon box and the gap below it. */
    const val HISTORY_ROW_HEIGHT = ICON_BOX + TEXT_GAP

    /** Height of the History tab's chart panel (title line and work bars) above its list. */
    const val HISTORY_CHART_HEIGHT = 56

    /** Gap between neighbouring bars in the History chart. */
    const val HISTORY_BAR_GAP = 2

    /** How long a Settings row's message after a click (unlinked, synced, nothing to sync) shows, in ms. */
    const val SETTINGS_MESSAGE_MS = 4_000L

    /**
     * The Collection tab's layout from [top] down, [gridWidth] wide from [left]: the summary line
     * ([lineHeight] tall), [TEXT_GAP] below it the progress bar, then when [showHint] the hint line
     * [STRIP_GAP] below the bar, [STRIP_GAP] below that the [pillLabels] wrapping within
     * [gridWidth] ([layoutPills], widths by [measure]), and [OUTER_MARGIN] below the last pill row
     * (or the hint or bar, without pills) the grid of [itemCount] cards in [columns] columns, down
     * to [OUTER_MARGIN] above the [screenHeight]. With [timelineRows], the drop timeline's heading
     * goes [OUTER_MARGIN] below the grid (or below an [EMPTY_GRID_HEIGHT] band for the empty-filter
     * message when there are no cards), its rows below it ([timelineRowY]), and both scroll with
     * the grid.
     */
    fun collectionGeometry(
        top: Int,
        left: Int,
        gridWidth: Int,
        screenHeight: Int,
        lineHeight: Int,
        showHint: Boolean,
        pillLabels: List<Pair<String, String>>,
        measure: (String) -> Int,
        itemCount: Int,
        columns: Int,
        timelineRows: Int,
    ): CollectionGeometry {
        val barY = top + lineHeight + TEXT_GAP
        val barBottom = barY + UiTheme.Sizing.PROGRESS_BAR_HEIGHT
        val hintY = if (showHint) barBottom + STRIP_GAP else null
        val hintOrBarBottom = hintY?.let { it + lineHeight } ?: barBottom
        val pills = layoutPills(pillLabels, measure, left, hintOrBarBottom + STRIP_GAP, gridWidth)
        val gridTop = (pills.maxOfOrNull { it.second.bottom } ?: hintOrBarBottom) + OUTER_MARGIN
        val gridHeight = when {
            itemCount > 0 -> contentHeight(itemCount, columns)
            timelineRows > 0 -> EMPTY_GRID_HEIGHT
            else -> 0
        }
        val timelineY = if (timelineRows > 0) gridTop + gridHeight + OUTER_MARGIN else null
        val contentBottom = timelineY?.let { timelineRowY(it, lineHeight, timelineRows) } ?: (gridTop + gridHeight)
        return CollectionGeometry(
            summaryY = top,
            barY = barY,
            hintY = hintY,
            pills = pills,
            viewport = Rect(left, gridTop, gridWidth, screenHeight - gridTop - OUTER_MARGIN),
            contentHeight = contentBottom - gridTop,
            gridHeight = gridHeight,
            timelineY = timelineY,
        )
    }

    /**
     * A Dyes card's category line in the text column [textLeft]..[textRight] with a badge
     * [badgeWidth] wide: the badge right-aligned at [textRight] but never left of [textLeft], and
     * the category text up to [TEXT_GAP] before it (`0` when nothing is left).
     */
    fun categoryLine(textLeft: Int, textRight: Int, badgeWidth: Int): CategoryLine {
        val badgeLeft = max(textLeft, textRight - badgeWidth)
        return CategoryLine(badgeLeft, categoryWidth = max(0, badgeLeft - TEXT_GAP - textLeft))
    }

    /** Top of drop timeline row [index] under a heading line ([lineHeight] tall) at [headingY]. */
    fun timelineRowY(headingY: Int, lineHeight: Int, index: Int): Int =
        headingY + lineHeight + TEXT_GAP + index * TIMELINE_ROW_HEIGHT

    /**
     * The Achievements tab's layout from [top] down, [width] wide from [left]: the summary line
     * ([lineHeight] tall), [TEXT_GAP] below it the progress bar, [STRIP_GAP] below the bar the
     * [pillLabels] wrapping within [width] ([layoutPills], widths by [measure]), and [OUTER_MARGIN]
     * below the last pill row (or the bar, without pills) the scrolling list down to
     * [OUTER_MARGIN] above the [screenHeight]: a section per [sectionSizes] entry (a header line,
     * then that many [ACHIEVEMENT_ROW_HEIGHT] rows), sections [ACHIEVEMENT_SECTION_GAP] apart.
     */
    fun achievementsGeometry(
        top: Int,
        left: Int,
        width: Int,
        screenHeight: Int,
        lineHeight: Int,
        pillLabels: List<Pair<String, String>>,
        measure: (String) -> Int,
        sectionSizes: List<Int>,
    ): AchievementsGeometry {
        val barY = top + lineHeight + TEXT_GAP
        val barBottom = barY + UiTheme.Sizing.PROGRESS_BAR_HEIGHT
        val pills = layoutPills(pillLabels, measure, left, barBottom + STRIP_GAP, width)
        val listTop = (pills.maxOfOrNull { it.second.bottom } ?: barBottom) + OUTER_MARGIN
        val sectionTops = mutableListOf<Int>()
        var bottom = listTop
        for (size in sectionSizes) {
            val sectionTop = if (sectionTops.isEmpty()) listTop else bottom + ACHIEVEMENT_SECTION_GAP
            sectionTops += sectionTop
            bottom = achievementRowY(sectionTop, lineHeight, size)
        }
        return AchievementsGeometry(
            summaryY = top,
            barY = barY,
            pills = pills,
            viewport = Rect(left, listTop, width, screenHeight - listTop - OUTER_MARGIN),
            contentHeight = bottom - listTop,
            sectionTops = sectionTops,
        )
    }

    /** Top of achievement row [index] in a section whose header line ([lineHeight] tall) is at [sectionTop]. */
    fun achievementRowY(sectionTop: Int, lineHeight: Int, index: Int): Int =
        sectionTop + lineHeight + TEXT_GAP + index * ACHIEVEMENT_ROW_HEIGHT

    /**
     * The History tab's layout from [top] down, [width] wide from [left]: the period [pillLabels]
     * wrapping within [width] ([layoutPills], widths by [measure]); [OUTER_MARGIN] below the last
     * pill row (or [top], without pills) the [HISTORY_CHART_HEIGHT] chart when there are rows; and
     * [OUTER_MARGIN] below the chart (or where it would start, without rows) the scrolling list of
     * [rowCount] [HISTORY_ROW_HEIGHT] rows down to [OUTER_MARGIN] above the [screenHeight].
     */
    fun historyGeometry(
        top: Int,
        left: Int,
        width: Int,
        screenHeight: Int,
        pillLabels: List<Pair<String, String>>,
        measure: (String) -> Int,
        rowCount: Int,
    ): HistoryGeometry {
        val pills = layoutPills(pillLabels, measure, left, top, width)
        val chartTop = (pills.maxOfOrNull { it.second.bottom } ?: top) + OUTER_MARGIN
        val chart = if (rowCount > 0) Rect(left, chartTop, width, HISTORY_CHART_HEIGHT) else null
        val listTop = chart?.let { it.bottom + OUTER_MARGIN } ?: chartTop
        return HistoryGeometry(
            pills = pills,
            chart = chart,
            viewport = Rect(left, listTop, width, screenHeight - listTop - OUTER_MARGIN),
            contentHeight = historyRowY(listTop, rowCount) - listTop,
        )
    }

    /** Top of History row [index] in a list whose unscrolled top is [listTop]. */
    fun historyRowY(listTop: Int, index: Int): Int = listTop + index * HISTORY_ROW_HEIGHT

    /**
     * Where the bars sit in the History [chart]: inside its [CARD_PADDING], [TEXT_GAP] below its
     * title line ([lineHeight] tall); `0` tall when the chart leaves no room.
     */
    fun historyBarArea(chart: Rect, lineHeight: Int): Rect {
        val barTop = chart.y + CARD_PADDING + lineHeight + TEXT_GAP
        return Rect(
            chart.x + CARD_PADDING,
            barTop,
            chart.width - 2 * CARD_PADDING,
            max(0, chart.bottom - CARD_PADDING - barTop),
        )
    }

    /**
     * Index of the History row under ([mouseX], [mouseY]) in [viewport], its [rowCount] rows
     * scrolled up by [scroll]; `null` outside the viewport or past the last row. A row takes its
     * whole [HISTORY_ROW_HEIGHT], the gap below its icon box too; the viewport is half-open like
     * [cardIndexAt]'s.
     */
    fun historyRowIndexAt(mouseX: Double, mouseY: Double, viewport: Rect, rowCount: Int, scroll: Int): Int? {
        val inViewport = mouseX >= viewport.x && mouseX < viewport.right &&
            mouseY >= viewport.y && mouseY < viewport.bottom
        if (!inViewport) return null
        val index = floor((mouseY - (viewport.y - scroll)) / HISTORY_ROW_HEIGHT).toInt()
        return if (index in 0 until rowCount) index else null
    }

    /** Horizontal distance from one card's left edge to the next column's. */
    private const val COLUMN_PITCH = CARD_WIDTH + CARD_GAP

    /** Vertical distance from one card's top edge to the next row's. */
    private const val ROW_PITCH = CARD_HEIGHT + CARD_GAP

    /** Card columns that fit in [availableWidth], from 1 to [MAX_COLUMNS]. */
    fun columns(availableWidth: Int): Int =
        ((availableWidth + CARD_GAP) / COLUMN_PITCH).coerceIn(1, MAX_COLUMNS)

    /** Width of a grid of [columns] cards, gaps included. */
    fun gridWidth(columns: Int): Int = columns * CARD_WIDTH + (columns - 1) * CARD_GAP

    /** Top-left of card [index] in a [columns]-wide grid whose top-left is ([gridLeft], [gridTop]). */
    fun cardOrigin(index: Int, columns: Int, gridLeft: Int, gridTop: Int): Pair<Int, Int> = Pair(
        gridLeft + (index % columns) * COLUMN_PITCH,
        gridTop + (index / columns) * ROW_PITCH,
    )

    /** Height of [itemCount] cards in a [columns]-wide grid, row gaps included; `0` for no items. */
    fun contentHeight(itemCount: Int, columns: Int): Int {
        if (itemCount <= 0) return 0
        val rows = (itemCount + columns - 1) / columns
        return rows * CARD_HEIGHT + (rows - 1) * CARD_GAP
    }

    /** [scroll] limited to `0..(contentHeight - viewportHeight)`; `0` when the content fits. */
    fun clampScroll(scroll: Int, contentHeight: Int, viewportHeight: Int): Int =
        scroll.coerceIn(0, max(0, contentHeight - viewportHeight))

    /**
     * How far the Settings panel scrolls: how much [rowCount] rows [rowHeight] tall overflow
     * [availableHeight]; `0` when they fit.
     */
    fun settingsMaxScroll(rowCount: Int, rowHeight: Int, availableHeight: Int): Int =
        max(0, rowCount * rowHeight - availableHeight)

    /** [value] limited to `0..max`; `0` when [max] isn't positive. */
    fun clampScroll(value: Int, max: Int): Int = value.coerceAtMost(max).coerceAtLeast(0)

    /**
     * Index of the card under ([mouseX], [mouseY]) in a [columns]-wide grid of [itemCount] cards
     * whose unscrolled top-left is ([gridLeft], [gridTop]), scrolled up by [scroll]; `null` outside
     * [viewport], in a gap, or past the last card. Card bounds are half-open (`[x, x + CARD_WIDTH)`,
     * `[y, y + CARD_HEIGHT)`), so a point on a card's right or bottom edge belongs to the gap; the
     * viewport is half-open too, so a point on its right or bottom edge misses a clipped card.
     */
    fun cardIndexAt(
        mouseX: Double,
        mouseY: Double,
        itemCount: Int,
        columns: Int,
        gridLeft: Int,
        gridTop: Int,
        scroll: Int,
        viewport: Rect,
    ): Int? {
        val inViewport = mouseX >= viewport.x && mouseX < viewport.right &&
            mouseY >= viewport.y && mouseY < viewport.bottom
        if (!inViewport) return null
        val localX = mouseX - gridLeft
        val localY = mouseY - (gridTop - scroll)
        if (localX < 0 || localY < 0) return null
        val column = floor(localX / COLUMN_PITCH).toInt()
        val row = floor(localY / ROW_PITCH).toInt()
        if (column >= columns) return null
        if (localX - column * COLUMN_PITCH >= CARD_WIDTH) return null
        if (localY - row * ROW_PITCH >= CARD_HEIGHT) return null
        val index = row * columns + column
        return if (index < itemCount) index else null
    }
}
