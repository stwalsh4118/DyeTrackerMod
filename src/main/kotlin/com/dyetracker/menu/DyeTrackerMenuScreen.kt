package com.dyetracker.menu

import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.client.showScreen
import com.dyetracker.dyeprogress.DyeProgressWidgetView
import com.dyetracker.rotation.DyeSprites
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Rect
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.texture.ImageTextureManager
import com.dyetracker.ui.theme.UiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The in-game DyeTracker menu (`/dyetracker menu`), styled after the website: a header bar with
 * the title and a tab bar, over a scrolling grid of the player's own dye cards (styled like the
 * website's `DyeCard`) for the profile selected in-game. Always loads by the in-game name,
 * linked or not.
 *
 * The data loads once per screen via [MenuData.load] on [Dispatchers.IO]; the result is posted
 * back to the render thread and dropped if the screen was closed meanwhile ([epoch] guard).
 */
class DyeTrackerMenuScreen : Screen(Component.literal(TITLE)) {

    /** What the content area shows. */
    private sealed class ViewState {
        data object Loading : ViewState()
        data class Loaded(val result: MenuLoadResult.Loaded) : ViewState()
        data class NoProfiles(val username: String) : ViewState()
        data class Failed(val message: String) : ViewState()
    }

    /** Where the card grid sits for the current screen size, and how tall its content is. */
    private data class GridGeometry(
        val columns: Int,
        val left: Int,
        val top: Int,
        val viewport: Rect,
        val contentHeight: Int,
    )

    private var state: ViewState = ViewState.Loading

    /** Pixels the card grid is scrolled up by, kept within [MenuLayout.clampScroll]. */
    private var scroll = 0

    /** Set on the first [init]; resize re-inits must not reload. */
    private var started = false

    /** Bumped per load and on [removed]; a posted result whose epoch is stale is dropped. */
    private var epoch = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init() {
        super.init()
        clampScrollToContent() // a resize changes the viewport and column count
        if (started) return
        started = true
        startLoad()
    }

    private fun startLoad() {
        val myEpoch = ++epoch
        state = ViewState.Loading
        val username = Minecraft.getInstance().user.name
        scope.launch {
            val result = try {
                MenuData.load(username, ApiClient::fetchProfiles, ApiClient::fetchDyeProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                MenuLoadResult.Failed("$UNEXPECTED_ERROR_PREFIX${e.message ?: e.javaClass.simpleName}")
            }
            Minecraft.getInstance().execute {
                if (myEpoch != epoch) return@execute // stale — screen closed or reloaded
                state = result.toViewState()
            }
        }
    }

    private fun MenuLoadResult.toViewState(): ViewState = when (this) {
        is MenuLoadResult.Loaded -> ViewState.Loaded(this)
        is MenuLoadResult.NoProfiles -> ViewState.NoProfiles(username)
        is MenuLoadResult.Failed -> ViewState.Failed(message)
    }

    override fun removed() {
        epoch++
        scope.cancel()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val grid = gridGeometry(loadedDyes().size)
        scroll = MenuLayout.clampScroll(
            scroll - (vertical * MenuLayout.SCROLL_STEP).toInt(),
            grid.contentHeight,
            grid.viewport.height,
        )
        return true
    }

    private fun clampScrollToContent() {
        val grid = gridGeometry(loadedDyes().size)
        scroll = MenuLayout.clampScroll(scroll, grid.contentHeight, grid.viewport.height)
    }

    /** The loaded dyes, or none while loading or after a failure. */
    private fun loadedDyes(): List<DyeProgressEntry> = (state as? ViewState.Loaded)?.result?.dyes.orEmpty()

    /** The card grid for [itemCount] cards, centred horizontally below the header. */
    private fun gridGeometry(itemCount: Int): GridGeometry {
        val columns = MenuLayout.columns(width - 2 * MenuLayout.OUTER_MARGIN)
        val gridWidth = MenuLayout.gridWidth(columns)
        val left = (width - gridWidth) / 2
        val top = MenuLayout.HEADER_HEIGHT + MenuLayout.OUTER_MARGIN
        val viewport = Rect(left, top, gridWidth, height - top - MenuLayout.OUTER_MARGIN)
        return GridGeometry(columns, left, top, viewport, MenuLayout.contentHeight(itemCount, columns))
    }

    override fun onClose() {
        Minecraft.getInstance().showScreen(null)
    }

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(context, mouseX, mouseY, delta)
        UiDraw.fillRect(context, 0, 0, width, height, UiTheme.Colors.MENU_BACKGROUND)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(context, mouseX, mouseY, delta)
        drawHeader(context)
        when (val current = state) {
            ViewState.Loading -> drawMessage(context, LOADING_TEXT, UiTheme.Colors.TEXT_SUBTLE)
            is ViewState.NoProfiles ->
                drawMessage(context, "$NO_PROFILES_PREFIX${current.username}.", UiTheme.Colors.TEXT_SUBTLE)
            is ViewState.Failed ->
                drawMessage(context, "$FAILED_PREFIX${current.message}", UiTheme.Colors.STATUS_ERROR)
            is ViewState.Loaded -> drawGrid(context, current.result.dyes, mouseX, mouseY)
        }
    }

