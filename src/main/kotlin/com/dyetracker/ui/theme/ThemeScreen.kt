package com.dyetracker.ui.theme

import com.dyetracker.client.showScreen
import com.dyetracker.config.ConfigManager
import com.dyetracker.menu.MenuLayout
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.picker.DyePickerScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * Picks the theme (PBI 81), opened from the menu's Settings tab: one button per preset and a dye
 * button ([themeButtons]), wrapping into rows; a live preview of a HUD panel and a menu card drawn
 * every frame with the active [UiTheme.Colors] (never the Spotify tint, so it shows the theme
 * itself); and Done. A preset button applies at once; the dye button opens a [DyePickerScreen] over
 * the dyes that have a theme color, which comes back here. Done or Esc goes back to [parent].
 */
class ThemeScreen(private val parent: Screen?) : Screen(Component.literal(TITLE)) {

    /** The panel's height for the current button rows, as of the last [init]. */
    private var panelHeight = 0

    /** Offset of the preview from the panel's top, as of the last [init]. */
    private var previewDy = 0

    override fun init() {
        super.init()
        val buttons = themeButtons(ConfigManager.themeKey())
        val contentWidth = THEME_PANEL_WIDTH - 2 * PANEL_PADDING_PX
        val slots = wrapIntoRows(buttons.map { font.width(it.label) + 2 * BUTTON_TEXT_PADDING_PX }, contentWidth)
        val rowCount = (slots.maxOfOrNull { it.row } ?: 0) + 1
        previewDy = BUTTONS_DY + rowCount * BUTTON_HEIGHT_PX + (rowCount - 1) * BUTTON_GAP_PX + SECTION_GAP_PX
        val doneDy = previewDy + PREVIEW_HEIGHT_PX + SECTION_GAP_PX
        panelHeight = doneDy + BUTTON_HEIGHT_PX + PANEL_PADDING_PX

        val left = panelLeft() + PANEL_PADDING_PX
        val top = panelTop()
        for ((button, slot) in buttons.zip(slots)) {
            addRenderableWidget(
                Button.builder(Component.literal(button.label)) { click(button.key) }
                    .bounds(
                        left + slot.dx,
                        top + BUTTONS_DY + slot.row * (BUTTON_HEIGHT_PX + BUTTON_GAP_PX),
                        slot.width,
                        BUTTON_HEIGHT_PX,
                    )
                    .build(),
            )
        }
        addRenderableWidget(
            Button.builder(Component.literal(DONE_LABEL)) { onClose() }
                .bounds(left + (contentWidth - DONE_WIDTH_PX) / 2, top + doneDy, DONE_WIDTH_PX, BUTTON_HEIGHT_PX)
                .build(),
        )
    }

    /** The button for [key]: a preset applies at once and relabels the buttons; the dye button opens the picker. */
    private fun click(key: String) {
        if (key == DYE_BUTTON_KEY) {
            openDyePicker()
            return
        }
        ConfigManager.setThemeKey(key)
        rebuildWidgets()
    }

    /** The dye picker over the dyes with a theme color; [init] relabels the buttons when it comes back. */
    private fun openDyePicker() {
        Minecraft.getInstance().showScreen(
            DyePickerScreen(
                parent = this,
                title = DYE_PICKER_TITLE,
                options = themeDyeOptions(),
                current = { ThemePresets.dyeIdOf(ConfigManager.themeKey()) ?: "" },
                onPick = { ConfigManager.setThemeKey(ThemePresets.dyeKey(it)) },
                onClear = null,
            ),
        )
    }

