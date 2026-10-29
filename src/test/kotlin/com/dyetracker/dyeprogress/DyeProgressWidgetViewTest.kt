package com.dyetracker.dyeprogress

import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.dyeprogress.DyeProgressWidgetView.RenderState
import com.dyetracker.ui.theme.HudTint
import com.dyetracker.ui.theme.TintColors
import com.dyetracker.ui.theme.UiTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit coverage for the pure helpers in [DyeProgressWidgetView] (tasks 34-5, 36-3): percent
 * formatting, render-state selection, accent-color parsing, dye-id humanizing, and the PBI 36
 * piece-toggle layout planners ([DyeProgressWidgetView.cardChrome],
 * [DyeProgressWidgetView.readyLayout]). The composed widget tree needs a live
 * `DrawContext`/`TextRenderer` and is verified in-client in 34-8 / 36-6.
 */
class DyeProgressWidgetViewTest {

    @AfterTest
    fun resetTint() {
        HudTint.colors = null
    }

    private fun entry(progress: Double?, color: String = "#e225f4") = DyeProgressEntry(
        dyeId = "matcha",
        name = "Matcha Dye",
        color = color,
        category = "Slayer",
        progress = progress,
        trackable = progress != null,
    )

    /** A widget config with overridable piece-toggle flags (placement/profile fields fixed). */
    private fun cfg(
        showBackground: Boolean = true,
        showBorder: Boolean = true,
        showProgressBar: Boolean = true,
        showName: Boolean = true,
        showSource: Boolean = true,
        showPercent: Boolean = true,
        showRotation: Boolean = true,
        percentInIconCorner: Boolean = false,
    ) = DyeProgressWidgetConfig(
        id = "w1",
        dyeId = "matcha",
        profileName = "Mango",
        profileId = "p1",
        showBackground = showBackground,
        showBorder = showBorder,
        showProgressBar = showProgressBar,
        showName = showName,
        showSource = showSource,
        showPercent = showPercent,
        showRotation = showRotation,
        percentInIconCorner = percentInIconCorner,
    )

    // --- formatProgress -----------------------------------------------------

    @Test
    fun `formatProgress shows N slash A for null`() {
        assertEquals("N/A", DyeProgressWidgetView.formatProgress(null))
    }

    @Test
    fun `formatProgress shows 100 percent at or above 100`() {
        assertEquals("100%", DyeProgressWidgetView.formatProgress(100.0))
        assertEquals("100%", DyeProgressWidgetView.formatProgress(137.5))
    }

    @Test
    fun `formatProgress shows one decimal below 100`() {
        assertEquals("42.0%", DyeProgressWidgetView.formatProgress(42.0))
        assertEquals("3.1%", DyeProgressWidgetView.formatProgress(3.14159))
        assertEquals("0.0%", DyeProgressWidgetView.formatProgress(0.0))
    }

    // --- selectState --------------------------------------------------------

    @Test
    fun `loading status selects LOADING`() {
        assertEquals(RenderState.LOADING, DyeProgressWidgetView.selectState(DyeProgressStore.Status.LOADING, null))
    }

    @Test
    fun `ok with a trackable entry selects READY`() {
        assertEquals(RenderState.READY, DyeProgressWidgetView.selectState(DyeProgressStore.Status.OK, entry(42.0)))
    }

    @Test
    fun `ok with a null-progress entry selects NOT_TRACKABLE`() {
        assertEquals(
            RenderState.NOT_TRACKABLE,
            DyeProgressWidgetView.selectState(DyeProgressStore.Status.OK, entry(null)),
        )
    }

    @Test
    fun `ok with a missing entry selects NOT_TRACKABLE`() {
        assertEquals(RenderState.NOT_TRACKABLE, DyeProgressWidgetView.selectState(DyeProgressStore.Status.OK, null))
    }

    @Test
    fun `error with prior trackable data selects READY (last known)`() {
        assertEquals(RenderState.READY, DyeProgressWidgetView.selectState(DyeProgressStore.Status.ERROR, entry(42.0)))
    }

    @Test
    fun `error with no prior data selects UNAVAILABLE`() {
        assertEquals(RenderState.UNAVAILABLE, DyeProgressWidgetView.selectState(DyeProgressStore.Status.ERROR, null))
    }

