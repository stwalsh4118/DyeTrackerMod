package com.dyetracker.spotify

import com.dyetracker.ui.theme.UiTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit coverage for the widget's colors from the cover (task 76-6): WCAG contrast, the palette a
 * cover gives (readable text, a visible accent, the icon by contrast), the color lerp, the fade
 * on a track change, and the color store's [nextFade].
 */
class SpotifyPaletteTest {

    private fun solid(rgb: Int, size: Int = SIZE): BufferedImage =
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until size) for (x in 0 until size) setRGB(x, y, rgb)
        }

    /** [top] in the first [topRows] rows, [bottom] in the rest. */
    private fun split(top: Int, bottom: Int, topRows: Int): BufferedImage =
        BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until SIZE) for (x in 0 until SIZE) setRGB(x, y, if (y < topRows) top else bottom)
        }

    private fun red(argb: Int) = (argb shr 16) and 0xFF
    private fun green(argb: Int) = (argb shr 8) and 0xFF
    private fun blue(argb: Int) = argb and 0xFF
    private fun alpha(argb: Int) = argb ushr 24

    // contrast

    @Test
    fun `white on black is 21 to 1`() {
        assertEquals(21.0, contrastRatio(0xFFFFFF, 0x000000), CONTRAST_TOLERANCE)
    }

    @Test
    fun `contrast is the same in either order`() {
        assertEquals(contrastRatio(0x000000, 0xFFFFFF), contrastRatio(0xFFFFFF, 0x000000))
    }

    @Test
    fun `a color against itself is 1 to 1`() {
        assertEquals(1.0, contrastRatio(0x1A1A2E, 0x1A1A2E))
    }

    @Test
    fun `contrast ignores alpha`() {
        assertEquals(contrastRatio(0xFFFFFF, 0x000000), contrastRatio(0x00_FF_FF_FF, 0xC8_00_00_00.toInt()))
    }

    @Test
    fun `relative luminance is 0 for black and 1 for white`() {
        assertEquals(0.0, relativeLuminance(0x000000))
        assertEquals(1.0, relativeLuminance(0xFFFFFF), CONTRAST_TOLERANCE)
    }

    // paletteOf

    @Test
    fun `a solid red cover gives a red background white text can be read on`() {
        val colors = paletteOf(solid(OPAQUE or 0xC0392B))
        val bg = colors.background
        assertTrue(red(bg) > green(bg) && red(bg) > blue(bg), "background ${hex(bg)} is not red")
        assertTrue(contrastRatio(WHITE, bg) >= SpotifyConstants.TEXT_MIN_CONTRAST)
        assertEquals(SpotifyConstants.PANEL_ALPHA, alpha(bg))
        assertTrue(contrastRatio(colors.accent, bg) >= SpotifyConstants.ACCENT_MIN_CONTRAST)
        assertEquals(0xFF, alpha(colors.accent))
        assertEquals(colors.accent, colors.border)
        assertEquals(WHITE, colors.text)
    }

    @Test
    fun `a fully transparent cover gives the fallback colors`() {
        assertSame(FALLBACK_COLORS, paletteOf(solid(0x00_C0_39_2B)))
    }

    @Test
    fun `pixels under the alpha cutoff are left out and pixels at it count`() {
        val below = (SpotifyConstants.PALETTE_ALPHA_CUTOFF - 1 shl 24) or 0xE94560
        val at = (SpotifyConstants.PALETTE_ALPHA_CUTOFF shl 24) or 0x1A1A2E
        assertEquals(paletteOf(solid(OPAQUE or 0x1A1A2E)), paletteOf(split(below, at, topRows = SIZE * 3 / 4)))
    }

    @Test
    fun `the dominant color is the background and a saturated minority color is the accent`() {
        val colors = paletteOf(split(OPAQUE or 0x1A1A2E, OPAQUE or 0xE94560, topRows = SIZE * 3 / 4))
        // #1A1A2E needs no darkening for white text, so the background is it at the panel alpha.
        assertEquals((SpotifyConstants.PANEL_ALPHA shl 24) or 0x1A1A2E, colors.background)
        val accent = colors.accent
        val fromMinority = red(accent) > green(accent) && red(accent) > blue(accent)
        assertTrue(fromMinority, "accent ${hex(accent)} is not from #E94560")
    }

    @Test
    fun `a minority color too close to the dominant is not the accent`() {
        // #20203A is saturated enough but within ACCENT_MIN_DISTANCE of #1A1A2E: the accent is the
        // dominant lightened, which keeps blue as its largest channel.
        val colors = paletteOf(split(OPAQUE or 0x1A1A2E, OPAQUE or 0x20203A, topRows = SIZE * 3 / 4))
        val accent = colors.accent
        assertTrue(blue(accent) > red(accent), "accent ${hex(accent)} is not the dominant lightened")
    }

    @Test
    fun `a grey minority color is not saturated enough to be the accent`() {
        val withGrey = paletteOf(split(OPAQUE or 0x1A1A2E, OPAQUE or 0x909090, topRows = SIZE * 3 / 4))
        assertEquals(paletteOf(solid(OPAQUE or 0x1A1A2E)).accent, withGrey.accent)
    }

    @Test
    fun `a solid white cover is darkened for white text and the icon follows the contrast rule`() {
        val colors = paletteOf(solid(WHITE))
        val bg = colors.background
        assertTrue(contrastRatio(WHITE, bg) >= SpotifyConstants.TEXT_MIN_CONTRAST)
        val greenReadable = contrastRatio(UiTheme.Colors.SPOTIFY_GREEN, bg) >= SpotifyConstants.ICON_MIN_CONTRAST
        assertEquals(if (greenReadable) SpotifyIcon.GREEN else SpotifyIcon.WHITE, colors.icon)
    }

    @Test
    fun `a green cover shows the white icon`() {
        // Spotify green itself: darkened just enough for white text, the green icon fades into it.
        val colors = paletteOf(solid(UiTheme.Colors.SPOTIFY_GREEN))
        assertEquals(SpotifyIcon.WHITE, colors.icon)
    }

    @Test
    fun `a black cover shows the green icon`() {
        assertEquals(SpotifyIcon.GREEN, paletteOf(solid(OPAQUE)).icon)
    }

    @Test
    fun `a wide cover is sampled and still gives its color`() {
        val colors = paletteOf(solid(OPAQUE or 0x1A1A2E, size = WIDE))
        assertEquals((SpotifyConstants.PANEL_ALPHA shl 24) or 0x1A1A2E, colors.background)
    }

    // lerpColor

    @Test
    fun `lerpColor halfway from black to white rounds to 80`() {
        assertEquals(0xFF_80_80_80.toInt(), lerpColor(OPAQUE, WHITE, 0.5f))
    }

    @Test
    fun `lerpColor lerps alpha too`() {
        assertEquals(0x80_00_00_00.toInt(), lerpColor(0x00_00_00_00, OPAQUE, 0.5f))
    }

    @Test
    fun `lerpColor clamps t`() {
        assertEquals(OPAQUE, lerpColor(OPAQUE, WHITE, -1f))
        assertEquals(WHITE, lerpColor(OPAQUE, WHITE, 2f))
        assertEquals(OPAQUE, lerpColor(OPAQUE, WHITE, 0f))
        assertEquals(WHITE, lerpColor(OPAQUE, WHITE, 1f))
    }

    // fadedColors

    private val from = SpotifyColors(OPAQUE, OPAQUE, OPAQUE, OPAQUE, SpotifyIcon.GREEN)
    private val to = SpotifyColors(WHITE, WHITE, WHITE, WHITE, SpotifyIcon.WHITE)

    @Test
    fun `fadedColors at 0 ms is from`() {
        assertEquals(from, fadedColors(from, to, 0))
    }

    @Test
    fun `fadedColors at the fade length is to`() {
        assertEquals(to, fadedColors(from, to, SpotifyConstants.COLOR_FADE_MS))
        assertEquals(to, fadedColors(from, to, SpotifyConstants.COLOR_FADE_MS * 2))
    }

    @Test
    fun `fadedColors before the change is from`() {
        assertEquals(from, fadedColors(from, to, -1))
    }

    @Test
    fun `fadedColors lerps every color halfway through`() {
        val half = fadedColors(from, to, SpotifyConstants.COLOR_FADE_MS / 2)
        val grey = 0xFF_80_80_80.toInt()
        assertEquals(SpotifyColors(grey, grey, grey, grey, SpotifyIcon.WHITE), half)
    }

    @Test
    fun `the icon flips halfway through the fade`() {
        val halfway = SpotifyConstants.COLOR_FADE_MS / 2
        assertEquals(SpotifyIcon.GREEN, fadedColors(from, to, halfway - 1).icon)
        assertEquals(SpotifyIcon.WHITE, fadedColors(from, to, halfway).icon)
    }

    // ColorFade

    @Test
    fun `the colors start on the fallback`() {
        assertEquals(ColorFade(FALLBACK_COLORS, FALLBACK_COLORS, 0), ColorFade.INITIAL)
        assertEquals(FALLBACK_COLORS, ColorFade.INITIAL.at(NOW))
    }

    @Test
    fun `a new palette makes the old current the previous and stamps the change`() {
        val fade = nextFade(ColorFade(from, from, 0), to, NOW)
        assertEquals(ColorFade(from, to, NOW), fade)
        assertEquals(from, fade.at(NOW))
        assertEquals(to, fade.at(NOW + SpotifyConstants.COLOR_FADE_MS))
    }

    @Test
    fun `the same palette again keeps the fade as it is`() {
        val fade = ColorFade(from, to, NOW)
        assertSame(fade, nextFade(fade, to.copy(), NOW + 1))
    }

    private fun hex(argb: Int) = "#%08X".format(argb)

    private companion object {
        const val SIZE = 64
        const val WIDE = 640
        const val OPAQUE = 0xFF_00_00_00.toInt()
        const val WHITE = 0xFF_FF_FF_FF.toInt()
        const val CONTRAST_TOLERANCE = 0.01
        const val NOW = 1_000_000L
    }
}
