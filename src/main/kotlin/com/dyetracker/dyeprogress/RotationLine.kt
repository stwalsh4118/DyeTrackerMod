package com.dyetracker.dyeprogress

import com.dyetracker.api.DyeRotationStats
import java.util.Locale

/*
 * Text for the single-dye widget's rotation line (PBI 72): the work done this SkyBlock year and
 * the chance it gave, e.g. `1,234 skeletons · 0.04% this rotation (2x)`. Pure, so it is unit-tested
 * without a client.
 */

/** Chances below this many percent get two decimals, so a small chance never reads as `0.0%`. */
private const val TWO_DECIMALS_BELOW_PERCENT = 1.0

private const val ROTATION_TEXT = "this rotation"

/** Boosts above this (Vincent's 2x and 3x) are tagged on the chance. */
private const val NO_BOOST = 1

/** Format a rotation chance [progress] (percent): `0.04%` below one percent, `1.2%` from there up. */
fun rotationPercentText(progress: Double): String =
    String.format(Locale.ROOT, if (progress < TWO_DECIMALS_BELOW_PERCENT) "%.2f%%" else "%.1f%%", progress)

/**
 * The rotation line for [r]: the work, then the chance when [DyeRotationStats.progress] is set,
 * then `this rotation`, then the year's boost (e.g. `(2x)`) when the chance was computed at one.
 * A count-only line has no boost tag. [DyeRotationStats.since] is ignored: a rotation that began
 * counting partway through still reads `this rotation`.
 */
fun rotationLineText(r: DyeRotationStats): String {
    val p = r.progress ?: return "${r.work} $ROTATION_TEXT"
    val boostTag = if (r.boost > NO_BOOST) " (${r.boost}x)" else ""
    return "${r.work} · ${rotationPercentText(p)} $ROTATION_TEXT$boostTag"
}
