package com.dyetracker.ui.picker

import com.dyetracker.dyeprogress.DyeCatalog
import com.dyetracker.ui.theme.DyeThemeColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [pickerRows]: the dye picker's filtered, capped dye list with the current pick first. */
class PickerRowsTest {

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

    @Test
    fun `options narrow the rows to those dyes`() {
        val options = DyeCatalog.all.take(3)

        val rows = pickerRows(query = "", current = "", visible = PICKER_VISIBLE_ROWS, options = options)

        assertEquals(options, rows.rows)
        assertEquals(0, rows.more)
    }

    @Test
    fun `a query over the theme dyes finds Jade and no retired dye`() {
        val themeDyes = DyeCatalog.all.filter { it.dyeId in DyeThemeColors.COLORS }

        val rows = pickerRows(query = "ja", current = "", visible = PICKER_VISIBLE_ROWS, options = themeDyes)

        assertTrue(rows.rows.any { it.dyeId == JADE }, "rows: ${rows.rows}")
        assertTrue(rows.rows.all { it.dyeId in DyeThemeColors.COLORS }, "rows: ${rows.rows}")
    }

    @Test
    fun `a retired dye in the catalog is not among the theme dyes`() {
        val themeDyes = DyeCatalog.all.filter { it.dyeId in DyeThemeColors.COLORS }

        val rows = pickerRows(query = WARDEN, current = "", visible = PICKER_VISIBLE_ROWS, options = themeDyes)

        assertTrue(DyeCatalog.all.any { it.dyeId == WARDEN })
        assertEquals(emptyList(), rows.rows)
        assertEquals(0, rows.more)
    }

    private companion object {
        const val NADESHIKO = "nadeshiko"
        const val JADE = "jade"

        /** A retired Fire Sale dye: in the catalog, without a theme color. */
        const val WARDEN = "warden"
    }
}
