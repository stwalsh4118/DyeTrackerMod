package com.dyetracker.menu

import com.dyetracker.api.AchievementsResponse
import com.dyetracker.api.PlayerAchievementEntry
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.core.Rect
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import java.time.ZoneId

/**
 * Draws the in-game menu's Achievements tab once its achievements are loaded, like the website's
 * Achievements page: the `N of M Achievements` summary and its percent over an emerald progress
 * bar, then each [AchievementSection]'s header (`label  unlocked / total`) and a row per
 * achievement, scrolling in the tab's own viewport. A row has its tier pill (when any shown
 * achievement has a tier), its name and description ([MenuAchievements.displayName], masked for a
 * locked secret) and its footer right-aligned on the name line, with a thin progress bar under a
 * locked achievement's `Progress n / m`. Rows aren't clickable and have no hover state.
 */
internal object AchievementsTabView {

    /**
     * The summary of [response], then [sections] laid out by [geometry] (whose `sectionTops`
     * follow [sections]) scrolled up by [scroll], skipping headers and rows outside the viewport.
     */
    fun draw(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        font: Font,
        response: AchievementsResponse,
        sections: List<AchievementSection>,
        geometry: AchievementsGeometry,
        scroll: Int,
    ) {
        val viewport = geometry.viewport
        val renderCtx = RenderContext(context, Util.getMillis(), false)
        val summary = MenuAchievements.summary(response)
        CollectionTabView.drawSummary(
            context,
            renderCtx,
            font,
            MenuAchievements.summaryText(summary.unlocked, summary.total),
            MenuAchievements.percentText(summary),
            MenuAchievements.percent(summary),
            viewport.x,
            geometry.summaryY,
            geometry.barY,
            viewport.width,
        )
        val tierColumn = tierColumnWidth(screen, sections)
        val zone = ZoneId.systemDefault()
        screen.drawScrolled(context, viewport, geometry.contentHeight, scroll) {
            for ((section, sectionTop) in sections.zip(geometry.sectionTops)) {
                val headerY = sectionTop - scroll
                if (headerY >= viewport.bottom) break // sections run downwards, so every later one is below too
                if (headerY + font.lineHeight > viewport.y) {
                    val header = MenuCards.fitText(MenuAchievements.sectionHeader(section), viewport.width) { font.width(it) }
                    UiDraw.drawText(context, font, header, viewport.x, headerY, UiTheme.Colors.TEXT_PRIMARY)
                }
                for ((index, entry) in section.entries.withIndex()) {
                    val rowY = MenuLayout.achievementRowY(headerY, font.lineHeight, index)
                    if (rowY >= viewport.bottom) break
                    if (rowY + MenuLayout.ACHIEVEMENT_ROW_HEIGHT <= viewport.y) continue
                    val row = Rect(viewport.x, rowY, viewport.width, MenuLayout.ACHIEVEMENT_ROW_HEIGHT)
                    drawRow(screen, context, renderCtx, font, entry, row, tierColumn, zone)
                }
            }
        }
    }

    /**
     * Width of the tier pill column: the widest pill among [sections]' entries, `0` when none has a
     * tier (an API not sending tiers yet), so names line up and take the whole row then.
     */
    private fun tierColumnWidth(screen: DyeTrackerMenuScreen, sections: List<AchievementSection>): Int =
        sections.flatMap { it.entries }
            .map { MenuAchievements.tierLabel(it.tier) }
            .filter { it.isNotEmpty() }
            .maxOfOrNull { screen.badgeWidth(it) } ?: 0

    /**
     * One achievement in [row]: its tier pill at the left (in [TIER_LOCKED][UiTheme.Colors.TIER_LOCKED]
     * when locked) in a [tierColumn]-wide column; the name (tier color when unlocked, subtle when
     * locked) with the small muted description under it, both vertically centred and fitted to end
     * before the footer; the small footer right-aligned on the name line (emerald when unlocked,
     * muted otherwise), with a thin bar in the tier color under it for a locked achievement's progress.
     */
    private fun drawRow(
        screen: DyeTrackerMenuScreen,
        context: GuiGraphicsExtractor,
        renderCtx: RenderContext,
        font: Font,
        entry: PlayerAchievementEntry,
        row: Rect,
        tierColumn: Int,
        zone: ZoneId,
    ) {
        val scale = MenuLayout.SMALL_TEXT_SCALE
        val smallLineHeight = screen.scaledLineHeight(scale)
        val tierColor = MenuAchievements.tierColor(entry.tier)
        val tierLabel = MenuAchievements.tierLabel(entry.tier)
        if (tierLabel.isNotEmpty()) {
            val pillColor = if (entry.unlocked) tierColor else UiTheme.Colors.TIER_LOCKED
            val pillHeight = screen.scaledLineHeight(MenuLayout.BADGE_SCALE) + 2 * MenuLayout.BADGE_PADDING
            screen.drawBadge(
                context,
                tierLabel,
                DyeTrackerMenuScreen.BadgeColors(pillColor, MenuAchievements.tierFill(pillColor), pillColor),
                row.x + screen.badgeWidth(tierLabel),
                row.y + (row.height - pillHeight) / 2,
            )
        }

        val nameY = row.y + (row.height - font.lineHeight - smallLineHeight) / 2
        val footer = MenuAchievements.footerText(entry, zone)
        val footerWidth = screen.scaledWidth(footer, scale)
        val footerX = row.right - footerWidth
        UiDraw.drawText(
            context,
            font,
            footer,
            footerX,
            nameY + (font.lineHeight - smallLineHeight) / 2,
            if (entry.unlocked) UiTheme.Colors.OWNED_TEXT else UiTheme.Colors.TEXT_MUTED,
            scale = scale,
        )
        MenuAchievements.progressFraction(entry)?.let { fraction ->
            ProgressBarWidget(
                value = fraction * ProgressBarWidget.MAX_VALUE,
                width = footerWidth,
                height = MenuLayout.ACHIEVEMENT_PROGRESS_BAR_HEIGHT,
                fillColor = tierColor,
            ).draw(renderCtx, footerX, nameY + font.lineHeight)
        }

        val textX = row.x + if (tierColumn > 0) tierColumn + MenuLayout.TEXT_GAP else 0
        val textWidth = footerX - MenuLayout.TEXT_GAP - textX
        val name = MenuCards.fitText(MenuAchievements.displayName(entry), textWidth) { font.width(it) }
        UiDraw.drawText(
            context,
            font,
            name,
            textX,
            nameY,
            if (entry.unlocked) tierColor else UiTheme.Colors.TEXT_SUBTLE,
        )
        val description = MenuCards.fitText(MenuAchievements.displayDescription(entry), textWidth) {
            screen.scaledWidth(it, scale)
        }
        UiDraw.drawText(
            context,
            font,
            description,
            textX,
            nameY + font.lineHeight,
            UiTheme.Colors.TEXT_MUTED,
            scale = scale,
        )
    }
}
