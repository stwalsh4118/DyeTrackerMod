package com.dyetracker.menu

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One drop timeline row: the dye's [name], its accent [color] (`#rrggbb`) and when it was first obtained ([obtainedAt], epoch ms). */
data class TimelineRow(val name: String, val color: String, val obtainedAt: Long)

/**
 * Pure logic for the Collection tab's drop timeline, mirroring the website's `DropTimeline`: the
 * owned dyes with a drop date, newest first, each with its date and how long ago that was.
 */
object MenuTimeline {
    /** The timeline section's heading. */
    const val HEADING = "Drop Timeline"

    /** A drop date, e.g. `Oct 26, 2026`, like the website's `en-US` short date. */
    private val DROP_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    private const val MS_PER_DAY = 86_400_000L
    private const val DAYS_PER_WEEK = 7L
    private const val DAYS_PER_MONTH = 30L
    private const val DAYS_PER_YEAR = 365L

    /** Between a row's date and its relative time. */
    private const val SEPARATOR = " · "

    /**
     * The owned [cells] with a known `obtainedAt`, newest first (ties keep the [cells] order),
     * over every cell whatever the Collection filters.
     */
    fun timeline(cells: List<CollectionCell>): List<TimelineRow> =
        cells.mapNotNull { cell ->
            val obtainedAt = cell.obtainedAt
            if (cell.owned && obtainedAt != null) TimelineRow(cell.entry.name, cell.entry.color, obtainedAt) else null
        }.sortedByDescending { it.obtainedAt }

    /** [ms] as its day in [zone], e.g. `Oct 26, 2026`. */
    fun formatDropDate(ms: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(ms).atZone(zone).format(DROP_DATE_FORMAT)

    /**
     * How long before [nowMs] the drop at [ms] was, on whole days like the website: `today` (also
     * for a drop in the future), `yesterday`, then days, weeks, months (30 days) or years (365).
     */
    fun formatRelative(nowMs: Long, ms: Long): String {
        val days = Math.floorDiv(nowMs - ms, MS_PER_DAY)
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < DAYS_PER_WEEK -> "$days days ago"
            days < DAYS_PER_MONTH -> "${days / DAYS_PER_WEEK} weeks ago"
            days < DAYS_PER_YEAR -> "${days / DAYS_PER_MONTH} months ago"
            else -> "${days / DAYS_PER_YEAR} years ago"
        }
    }

    /** [row]'s date line, e.g. `Oct 26, 2026 · 2 days ago`. */
    fun timelineText(row: TimelineRow, nowMs: Long, zone: ZoneId): String =
        "${formatDropDate(row.obtainedAt, zone)}$SEPARATOR${formatRelative(nowMs, row.obtainedAt)}"
}
