package com.dyetracker.menu

import com.dyetracker.ui.core.Rect
import kotlin.math.floor
import kotlin.math.max

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

    /** Height of one Settings row (label, detail line, control pill). */
    const val SETTINGS_ROW_HEIGHT = 28

    /** How long a Settings row's message after a click (unlinked, synced, nothing to sync) shows, in ms. */
    const val SETTINGS_MESSAGE_MS = 4_000L

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
