package com.dyetracker.menu

import com.dyetracker.ui.core.Rect

/**
 * One clickable pill in the menu (category filters, the sort pill), styled like the website's
 * `Button`: [selected] uses the primary (white) variant, else the secondary (card) variant.
 * [key] identifies what a click on it does; [label] is the text drawn centred in [rect].
 */
data class MenuPill(val key: String, val label: String, val rect: Rect, val selected: Boolean)

/** The first pill in [pills] whose rect contains ([mouseX], [mouseY]), or `null`. */
fun pillAt(pills: List<MenuPill>, mouseX: Double, mouseY: Double): MenuPill? =
    pills.firstOrNull { it.rect.contains(mouseX, mouseY) }

/** Width of a pill showing [label]: its [measure]d width plus [MenuLayout.PILL_PADDING_X] on both sides. */
fun pillWidth(label: String, measure: (String) -> Int): Int = measure(label) + 2 * MenuLayout.PILL_PADDING_X

/**
 * Rects for [labels] (`key to label` pairs), laid out left to right from ([left], [top]) with
 * [MenuLayout.PILL_GAP] between pills. Each pill is [pillWidth] wide and [MenuLayout.PILL_HEIGHT]
 * tall. A pill that would pass `left + maxWidth` starts a new row [MenuLayout.PILL_GAP] below; a
 * row's first pill stays on its row even when it alone is wider than [maxWidth].
 */
fun layoutPills(
    labels: List<Pair<String, String>>,
    measure: (String) -> Int,
    left: Int,
    top: Int,
    maxWidth: Int,
): List<Pair<String, Rect>> {
    val right = left + maxWidth
    var x = left
    var y = top
    return labels.map { (key, label) ->
        val width = pillWidth(label, measure)
        if (x > left && x + width > right) {
            x = left
            y += MenuLayout.PILL_HEIGHT + MenuLayout.PILL_GAP
        }
        val rect = Rect(x, y, width, MenuLayout.PILL_HEIGHT)
        x += width + MenuLayout.PILL_GAP
        key to rect
    }
}
