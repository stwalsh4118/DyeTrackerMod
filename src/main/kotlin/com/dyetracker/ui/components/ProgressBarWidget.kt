package com.dyetracker.ui.components

import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.Size
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.theme.UiTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A horizontal progress bar: a full-width track with a proportional fill, mirroring the
 * website single-dye overlay's `ProgressBar` (track + fill at `value%`,
 * `packages/web/src/components/ui/ProgressBar.tsx`). Purely presentational — it takes a
 * static [value] and draws; no data fetching and no animation. An optional [headColor] draws the
 * last [headWidth] px of the fill (see [headX]) brighter; by default there is no head.
 *
 * The bar reserves a fixed [width] × [height] footprint (it does not grow with content).
 * [value] is clamped to `0..100`; the fill width is `round(width * value / 100)` clamped to
 * `[0, width]`, so `value >= 100` fills the whole track and `value <= 0` shows the bare track.
 */
class ProgressBarWidget(
    private val value: Float,
    private val width: Int,
    private val height: Int = UiTheme.Sizing.PROGRESS_BAR_HEIGHT,
    private val trackColor: Int = UiTheme.Colors.PROGRESS_TRACK,
    private val fillColor: Int = UiTheme.Colors.PROGRESS_FILL,
    private val headColor: Int? = null,
    private val headWidth: Int = 0,
) : Widget {

    override fun measure(): Size = Size(width, height)

    override fun draw(ctx: RenderContext, x: Int, y: Int) {
        UiDraw.fillRect(ctx.drawContext, x, y, width, height, trackColor)
        val fill = fillWidth(value, width)
        if (fill > 0) {
            UiDraw.fillRect(ctx.drawContext, x, y, fill, height, fillColor)
        }
        val head = headColor ?: return
        val hx = headX(width, value, headWidth) ?: return
        UiDraw.fillRect(ctx.drawContext, x + hx, y, min(headWidth, fill), height, head)
    }

    companion object {
        const val MIN_VALUE: Float = 0f
        const val MAX_VALUE: Float = 100f

        /**
         * Pixel width of the filled portion for [value] (clamped to `0..100`) over a [width]px
         * track, clamped to `[0, width]`. Returns 0 for a non-positive [width].
         */
        fun fillWidth(value: Float, width: Int): Int {
            if (width <= 0) return 0
            val clamped = value.coerceIn(MIN_VALUE, MAX_VALUE)
            return (width * clamped / MAX_VALUE).roundToInt().coerceIn(0, width)
        }

        /**
         * X offset of the fill's head ([headWidth] px at its leading edge) for [value] over a
         * [width]px track: `max(0, fillWidth - headWidth)`, or null with no fill or no head.
         */
        fun headX(width: Int, value: Float, headWidth: Int): Int? {
            val fill = fillWidth(value, width)
            if (fill == 0 || headWidth <= 0) return null
            return max(0, fill - headWidth)
        }
    }
}
