package com.dyetracker.dyeprogress

import com.dyetracker.api.DyeRotationStats
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10

/*
 * Text for the single-dye widget's rotation line (PBI 72): the work done this SkyBlock year and
 * the chance it gave, e.g. `1,234 skeletons · 0.04% this rotation (2x)`. Pure, so it is unit-tested
 * without a client.
 */

/** Chances at or above this many percent get one decimal, e.g. `1.2%`. */
private const val ONE_DECIMAL_FROM_PERCENT = 1.0

/** Chances from this many percent up to [ONE_DECIMAL_FROM_PERCENT] get two decimals, e.g. `0.04%`. */
private const val TWO_DECIMALS_FROM_PERCENT = 0.01

/** Chances below [TWO_DECIMALS_FROM_PERCENT] keep this many significant digits, e.g. `0.0027%`. */
private const val SMALL_CHANCE_SIGNIFICANT_DIGITS = 2

/** A zero (or negative) chance. */
private const val ZERO_PERCENT_TEXT = "0.00%"

private const val ROTATION_TEXT = "this rotation"

/** Boosts above this (Vincent's 2x and 3x) are tagged on the chance. */
private const val NO_BOOST = 1

/**
 * Format a rotation chance [progress] (percent): `1.2%` from one percent up, `0.04%` from a
 * hundredth of a percent, two significant digits below that (`0.0027%`, `0.000040%`), so a tiny
 * per-rotation chance never reads as `0.00%`. Zero or less reads `0.00%`.
 */
fun rotationPercentText(progress: Double): String = when {
    progress >= ONE_DECIMAL_FROM_PERCENT -> String.format(Locale.ROOT, "%.1f%%", progress)
    progress >= TWO_DECIMALS_FROM_PERCENT -> String.format(Locale.ROOT, "%.2f%%", progress)
    progress <= 0.0 -> ZERO_PERCENT_TEXT
    else -> {
        val decimals = -floor(log10(progress)).toInt() + SMALL_CHANCE_SIGNIFICANT_DIGITS - 1
        String.format(Locale.ROOT, "%.${decimals}f%%", progress)
    }
}

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
