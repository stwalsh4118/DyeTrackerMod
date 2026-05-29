package com.dyetracker.ui.core

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import kotlin.math.roundToInt

/**
 * Thin wrappers over the repetitive [GuiGraphicsExtractor] primitives used across the toolkit:
 * solid fills, 1px bordered rectangles, alignment-aware text, and the non-tiling
 * stretched-texture blit. Centralizing these keeps widgets free of raw render-context
 * boilerplate and documents the single correct way to do each.
 *
 * TODO(PBI-52): the actual draw calls are stubbed to no-ops for the 26.1 compile-green
 * milestone (PBI-51 task 51-8). 26.1 replaced the immediate-mode `DrawContext`/`GuiGraphics`
 * draw API with the `GuiGraphicsExtractor` render-state extraction model; porting the real
 * `fill`/`text`/`blit` calls (and the matrix/`pose()` scale path) is PBI-52's scope, verified
 * in-client there. Signatures + text measurement are kept real so callers type-check and
 * layout math stays correct; only the GPU-facing primitives are deferred.
 */
object UiDraw {

    const val DEFAULT_BORDER_PX = 1

    /** Fill a solid rectangle of [width]×[height] at ([x], [y]) with [argb]. No-op if empty. */
    @Suppress("UNUSED_PARAMETER")
    fun fillRect(context: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, argb: Int) {
        if (width <= 0 || height <= 0) return
        // TODO(PBI-52): real render — context.fill(x, y, x + width, y + height, argb)
    }

    /**
     * Stroke a [thickness]px border along the inner edges of the [width]×[height] rect at
     * ([x], [y]). For an *outset* focus border, pass an expanded rect.
     */
    @Suppress("UNUSED_PARAMETER")
    fun strokeRect(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        argb: Int,
        thickness: Int = DEFAULT_BORDER_PX,
    ) {
        if (width <= 0 || height <= 0 || thickness <= 0) return
        // TODO(PBI-52): real render — four context.fill(...) edge strokes (top/bottom/left/right)
    }

    /** Fill [fillArgb] then stroke a [borderThickness]px [borderArgb] border on the same rect. */
    fun filledBox(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fillArgb: Int,
        borderArgb: Int,
        borderThickness: Int = DEFAULT_BORDER_PX,
    ) {
        fillRect(context, x, y, width, height, fillArgb)
        strokeRect(context, x, y, width, height, borderArgb, borderThickness)
    }

    /**
     * Draw [text] at ([x], [y]) honoring [alignment] within [availableWidth] (defaults to the
     * text's own width × [scale]). Optionally drawn with a drop [shadow].
     */
    @Suppress("UNUSED_PARAMETER")
    fun drawText(
        context: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        argb: Int,
        shadow: Boolean = true,
        scale: Float = 1f,
        availableWidth: Int = (font.width(text) * scale).roundToInt(),
        alignment: HorizontalAlignment = HorizontalAlignment.START,
    ) {
        // TODO(PBI-52): real render — alignment offset + context.drawText(font, text, drawX, y, argb, shadow),
        // with the scale != 1f matrix/pose() path. Text measurement (font.width) is kept for layout sizing.
    }

    /**
     * Blit [textureId] into a [destWidth]×[destHeight] quad at ([x], [y]), bilinearly stretching
     * the [srcWidth]×[srcHeight] source when the destination size differs.
     */
    @Suppress("UNUSED_PARAMETER")
    fun blitStretched(
        context: GuiGraphicsExtractor,
        textureId: Identifier,
        x: Int,
        y: Int,
        destWidth: Int,
        destHeight: Int,
        srcWidth: Int,
        srcHeight: Int,
    ) {
        if (destWidth <= 0 || destHeight <= 0 || srcWidth <= 0 || srcHeight <= 0) return
        // TODO(PBI-52): real render — the 12-arg stretched context.drawTexture(RenderPipelines.GUI_TEXTURED, ...)
    }
}
