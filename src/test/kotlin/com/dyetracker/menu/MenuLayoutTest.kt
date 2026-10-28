package com.dyetracker.menu

import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit coverage for the menu card-grid geometry (task 67-1). */
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
}
