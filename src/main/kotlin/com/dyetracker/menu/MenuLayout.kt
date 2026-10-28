package com.dyetracker.menu

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

    /** Horizontal gap before each header tab label (after the title, between tabs). */
    const val TAB_GAP = 16

    /** Thickness of the underline marking the active tab. */
    const val TAB_UNDERLINE_HEIGHT = 1

    /** Card columns that fit in [availableWidth], from 1 to [MAX_COLUMNS]. */
    fun columns(availableWidth: Int): Int =
        ((availableWidth + CARD_GAP) / (CARD_WIDTH + CARD_GAP)).coerceIn(1, MAX_COLUMNS)

    /** Width of a grid of [columns] cards, gaps included. */
    fun gridWidth(columns: Int): Int = columns * CARD_WIDTH + (columns - 1) * CARD_GAP

    /** Top-left of card [index] in a [columns]-wide grid whose top-left is ([gridLeft], [gridTop]). */
    fun cardOrigin(index: Int, columns: Int, gridLeft: Int, gridTop: Int): Pair<Int, Int> = Pair(
        gridLeft + (index % columns) * (CARD_WIDTH + CARD_GAP),
        gridTop + (index / columns) * (CARD_HEIGHT + CARD_GAP),
    )
}
