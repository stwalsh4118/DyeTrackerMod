package com.dyetracker.discord

import com.dyetracker.dyeprogress.DyeCatalog
import com.dyetracker.dyeprogress.DyeOption

/** How many dye rows the hunt picker shows at once. */
const val PICKER_VISIBLE_ROWS = 8

/** The hunt picker's shown dye [rows], and how many [more] matches there are beyond them. */
data class PickerRows(val rows: List<DyeOption>, val more: Int)

/**
 * The hunt picker's rows for [query] ([DyeCatalog.filter]): at most [visible] matches, the
 * [current] pick (a dye id; blank for none) first when it matches, then the rest in catalog order.
 */
fun pickerRows(query: String, current: String, visible: Int): PickerRows {
    val matches = DyeCatalog.filter(query)
    val (picked, others) = matches.partition { it.dyeId == current }
    val rows = (picked + others).take(visible)
    return PickerRows(rows, matches.size - rows.size)
}