    /** Header bar: the title, then the tab bar (one active tab, underlined, for now). */
    private fun drawHeader(context: GuiGraphicsExtractor) {
        UiDraw.fillRect(context, 0, 0, width, MenuLayout.HEADER_HEIGHT, UiTheme.Colors.MENU_SURFACE)
        val textY = (MenuLayout.HEADER_HEIGHT - font.lineHeight) / 2
        UiDraw.drawText(context, font, TITLE, MenuLayout.OUTER_MARGIN, textY, UiTheme.Colors.ACCENT_PRIMARY)

        val tabX = MenuLayout.OUTER_MARGIN + font.width(TITLE) + MenuLayout.TAB_GAP
        UiDraw.drawText(context, font, DYES_TAB_LABEL, tabX, textY, UiTheme.Colors.TEXT_PRIMARY)
        UiDraw.fillRect(
            context,
            tabX,
            textY + font.lineHeight,
            font.width(DYES_TAB_LABEL),
            MenuLayout.TAB_UNDERLINE_HEIGHT,
            UiTheme.Colors.ACCENT_PRIMARY,
        )
    }

    /** One line of [text], centred in the content area below the header. */
    private fun drawMessage(context: GuiGraphicsExtractor, text: String, argb: Int) {
        val contentHeight = height - MenuLayout.HEADER_HEIGHT
        val y = MenuLayout.HEADER_HEIGHT + (contentHeight - font.lineHeight) / 2
        UiDraw.drawText(
            context,
            font,
            text,
            0,
            y,
            argb,
            availableWidth = width,
            alignment = HorizontalAlignment.CENTER,
        )
    }

    /**
     * The dye cards, clipped to the grid viewport and scrolled by [scroll]; only cards that
     * intersect the viewport are drawn. Nothing is drawn when the window leaves the viewport empty
     * (26.2 rejects a zero-size scissor).
     */
    private fun drawGrid(context: GuiGraphicsExtractor, dyes: List<DyeProgressEntry>, mouseX: Int, mouseY: Int) {
        val grid = gridGeometry(dyes.size)
        val viewport = grid.viewport
        if (viewport.width <= 0 || viewport.height <= 0) return
        val hovered = MenuLayout.cardIndexAt(
            mouseX.toDouble(),
            mouseY.toDouble(),
            dyes.size,
            grid.columns,
            grid.left,
            grid.top,
            scroll,
            viewport,
        )
        val renderCtx = RenderContext(context, Util.getMillis(), false)
        context.enableScissor(viewport.x, viewport.y, viewport.right, viewport.bottom)
        for ((index, entry) in dyes.withIndex()) {
            val (x, y) = MenuLayout.cardOrigin(index, grid.columns, grid.left, grid.top - scroll)
            if (y >= viewport.bottom) break // cards are row-ordered, so every later card is below too
            if (y + MenuLayout.CARD_HEIGHT <= viewport.y) continue
            drawCard(context, renderCtx, entry, x, y, hovered = index == hovered)
        }
        context.disableScissor()
        drawScrollbar(context, viewport, grid.contentHeight)
    }

