package com.dyetracker.ui.theme

import kotlin.math.roundToInt

/** The HUD widget panels' background alpha by default (PBI 81): the original `0xC8`, shown as 78%. */
const val HUD_PANEL_ALPHA_DEFAULT = 200

/** The lowest HUD panel alpha: see-through. */
private const val HUD_ALPHA_MIN = 0

/** The highest HUD panel alpha: solid. */
private const val HUD_ALPHA_MAX = 255

/** The lowest opacity percent. */
private const val PERCENT_MIN = 0

/** The highest opacity percent. */
private const val PERCENT_MAX = 100

/** Before the percent on the Theme screen's opacity slider. */
private const val HUD_OPACITY_LABEL = "HUD opacity: "

/** After the percent on the Theme screen's opacity slider. */
private const val PERCENT_SUFFIX = "%"

/** [alpha] kept within `0..255`. */
fun clampHudAlpha(alpha: Int): Int = alpha.coerceIn(HUD_ALPHA_MIN, HUD_ALPHA_MAX)

/** [alpha] (clamped) as a rounded percent, `0..100`. */
fun alphaToPercent(alpha: Int): Int = (clampHudAlpha(alpha) * PERCENT_MAX / HUD_ALPHA_MAX.toDouble()).roundToInt()

/** [percent] (clamped to `0..100`) as a rounded alpha, `0..255`. */
fun percentToAlpha(percent: Int): Int =
    (percent.coerceIn(PERCENT_MIN, PERCENT_MAX) * HUD_ALPHA_MAX / PERCENT_MAX.toDouble()).roundToInt()

/** The alpha a slider [value] (`0..1`) stands for, rounded and clamped. */
fun sliderValueToAlpha(value: Double): Int = clampHudAlpha((value * HUD_ALPHA_MAX).roundToInt())

/** The slider value (`0..1`) for [alpha]. */
fun alphaToSliderValue(alpha: Int): Double = clampHudAlpha(alpha) / HUD_ALPHA_MAX.toDouble()

/** The opacity slider's message for [alpha]: `HUD opacity: 78%`. */
fun hudOpacityLabel(alpha: Int): String = HUD_OPACITY_LABEL + alphaToPercent(alpha) + PERCENT_SUFFIX
