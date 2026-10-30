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
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * Picks the theme (PBI 81), opened from the menu's Settings tab: one button per preset, a dye
 * button and, once there is a Custom slot, a Custom button ([themeButtons]), wrapping into rows; six
 * labeled hex boxes, one per [ThemeOverrides.KEYS] color, showing the active theme's colors
 * ([overrideBoxText]); the HUD opacity slider ([HudOpacitySlider]); a live preview of a HUD panel
 * (at the HUD opacity) and a menu card drawn every frame with the active [UiTheme.Colors] (never the
 * Spotify tint, so it shows the theme itself); and Reset and Done.
 * A preset or Custom button applies at once; the dye button opens a [DyePickerScreen] over the dyes
 * that have a theme color, which comes back here. A valid hex in a box saves it to the Custom slot
 * at once ([ConfigManager.editCustomColor]); an invalid one turns the box red and saves nothing.
 * Switching theme and Reset refill the boxes; typing never does. The slider works on every theme
 * and never switches to Custom; closing the screen saves an opacity not yet saved
 * ([saveHudOpacity]). Done or Esc goes back to [parent].
 */
class ThemeScreen(private val parent: Screen?) : Screen(Component.literal(TITLE)) {

    /** The panel's height for the current button rows, as of the last [init]. */
    private var panelHeight = 0

    /** Offset of the preview from the panel's top, as of the last [init]. */
    private var previewDy = 0

    /** Offset of the override boxes from the panel's top, as of the last [init]. */
    private var overridesDy = 0

    /**
     * What each override box holds, by [ThemeOverrides.KEYS] key, kept across [init] so relabeling
     * the buttons or a resize keeps what the player typed. Emptied to refill the boxes from the
     * active theme on the next [init].
     */
    private val boxText = mutableMapOf<String, String>()

    /** The override boxes by [ThemeOverrides.KEYS] key, as of the last [init]. */
    private val boxes = mutableMapOf<String, EditBox>()

    override fun init() {
        super.init()
        val selectedKey = selectedThemeKey(ConfigManager.config)
        val buttons = themeButtons(selectedKey, ConfigManager.hasCustomTheme())
        val contentWidth = THEME_PANEL_WIDTH - 2 * PANEL_PADDING_PX
        val slots = wrapIntoRows(buttons.map { font.width(it.label) + 2 * BUTTON_TEXT_PADDING_PX }, contentWidth)
        val rowCount = (slots.maxOfOrNull { it.row } ?: 0) + 1
        overridesDy = BUTTONS_DY + rowCount * BUTTON_HEIGHT_PX + (rowCount - 1) * BUTTON_GAP_PX + SECTION_GAP_PX
        val overrideRows = (ThemeOverrides.KEYS.size + OVERRIDE_COLUMNS - 1) / OVERRIDE_COLUMNS
        val sliderDy = overridesDy + overrideRows * FIELD_HEIGHT_PX + (overrideRows - 1) * BUTTON_GAP_PX + SECTION_GAP_PX
        previewDy = sliderDy + SLIDER_HEIGHT_PX + SECTION_GAP_PX
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
        addOverrideBoxes(left, top + overridesDy)
        addRenderableWidget(HudOpacitySlider(left, top + sliderDy, contentWidth, SLIDER_HEIGHT_PX))
        val bottomLeft = left + (contentWidth - BOTTOM_ROW_WIDTH_PX) / 2
        val reset = Button.builder(Component.literal(RESET_LABEL)) { reset() }
            .bounds(bottomLeft, top + doneDy, BOTTOM_BUTTON_WIDTH_PX, BUTTON_HEIGHT_PX)
            .build()
        reset.active = selectedKey == ThemePresets.CUSTOM
        addRenderableWidget(reset)
        addRenderableWidget(
            Button.builder(Component.literal(DONE_LABEL)) { onClose() }
                .bounds(
                    bottomLeft + BOTTOM_BUTTON_WIDTH_PX + BUTTON_GAP_PX,
                    top + doneDy,
                    BOTTOM_BUTTON_WIDTH_PX,
                    BUTTON_HEIGHT_PX,
                )
                .build(),
        )
    }

