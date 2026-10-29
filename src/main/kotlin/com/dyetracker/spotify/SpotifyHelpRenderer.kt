package com.dyetracker.spotify

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import java.net.URI

/**
 * Turns [SETUP_HELP] into chat lines for `/dyetracker spotify help` (task 76-11): one [Component]
 * per line, its segments appended in order, colored by tone, links clickable with a hover hint.
 */
object SpotifyHelpRenderer {

    private const val OPEN_TOOLTIP = "Click to open"
    private const val COPY_TOOLTIP = "Click to copy"
    private const val SUGGEST_TOOLTIP = "Click to put it in the chat box"

    /** The setup guide, one component per chat line. */
    fun lines(): List<Component> = SETUP_HELP.map(::line)

    private fun line(segments: List<HelpSegment>): Component {
        val line = Component.empty()
        segments.forEach { line.append(segment(it)) }
        return line
    }

    private fun segment(segment: HelpSegment): MutableComponent =
        Component.literal(segment.text).withStyle { style ->
            val toned = tone(style, segment.tone)
            segment.click?.let { click ->
                toned.withClickEvent(clickEvent(click))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(tooltip(click))))
            } ?: toned
        }

    private fun tone(style: Style, tone: HelpTone): Style = when (tone) {
        HelpTone.HEADER -> style.withColor(ChatFormatting.AQUA).withBold(true)
        HelpTone.BODY -> style.withColor(ChatFormatting.WHITE)
        HelpTone.HINT -> style.withColor(ChatFormatting.GRAY)
        HelpTone.LINK -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
    }

    private fun clickEvent(click: HelpClick): ClickEvent = when (click) {
        is HelpClick.OpenUrl -> ClickEvent.OpenUrl(URI.create(click.url))
        is HelpClick.Copy -> ClickEvent.CopyToClipboard(click.text)
        is HelpClick.Suggest -> ClickEvent.SuggestCommand(click.command)
    }

    private fun tooltip(click: HelpClick): String = when (click) {
        is HelpClick.OpenUrl -> OPEN_TOOLTIP
        is HelpClick.Copy -> COPY_TOOLTIP
        is HelpClick.Suggest -> SUGGEST_TOOLTIP
    }
}
