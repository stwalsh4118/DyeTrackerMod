package com.dyetracker.discord

import com.dyetracker.dyeprogress.DyeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [pickerRows]: the hunt picker's filtered, capped dye list with the current pick first. */
class HuntPickerRowsTest {

    @Test
    fun `an empty query shows the first catalog dyes and counts the rest`() {
        val rows = pickerRows(query = "", current = "", visible = PICKER_VISIBLE_ROWS)

        assertEquals(DyeCatalog.all.take(PICKER_VISIBLE_ROWS), rows.rows)
        assertEquals(DyeCatalog.all.size - PICKER_VISIBLE_ROWS, rows.more)
    }

    @Test
    fun `a query narrows the rows to its matches`() {
        val rows = pickerRows(query = "nad", current = "", visible = PICKER_VISIBLE_ROWS)

        assertTrue(rows.rows.any { it.dyeId == NADESHIKO && it.displayName == "Nadeshiko" }, "rows: ${rows.rows}")
        assertEquals(0, rows.more)
    }

    @Test
    fun `the current pick is listed first when it matches`() {
        val last = DyeCatalog.all.last()

        val rows = pickerRows(query = "", current = last.dyeId, visible = PICKER_VISIBLE_ROWS)

        assertEquals(last, rows.rows.first())
        assertEquals(PICKER_VISIBLE_ROWS, rows.rows.size)
        assertEquals(DyeCatalog.all.size - PICKER_VISIBLE_ROWS, rows.more)
        assertEquals(DyeCatalog.all.filter { it != last }.take(PICKER_VISIBLE_ROWS - 1), rows.rows.drop(1))
    }

    @Test
    fun `a current pick the query does not match is left out`() {
        val rows = pickerRows(query = "nad", current = "mango", visible = PICKER_VISIBLE_ROWS)

        assertTrue(rows.rows.none { it.dyeId == "mango" }, "rows: ${rows.rows}")
        assertEquals(NADESHIKO, rows.rows.first().dyeId)
    }

    @Test
    fun `a query with no match has no rows and nothing more`() {
        val rows = pickerRows(query = "zzzz-no-such-dye", current = NADESHIKO, visible = PICKER_VISIBLE_ROWS)

        assertEquals(emptyList(), rows.rows)
        assertEquals(0, rows.more)
    }

    private companion object {
        const val NADESHIKO = "nadeshiko"
    }
}