    /** A thin track right of the grid with a thumb sized and placed by the scroll; only when content overflows. */
    private fun drawScrollbar(context: GuiGraphicsExtractor, viewport: Rect, contentHeight: Int) {
        val maxScroll = contentHeight - viewport.height
        if (maxScroll <= 0) return
        val trackX = viewport.right + MenuLayout.SCROLLBAR_GAP
        UiDraw.fillRect(
            context,
            trackX,
            viewport.y,
            MenuLayout.SCROLLBAR_WIDTH,
            viewport.height,
            UiTheme.Colors.PROGRESS_TRACK,
        )
        val thumbHeight = max(MenuLayout.SCROLLBAR_MIN_THUMB, viewport.height * viewport.height / contentHeight)
            .coerceAtMost(viewport.height)
        val thumbY = viewport.y + (viewport.height - thumbHeight) * scroll / maxScroll
        UiDraw.fillRect(context, trackX, thumbY, MenuLayout.SCROLLBAR_WIDTH, thumbHeight, UiTheme.Colors.ACCENT_PRIMARY)
    }

    /**
     * One card, like the website's `DyeCard`: icon box with the sprite, name and category, a
     * status badge top-right, and a label/value line over the progress bar. The border takes the
     * dye's accent when [hovered].
     */
    private fun drawCard(
        context: GuiGraphicsExtractor,
        renderCtx: RenderContext,
        entry: DyeProgressEntry,
        x: Int,
        y: Int,
        hovered: Boolean,
    ) {
        val padding = MenuLayout.CARD_PADDING
        val innerWidth = MenuLayout.CARD_WIDTH - 2 * padding
        val accent = DyeProgressWidgetView.parseAccentColor(entry.color)
        UiDraw.filledBox(
            context,
            x,
            y,
            MenuLayout.CARD_WIDTH,
            MenuLayout.CARD_HEIGHT,
            UiTheme.Colors.CARD_BACKGROUND,
            if (hovered) accent else UiTheme.Colors.CARD_BORDER,
        )
        drawIconBox(context, renderCtx, entry.dyeId, x + padding, y + padding)
        val badgeLeft = drawBadge(context, MenuCards.badgeFor(entry), x + MenuLayout.CARD_WIDTH - padding, y + padding)

        val textX = x + padding + MenuLayout.ICON_BOX + MenuLayout.TEXT_GAP
        val nameY = y + padding + MenuLayout.NAME_TOP_OFFSET
        val name = MenuCards.fitText(entry.name, badgeLeft - MenuLayout.TEXT_GAP - textX) { font.width(it) }
        UiDraw.drawText(context, font, name, textX, nameY, UiTheme.Colors.TEXT_PRIMARY)
        val textRight = x + MenuLayout.CARD_WIDTH - padding
        val category = MenuCards.fitText(MenuCards.formatCategory(entry.category), textRight - textX) {
            scaledWidth(it, MenuLayout.SMALL_TEXT_SCALE)
        }
        UiDraw.drawText(
            context,
            font,
            category,
            textX,
            nameY + font.lineHeight,
            UiTheme.Colors.TEXT_MUTED,
            scale = MenuLayout.SMALL_TEXT_SCALE,
        )

        val barHeight = UiTheme.Sizing.PROGRESS_BAR_HEIGHT
        val barY = y + MenuLayout.CARD_HEIGHT - padding - barHeight
        val lineY = barY - MenuLayout.TEXT_GAP - scaledLineHeight(MenuLayout.SMALL_TEXT_SCALE)
        UiDraw.drawText(
            context,
            font,
            MenuCards.statusLabel(entry),
            x + padding,
            lineY,
            UiTheme.Colors.TEXT_SUBTLE,
            scale = MenuLayout.SMALL_TEXT_SCALE,
        )
        UiDraw.drawText(
            context,
            font,
            MenuCards.valueText(entry),
            x + padding,
            lineY,
            UiTheme.Colors.TEXT_PRIMARY,
            scale = MenuLayout.SMALL_TEXT_SCALE,
            availableWidth = innerWidth,
            alignment = HorizontalAlignment.END,
        )
        ProgressBarWidget(
            value = (entry.progress ?: 0.0).toFloat().coerceIn(ProgressBarWidget.MIN_VALUE, ProgressBarWidget.MAX_VALUE),
            width = innerWidth,
            height = barHeight,
            fillColor = accent,
        ).draw(renderCtx, x + padding, barY)
    }

