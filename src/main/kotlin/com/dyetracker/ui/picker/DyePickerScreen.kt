package com.dyetracker.ui.picker

import com.dyetracker.client.showScreen
import com.dyetracker.dyeprogress.DyeOption
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
 * Picks one dye out of [options] (PBI 77, generalized in PBI 81): a search field over a fixed pool
 * of [PICKER_VISIBLE_ROWS] dye-row buttons ([pickerRows], the [current] pick marked and listed
 * first), a `+N more` line when more dyes match, and Clear / Done buttons; Clear is there only
 * when [onClear] is given. Picking a dye calls [onPick] with its id, Clear calls [onClear]; Done
 * or Esc changes nothing. All of them go back to [parent].
 *
 * @param title shown at the top of the panel.
 * @param current the current pick's dye id, blank for none; read each time the rows refresh.
 */
class DyePickerScreen(
    private val parent: Screen?,
    title: String,
    private val options: List<DyeOption>,
    private val current: () -> String,
    private val onPick: (String) -> Unit,
    private val onClear: (() -> Unit)?,
) : Screen(Component.literal(title)) {

    /** The panel's title text. */
    private val titleText = title

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
        if (onClear != null) {
            addRenderableWidget(
                Button.builder(Component.literal(CLEAR_LABEL)) { clear(onClear) }
                    .bounds(left, buttonsY, BUTTON_WIDTH_PX, CONTROL_HEIGHT_PX)
                    .build(),
            )
        }
        addRenderableWidget(
            Button.builder(Component.literal(DONE_LABEL)) { onClose() }
                .bounds(left + contentWidth - BUTTON_WIDTH_PX, buttonsY, BUTTON_WIDTH_PX, CONTROL_HEIGHT_PX)
                .build(),
        )

        refreshRows()
    }

    /** Recompute [shown] for [query] and the current pick, and repaint the row-button pool. */
    private fun refreshRows() {
        val picked = current()
        shown = pickerRows(query, picked, PICKER_VISIBLE_ROWS, options)
        for ((index, button) in rowButtons.withIndex()) {
            val option = shown.rows.getOrNull(index)
            button.visible = option != null
            button.active = option != null
            if (option != null) {
                val marker = if (option.dyeId == picked) PICKED_PREFIX else UNPICKED_PREFIX
                button.message = Component.literal(marker + option.displayName)
            }
        }
    }

    /** Hand row [index]'s dye to [onPick] and go back. */
    private fun pick(index: Int) {
        val option = shown.rows.getOrNull(index) ?: return
        onPick(option.dyeId)
        onClose()
    }

    /** Run [clearPick] and go back. */
    private fun clear(clearPick: () -> Unit) {
        clearPick()
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
            titleText,
            left,
            top + TITLE_DY,
            UiTheme.Colors.TEXT_PRIMARY,
            availableWidth = contentWidth,
            alignment = HorizontalAlignment.CENTER,
        )
        if (shown.more > 0) {
            UiDraw.drawText(context, font, "+${shown.more}$MORE_SUFFIX", left, top + FOOTER_DY, UiTheme.Colors.TEXT_SECONDARY)
        }
    }

    private fun panelLeft(): Int = (width - PANEL_WIDTH_PX) / 2

    /** The panel is centred vertically, but never above [PANEL_MIN_TOP_PX]. */
    private fun panelTop(): Int = maxOf(PANEL_MIN_TOP_PX, (height - PANEL_HEIGHT_PX) / 2)

    private companion object {
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
