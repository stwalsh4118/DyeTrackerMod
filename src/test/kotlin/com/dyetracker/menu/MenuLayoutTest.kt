package com.dyetracker.menu

import com.dyetracker.ui.core.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit coverage for the menu card-grid geometry (tasks 67-1, 67-2). */
class MenuLayoutTest {

    @Test
    fun `one card width fits one column`() {
        assertEquals(1, MenuLayout.columns(160))
    }

    @Test
    fun `two cards plus one gap fit two columns`() {
        assertEquals(2, MenuLayout.columns(2 * 160 + 8))
    }

    @Test
    fun `one pixel short of two columns stays at one`() {
        assertEquals(1, MenuLayout.columns(327))
    }

    @Test
    fun `a very wide screen caps at the maximum columns`() {
        assertEquals(6, MenuLayout.columns(5000))
    }

    @Test
    fun `zero width still yields one column`() {
        assertEquals(1, MenuLayout.columns(0))
    }

    @Test
    fun `grid width counts cards and the gaps between them`() {
        assertEquals(496, MenuLayout.gridWidth(3))
    }

    @Test
    fun `card origin wraps by column count`() {
        assertEquals(10 + 168 to 20 + 78, MenuLayout.cardOrigin(4, 3, 10, 20))
    }

    // --- 67-2: scrolling and hit-testing ---

    /** A 3-column grid at (10, 20) with a viewport tall enough for two full rows. */
    private val gridLeft = 10
    private val gridTop = 20
    private val viewport = Rect(gridLeft, gridTop, MenuLayout.gridWidth(3), 2 * 70 + 8)

    /** Centre of card [index] in the 3-column grid, scrolled by [scroll]. */
    private fun centreOf(index: Int, scroll: Int = 0): Pair<Double, Double> {
        val (x, y) = MenuLayout.cardOrigin(index, 3, gridLeft, gridTop - scroll)
        return Pair(x + 160 / 2.0, y + 70 / 2.0)
    }

    private fun hit(x: Double, y: Double, itemCount: Int = 7, scroll: Int = 0): Int? =
        MenuLayout.cardIndexAt(x, y, itemCount, 3, gridLeft, gridTop, scroll, viewport)

    @Test
    fun `scroll step is twenty pixels`() {
        assertEquals(20, MenuLayout.SCROLL_STEP)
    }

    @Test
    fun `content height of no items is zero`() {
        assertEquals(0, MenuLayout.contentHeight(0, 3))
    }

    @Test
    fun `content height counts rows and the gaps between them`() {
        assertEquals(3 * 70 + 2 * 8, MenuLayout.contentHeight(7, 3))
    }

    @Test
    fun `content height of one full row has no gap`() {
        assertEquals(70, MenuLayout.contentHeight(3, 3))
    }

    @Test
    fun `negative scroll clamps to zero`() {
        assertEquals(0, MenuLayout.clampScroll(-5, 500, 200))
    }

    @Test
    fun `scroll past the end clamps to the overflow`() {
        assertEquals(300, MenuLayout.clampScroll(400, 500, 200))
    }

    @Test
    fun `content shorter than the viewport never scrolls`() {
        assertEquals(0, MenuLayout.clampScroll(10, 100, 200))
    }

    @Test
    fun `scroll within range is unchanged`() {
        assertEquals(150, MenuLayout.clampScroll(150, 500, 200))
    }

    @Test
    fun `the centre of card 4 in three columns hits card 4`() {
        val (x, y) = centreOf(4)
        assertEquals(4, hit(x, y))
    }

    @Test
    fun `the gap between card 0 and card 1 hits nothing`() {
        val (_, y) = centreOf(0)
        assertNull(hit(gridLeft + 160 + 8 / 2.0, y))
    }

    @Test
    fun `the gap between rows hits nothing`() {
        val (x, _) = centreOf(0)
        assertNull(hit(x, gridTop + 70 + 8 / 2.0))
    }

    @Test
    fun `a slot past the last item hits nothing`() {
        // 4 items: row 1 holds only card 3, so the slot of card 4 is empty.
        val (x, y) = centreOf(4)
        assertNull(hit(x, y, itemCount = 4))
    }

    @Test
    fun `a point outside the viewport hits nothing`() {
        val (x, _) = centreOf(0)
        assertNull(hit(x, gridTop - 1.0))
        assertNull(hit(gridLeft - 1.0, gridTop + 1.0))
    }

    @Test
    fun `a card scrolled under the viewport bottom is not hit below it`() {
        // Card 6 starts at row 2 (y = 20 + 156), below the 148px viewport; its centre is outside.
        val (x, y) = centreOf(6)
        assertNull(hit(x, y))
    }

    @Test
    fun `scrolling by one row moves the point over card 3 to card 6`() {
        val (x, y) = centreOf(3)
        assertEquals(3, hit(x, y))
        assertEquals(6, hit(x, y, scroll = 78))
    }

    @Test
    fun `card bounds are half-open so a shared edge belongs to one card`() {
        val (x0, y0) = MenuLayout.cardOrigin(0, 3, gridLeft, gridTop)
        assertEquals(0, hit(x0.toDouble(), y0.toDouble()))
        assertNull(hit((x0 + 160).toDouble(), y0 + 1.0)) // right edge is the gap
        assertNull(hit(x0 + 1.0, (y0 + 70).toDouble())) // bottom edge is the gap
        assertEquals(0, hit(x0 + 159.5, y0 + 69.5))
    }

    @Test
    fun `the viewport bottom edge is outside the viewport`() {
        // Scrolled by 10, card 6 (row 2) spans y 166..236, so it covers the viewport bottom (168).
        val (x, _) = centreOf(6)
        val bottom = viewport.bottom.toDouble()
        assertEquals(6, hit(x, bottom - 0.5, scroll = 10))
        assertNull(hit(x, bottom, scroll = 10))
    }

    @Test
    fun `the viewport right edge is outside the viewport`() {
        // A viewport narrower than card 0, so its right edge (110) falls inside the card.
        val narrow = Rect(gridLeft, gridTop, 100, 2 * 70 + 8)
        val (_, y) = centreOf(0)
        val right = narrow.right.toDouble()
        assertEquals(0, MenuLayout.cardIndexAt(right - 0.5, y, 7, 3, gridLeft, gridTop, 0, narrow))
        assertNull(MenuLayout.cardIndexAt(right, y, 7, 3, gridLeft, gridTop, 0, narrow))
    }

    @Test
    fun `no items hits nothing`() {
        val (x, y) = centreOf(0)
        assertNull(hit(x, y, itemCount = 0))
    }
}
