package com.dyetracker.menu

import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.client.showScreen
import com.dyetracker.dyeprogress.DyeProgressWidgetView
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
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

/**
 * The in-game DyeTracker menu (`/dyetracker menu`), styled after the website: a header bar with
 * the title and a tab bar, over a grid of the player's own dye cards (name, percent, progress
 * bar) for the profile selected in-game. Always loads by the in-game name, linked or not.
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

    private var state: ViewState = ViewState.Loading

    /** Set on the first [init]; resize re-inits must not reload. */
    private var started = false

    /** Bumped per load and on [removed]; a posted result whose epoch is stale is dropped. */
    private var epoch = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init() {
        super.init()
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
            is ViewState.Loaded -> drawGrid(context, current.result.dyes)
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

    /** The dye cards, centred horizontally; rows starting below the screen are skipped (no scrolling yet). */
    private fun drawGrid(context: GuiGraphicsExtractor, dyes: List<DyeProgressEntry>) {
        val columns = MenuLayout.columns(width - 2 * MenuLayout.OUTER_MARGIN)
        val gridLeft = (width - MenuLayout.gridWidth(columns)) / 2
        val gridTop = MenuLayout.HEADER_HEIGHT + MenuLayout.OUTER_MARGIN
        val renderCtx = RenderContext(context, Util.getMillis(), false)
        for ((index, entry) in dyes.withIndex()) {
            val (x, y) = MenuLayout.cardOrigin(index, columns, gridLeft, gridTop)
            if (y >= height) break // cards are row-ordered, so every later card is off-screen too
            drawCard(context, renderCtx, entry, x, y)
        }
    }

    private fun drawCard(context: GuiGraphicsExtractor, renderCtx: RenderContext, entry: DyeProgressEntry, x: Int, y: Int) {
        val padding = MenuLayout.CARD_PADDING
        val innerWidth = MenuLayout.CARD_WIDTH - 2 * padding
        UiDraw.filledBox(
            context,
            x,
            y,
            MenuLayout.CARD_WIDTH,
            MenuLayout.CARD_HEIGHT,
            UiTheme.Colors.CARD_BACKGROUND,
            UiTheme.Colors.CARD_BORDER,
        )
        UiDraw.drawText(context, font, entry.name, x + padding, y + padding, UiTheme.Colors.TEXT_PRIMARY)
        UiDraw.drawText(
            context,
            font,
            DyeProgressWidgetView.formatProgress(entry.progress),
            x + padding,
            y + padding,
            UiTheme.Colors.TEXT_SUBTLE,
            availableWidth = innerWidth,
            alignment = HorizontalAlignment.END,
        )
        val barHeight = UiTheme.Sizing.PROGRESS_BAR_HEIGHT
        ProgressBarWidget(
            value = (entry.progress ?: 0.0).toFloat().coerceIn(ProgressBarWidget.MIN_VALUE, ProgressBarWidget.MAX_VALUE),
            width = innerWidth,
            height = barHeight,
            fillColor = DyeProgressWidgetView.parseAccentColor(entry.color),
        ).draw(renderCtx, x + padding, y + MenuLayout.CARD_HEIGHT - padding - barHeight)
    }

    companion object {
        private const val TITLE = "DyeTracker"
        private const val DYES_TAB_LABEL = "Dyes"
        private const val LOADING_TEXT = "Loading your dyes…"
        private const val NO_PROFILES_PREFIX = "No SkyBlock profiles found for "
        private const val FAILED_PREFIX = "Couldn't load your dyes: "
        private const val UNEXPECTED_ERROR_PREFIX = "Unexpected error: "
    }
}