    /** The black icon box at ([x], [y]) with the dye's sprite centred in it, when the sprite is loaded. */
    private fun drawIconBox(context: GuiGraphicsExtractor, renderCtx: RenderContext, dyeId: String, x: Int, y: Int) {
        UiDraw.filledBox(
            context,
            x,
            y,
            MenuLayout.ICON_BOX,
            MenuLayout.ICON_BOX,
            UiTheme.Colors.ICON_BOX_BACKGROUND,
            UiTheme.Colors.ICON_BOX_BORDER,
        )
        if (!DyeSprites.has(dyeId)) return
        val frameId = ImageTextureManager.currentFrameIdentifier(
            DyeSprites.spriteId(dyeId),
            renderCtx.wallClockMs,
            renderCtx.paused,
        ) ?: return
        val inset = (MenuLayout.ICON_BOX - MenuLayout.ICON_SPRITE) / 2
        UiDraw.blitStretched(
            context,
            frameId,
            x + inset,
            y + inset,
            MenuLayout.ICON_SPRITE,
            MenuLayout.ICON_SPRITE,
            MenuLayout.SPRITE_SOURCE_SIZE,
            MenuLayout.SPRITE_SOURCE_SIZE,
        )
    }

    /** Draws [badge] right-aligned to [right] with its top at [top]; returns the badge's left edge. */
    private fun drawBadge(context: GuiGraphicsExtractor, badge: CardBadge, right: Int, top: Int): Int {
        val pad = MenuLayout.BADGE_PADDING
        val boxWidth = scaledWidth(badge.label, MenuLayout.BADGE_SCALE) + 2 * pad
        val boxHeight = scaledLineHeight(MenuLayout.BADGE_SCALE) + 2 * pad
        val left = right - boxWidth
        val colors = badgeColors(badge)
        UiDraw.filledBox(context, left, top, boxWidth, boxHeight, colors.background, colors.border)
        UiDraw.drawText(
            context,
            font,
            badge.label,
            left + pad,
            top + pad,
            colors.text,
            scale = MenuLayout.BADGE_SCALE,
        )
        return left
    }

    private data class BadgeColors(val text: Int, val background: Int, val border: Int)

    private fun badgeColors(badge: CardBadge): BadgeColors = when (badge) {
        CardBadge.MOD -> BadgeColors(
            UiTheme.Colors.BADGE_MOD_TEXT,
            UiTheme.Colors.BADGE_MOD_BG,
            UiTheme.Colors.BADGE_MOD_BORDER,
        )
        CardBadge.ESTIMATED -> BadgeColors(
            UiTheme.Colors.BADGE_ESTIMATED_TEXT,
            UiTheme.Colors.BADGE_ESTIMATED_BG,
            UiTheme.Colors.BADGE_ESTIMATED_BORDER,
        )
        CardBadge.MOD_ONLY, CardBadge.NOT_TRACKABLE -> BadgeColors(
            UiTheme.Colors.BADGE_MUTED_TEXT,
            UiTheme.Colors.BADGE_MUTED_BG,
            UiTheme.Colors.BADGE_MUTED_BORDER,
        )
    }

    /** Width of [text] drawn at [scale], rounded the way [UiDraw.drawText] rounds it. */
    private fun scaledWidth(text: String, scale: Float): Int = (font.width(text) * scale).roundToInt()

    /** Height of one text line drawn at [scale]. */
    private fun scaledLineHeight(scale: Float): Int = (font.lineHeight * scale).roundToInt()

    companion object {
        private const val TITLE = "DyeTracker"
        private const val DYES_TAB_LABEL = "Dyes"
        private const val LOADING_TEXT = "Loading your dyes…"
        private const val NO_PROFILES_PREFIX = "No SkyBlock profiles found for "
        private const val FAILED_PREFIX = "Couldn't load your dyes: "
        private const val UNEXPECTED_ERROR_PREFIX = "Unexpected error: "
    }
}
