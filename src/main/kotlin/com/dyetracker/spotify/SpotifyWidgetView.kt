package com.dyetracker.spotify

import com.dyetracker.menu.MenuCards
import com.dyetracker.ui.components.PanelWidget
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.components.SpriteWidget
import com.dyetracker.ui.components.TextWidget
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.layout.Column
import com.dyetracker.ui.layout.Row
import com.dyetracker.ui.texture.ImageTextureManager
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.Minecraft

/**
 * Builds the Spotify now-playing widget from PBI 30 toolkit components, one layout per
 * [SpotifyWidgetState] (task 76-4). A track or episode shows, on Spotify's fallback panel color,
 * its cover at the left once loaded (task 76-5), then the title with the Spotify icon, the
 * artists (or show), and a live progress bar with `m:ss / m:ss`; paused, the same layout dimmed by
 * [SpotifyConstants.PAUSED_ALPHA_FACTOR] (the icon and the cover never are) with `Paused · `
 * before the time. An ad shows [SpotifyConstants.AD_TITLE]; not set up, logged out and errors
 * show a message panel. Text is fitted to [SpotifyConstants.TEXT_WIDTH_PX] with `…`, except a
 * message's command hint.
 */
object SpotifyWidgetView {

    private val font get() = Minecraft.getInstance().font

    /**
     * The widget for [state] at [nowMs] (the bar moves between polls), or null when there is
     * nothing to draw. [editing] (the HUD edit screen is open) turns [SpotifyWidgetState.Hidden]
     * into a [SpotifyConstants.EDIT_PLACEHOLDER] panel, so the widget can still be placed.
     */
    fun build(state: SpotifyWidgetState, nowMs: Long, editing: Boolean): Widget? = when (state) {
        is SpotifyWidgetState.Playing -> track(state.s, nowMs, paused = false)
        is SpotifyWidgetState.Paused -> track(state.s, nowMs, paused = true)
        SpotifyWidgetState.Ad -> panel(
            listOf(titleRow(fit(SpotifyConstants.AD_TITLE), UiTheme.Colors.TEXT_PRIMARY, SpotifyConstants.TEXT_WIDTH_PX)),
        )
        SpotifyWidgetState.NotSetUp, SpotifyWidgetState.LoggedOut -> message(messageLines(state))
        is SpotifyWidgetState.Error -> message(messageLines(state).map(::fit))
        SpotifyWidgetState.Hidden -> if (editing) placeholder() else null
    }

    private fun track(s: NowPlayingSnapshot, nowMs: Long, paused: Boolean): Widget {
        val textWidth = SpotifyConstants.TEXT_WIDTH_PX
        val time = timeText(s, nowMs, paused)
        val barWidth = (textWidth - font.width(time) - UiTheme.Spacing.INNER_GAP).coerceAtLeast(0)
        val subtitle = TextWidget(fit(s.subtitle), color = tone(UiTheme.Colors.TEXT_SECONDARY, paused))
        val barRow = Row(
            children = listOf(
                ProgressBarWidget(
                    value = progressPercent(s, nowMs),
                    width = barWidth,
                    trackColor = tone(UiTheme.Colors.PROGRESS_TRACK, paused),
                    fillColor = tone(UiTheme.Colors.SPOTIFY_GREEN, paused),
                ),
                TextWidget(time, color = tone(UiTheme.Colors.TEXT_PRIMARY, paused)),
            ),
            spacing = UiTheme.Spacing.INNER_GAP,
        )
        return panel(
            listOf(titleRow(fit(s.title), tone(UiTheme.Colors.TEXT_PRIMARY, paused), textWidth), subtitle, barRow),
            paused,
            art(),
        )
    }

    /**
     * The cover, [SpotifyConstants.ART_DRAW_PX] wide, or null until it is loaded. Spotify's
     * branding rules: it is never dimmed or tinted (a [SpriteWidget] takes no color) and nothing
     * is drawn over it.
     */
    private fun art(): Widget? {
        val width = ImageTextureManager.widthOf(SpotifyConstants.ART_TEXTURE_ID)
        if (width <= 0) return null
        return SpriteWidget(SpotifyConstants.ART_TEXTURE_ID, SpotifyConstants.ART_DRAW_PX / width.toFloat())
    }

    /** The first line with the icon, then any further lines (the command hint) in secondary text. */
    private fun message(lines: List<String>): Widget {
        val first = titleRow(lines.firstOrNull().orEmpty(), UiTheme.Colors.TEXT_PRIMARY)
        val rest = lines.drop(1).map { TextWidget(it, color = UiTheme.Colors.TEXT_SECONDARY) }
        return panel(listOf(first) + rest)
    }

    private fun placeholder(): Widget = panel(listOf(titleRow(SpotifyConstants.EDIT_PLACEHOLDER, UiTheme.Colors.TEXT_PRIMARY)))

    /** [text] (already fitted) then the Spotify icon, never dimmed. */
    private fun titleRow(text: String, color: Int, minWidth: Int = 0): Widget = Row(
        children = listOf(
            TextWidget(text, color = color, minWidth = minWidth),
            SpriteWidget(SpotifyConstants.ICON_GREEN_ID, SpotifyConstants.ICON_SCALE),
        ),
        spacing = UiTheme.Spacing.INNER_GAP,
    )

    /** [lines] in a column on the panel, with [art] at their left when there is one. */
    private fun panel(lines: List<Widget>, paused: Boolean = false, art: Widget? = null): Widget {
        val text = Column(
            children = lines,
            spacing = SpotifyConstants.LINE_GAP_PX,
            crossAxisAlignment = HorizontalAlignment.START,
        )
        return PanelWidget(
            child = if (art == null) text else Row(children = listOf(art, text), spacing = UiTheme.Spacing.INNER_GAP),
            background = tone(UiTheme.Colors.SPOTIFY_FALLBACK_BACKGROUND, paused),
            border = tone(UiTheme.Colors.PANEL_BORDER, paused),
        )
    }

    /** [color], dimmed when [paused]. */
    private fun tone(color: Int, paused: Boolean): Int =
        if (paused) dim(color, SpotifyConstants.PAUSED_ALPHA_FACTOR) else color

    private fun fit(text: String): String =
        MenuCards.fitText(text, SpotifyConstants.TEXT_WIDTH_PX) { font.width(it) }
}
