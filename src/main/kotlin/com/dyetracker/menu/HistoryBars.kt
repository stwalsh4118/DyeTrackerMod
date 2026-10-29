package com.dyetracker.menu

import com.dyetracker.api.HistoryBucket
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** One bar of the History chart: its top-left ([x], [y]) and size, in GUI pixels. */
data class BarRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** A bar's time slice when it is shorter than this (ms): the tooltip shows its times, else its day. */
private const val MS_PER_DAY = 86_400_000L

/** A bar's start and end time, e.g. `14:00`. */
private val BAR_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** A 1-day bar's day, e.g. `Oct 26`, in English like [MenuTimeline]'s drop dates. */
private val BAR_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** Between a bar's start and end time. */
private const val TIME_RANGE_SEPARATOR = "–"

/** Between a bar's time or day and its work. */
private const val TOOLTIP_SEPARATOR = ": "

/** A bar's work when its slice has no snapshots to tell. */
private const val NO_DATA_TEXT = "no data"

/** Between the chart title's dye name and its slice. */
private const val TITLE_SEPARATOR = " · work per "

/** What the chart says when no slice has work to draw. */
private const val NOT_ENOUGH_SNAPSHOTS_TEXT = "Not enough snapshots for a chart"

/** A zero-work bar's height: a baseline tick, so the slice still shows. */
private const val BASELINE_TICK_PX = 1

/** The least a bar of positive work is tall, and the least any bar is wide. */
private const val MIN_BAR_PX = 1

/**
 * Bar heights for [works] scaled so the most work is [maxHeightPx] tall: rounded, at least
 * [MIN_BAR_PX] for any positive work; `null` work stays `null`. Without positive work every
 * non-null bar is `0`.
 */
fun barHeights(works: List<Double?>, maxHeightPx: Int): List<Int?> {
    val most = works.filterNotNull().maxOrNull() ?: 0.0
    return works.map { work ->
        when {
            work == null -> null
            most <= 0.0 || work <= 0.0 -> 0
            else -> max(MIN_BAR_PX, (work / most * maxHeightPx).roundToInt())
        }
    }
}

/**
 * Rects for bars of [heights] standing on [bottom], in equal slots across [width] from [left]:
 * each bar the slot less [gapPx] wide (at least [MIN_BAR_PX]) at its slot's left. A `null` height
 * has no bar; a `0` height is a [BASELINE_TICK_PX] tick.
 */
fun barRects(heights: List<Int?>, left: Int, bottom: Int, width: Int, gapPx: Int): List<BarRect?> {
    if (heights.isEmpty()) return emptyList()
    val slot = width / heights.size
    val barWidth = max(MIN_BAR_PX, slot - gapPx)
    return heights.mapIndexed { index, height ->
        height?.let {
            val barHeight = if (it == 0) BASELINE_TICK_PX else it
            BarRect(left + slot * index, bottom - barHeight, barWidth, barHeight)
        }
    }
}

/**
 * Index of the [barRects] slot under [mouseX] for [count] bars across [width] from [left], so a
 * short bar or an empty slice still has its tooltip; `null` outside the slots, or without any.
 */
fun barSlotAt(mouseX: Double, left: Int, width: Int, count: Int): Int? {
    if (count <= 0) return null
    val slot = width / count
    if (slot <= 0 || mouseX < left) return null
    val index = floor((mouseX - left) / slot).toInt()
    return if (index < count) index else null
}

/** How long one bar of [period] is, for the chart title: `5 min`, `hour`, `6 h` or `day`. */
fun sliceLabel(period: HistoryPeriod): String = when (period) {
    HistoryPeriod.HOUR -> "5 min"
    HistoryPeriod.DAY -> "hour"
    HistoryPeriod.WEEK -> "6 h"
    HistoryPeriod.MONTH -> "day"
}

/** The chart's title for the dye [name] over [period], e.g. `Bone Dye · work per hour`. */
fun chartTitle(name: String, period: HistoryPeriod): String = "$name$TITLE_SEPARATOR${sliceLabel(period)}"

/**
 * A hovered bar's text: `14:00–15:00: 1,234` for a slice shorter than a day, `Oct 26: 1,234` for a
 * day, in [zone]; the work grouped by thousands (one decimal when not whole), or `no data`.
 */
fun barTooltip(bucket: HistoryBucket, zone: ZoneId): String {
    val start = Instant.ofEpochMilli(bucket.start).atZone(zone)
    val time = if (bucket.end - bucket.start < MS_PER_DAY) {
        val end = Instant.ofEpochMilli(bucket.end).atZone(zone)
        "${start.format(BAR_TIME_FORMAT)}$TIME_RANGE_SEPARATOR${end.format(BAR_TIME_FORMAT)}"
    } else {
        start.format(BAR_DAY_FORMAT)
    }
    return "$time$TOOLTIP_SEPARATOR${bucket.work?.let(::workText) ?: NO_DATA_TEXT}"
}

/** What the chart says instead of its bars: [NOT_ENOUGH_SNAPSHOTS_TEXT] when no bucket has work, else `null`. */
fun chartMessage(buckets: List<HistoryBucket>): String? =
    if (buckets.all { it.work == null }) NOT_ENOUGH_SNAPSHOTS_TEXT else null

/** [work] grouped by thousands, e.g. `1,234`, or `1,234.5` when it isn't whole. */
private fun workText(work: Double): String =
    if (work % 1.0 == 0.0) String.format(Locale.ROOT, "%,d", work.toLong()) else String.format(Locale.ROOT, "%,.1f", work)