    @Test
    fun `error with prior null-progress entry selects NOT_TRACKABLE`() {
        assertEquals(
            RenderState.NOT_TRACKABLE,
            DyeProgressWidgetView.selectState(DyeProgressStore.Status.ERROR, entry(null)),
        )
    }

    // --- parseAccentColor ---------------------------------------------------

    @Test
    fun `parseAccentColor parses a 6-digit hex with full opacity`() {
        assertEquals(0xFFE225F4.toInt(), DyeProgressWidgetView.parseAccentColor("#e225f4"))
        assertEquals(0xFFE225F4.toInt(), DyeProgressWidgetView.parseAccentColor("e225f4"))
    }

    @Test
    fun `parseAccentColor parses an 8-digit hex`() {
        assertEquals(0x80FF0000.toInt(), DyeProgressWidgetView.parseAccentColor("80ff0000"))
    }

    @Test
    fun `parseAccentColor is case-insensitive and trims whitespace`() {
        assertEquals(0xFFE225F4.toInt(), DyeProgressWidgetView.parseAccentColor("#E225F4"))
        assertEquals(0xFFE225F4.toInt(), DyeProgressWidgetView.parseAccentColor("# e225f4 "))
    }

    @Test
    fun `parseAccentColor falls back on null or bad input`() {
        assertEquals(UiTheme.Colors.PROGRESS_FILL, DyeProgressWidgetView.parseAccentColor(null))
        assertEquals(UiTheme.Colors.PROGRESS_FILL, DyeProgressWidgetView.parseAccentColor(""))
        assertEquals(UiTheme.Colors.PROGRESS_FILL, DyeProgressWidgetView.parseAccentColor("not-a-color"))
        assertEquals(UiTheme.Colors.PROGRESS_FILL, DyeProgressWidgetView.parseAccentColor("#abc"))
    }

    // --- humanizeDyeId ------------------------------------------------------

    @Test
    fun `humanizeDyeId title-cases snake_case ids`() {
        assertEquals("Wild Strawberry", DyeProgressWidgetView.humanizeDyeId("wild_strawberry"))
        assertEquals("Matcha", DyeProgressWidgetView.humanizeDyeId("matcha"))
        assertEquals("Pure Black", DyeProgressWidgetView.humanizeDyeId("pure_black"))
    }

    // --- cardChrome (background/border toggles) ------------------------------

    @Test
    fun `cardChrome with both on uses the neutral panel fill and border`() {
        val chrome = DyeProgressWidgetView.cardChrome(showBackground = true, showBorder = true)
        assertEquals(UiTheme.Colors.PANEL_BACKGROUND, chrome.background)
        assertEquals(UiTheme.Colors.PANEL_BORDER, chrome.border)
    }

    @Test
    fun `cardChrome with background off uses a transparent fill`() {
        val chrome = DyeProgressWidgetView.cardChrome(showBackground = false, showBorder = true)
        assertEquals(UiTheme.Colors.TRANSPARENT, chrome.background)
        assertEquals(UiTheme.Colors.PANEL_BORDER, chrome.border)
    }

    @Test
    fun `cardChrome with border off drops the border`() {
        val chrome = DyeProgressWidgetView.cardChrome(showBackground = true, showBorder = false)
        assertEquals(UiTheme.Colors.PANEL_BACKGROUND, chrome.background)
        assertNull(chrome.border)
    }

    @Test
    fun `cardChrome with both off is transparent and borderless`() {
        val chrome = DyeProgressWidgetView.cardChrome(showBackground = false, showBorder = false)
        assertEquals(UiTheme.Colors.TRANSPARENT, chrome.background)
        assertNull(chrome.border)
    }

    @Test
    fun `cardChrome with both on takes the HUD tint when one is set`() {
        HudTint.colors = TintColors(TINT_BACKGROUND, TINT_BORDER)
        val chrome = DyeProgressWidgetView.cardChrome(showBackground = true, showBorder = true)
        assertEquals(TINT_BACKGROUND, chrome.background)
        assertEquals(TINT_BORDER, chrome.border)
    }

    @Test
    fun `cardChrome with both off stays transparent and borderless under a HUD tint`() {
        HudTint.colors = TintColors(TINT_BACKGROUND, TINT_BORDER)
        val chrome = DyeProgressWidgetView.cardChrome(showBackground = false, showBorder = false)
        assertEquals(UiTheme.Colors.TRANSPARENT, chrome.background)
        assertNull(chrome.border)
    }