    /**
     * The six override boxes in a grid from ([left], [top]), each right of its label, holding
     * [boxText] (refilled from the active theme when empty). Each box's responder is set after its
     * text, so filling a box never saves.
     */
    private fun addOverrideBoxes(left: Int, top: Int) {
        if (boxText.isEmpty()) boxText.putAll(overrideBoxText(ConfigManager.config))
        val labelWidth = ThemeOverrides.LABELS.values.maxOf { font.width(it) }
        val boxWidth = OVERRIDE_COLUMN_WIDTH_PX - labelWidth - INNER_GAP_PX
        boxes.clear()
        for ((index, key) in ThemeOverrides.KEYS.withIndex()) {
            val box = EditBox(
                font,
                left + overrideCellDx(index) + labelWidth + INNER_GAP_PX,
                top + overrideCellDy(index),
                boxWidth,
                FIELD_HEIGHT_PX,
                Component.literal(ThemeOverrides.LABELS.getValue(key)),
            )
            box.setMaxLength(HEX_MAX_LENGTH)
            box.setHint(Component.literal(HEX_HINT))
            val text = boxText.getValue(key)
            box.setValue(text)
            box.setTextColor(boxTextColor(parseHexRgb(text)))
            box.setResponder { value -> boxEdited(key, box, value) }
            boxes[key] = addRenderableWidget(box)
        }
    }

    /**
     * The player changed box [key] to [value]. A valid color is saved to the Custom slot at once
     * and the box text turns white; when that switched to Custom or created the slot, the buttons
     * are relabeled, keeping what the boxes hold and the focus in this box. An invalid value turns
     * the box text red and saves nothing.
     */
    private fun boxEdited(key: String, box: EditBox, value: String) {
        boxText[key] = value
        val rgb = parseHexRgb(value)
        box.setTextColor(boxTextColor(rgb))
        if (rgb == null) return
        val before = buttonState()
        ConfigManager.editCustomColor(key, rgb)
        if (buttonState() == before) return
        val cursor = box.cursorPosition
        rebuildWidgets()
        val rebuilt = boxes.getValue(key)
        setFocused(rebuilt)
        rebuilt.setCursorPosition(cursor)
    }

    /** What the buttons depend on: the active theme key and whether there is a Custom slot. */
    private fun buttonState(): Pair<String, Boolean> = ConfigManager.themeKey() to ConfigManager.hasCustomTheme()

    /** A box's text color: white for a valid color ([rgb] not `null`), [UiTheme.Colors.STATUS_ERROR] otherwise. */
    private fun boxTextColor(rgb: Int?): Int = if (rgb != null) BOX_TEXT_COLOR else UiTheme.Colors.STATUS_ERROR

    /** Reset: clear the Custom slot's overrides, back to its base theme, and refill the boxes. */
    private fun reset() {
        ConfigManager.resetCustomTheme()
        refill()
    }

    /** Rebuild the widgets with the boxes refilled from the active theme. */
    private fun refill() {
        boxText.clear()
        rebuildWidgets()
    }

    /**
     * The button for [key]: a preset or Custom applies at once, relabels the buttons and refills the
     * boxes; the dye button opens the picker.
     */
    private fun click(key: String) {
        if (key == DYE_BUTTON_KEY) {
            openDyePicker()
            return
        }
        ConfigManager.setThemeKey(key)
        refill()
    }

    /**
     * The dye picker over the dyes with a theme color; [init] relabels the buttons when it comes back,
     * and refills the boxes when a dye was picked.
     */
    private fun openDyePicker() {
        Minecraft.getInstance().showScreen(
            DyePickerScreen(
                parent = this,
                title = DYE_PICKER_TITLE,
                options = themeDyeOptions(),
                current = { ThemePresets.dyeIdOf(ConfigManager.themeKey()) ?: "" },
                onPick = {
                    ConfigManager.setThemeKey(ThemePresets.dyeKey(it))
                    boxText.clear()
                },
                onClear = null,
            ),
        )
    }

