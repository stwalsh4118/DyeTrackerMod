package com.dyetracker.menu

import com.dyetracker.ui.core.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit coverage for the menu pill layout and hit-test (task 67-3). */
class MenuPillTest {

    /** Every character measures 6px, like a fixed-width font. */
    private val measure: (String) -> Int = { it.length * 6 }

    /** Pill widths with 8px padding each side: All = 34, Slayer = 52, Dungeon = 58. */
    private val labels = listOf("all" to "All", "slayer" to "Slayer", "dungeon" to "Dungeon")

    private val left = 10
    private val top = 20

    @Test
    fun `pill constants match the brief`() {
        assertEquals(16, MenuLayout.PILL_HEIGHT)
        assertEquals(8, MenuLayout.PILL_PADDING_X)
        assertEquals(4, MenuLayout.PILL_GAP)
    }

    @Test
    fun `a pill is its label width plus padding on both sides`() {
        assertEquals(6 * 6 + 2 * 8, pillWidth("Slayer", measure))
    }

    @Test
    fun `pills are laid out left to right with four pixel gaps`() {
        assertEquals(
            listOf(
                "all" to Rect(10, 20, 34, 16),
                "slayer" to Rect(10 + 34 + 4, 20, 52, 16),
                "dungeon" to Rect(10 + 34 + 4 + 52 + 4, 20, 58, 16),
            ),
            layoutPills(labels, measure, left, top, maxWidth = 500),
        )
    }

    @Test
    fun `a pill that would pass the max width wraps to a new row`() {
        val pills = layoutPills(labels, measure, left, top, maxWidth = 100)
        assertEquals(Rect(48, 20, 52, 16), pills[1].second)
        assertEquals("dungeon" to Rect(10, 20 + 16 + 4, 58, 16), pills[2])
    }

    @Test
    fun `a pill ending exactly at the max width stays on the row`() {
        // Slayer ends at 48 + 52 = 100 = left + 90.
        val pills = layoutPills(labels, measure, left, top, maxWidth = 90)
        assertEquals(Rect(48, 20, 52, 16), pills[1].second)
        assertEquals(Rect(10, 40, 58, 16), pills[2].second)
    }

    @Test
    fun `a pill wider than the max width still takes its own row`() {
        val pills = layoutPills(labels, measure, left, top, maxWidth = 20)
        assertEquals(
            listOf(Rect(10, 20, 34, 16), Rect(10, 40, 52, 16), Rect(10, 60, 58, 16)),
            pills.map { it.second },
        )
    }

    @Test
    fun `no labels lay out no pills`() {
        assertTrue(layoutPills(emptyList(), measure, left, top, maxWidth = 100).isEmpty())
    }

    private val pills = layoutPills(labels, measure, left, top, maxWidth = 500)
        .map { (key, rect) -> MenuPill(key, key, rect, selected = key == "all") }

    @Test
    fun `a point inside a pill hits it`() {
        assertEquals("slayer", pillAt(pills, 60.0, 28.0)?.key)
        assertEquals("all", pillAt(pills, 10.0, 20.0)?.key)
    }

    @Test
    fun `a point in the gap between pills hits nothing`() {
        // All ends at 44, Slayer starts at 48.
        assertNull(pillAt(pills, 46.0, 28.0))
    }

    @Test
    fun `a point below the pills hits nothing`() {
        assertNull(pillAt(pills, 20.0, 40.0))
    }

    @Test
    fun `no pills hit nothing`() {
        assertNull(pillAt(emptyList(), 20.0, 28.0))
    }
}
