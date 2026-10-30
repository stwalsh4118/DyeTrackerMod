package com.dyetracker.ui.theme

import com.dyetracker.spotify.lerpColor

/** Bit position of the alpha byte in an ARGB color. */
private const val ALPHA_SHIFT = 24

/** Keeps only the RGB channels of an ARGB color. */
private const val RGB_MASK = 0x00FFFFFF

/** Full alpha, so two RGB colors lerp as opaque colors. */
private const val OPAQUE = 0xFF000000.toInt()

/** [rgb] with its alpha byte replaced by [alpha] (`0x00..0xFF`). */
fun themeWithAlpha(alpha: Int, rgb: Int): Int = (alpha shl ALPHA_SHIFT) or (rgb and RGB_MASK)

/** The RGB color [t] (`0..1`) of the way from [a] to [b], with no alpha. */
fun themeMix(a: Int, b: Int, t: Float): Int = lerpColor(a or OPAQUE, b or OPAQUE, t) and RGB_MASK