    // --- readyLayout (piece composition + percent placement) ----------------

    @Test
    fun `readyLayout defaults reproduce today's full layout`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(), hasSource = true, hasRotation = false)
        assertTrue(layout.nameInHeader)
        assertTrue(layout.percentInHeader)
        assertFalse(layout.percentInCorner)
        assertTrue(layout.showBar)
        assertTrue(layout.showSource)
        assertTrue(layout.hasHeader)
    }

    @Test
    fun `readyLayout drops the bar when showProgressBar is off`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(showProgressBar = false), hasSource = true, hasRotation = false)
        assertFalse(layout.showBar)
        assertTrue(layout.nameInHeader)
        assertTrue(layout.percentInHeader)
    }

    @Test
    fun `readyLayout drops the name but keeps the percent when showName is off`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(showName = false), hasSource = true, hasRotation = false)
        assertFalse(layout.nameInHeader)
        assertTrue(layout.percentInHeader)
        assertTrue(layout.hasHeader)
    }

    @Test
    fun `readyLayout removes the percent from both placements when showPercent is off`() {
        val layout = DyeProgressWidgetView.readyLayout(
            cfg(showPercent = false, percentInIconCorner = true),
            hasSource = true,
            hasRotation = false,
        )
        assertFalse(layout.percentInHeader)
        assertFalse(layout.percentInCorner)
    }

    @Test
    fun `readyLayout routes the percent to the corner when percentInIconCorner is on`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(percentInIconCorner = true), hasSource = true, hasRotation = false)
        assertTrue(layout.percentInCorner)
        assertFalse(layout.percentInHeader)
        assertTrue(layout.nameInHeader)
    }

    @Test
    fun `readyLayout keeps the percent in the header when percentInIconCorner is off`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(percentInIconCorner = false), hasSource = true, hasRotation = false)
        assertTrue(layout.percentInHeader)
        assertFalse(layout.percentInCorner)
    }

    @Test
    fun `readyLayout omits the source line when there is no source text even if enabled`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(showSource = true), hasSource = false, hasRotation = false)
        assertFalse(layout.showSource)
    }

    @Test
    fun `readyLayout omits the source line when showSource is off`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(showSource = false), hasSource = true, hasRotation = false)
        assertFalse(layout.showSource)
    }

    @Test
    fun `readyLayout with every piece but the icon off yields an empty info column`() {
        val layout = DyeProgressWidgetView.readyLayout(
            cfg(
                showName = false,
                showPercent = false,
                showProgressBar = false,
                showSource = false,
            ),
            hasSource = true,
            hasRotation = false,
        )
        assertFalse(layout.nameInHeader)
        assertFalse(layout.percentInHeader)
        assertFalse(layout.percentInCorner)
        assertFalse(layout.showBar)
        assertFalse(layout.showSource)
        assertFalse(layout.hasHeader)
    }

    @Test
    fun `readyLayout icon-only-plus-corner-percent keeps just the corner badge`() {
        val layout = DyeProgressWidgetView.readyLayout(
            cfg(
                showName = false,
                showProgressBar = false,
                showSource = false,
                percentInIconCorner = true,
            ),
            hasSource = true,
            hasRotation = false,
        )
        assertFalse(layout.hasHeader)
        assertFalse(layout.showBar)
        assertFalse(layout.showSource)
        assertTrue(layout.percentInCorner)
    }

    @Test
    fun `readyLayout without a rotation hides the rotation line`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(showRotation = true), hasSource = true, hasRotation = false)
        assertFalse(layout.showRotation)
    }

    @Test
    fun `readyLayout shows the rotation line when the flag is on and a rotation exists`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(showRotation = true), hasSource = true, hasRotation = true)
        assertTrue(layout.showRotation)
    }

    @Test
    fun `readyLayout hides the rotation line when the flag is off even with a rotation`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(showRotation = false), hasSource = true, hasRotation = true)
        assertFalse(layout.showRotation)
    }

    @Test
    fun `readyLayout rotation line is independent of the source line`() {
        val layout = DyeProgressWidgetView.readyLayout(cfg(showSource = false), hasSource = false, hasRotation = true)
        assertFalse(layout.showSource)
        assertTrue(layout.showRotation)
    }

    private companion object {
        const val TINT_BACKGROUND = 0xC8_20_40_60.toInt()
        const val TINT_BORDER = 0xFF_A0_B0_C0.toInt()
    }
}
