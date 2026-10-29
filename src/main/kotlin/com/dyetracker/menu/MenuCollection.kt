package com.dyetracker.menu

import com.dyetracker.api.CollectionEntry
import com.dyetracker.api.DyeProgressEntry
import java.util.Locale

/**
 * One card of the Collection tab: the dye [entry], whether the player [owned] it, how many they
 * own ([count], `0` when not owned) and when they first got it ([obtainedAt], epoch ms) if known.
 */
data class CollectionCell(val entry: DyeProgressEntry, val owned: Boolean, val count: Int, val obtainedAt: Long?)

/** The Collection tab's All / Owned / Unowned filter, like the website's; [label] starts its pill's text. */
enum class OwnedFilter(val label: String) { ALL("All"), OWNED("Owned"), UNOWNED("Unowned") }

/** How many of the [total] dyes in [category] the player [owned]: one category chip. */
data class CategoryCount(val category: String, val owned: Int, val total: Int)

/**
 * Pure logic for the in-game menu's Collection tab, mirroring the website's Collection page: one
 * cell per dye of the Dyes tab's list, the owned/total summary, an owned card's badge, when to
 * explain an empty collection, and the category chips and All / Owned / Unowned filter; and the
 * Dyes tab's OBTAINED badge and obtained totals.
 */
object MenuCollection {
    /** Shown when the player owns none of the dyes. */
    const val EMPTY_HINT = "Link your account and open Vincent's Dye Guide to record your collection."

    /** An owned card's badge for a count of exactly one. */
    private const val SINGLE_BADGE = "✓"

    /** A Dyes tab card's badge for an owned dye, like the website's. */
    private const val OBTAINED_BADGE = "OBTAINED"

    /** Before the count in an owned card's badge for two or more, like the website's `x3`. */
    private const val MULTIPLE_BADGE_PREFIX = "x"

    private const val PERCENT_FACTOR = 100.0

    /** The grid's message when the Owned filter leaves no dyes. */
    private const val NO_OWNED_MESSAGE = "No dyes obtained yet"

    /** The grid's message when the Unowned filter leaves no dyes. */
    private const val NO_UNOWNED_MESSAGE = "All dyes obtained!"

    /** The grid's message when the filters leave no dyes otherwise. */
    private const val NO_DYES_MESSAGE = "No dyes found"

    /** How many of the [total] dyes the player [owned]. */
    data class CollectionSummary(val owned: Int, val total: Int)

    /**
     * One cell per dye in [dyes], in that order. A dye is owned when any of [owned] has its
     * `dyeId`; its count is the sum of those entries' counts and its `obtainedAt` the earliest
     * non-null one. Owned entries for a dye not in [dyes] are ignored.
     */
    fun cells(dyes: List<DyeProgressEntry>, owned: List<CollectionEntry>): List<CollectionCell> {
        val byDye = owned.groupBy { it.dyeId }
        return dyes.map { dye ->
            val entries = byDye[dye.dyeId].orEmpty()
            CollectionCell(
                entry = dye,
                owned = entries.isNotEmpty(),
                count = entries.sumOf { it.count },
                obtainedAt = entries.mapNotNull { it.obtainedAt }.minOrNull(),
            )
        }
    }

    /** How many of [cells] are owned, out of all of them. */
    fun summary(cells: List<CollectionCell>): CollectionSummary =
        CollectionSummary(owned = cells.count { it.owned }, total = cells.size)

    /** E.g. `1 of 42 Dyes`. */
    fun summaryText(s: CollectionSummary): String = "${s.owned} of ${s.total} Dyes"

    /** The owned share with one decimal, e.g. `2.4% complete`; `0.0% complete` when there are no dyes. */
    fun percentText(s: CollectionSummary): String =
        String.format(Locale.ROOT, "%.1f%% complete", percent(s))

    /** The owned share of [s] in percent (`0` to `100`); `0` when there are no dyes. */
    fun percent(s: CollectionSummary): Double =
        if (s.total == 0) 0.0 else s.owned * PERCENT_FACTOR / s.total

    /** An owned card's badge: `✓` for one, `x<count>` for two or more, like the website. */
    fun ownedBadge(count: Int): String = if (count <= 1) SINGLE_BADGE else "$MULTIPLE_BADGE_PREFIX$count"

    /**
     * A Dyes tab card's badge for a dye the player owns [count] of: `OBTAINED`, or
     * `OBTAINED x<count>` for two or more, like the website; `null` when not owned.
     */
    fun obtainedBadge(count: Int): String? = when {
        count <= 0 -> null
        count == 1 -> OBTAINED_BADGE
        else -> "$OBTAINED_BADGE $MULTIPLE_BADGE_PREFIX$count"
    }

    /**
     * The profile strip's totals once the collection is loaded, e.g. `5 / 42 obtained · 3 at 100%`:
     * [owned] of the profile's [total] dyes, and how many are [complete].
     */
    fun obtainedTotalsText(owned: Int, total: Int, complete: Int): String =
        "$owned / $total obtained · $complete at 100%"

    /** Whether to show [EMPTY_HINT]: none of [cells] is owned. */
    fun showEmptyHint(cells: List<CollectionCell>): Boolean = cells.none { it.owned }

    /** The cells in [category] (`null`: every category) that [owned] keeps, in their order. */
    fun filterCells(cells: List<CollectionCell>, category: String?, owned: OwnedFilter): List<CollectionCell> =
        cells.filter { (category == null || it.entry.category == category) && matches(it, owned) }

    /**
     * One count per category of [cells], in the order each category first appears there (the
     * website's order), over every cell whatever the filters.
     */
    fun categoryCounts(cells: List<CollectionCell>): List<CategoryCount> =
        cells.groupBy { it.entry.category }.map { (category, inCategory) ->
            CategoryCount(category, owned = inCategory.count { it.owned }, total = inCategory.size)
        }

    /** A category chip's text, e.g. `Mob Drop 3/19`. */
    fun categoryChipLabel(c: CategoryCount): String = "${categoryLabel(c.category)} ${c.owned}/${c.total}"

    /** An owned-filter pill's text with how many of [cells] it keeps, e.g. `Owned (2)`. */
    fun ownedFilterLabel(filter: OwnedFilter, cells: List<CollectionCell>): String =
        "${filter.label} (${cells.count { matches(it, filter) }})"

    /** What the grid says when the filters leave no cells, like the website. */
    fun emptyFilterMessage(filter: OwnedFilter): String = when (filter) {
        OwnedFilter.OWNED -> NO_OWNED_MESSAGE
        OwnedFilter.UNOWNED -> NO_UNOWNED_MESSAGE
        OwnedFilter.ALL -> NO_DYES_MESSAGE
    }

    /** Whether [filter] keeps [cell]. */
    private fun matches(cell: CollectionCell, filter: OwnedFilter): Boolean = when (filter) {
        OwnedFilter.ALL -> true
        OwnedFilter.OWNED -> cell.owned
        OwnedFilter.UNOWNED -> !cell.owned
    }
}
