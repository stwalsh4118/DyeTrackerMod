package com.dyetracker.spotify

import com.dyetracker.ui.theme.UiTheme
import java.awt.image.BufferedImage
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The widget's colors from the current cover (PBI 76, task 76-6), the way Spotify's guidelines
 * suggest: a background and an accent from the art, white text kept readable (WCAG contrast),
 * the green or white Spotify icon by contrast, and a fade between two palettes. Only the panel,
 * border, bar and text are colored; the art itself never is. Pure, so it is unit-tested without
 * a client.
 */

private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val CHANNEL_MAX = 255.0
private const val RGB_MASK = 0x00_FF_FF_FF
private const val OPAQUE_BLACK = 0xFF_00_00_00.toInt()
private const val OPAQUE_WHITE = 0xFF_FF_FF_FF.toInt()

/** A palette bucket is the top bits of each channel. */
private const val BUCKET_BITS = 4
private const val BUCKET_SHIFT = 8 - BUCKET_BITS

// WCAG 2.x relative luminance: sRGB linearization and the channel weights.
private const val SRGB_LINEAR_THRESHOLD = 0.03928
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_GAMMA = 2.4
private const val LUMA_RED = 0.2126
private const val LUMA_GREEN = 0.7152
private const val LUMA_BLUE = 0.0722

/** The flare added to both luminances in a WCAG contrast ratio. */
private const val CONTRAST_FLARE = 0.05

/** Darken or lighten steps until a color is black or white. */
private val MAX_COLOR_STEPS = (1f / SpotifyConstants.COLOR_STEP).roundToInt()

/** How far through a fade the icon switches to the new palette's. */
private const val ICON_SWITCH_T = 0.5f

/** Which of the two Spotify icon textures the widget draws. */
enum class SpotifyIcon { GREEN, WHITE }

/** The widget's colors (ARGB) and its icon, from a cover or the fallback. */
data class SpotifyColors(val background: Int, val border: Int, val accent: Int, val text: Int, val icon: SpotifyIcon)

/** No cover: Spotify's fallback panel, green border and bar, white text, the green icon. */
val FALLBACK_COLORS = SpotifyColors(
    UiTheme.Colors.SPOTIFY_FALLBACK_BACKGROUND,
    UiTheme.Colors.SPOTIFY_GREEN,
    UiTheme.Colors.SPOTIFY_GREEN,
    OPAQUE_WHITE,
    SpotifyIcon.GREEN,
)

private fun red(rgb: Int) = (rgb shr RED_SHIFT) and CHANNEL_MASK
private fun green(rgb: Int) = (rgb shr GREEN_SHIFT) and CHANNEL_MASK
private fun blue(rgb: Int) = rgb and CHANNEL_MASK
private fun alpha(argb: Int) = argb ushr ALPHA_SHIFT

private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue

private fun linear(channel: Int): Double {
    val c = channel / CHANNEL_MAX
    return if (c <= SRGB_LINEAR_THRESHOLD) c / SRGB_LINEAR_DIVISOR else ((c + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
}

/** WCAG 2.x relative luminance of [rgb], 0 (black) to 1 (white). Alpha is ignored. */
fun relativeLuminance(rgb: Int): Double =
    LUMA_RED * linear(red(rgb)) + LUMA_GREEN * linear(green(rgb)) + LUMA_BLUE * linear(blue(rgb))

/** WCAG 2.x contrast ratio of [a] and [b], lighter over darker: 1 (same) to 21. Alpha is ignored. */
fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + CONTRAST_FLARE) / (minOf(la, lb) + CONTRAST_FLARE)
}

/** One palette bucket: how many sampled pixels fell in it and their channel sums. */
private class Bucket {
    var count = 0
    var red = 0L
    var green = 0L
    var blue = 0L

    fun add(rgb: Int) {
        count++
        red += red(rgb)
        green += green(rgb)
        blue += blue(rgb)
    }

    /** The bucket's average color, opaque. */
    fun average(): Int = argb(
        CHANNEL_MASK,
        (red.toDouble() / count).roundToInt(),
        (green.toDouble() / count).roundToInt(),
        (blue.toDouble() / count).roundToInt(),
    )
}

private fun bucketKey(rgb: Int): Int =
    ((red(rgb) shr BUCKET_SHIFT) shl (2 * BUCKET_BITS)) or ((green(rgb) shr BUCKET_SHIFT) shl BUCKET_BITS) or
        (blue(rgb) shr BUCKET_SHIFT)

/** HSV saturation of [rgb], 0 (grey) to 1. */
private fun saturation(rgb: Int): Float {
    val max = maxOf(red(rgb), green(rgb), blue(rgb))
    val min = minOf(red(rgb), green(rgb), blue(rgb))
    return if (max == 0) 0f else (max - min).toFloat() / max
}

private fun distance(a: Int, b: Int): Double {
    val dr = (red(a) - red(b)).toDouble()
    val dg = (green(a) - green(b)).toDouble()
    val db = (blue(a) - blue(b)).toDouble()
    return sqrt(dr * dr + dg * dg + db * db)
}

/** [rgb] with each channel multiplied by [factor] (clamped `0..1`), opaque. */
private fun scaled(rgb: Int, factor: Float): Int {
    val f = factor.coerceIn(0f, 1f)
    return argb(CHANNEL_MASK, (red(rgb) * f).roundToInt(), (green(rgb) * f).roundToInt(), (blue(rgb) * f).roundToInt())
}

