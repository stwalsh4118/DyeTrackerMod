package com.dyetracker.menu

import com.dyetracker.api.CollectionEntry
import com.dyetracker.api.DyeProgressEntry
import java.util.Locale

/**
 * One card of the Collection tab: the dye [entry], whether the player [owned] it, how many they
 * own ([count], `0` when not owned) and when they first got it ([obtainedAt], epoch ms) if known.
 */
data class CollectionCell(val entry: DyeProgressEntry, val owned: Boolean, val count: Int, val obtainedAt: Long?)

/**
 * Pure logic for the in-game menu's Collection tab, mirroring the website's Collection page: one
 * cell per dye of the Dyes tab's list, the owned/total summary, an owned card's badge, and when to
 * explain an empty collection.
 */
object MenuCollection {
    /** Shown when the player owns none of the dyes. */
    const val EMPTY_HINT = "Link your account and open Vincent's Dye Guide to record your collection."

    /** An owned card's badge for a count of exactly one. */
    private const val SINGLE_BADGE = "✓"

    /** Before the count in an owned card's badge for two or more, like the website's `x3`. */
    private const val MULTIPLE_BADGE_PREFIX = "x"

    private const val PERCENT_FACTOR = 100.0

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

    /** Whether to show [EMPTY_HINT]: none of [cells] is owned. */
    fun showEmptyHint(cells: List<CollectionCell>): Boolean = cells.none { it.owned }
}
