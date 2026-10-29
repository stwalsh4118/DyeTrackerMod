package com.dyetracker.spotify

/**
 * The widget's colors fading from [previous] to [current] since [changedAtMs] (task 76-6). One
 * immutable value, so the render thread never reads a half-updated set.
 */
data class ColorFade(val previous: SpotifyColors, val current: SpotifyColors, val changedAtMs: Long) {

    /** The colors to draw at [nowMs]. */
    fun at(nowMs: Long): SpotifyColors = fadedColors(previous, current, nowMs - changedAtMs)

    companion object {
        /** Before any cover: the fallback, with nothing to fade from. */
        val INITIAL = ColorFade(FALLBACK_COLORS, FALLBACK_COLORS, 0)
    }
}

/**
 * The fade after [target] becomes current at [nowMs]: the old current becomes the previous and
 * the change is stamped. A [target] equal to the current keeps [prev] as it is.
 */
fun nextFade(prev: ColorFade, target: SpotifyColors, nowMs: Long): ColorFade =
    if (target == prev.current) prev else ColorFade(prev.current, target, nowMs)

/**
 * The widget's current [ColorFade] (task 76-6). Written only on the art thread by
 * [SpotifyArtFetcher] when a cover's palette (or the fallback) becomes current; read by the HUD
 * provider on the render thread.
 */
object SpotifyColorStore {

    @Volatile
    var fade: ColorFade = ColorFade.INITIAL
        private set

    /** Make [target] current at [nowMs]. Art thread only: the one writer, so read-then-write is safe. */
    fun recolor(target: SpotifyColors, nowMs: Long) {
        fade = nextFade(fade, target, nowMs)
    }

    /** The colors to draw at [nowMs]. */
    fun colorsAt(nowMs: Long): SpotifyColors = fade.at(nowMs)
}