    /** Back to [parent]; the theme stays as last picked. */
    override fun onClose() {
        Minecraft.getInstance().showScreen(parent)
    }

    /** Leaving the screen, by Done, Esc or the dye picker, saves an opacity the slider has not saved yet. */
    override fun removed() {
        saveHudOpacity()
        super.removed()
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
        drawOverrideLabels(context, left, top + overridesDy)
        val boxWidth = (contentWidth - PREVIEW_GAP_PX) / 2
        val previewTop = top + previewDy
        drawHudSample(context, left, previewTop, boxWidth)
        drawMenuSample(context, left + boxWidth + PREVIEW_GAP_PX, previewTop, boxWidth)
    }

    /** Each override box's label, left of its box in the grid from ([left], [top]), centred on the box's height. */
    private fun drawOverrideLabels(context: GuiGraphicsExtractor, left: Int, top: Int) {
        for ((index, key) in ThemeOverrides.KEYS.withIndex()) {
            UiDraw.drawText(
                context,
                font,
                ThemeOverrides.LABELS.getValue(key),
                left + overrideCellDx(index),
                top + overrideCellDy(index) + (FIELD_HEIGHT_PX - TEXT_HEIGHT_PX) / 2,
                UiTheme.Colors.TEXT_PRIMARY,
            )
        }
    }

    /** The HUD sample: a rotation widget's panel, at the HUD opacity, and lines, in the theme's HUD colors. */
    private fun drawHudSample(context: GuiGraphicsExtractor, x: Int, y: Int, boxWidth: Int) {
        UiDraw.filledBox(
            context,
            x,
            y,
            boxWidth,
            PREVIEW_HEIGHT_PX,
            themeWithAlpha(ThemeState.hudPanelAlpha, UiTheme.Colors.PANEL_BACKGROUND),
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

    /** Offset of override cell [index] (label then box) from the content's left edge: columns fill left to right. */
    private fun overrideCellDx(index: Int): Int = (index % OVERRIDE_COLUMNS) * (OVERRIDE_COLUMN_WIDTH_PX + OVERRIDE_COLUMN_GAP_PX)

    /** Offset of override cell [index] from the grid's top: a new row every [OVERRIDE_COLUMNS] cells. */
    private fun overrideCellDy(index: Int): Int = (index / OVERRIDE_COLUMNS) * (FIELD_HEIGHT_PX + BUTTON_GAP_PX)

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
        const val RESET_LABEL = "Reset"

        /** An override box's hint while empty. */
        const val HEX_HINT = "RRGGBB"

        /** Room in an override box for `#` and six hex digits. */
        const val HEX_MAX_LENGTH = 7

        /** An override box's text color while it holds a valid color: white. */
        const val BOX_TEXT_COLOR = 0xFF_FF_FF_FF.toInt()

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

        /** Width of each of the bottom row's buttons, Reset and Done. */
        const val BOTTOM_BUTTON_WIDTH_PX = 80

        /** Reset and Done side by side, centred as one row. */
        const val BOTTOM_ROW_WIDTH_PX = 2 * BOTTOM_BUTTON_WIDTH_PX + BUTTON_GAP_PX

        /** The HUD opacity slider's height: a vanilla button's. */
        const val SLIDER_HEIGHT_PX = 20

        /** The override boxes' height: a vanilla text box's. */
        const val FIELD_HEIGHT_PX = 20

        /** The override grid's columns. */
        const val OVERRIDE_COLUMNS = 2

        /** Gap between the override grid's columns. */
        const val OVERRIDE_COLUMN_GAP_PX = 8

        /** Width of one override grid column: a label and its box. */
        const val OVERRIDE_COLUMN_WIDTH_PX =
            (THEME_PANEL_WIDTH - 2 * PANEL_PADDING_PX - (OVERRIDE_COLUMNS - 1) * OVERRIDE_COLUMN_GAP_PX) / OVERRIDE_COLUMNS

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