/** [rgb] moved [fraction] of the way to white, opaque. */
private fun lightened(rgb: Int, fraction: Float): Int = lerpColor(rgb or OPAQUE_BLACK, OPAQUE_WHITE, fraction)

/** The sampled buckets of [img], by key; pixels under [SpotifyConstants.PALETTE_ALPHA_CUTOFF] are left out. */
private fun bucketsOf(img: BufferedImage): Map<Int, Bucket> {
    val step = if (img.width > SpotifyConstants.PALETTE_SAMPLE_PX) img.width / SpotifyConstants.PALETTE_SAMPLE_PX else 1
    val buckets = HashMap<Int, Bucket>()
    for (y in 0 until img.height step step) {
        for (x in 0 until img.width step step) {
            val pixel = img.getRGB(x, y)
            if (alpha(pixel) < SpotifyConstants.PALETTE_ALPHA_CUTOFF) continue
            buckets.getOrPut(bucketKey(pixel), ::Bucket).add(pixel)
        }
    }
    return buckets
}

/** Highest count first; on a tie, the lower bucket key. */
private val byCountThenKey = compareByDescending<Map.Entry<Int, Bucket>> { it.value.count }.thenBy { it.key }

/**
 * The widget's colors from the cover [img]. Pixels with alpha under
 * [SpotifyConstants.PALETTE_ALPHA_CUTOFF] are left out; a cover wider than
 * [SpotifyConstants.PALETTE_SAMPLE_PX] is sampled every `width / PALETTE_SAMPLE_PX` pixels. Each
 * color falls in a bucket by the top 4 bits of R, G and B. The most common bucket is the
 * dominant; the most common other bucket at least [SpotifyConstants.ACCENT_MIN_SATURATION]
 * saturated and [SpotifyConstants.ACCENT_MIN_DISTANCE] from it is the accent (else the dominant
 * lightened by [SpotifyConstants.ACCENT_FALLBACK_LIGHTEN]). The background is the dominant
 * darkened until white text reaches [SpotifyConstants.TEXT_MIN_CONTRAST], at
 * [SpotifyConstants.PANEL_ALPHA]; the accent (and border) is lightened until it reaches
 * [SpotifyConstants.ACCENT_MIN_CONTRAST] on it. The icon is green when green reaches
 * [SpotifyConstants.ICON_MIN_CONTRAST] on the background, else white. No usable pixel gives
 * [FALLBACK_COLORS].
 */
fun paletteOf(img: BufferedImage): SpotifyColors {
    val ranked = bucketsOf(img).entries.sortedWith(byCountThenKey)
    if (ranked.isEmpty()) return FALLBACK_COLORS
    val dominant = ranked.first().value.average()
    val accentBase = ranked.drop(1).map { it.value.average() }.firstOrNull {
        saturation(it) >= SpotifyConstants.ACCENT_MIN_SATURATION &&
            distance(it, dominant) >= SpotifyConstants.ACCENT_MIN_DISTANCE
    } ?: lightened(dominant, SpotifyConstants.ACCENT_FALLBACK_LIGHTEN)

    // Black reads 21:1 under white text, and white reaches any accent contrast on a background
    // that white text reads on, so both searches always end by their last step.
    val backgroundRgb = (0..MAX_COLOR_STEPS).asSequence()
        .map { scaled(dominant, 1f - SpotifyConstants.COLOR_STEP * it) }
        .firstOrNull { contrastRatio(OPAQUE_WHITE, it) >= SpotifyConstants.TEXT_MIN_CONTRAST } ?: OPAQUE_BLACK
    val accent = (0..MAX_COLOR_STEPS).asSequence()
        .map { lightened(accentBase, SpotifyConstants.COLOR_STEP * it) }
        .firstOrNull { contrastRatio(it, backgroundRgb) >= SpotifyConstants.ACCENT_MIN_CONTRAST } ?: OPAQUE_WHITE
    val icon = if (contrastRatio(UiTheme.Colors.SPOTIFY_GREEN, backgroundRgb) >= SpotifyConstants.ICON_MIN_CONTRAST) {
        SpotifyIcon.GREEN
    } else {
        SpotifyIcon.WHITE
    }
    val background = (SpotifyConstants.PANEL_ALPHA shl ALPHA_SHIFT) or (backgroundRgb and RGB_MASK)
    return SpotifyColors(background, border = accent, accent = accent, text = OPAQUE_WHITE, icon = icon)
}

private fun lerpChannel(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt()

/** [a] to [b] by [t] (clamped `0..1`), each channel including alpha, rounded. */
fun lerpColor(a: Int, b: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    return argb(
        lerpChannel(alpha(a), alpha(b), f),
        lerpChannel(red(a), red(b), f),
        lerpChannel(green(a), green(b), f),
        lerpChannel(blue(a), blue(b), f),
    )
}

/**
 * The colors [msSinceChange] into the fade from [from] to [to], over
 * [SpotifyConstants.COLOR_FADE_MS]: every color lerped; the icon switches to [to]'s halfway.
 */
fun fadedColors(from: SpotifyColors, to: SpotifyColors, msSinceChange: Long): SpotifyColors {
    val t = msSinceChange.toFloat() / SpotifyConstants.COLOR_FADE_MS
    return SpotifyColors(
        background = lerpColor(from.background, to.background, t),
        border = lerpColor(from.border, to.border, t),
        accent = lerpColor(from.accent, to.accent, t),
        text = lerpColor(from.text, to.text, t),
        icon = if (t >= ICON_SWITCH_T) to.icon else from.icon,
    )
}
