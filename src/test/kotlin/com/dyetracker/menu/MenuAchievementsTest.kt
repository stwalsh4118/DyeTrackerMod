package com.dyetracker.menu

import com.dyetracker.api.AchievementProgress
import com.dyetracker.api.AchievementsResponse
import com.dyetracker.api.PlayerAchievementEntry
import com.dyetracker.ui.theme.UiTheme
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit coverage for the Achievements tab's sections, masking, tier labels, footers and summary (68-6). */
class MenuAchievementsTest {

    private fun entry(
        id: String,
        category: String? = null,
        name: String? = null,
        description: String? = null,
        tier: String? = null,
        secret: Boolean = false,
        unlocked: Boolean = false,
        unlockedAt: Long? = null,
        progress: AchievementProgress? = null,
    ) = PlayerAchievementEntry(
        achievementId = id,
        name = name,
        description = description,
        category = category,
        tier = tier,
        secret = secret,
        unlocked = unlocked,
        unlockedAt = unlockedAt,
        progress = progress,
    )

    /** 2025-10-20T22:40:00Z. */
    private val unlockTime = 1_761_000_000_000L

    @Test
    fun `categoryOf takes the entry's category when it has one`() {
        assertEquals("sweep", MenuAchievements.categoryOf(entry("drop_matcha", category = "sweep")))
    }

    @Test
    fun `categoryOf falls back to the id prefix without a category`() {
        assertEquals("dye-drop", MenuAchievements.categoryOf(entry("drop_matcha")))
        assertEquals("count", MenuAchievements.categoryOf(entry("count_ten")))
        assertEquals("sweep", MenuAchievements.categoryOf(entry("sweep_mob_drop")))
        assertEquals("progress", MenuAchievements.categoryOf(entry("progress_half")))
        assertEquals("combo", MenuAchievements.categoryOf(entry("combo_rainbow")))
    }

    @Test
    fun `categoryOf is other for an unknown prefix without a category`() {
        assertEquals("other", MenuAchievements.categoryOf(entry("mystery_thing")))
        assertEquals("other", MenuAchievements.categoryOf(entry("dropmatcha")))
    }

    @Test
    fun `section labels match the website's, an unknown one is title-cased`() {
        assertEquals(
            listOf("Dye Drops", "Milestones", "Sweeps", "Progress", "Secrets"),
            MenuAchievements.ACHIEVEMENT_SECTIONS.map(MenuAchievements::sectionLabel),
        )
        assertEquals("Limited Time", MenuAchievements.sectionLabel("limited-time"))
        assertEquals("Other", MenuAchievements.sectionLabel("other"))
    }

    @Test
    fun `sections follow the website's order, then unknown categories as they appear, omitting empty ones`() {
        // API order is neither section order nor grouped; there is no sweep or progress entry.
        val entries = listOf(
            entry("combo_rainbow"),
            entry("event_one", category = "limited-time"),
            entry("drop_matcha"),
            entry("mystery_thing"),
            entry("count_ten"),
            entry("drop_brick_red"),
            entry("event_two", category = "limited-time"),
        )

        val sections = MenuAchievements.sections(entries)

        assertEquals(
            listOf("dye-drop", "count", "combo", "limited-time", "other"),
            sections.map { it.category },
        )
        assertEquals(
            listOf("Dye Drops", "Milestones", "Secrets", "Limited Time", "Other"),
            sections.map { it.label },
        )
        assertEquals(listOf("drop_matcha", "drop_brick_red"), sections[0].entries.map { it.achievementId })
        assertEquals(listOf("event_one", "event_two"), sections[3].entries.map { it.achievementId })
    }

    @Test
    fun `no entries give no sections`() {
        assertEquals(emptyList(), MenuAchievements.sections(emptyList()))
    }

    @Test
    fun `a section header counts its unlocked entries`() {
        val section = AchievementSection(
            "dye-drop",
            "Dye Drops",
            listOf(entry("drop_a", unlocked = true), entry("drop_b"), entry("drop_c", unlocked = true)),
        )

        assertEquals("Dye Drops  2 / 3", MenuAchievements.sectionHeader(section))
    }

    @Test
    fun `a locked secret's name is masked`() {
        assertEquals("???", MenuAchievements.displayName(entry("combo_rainbow", name = "Rainbow", secret = true)))
    }

    @Test
    fun `an unlocked secret shows its name`() {
        val unlocked = entry("combo_rainbow", name = "Rainbow", secret = true, unlocked = true)

        assertEquals("Rainbow", MenuAchievements.displayName(unlocked))
    }

    @Test
    fun `a missing name is the id humanised without its prefix`() {
        assertEquals("Brick Red", MenuAchievements.displayName(entry("drop_brick_red")))
        assertEquals("Matcha", MenuAchievements.displayName(entry("matcha")))
    }

    @Test
    fun `a locked secret's description is masked`() {
        val masked = entry("combo_rainbow", description = "Own every color", secret = true)

        assertEquals("Secret achievement — unlock to reveal", MenuAchievements.displayDescription(masked))
    }

