package com.dyetracker.discord

import com.dyetracker.client.showScreen
import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Picks the Discord card's hunt dye (PBI 77), opened from the menu's Settings tab: a search field
 * over a fixed pool of [PICKER_VISIBLE_ROWS] dye-row buttons ([pickerRows], the current pick
 * marked and listed first), a `+N more` line when more dyes match, and Clear / Done buttons.
 * Picking a dye or Clear saves it and wakes the presence worker; Done or Esc changes nothing. All
 * of them go back to [parent].
 */
class DiscordHuntPickerScreen(private val parent: Screen?) : Screen(Component.literal(TITLE)) {

    /** The search text; kept across [init] so a resize doesn't lose it. */
    private var query = ""

    private var rowButtons: List<Button> = emptyList()

    /** The rows the buttons show, as of the last [refreshRows]. */
    private var shown = PickerRows(emptyList(), 0)

    override fun init() {
        super.init()
        val left = panelLeft() + PANEL_PADDING_PX
        val top = panelTop()
        val contentWidth = PANEL_WIDTH_PX - 2 * PANEL_PADDING_PX

        val search = EditBox(font, left, top + SEARCH_DY, contentWidth, FIELD_HEIGHT_PX, Component.literal(SEARCH_LABEL))
        search.setHint(Component.literal(SEARCH_HINT))
        search.setValue(query)
        search.setResponder { value ->
            query = value
            refreshRows()
        }
        addRenderableWidget(search)
        setInitialFocus(search)

        rowButtons = (0 until PICKER_VISIBLE_ROWS).map { index ->
            addRenderableWidget(
                Button.builder(Component.literal(" ")) { pick(index) }
                    .bounds(left, top + LIST_TOP_DY + index * ROW_STRIDE_PX, contentWidth, ROW_HEIGHT_PX)
                    .build(),
            )
        }

        val buttonsY = top + BUTTONS_DY
        addRenderableWidget(
            Button.builder(Component.literal(CLEAR_LABEL)) { clear() }
                .bounds(left, buttonsY, BUTTON_WIDTH_PX, CONTROL_HEIGHT_PX)
                .build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal(DONE_LABEL)) { onClose() }
                .bounds(left + contentWidth - BUTTON_WIDTH_PX, buttonsY, BUTTON_WIDTH_PX, CONTROL_HEIGHT_PX)
                .build(),
        )

        refreshRows()
    }

    /** Recompute [shown] for [query] and the current pick, and repaint the row-button pool. */
    private fun refreshRows() {
        val current = ConfigManager.discordHuntDyeId()
        shown = pickerRows(query, current, PICKER_VISIBLE_ROWS)
        for ((index, button) in rowButtons.withIndex()) {
            val option = shown.rows.getOrNull(index)
            button.visible = option != null
            button.active = option != null
            if (option != null) {
                val marker = if (option.dyeId == current) PICKED_PREFIX else UNPICKED_PREFIX
                button.message = Component.literal(marker + option.displayName)
            }
        }
    }

    /** Save row [index]'s dye as the hunt, wake the presence worker and go back. */
    private fun pick(index: Int) {
        val option = shown.rows.getOrNull(index) ?: return
        ConfigManager.setDiscordHuntDyeId(option.dyeId)
        DiscordPresence.wake()
        onClose()
    }

    /** Clear the hunt, wake the presence worker and go back. */
    private fun clear() {
        ConfigManager.setDiscordHuntDyeId("")
        DiscordPresence.wake()
        onClose()
    }

    /** Back to [parent], unchanged unless a row or Clear was clicked first. */
    override fun onClose() {
        Minecraft.getInstance().showScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(context, mouseX, mouseY, delta)
        UiDraw.fillRect(context, 0, 0, width, height, UiTheme.Colors.MENU_BACKGROUND)
        UiDraw.filledBox(
            context,
            panelLeft(),
            panelTop(),
            PANEL_WIDTH_PX,
            PANEL_HEIGHT_PX,
            UiTheme.Colors.PANEL_BACKGROUND,
            UiTheme.Colors.PANEL_BORDER,
        )
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(context, mouseX, mouseY, delta)
        val left = panelLeft() + PANEL_PADDING_PX
        val top = panelTop()
        val contentWidth = PANEL_WIDTH_PX - 2 * PANEL_PADDING_PX
        UiDraw.drawText(
            context,
            font,
            TITLE,
            left,
            top + TITLE_DY,
            UiTheme.Colors.TEXT_PRIMARY,
            availableWidth = contentWidth,
            alignment = HorizontalAlignment.CENTER,
        )
        val footer = when {
            shown.more > 0 -> "+${shown.more}$MORE_SUFFIX"
            shown.rows.isEmpty() -> NO_MATCH_TEXT
            else -> null
        }
        if (footer != null) {
            UiDraw.drawText(context, font, footer, left, top + FOOTER_DY, UiTheme.Colors.TEXT_SECONDARY)
        }
    }

    private fun panelLeft(): Int = (width - PANEL_WIDTH_PX) / 2

    /** The panel is centred vertically, but never above [PANEL_MIN_TOP_PX]. */
    private fun panelTop(): Int = maxOf(PANEL_MIN_TOP_PX, (height - PANEL_HEIGHT_PX) / 2)

    private companion object {
        const val TITLE = "Discord hunt"
        const val SEARCH_LABEL = "Search dyes"
        const val SEARCH_HINT = "Search dyes…"
        const val CLEAR_LABEL = "Clear"
        const val DONE_LABEL = "Done"

        /** Before the current pick's name in its row. */
        const val PICKED_PREFIX = "✔ "

        /** Before every other row's name, so the names line up with the pick's. */
        const val UNPICKED_PREFIX = "  "

        /** After the count of matches beyond the shown rows: `+52 more`. */
        const val MORE_SUFFIX = " more"

        /** The footer when no dye matches the search. */
        const val NO_MATCH_TEXT = "No dye matches."

        const val PANEL_WIDTH_PX = 220
        const val PANEL_MIN_TOP_PX = 8
        const val PANEL_PADDING_PX = 8
        const val INNER_GAP_PX = 4
        const val TEXT_HEIGHT_PX = 9
        const val FIELD_HEIGHT_PX = 20
        const val ROW_HEIGHT_PX = 16
        const val ROW_GAP_PX = 2
        const val ROW_STRIDE_PX = ROW_HEIGHT_PX + ROW_GAP_PX
        const val CONTROL_HEIGHT_PX = 20
        const val BUTTON_WIDTH_PX = 80

        // Vertical offsets from the panel's top.
        const val TITLE_DY = PANEL_PADDING_PX
        const val SEARCH_DY = TITLE_DY + TEXT_HEIGHT_PX + INNER_GAP_PX
        const val LIST_TOP_DY = SEARCH_DY + FIELD_HEIGHT_PX + INNER_GAP_PX
        const val FOOTER_DY = LIST_TOP_DY + PICKER_VISIBLE_ROWS * ROW_STRIDE_PX
        const val BUTTONS_DY = FOOTER_DY + TEXT_HEIGHT_PX + INNER_GAP_PX
        const val PANEL_HEIGHT_PX = BUTTONS_DY + CONTROL_HEIGHT_PX + PANEL_PADDING_PX
    }
}
