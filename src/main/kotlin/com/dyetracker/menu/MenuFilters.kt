package com.dyetracker.menu

import com.dyetracker.api.DyeProgressEntry

/**
 * Pure category filter and sort for the menu's card grid, mirroring the website dashboard's
 * `CategoryFilters` pills and sort options (`packages/web/src/components/CategoryFilters.tsx`,
 * `DyeGrid.tsx`).
 */

/** The filterable dye categories, in the website's `DYE_CATEGORIES` order. */
val MENU_CATEGORIES: List<String> = listOf(
    "slayer",
    "dungeon",
    "nucleus",
    "experimentation",
    "kuudra",
    "mob_drop",
    "skill_action",
    "purchase",
)

/** Label of the "no category filter" pill. */
private const val ALL_CATEGORIES_LABEL = "All Dyes"

/** The website's pill label per category id; ids missing here fall back to [MenuCards.formatCategory]. */
private val CATEGORY_LABELS: Map<String, String> = mapOf(
    "slayer" to "Slayer",
    "dungeon" to "Dungeon",
    "nucleus" to "Crystal Nucleus",
    "experimentation" to "Experimentation",
    "kuudra" to "Kuudra",
    "mob_drop" to "Mob Drop",
    "skill_action" to "Skills",
    "purchase" to "Purchase",
)

/** Sort key for an entry with no progress: below every real percent, like the website's `?? -1`. */
private const val NO_PROGRESS_SORT_KEY = -1.0

/** Pill label for [category]: `null` is "All Dyes"; unknown ids are title-cased from the id. */
fun categoryLabel(category: String?): String = when (category) {
    null -> ALL_CATEGORIES_LABEL
    else -> CATEGORY_LABELS[category] ?: MenuCards.formatCategory(category)
}

/** The grid's sort order; the sort pill cycles through these with [next]. */
enum class MenuSort(val label: String) {
    DEFAULT("Default"),
    PROGRESS_DESC("Progress ↓"),
    PROGRESS_ASC("Progress ↑"),
    NAME_ASC("Name A→Z"),
    NAME_DESC("Name Z→A"),
    ;

    /** The next sort in declaration order, wrapping from the last to the first. */
    fun next(): MenuSort = entries[(ordinal + 1) % entries.size]
}

/**
 * [dyes] limited to [category] (`null` keeps all) and ordered by [sort]. [MenuSort.DEFAULT] keeps
 * the API order; progress sorts treat a `null` progress as below every percent; name sorts ignore
 * case. Every sort is stable, so ties keep the API order.
 */
fun applyFilters(dyes: List<DyeProgressEntry>, category: String?, sort: MenuSort): List<DyeProgressEntry> {
    val filtered = if (category == null) dyes else dyes.filter { it.category == category }
    return when (sort) {
        MenuSort.DEFAULT -> filtered
        MenuSort.PROGRESS_DESC -> filtered.sortedByDescending { it.progress ?: NO_PROGRESS_SORT_KEY }
        MenuSort.PROGRESS_ASC -> filtered.sortedBy { it.progress ?: NO_PROGRESS_SORT_KEY }
        MenuSort.NAME_ASC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        MenuSort.NAME_DESC -> filtered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
