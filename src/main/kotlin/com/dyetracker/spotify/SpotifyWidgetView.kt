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
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.Minecraft

/**
 * Builds the Spotify now-playing widget from PBI 30 toolkit components: on Spotify's fallback
 * panel color, the title with the Spotify icon, the artists (or show), and a live progress bar
 * with `m:ss / m:ss`. Text is fitted to [SpotifyConstants.TEXT_WIDTH_PX] with `…`.
 */
object SpotifyWidgetView {

    private const val TIME_SEPARATOR = " / "

    private val font get() = Minecraft.getInstance().font

    /** The widget for [s] at [nowMs] (the bar moves between polls). */
    fun build(s: NowPlayingSnapshot, nowMs: Long): Widget {
        val textWidth = SpotifyConstants.TEXT_WIDTH_PX
        val time = "${formatTime(interpolatedProgressMs(s, nowMs))}$TIME_SEPARATOR${formatTime(s.durationMs)}"
        val barWidth = (textWidth - font.width(time) - UiTheme.Spacing.INNER_GAP).coerceAtLeast(0)
        val titleRow = Row(
            children = listOf(
                TextWidget(fit(s.title, textWidth), minWidth = textWidth),
                SpriteWidget(SpotifyConstants.ICON_GREEN_ID, SpotifyConstants.ICON_SCALE),
            ),
            spacing = UiTheme.Spacing.INNER_GAP,
        )
        val subtitle = TextWidget(fit(s.subtitle, textWidth), color = UiTheme.Colors.TEXT_SECONDARY)
        val barRow = Row(
            children = listOf(
                ProgressBarWidget(
                    value = progressPercent(s, nowMs),
                    width = barWidth,
                    fillColor = UiTheme.Colors.SPOTIFY_GREEN,
                ),
                TextWidget(time),
            ),
            spacing = UiTheme.Spacing.INNER_GAP,
        )
        return PanelWidget(
            child = Column(
                children = listOf(titleRow, subtitle, barRow),
                spacing = SpotifyConstants.LINE_GAP_PX,
                crossAxisAlignment = HorizontalAlignment.START,
            ),
            background = UiTheme.Colors.SPOTIFY_FALLBACK_BACKGROUND,
            border = UiTheme.Colors.PANEL_BORDER,
        )
    }

    private fun fit(text: String, maxWidth: Int): String = MenuCards.fitText(text, maxWidth) { font.width(it) }
}