    @Test
    fun `a description shows as given, and is empty when missing`() {
        assertEquals("Own every color", MenuAchievements.displayDescription(entry("combo_x", description = "Own every color")))
        assertEquals("", MenuAchievements.displayDescription(entry("drop_matcha")))
    }

    @Test
    fun `tier label is the tier upper-cased, empty without one`() {
        assertEquals("GOLD", MenuAchievements.tierLabel("gold"))
        assertEquals("", MenuAchievements.tierLabel(null))
    }

    @Test
    fun `tier colors follow the website's badge, unknown or none muted`() {
        assertEquals(
            listOf(
                UiTheme.Colors.TIER_BRONZE,
                UiTheme.Colors.TIER_SILVER,
                UiTheme.Colors.TIER_GOLD,
                UiTheme.Colors.TIER_PLATINUM,
                UiTheme.Colors.TIER_LEGENDARY,
            ),
            listOf("bronze", "silver", "gold", "platinum", "legendary").map(MenuAchievements::tierColor),
        )
        assertEquals(UiTheme.Colors.TEXT_MUTED, MenuAchievements.tierColor("mythic"))
        assertEquals(UiTheme.Colors.TEXT_MUTED, MenuAchievements.tierColor(null))
    }

    @Test
    fun `an unlocked name takes its tier color`() {
        assertEquals(UiTheme.Colors.TIER_GOLD, MenuAchievements.nameColor(entry("drop_a", tier = "gold", unlocked = true)))
    }

    @Test
    fun `an unlocked name without a known tier is primary, like the website's white`() {
        assertEquals(UiTheme.Colors.TEXT_PRIMARY, MenuAchievements.nameColor(entry("drop_a", unlocked = true)))
        assertEquals(UiTheme.Colors.TEXT_PRIMARY, MenuAchievements.nameColor(entry("drop_a", tier = "mythic", unlocked = true)))
    }

    @Test
    fun `a locked name is subtle whatever its tier`() {
        assertEquals(UiTheme.Colors.TEXT_SUBTLE, MenuAchievements.nameColor(entry("drop_a", tier = "gold")))
        assertEquals(UiTheme.Colors.TEXT_SUBTLE, MenuAchievements.nameColor(entry("drop_a")))
    }

    @Test
    fun `a tier pill's fill is its color at the pill alpha`() {
        assertEquals(0x26_FF_D7_00, MenuAchievements.tierFill(UiTheme.Colors.TIER_GOLD))
    }

    @Test
    fun `footer of an unlocked entry with a time is its date`() {
        val unlocked = entry("drop_matcha", unlocked = true, unlockedAt = unlockTime)

        assertEquals("Unlocked Oct 20, 2025", MenuAchievements.footerText(unlocked, ZoneOffset.UTC))
    }

    @Test
    fun `footer of an unlocked entry without a time`() {
        val unlocked = entry("drop_matcha", unlocked = true, progress = AchievementProgress(5, 5))

        assertEquals("Unlocked", MenuAchievements.footerText(unlocked, ZoneOffset.UTC))
    }

    @Test
    fun `footer of a locked entry with progress`() {
        val locked = entry("count_five", progress = AchievementProgress(current = 3, total = 5))

        assertEquals("Progress 3 / 5", MenuAchievements.footerText(locked, ZoneOffset.UTC))
    }

    @Test
    fun `footer of a locked entry without progress`() {
        assertEquals("Locked", MenuAchievements.footerText(entry("drop_matcha"), ZoneOffset.UTC))
    }

    @Test
    fun `progress fraction of a locked entry with progress`() {
        assertEquals(0.6f, MenuAchievements.progressFraction(entry("count_five", progress = AchievementProgress(3, 5))))
    }

    @Test
    fun `no progress fraction when unlocked, without progress, or with a zero total`() {
        assertNull(MenuAchievements.progressFraction(entry("count_five", unlocked = true, progress = AchievementProgress(5, 5))))
        assertNull(MenuAchievements.progressFraction(entry("count_five")))
        assertNull(MenuAchievements.progressFraction(entry("count_five", progress = AchievementProgress(0, 0))))
    }

    @Test
    fun `summary text`() {
        assertEquals("12 of 50 Achievements", MenuAchievements.summaryText(12, 50))
    }

    @Test
    fun `summary takes the response's counts`() {
        // The counts disagree with the entries, so the test tells which is used.
        val response = AchievementsResponse(achievements = listOf(entry("drop_a")), unlockedCount = 12, totalCount = 50)

        assertEquals(AchievementSummary(unlocked = 12, total = 50), MenuAchievements.summary(response))
    }

    @Test
    fun `summary counts the entries when the response's total is 0`() {
        val response = AchievementsResponse(
            achievements = listOf(entry("drop_a", unlocked = true), entry("drop_b"), entry("drop_c")),
            unlockedCount = 0,
            totalCount = 0,
        )

        assertEquals(AchievementSummary(unlocked = 1, total = 3), MenuAchievements.summary(response))
    }

    @Test
    fun `percent text has one decimal`() {
        assertEquals("24.0% complete", MenuAchievements.percentText(AchievementSummary(12, 50)))
        assertEquals("0.0% complete", MenuAchievements.percentText(AchievementSummary(0, 0)))
    }
}