    /** Back to [parent]; the theme stays as last picked. */
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
            THEME_PANEL_WIDTH,
            panelHeight,
            UiTheme.Colors.CARD_BACKGROUND,
            UiTheme.Colors.CARD_BORDER,
        )
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(context, mouseX, mouseY, delta)
        val left = panelLeft() + PANEL_PADDING_PX
        val top = panelTop()
        val contentWidth = THEME_PANEL_WIDTH - 2 * PANEL_PADDING_PX
        UiDraw.drawText(
            context,
            font,
            TITLE,
            left,
            top + TITLE_DY,
            UiTheme.Colors.ACCENT_PRIMARY,
            availableWidth = contentWidth,
            alignment = HorizontalAlignment.CENTER,
        )
        val boxWidth = (contentWidth - PREVIEW_GAP_PX) / 2
        val previewTop = top + previewDy
        drawHudSample(context, left, previewTop, boxWidth)
        drawMenuSample(context, left + boxWidth + PREVIEW_GAP_PX, previewTop, boxWidth)
    }

    /** The HUD sample: a rotation widget's panel and lines, in the theme's HUD colors. */
    private fun drawHudSample(context: GuiGraphicsExtractor, x: Int, y: Int, boxWidth: Int) {
        UiDraw.filledBox(
            context,
            x,
            y,
            boxWidth,
            PREVIEW_HEIGHT_PX,
            UiTheme.Colors.PANEL_BACKGROUND,
            UiTheme.Colors.PANEL_BORDER,
        )
        val textX = x + PREVIEW_PADDING_PX
        val textY = y + PREVIEW_PADDING_PX
        UiDraw.drawText(context, font, HUD_SAMPLE_TITLE, textX, textY, UiTheme.Colors.TEXT_PRIMARY)
        val dyeY = textY + LINE_STRIDE_PX
        UiDraw.drawText(context, font, HUD_SAMPLE_DYE, textX, dyeY, UiTheme.Colors.TEXT_PRIMARY)
        UiDraw.drawText(context, font, HUD_SAMPLE_BOOST, textX + font.width(HUD_SAMPLE_DYE), dyeY, UiTheme.Colors.BOOST_2X)
        UiDraw.drawText(context, font, HUD_SAMPLE_ENDS, textX, dyeY + LINE_STRIDE_PX, UiTheme.Colors.TEXT_SECONDARY)
    }

    /** The menu sample: a card with the title, an On pill, a MOD badge and a muted line, as the menu draws them. */
    private fun drawMenuSample(context: GuiGraphicsExtractor, x: Int, y: Int, boxWidth: Int) {
        UiDraw.filledBox(context, x, y, boxWidth, PREVIEW_HEIGHT_PX, UiTheme.Colors.CARD_BACKGROUND, UiTheme.Colors.CARD_BORDER)
        val innerX = x + PREVIEW_PADDING_PX
        val titleY = y + PREVIEW_PADDING_PX
        UiDraw.drawText(context, font, MENU_SAMPLE_TITLE, innerX, titleY, UiTheme.Colors.ACCENT_PRIMARY)

        val pillY = titleY + TEXT_HEIGHT_PX + INNER_GAP_PX
        val pillWidth = font.width(MENU_SAMPLE_PILL) + 2 * MenuLayout.PILL_PADDING_X
        UiDraw.filledBox(
            context,
            innerX,
            pillY,
            pillWidth,
            MenuLayout.PILL_HEIGHT,
            UiTheme.Colors.TOGGLE_ON_BG,
            UiTheme.Colors.TOGGLE_ON_BORDER,
        )
        UiDraw.drawText(
            context,
            font,
            MENU_SAMPLE_PILL,
            innerX,
            pillY + (MenuLayout.PILL_HEIGHT - font.lineHeight) / 2,
            UiTheme.Colors.ACCENT_SECONDARY,
            availableWidth = pillWidth,
            alignment = HorizontalAlignment.CENTER,
        )

        val badgePad = MenuLayout.BADGE_PADDING
        val badgeWidth = (font.width(MENU_SAMPLE_BADGE) * MenuLayout.BADGE_SCALE).roundToInt() + 2 * badgePad
        val badgeHeight = (font.lineHeight * MenuLayout.BADGE_SCALE).roundToInt() + 2 * badgePad
        val badgeX = innerX + pillWidth + INNER_GAP_PX
        val badgeY = pillY + (MenuLayout.PILL_HEIGHT - badgeHeight) / 2
        UiDraw.filledBox(
            context,
            badgeX,
            badgeY,
            badgeWidth,
            badgeHeight,
            UiTheme.Colors.BADGE_MOD_BG,
            UiTheme.Colors.BADGE_MOD_BORDER,
        )
        UiDraw.drawText(
            context,
            font,
            MENU_SAMPLE_BADGE,
            badgeX + badgePad,
            badgeY + badgePad,
            UiTheme.Colors.BADGE_MOD_TEXT,
            scale = MenuLayout.BADGE_SCALE,
        )

        val syncedY = pillY + MenuLayout.PILL_HEIGHT + INNER_GAP_PX
        UiDraw.drawText(context, font, MENU_SAMPLE_SYNCED, innerX, syncedY, UiTheme.Colors.TEXT_MUTED)
    }

    private fun panelLeft(): Int = (width - THEME_PANEL_WIDTH) / 2

    /** The panel is centred vertically, but never above [PANEL_MIN_TOP_PX]. */
    private fun panelTop(): Int = maxOf(PANEL_MIN_TOP_PX, (height - panelHeight) / 2)

    /** Where a button goes: [dx] from the content's left edge, on button row [row], [width] wide. */
    private data class ButtonSlot(val dx: Int, val row: Int, val width: Int)

    /**
     * Lays buttons [widths] wide out left to right, [BUTTON_GAP_PX] apart, starting a new row when
     * the next would pass [maxWidth]. A button wider than [maxWidth] gets a row of its own.
     */
    private fun wrapIntoRows(widths: List<Int>, maxWidth: Int): List<ButtonSlot> {
        var dx = 0
        var row = 0
        return widths.map { buttonWidth ->
            if (dx > 0 && dx + buttonWidth > maxWidth) {
                dx = 0
                row++
            }
            ButtonSlot(dx, row, buttonWidth).also { dx += buttonWidth + BUTTON_GAP_PX }
        }
    }

    private companion object {
        const val TITLE = "Theme"
        const val DONE_LABEL = "Done"

        /** The dye picker's title when it picks the theme's dye. */
        const val DYE_PICKER_TITLE = "Theme dye"

        // The preview's sample text.
        const val HUD_SAMPLE_TITLE = "Year 523 rotation"
        const val HUD_SAMPLE_DYE = "Nadeshiko "
        const val HUD_SAMPLE_BOOST = "2x"
        const val HUD_SAMPLE_ENDS = "ends in 4h 12m"
        const val MENU_SAMPLE_TITLE = "DyeTracker"
        const val MENU_SAMPLE_PILL = "On"
        const val MENU_SAMPLE_BADGE = "MOD"
        const val MENU_SAMPLE_SYNCED = "last synced 2m ago"

        const val THEME_PANEL_WIDTH = 320
        const val PANEL_MIN_TOP_PX = 8
        const val PANEL_PADDING_PX = 8
        const val INNER_GAP_PX = 4
        const val SECTION_GAP_PX = 8
        const val TEXT_HEIGHT_PX = 9
        const val BUTTON_HEIGHT_PX = 20
        const val BUTTON_GAP_PX = 4

        /** Room left and right of a theme button's label. */
        const val BUTTON_TEXT_PADDING_PX = 8
        const val DONE_WIDTH_PX = 80

        /** Gap between the two preview boxes. */
        const val PREVIEW_GAP_PX = 4
        const val PREVIEW_PADDING_PX = 6
        const val LINE_HEIGHT_GAP_PX = 2
        const val LINE_STRIDE_PX = TEXT_HEIGHT_PX + LINE_HEIGHT_GAP_PX

        /** Both preview boxes are as tall as the menu sample: title, pill, muted line. */
        const val PREVIEW_HEIGHT_PX = PREVIEW_PADDING_PX + TEXT_HEIGHT_PX + INNER_GAP_PX +
            MenuLayout.PILL_HEIGHT + INNER_GAP_PX + TEXT_HEIGHT_PX + PREVIEW_PADDING_PX

        // Vertical offsets from the panel's top.
        const val TITLE_DY = PANEL_PADDING_PX
        const val BUTTONS_DY = TITLE_DY + TEXT_HEIGHT_PX + INNER_GAP_PX
    }
}
