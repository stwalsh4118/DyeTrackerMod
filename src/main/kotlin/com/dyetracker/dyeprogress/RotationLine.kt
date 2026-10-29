package com.dyetracker.dyeprogress

import com.dyetracker.api.DyeRotationStats
import java.util.Locale
import kotlin.math.max

/*
 * Text for the single-dye widget's rotation line (PBI 72): the work done this SkyBlock year and
 * the chance it gave, e.g. `1,234 skeletons · 0.04% this rotation`. Pure, so it is unit-tested
 * without a client.
 */

/** Ages below this read as `just now`. */
private const val MINUTE_MS = 60_000L

/** Ages below this read in minutes. */
private const val HOUR_MS = 3_600_000L

/** Ages below this read in hours; anything older reads in days. */
private const val DAY_MS = 86_400_000L

/** Chances below this many percent get two decimals, so a small chance never reads as `0.0%`. */
private const val TWO_DECIMALS_BELOW_PERCENT = 1.0

private const val JUST_NOW_TEXT = "just now"
private const val FULL_ROTATION_TEXT = "this rotation"

/** Format a rotation chance [progress] (percent): `0.04%` below one percent, `1.2%` from there up. */
fun rotationPercentText(progress: Double): String =
    String.format(Locale.ROOT, if (progress < TWO_DECIMALS_BELOW_PERCENT) "%.2f%%" else "%.1f%%", progress)

/** How long ago [sinceMs] was at [nowMs], e.g. `3h ago`. A [sinceMs] in the future reads as `just now`. */
fun sinceAgoText(sinceMs: Long, nowMs: Long): String {
    val d = max(0L, nowMs - sinceMs)
    return when {
        d < MINUTE_MS -> JUST_NOW_TEXT
        d < HOUR_MS -> "${d / MINUTE_MS}m ago"
        d < DAY_MS -> "${d / HOUR_MS}h ago"
        else -> "${d / DAY_MS}d ago"
    }
}

/**
 * The rotation line for [r] at [nowMs]: the work, then the chance when [DyeRotationStats.progress]
 * is set, then `this rotation` (counted all year) or `since <age>` (counting began partway).
 */
fun rotationLineText(r: DyeRotationStats, nowMs: Long): String {
    val suffix = if (r.since == null) FULL_ROTATION_TEXT else "since ${sinceAgoText(r.since, nowMs)}"
    val p = r.progress
    return if (p != null) "${r.work} · ${rotationPercentText(p)} $suffix" else "${r.work} $suffix"
}
