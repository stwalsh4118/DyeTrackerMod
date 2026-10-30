package com.dyetracker.ui.picker

import com.dyetracker.dyeprogress.DyeCatalog
import com.dyetracker.dyeprogress.DyeOption

/** How many dye rows the dye picker shows at once. */
const val PICKER_VISIBLE_ROWS = 8

/** The dye picker's shown dye [rows], and how many [more] matches there are beyond them. */
data class PickerRows(val rows: List<DyeOption>, val more: Int)

/**
 * The dye picker's rows for [query] ([DyeCatalog.filter] over [options]): at most [visible]
 * matches, the [current] pick (a dye id; blank for none) first when it matches, then the rest in
 * the order of [options].
 */
fun pickerRows(query: String, current: String, visible: Int, options: List<DyeOption> = DyeCatalog.all): PickerRows {
    val matches = DyeCatalog.filter(query, options)
    val (picked, others) = matches.partition { it.dyeId == current }
    val rows = (picked + others).take(visible)
    return PickerRows(rows, matches.size - rows.size)
}
