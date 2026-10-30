package com.dyetracker.ui.theme

import com.dyetracker.spotify.SpotifyConstants
import com.dyetracker.spotify.contrastRatio
import kotlin.math.roundToInt

/** The panel fill of every dye theme: the same dark gray as Default's. */
const val DYE_PANEL_BACKGROUND = 0x101010

/** The WCAG contrast a dye theme's accent must reach on [DYE_PANEL_BACKGROUND]. */
const val DYE_ACCENT_MIN_CONTRAST = 3.0

/** How far a dye theme's secondary accent moves from its primary accent toward white. */
const val DYE_SECONDARY_LIGHTEN = 0.35f

/** The alpha of a dye theme's panel border (40%), over its primary accent. */
const val DYE_BORDER_ALPHA = 0x66

/** White: the lightening target, the accent when nothing lighter reads, and the primary text. */
private const val WHITE = 0xFFFFFF

/** The secondary text color of a dye theme (Default's). */
private const val DYE_TEXT_SECONDARY = 0xBFBFBF

/** Lightening steps until a color is white. */
private val MAX_LIGHTEN_STEPS = (1f / SpotifyConstants.COLOR_STEP).roundToInt()

/**
 * The palette of the dye theme for the dye colored [dyeRgb] (`0xRRGGBB`, PBI 81). The primary accent
 * is the dye lightened by [SpotifyConstants.COLOR_STEP] steps until it reaches
 * [DYE_ACCENT_MIN_CONTRAST] on [DYE_PANEL_BACKGROUND] (white if no step does); the secondary is
 * that accent [DYE_SECONDARY_LIGHTEN] of the way to white; the border is the accent at
 * [DYE_BORDER_ALPHA]. Panels are dark and the text white and light gray.
 */
fun dyePalette(dyeRgb: Int): ThemePalette {
    val accent = (0..MAX_LIGHTEN_STEPS).asSequence()
        .map { themeMix(dyeRgb, WHITE, SpotifyConstants.COLOR_STEP * it) }
        .firstOrNull { contrastRatio(it, DYE_PANEL_BACKGROUND) >= DYE_ACCENT_MIN_CONTRAST } ?: WHITE
    return ThemePalette(
        accentPrimary = accent,
        accentSecondary = themeMix(accent, WHITE, DYE_SECONDARY_LIGHTEN),
        panelBackground = DYE_PANEL_BACKGROUND,
        panelBorder = themeWithAlpha(DYE_BORDER_ALPHA, accent),
        textPrimary = WHITE,
        textSecondary = DYE_TEXT_SECONDARY,
    )
}
