package com.dyetracker.menu

import com.dyetracker.api.AchievementsResponse
import com.dyetracker.api.PlayerAchievementEntry
import com.dyetracker.dyeprogress.DyeProgressWidgetView
import com.dyetracker.ui.theme.UiTheme
import java.time.ZoneId

/** One category section of the Achievements tab: its [category] id, header [label] and [entries] in API order. */
data class AchievementSection(val category: String, val label: String, val entries: List<PlayerAchievementEntry>)

/** Which achievements the Achievements tab shows by status, like the website's status pills; [label] starts the pill. */
enum class StatusFilter(val label: String) { ALL("All"), UNLOCKED("Unlocked"), LOCKED("Locked") }

/** How many of the [total] achievements the player [unlocked]. */
data class AchievementSummary(val unlocked: Int, val total: Int)

/**
 * Pure logic for the in-game menu's Achievements tab, mirroring the website's Achievements page
 * (`AchievementsPage`, `AchievementCard`, `AchievementTierBadge`): the category sections in the
 * website's order, a locked secret's mask, the tier label and color, each row's footer, the
 * `N of M Achievements` summary, and the All / Unlocked / Locked status filter. An entry from an
 * API that doesn't send the catalog fields yet falls back: its category from its id prefix, its
 * name from its id.
 */
object MenuAchievements {
    private const val DYE_DROP = "dye-drop"
    private const val COUNT = "count"
    private const val SWEEP = "sweep"
    private const val PROGRESS = "progress"
    private const val COMBO = "combo"

    /** The category of an entry without one whose id prefix is none of [PREFIX_CATEGORIES]'. */
    private const val OTHER_CATEGORY = "other"

    /** The website's category sections, in its order. */
    val ACHIEVEMENT_SECTIONS = listOf(DYE_DROP, COUNT, SWEEP, PROGRESS, COMBO)

    /** The website's label of each of [ACHIEVEMENT_SECTIONS]. */
    private val SECTION_LABELS = mapOf(
        DYE_DROP to "Dye Drops",
        COUNT to "Milestones",
        SWEEP to "Sweeps",
        PROGRESS to "Progress",
        COMBO to "Secrets",
    )

    /** An entry's category by its id prefix, for an entry without a category. */
    private val PREFIX_CATEGORIES = listOf(
        "drop_" to DYE_DROP,
        "count_" to COUNT,
        "sweep_" to SWEEP,
        "progress_" to PROGRESS,
        "combo_" to COMBO,
    )

    /** Each tier's color, from the website's `AchievementTierBadge`. */
    private val TIER_COLORS = mapOf(
        "bronze" to UiTheme.Colors.TIER_BRONZE,
        "silver" to UiTheme.Colors.TIER_SILVER,
        "gold" to UiTheme.Colors.TIER_GOLD,
        "platinum" to UiTheme.Colors.TIER_PLATINUM,
        "legendary" to UiTheme.Colors.TIER_LEGENDARY,
    )

    /** Separates an achievement id's prefix from the rest, and the words of an unknown category. */
    private const val ID_SEPARATOR = '_'
    private const val CATEGORY_SEPARATOR = '-'

    /** A locked secret's name, like the website's. */
    private const val SECRET_NAME = "???"

    /** A locked secret's description, like the website's. */
    private const val SECRET_DESCRIPTION = "Secret achievement — unlock to reveal"

    private const val UNLOCKED_TEXT = "Unlocked"
    private const val LOCKED_TEXT = "Locked"
    private const val PROGRESS_PREFIX = "Progress "

    /** Mask isolating the RGB channels of an ARGB color (drops the alpha byte). */
    private const val RGB_MASK = 0x00FFFFFF

    /** Alpha of a tier pill's fill (~15%, the website badge's translucent frame). */
    private const val TIER_FILL_ALPHA = 0x26_00_00_00

    /** The section header of [category]: the website's label, else the category title-cased (`limited-time` → `Limited Time`). */
    fun sectionLabel(category: String): String =
        SECTION_LABELS[category] ?: MenuCards.formatCategory(category.replace(CATEGORY_SEPARATOR, ID_SEPARATOR))

    /** [e]'s category; without one, by its id prefix (`drop_` → `dye-drop`, …), else `other`. */
    fun categoryOf(e: PlayerAchievementEntry): String =
        e.category ?: PREFIX_CATEGORIES.firstOrNull { (prefix, _) -> e.achievementId.startsWith(prefix) }?.second
            ?: OTHER_CATEGORY

    /**
     * [entries] grouped by [categoryOf]: [ACHIEVEMENT_SECTIONS] in order, then any other category
     * in the order it first appears; entries keep their order, and a category with none is left out.
     */
    fun sections(entries: List<PlayerAchievementEntry>): List<AchievementSection> {
        val byCategory = entries.groupBy(::categoryOf) // keeps first-appearance order and entry order
        val order = ACHIEVEMENT_SECTIONS.filter { it in byCategory } + byCategory.keys.filter { it !in ACHIEVEMENT_SECTIONS }
        return order.map { category -> AchievementSection(category, sectionLabel(category), byCategory.getValue(category)) }
    }

    /** What the tab says when the status filter leaves no achievements, like the website. */
    const val NO_MATCH_TEXT = "No achievements match these filters"

    /** Whether [f] keeps [e]: everything under `ALL`, else by whether it is unlocked. */
    fun matches(e: PlayerAchievementEntry, f: StatusFilter): Boolean = when (f) {
        StatusFilter.ALL -> true
        StatusFilter.UNLOCKED -> e.unlocked
        StatusFilter.LOCKED -> !e.unlocked
    }

    /** [sections] of the [entries] that [f] keeps; a section left with none is omitted. */
    fun filteredSections(entries: List<PlayerAchievementEntry>, f: StatusFilter): List<AchievementSection> =
        sections(entries.filter { matches(it, f) })

    /**
     * A section's header. Under `ALL`, how many of its [sectionTotal] entries (all of its category,
     * before filtering) are unlocked, e.g. `Dye Drops  2 / 13`; under a status filter, just how many
     * it shows, e.g. `Dye Drops  11`, as the website counts a filtered section.
     */
    fun sectionHeader(s: AchievementSection, sectionTotal: Int, f: StatusFilter): String = when (f) {
        StatusFilter.ALL -> "${s.label}  ${s.entries.count { it.unlocked }} / $sectionTotal"
        StatusFilter.UNLOCKED, StatusFilter.LOCKED -> "${s.label}  ${s.entries.size}"
    }

    /** A status pill's text: [f]'s label and how many of [entries] it keeps, e.g. `Unlocked (2)`. */
    fun statusPillLabel(f: StatusFilter, entries: List<PlayerAchievementEntry>): String =
        "${f.label} (${entries.count { matches(it, f) }})"

    /** `???` for a locked secret; else [e]'s name, or its id without the prefix title-cased (`drop_brick_red` → `Brick Red`). */
    fun displayName(e: PlayerAchievementEntry): String = when {
        isMasked(e) -> SECRET_NAME
        else -> e.name ?: DyeProgressWidgetView.humanizeDyeId(e.achievementId.substringAfter(ID_SEPARATOR))
    }

    /** The secret placeholder for a locked secret; else [e]'s description, `""` without one. */
    fun displayDescription(e: PlayerAchievementEntry): String =
        if (isMasked(e)) SECRET_DESCRIPTION else e.description ?: ""

    /** A tier pill's text: [tier] upper-cased (`GOLD`), `""` without one. */
    fun tierLabel(tier: String?): String = tier?.uppercase() ?: ""

    /** [tier]'s color from the website's tier badge; an unknown or missing tier is muted. */
    fun tierColor(tier: String?): Int = TIER_COLORS[tier] ?: UiTheme.Colors.TEXT_MUTED

    /**
     * [e]'s name color: its tier's when unlocked, [TEXT_PRIMARY][UiTheme.Colors.TEXT_PRIMARY] (the
     * website's white) when unlocked without a known tier, [TEXT_SUBTLE][UiTheme.Colors.TEXT_SUBTLE] when locked.
     */
    fun nameColor(e: PlayerAchievementEntry): Int = when {
        !e.unlocked -> UiTheme.Colors.TEXT_SUBTLE
        e.tier in TIER_COLORS -> tierColor(e.tier)
        else -> UiTheme.Colors.TEXT_PRIMARY
    }

    /** A tier pill's fill: [color] at [TIER_FILL_ALPHA]. */
    fun tierFill(color: Int): Int = (color and RGB_MASK) or TIER_FILL_ALPHA

    /**
     * A row's footer, in the website's order: `Unlocked <date>` (its day in [zone]) when unlocked
     * with a time, `Unlocked` without one, `Progress <current> / <total>` when locked with progress,
     * else `Locked`.
     */
    fun footerText(e: PlayerAchievementEntry, zone: ZoneId): String {
        val unlockedAt = e.unlockedAt
        val progress = e.progress
        return when {
            e.unlocked && unlockedAt != null -> "$UNLOCKED_TEXT ${MenuTimeline.formatDropDate(unlockedAt, zone)}"
            e.unlocked -> UNLOCKED_TEXT
            progress != null -> "$PROGRESS_PREFIX${progress.current} / ${progress.total}"
            else -> LOCKED_TEXT
        }
    }

    /** How far a locked [e] with progress is (`current / total`); `null` when unlocked, without progress, or the total is 0. */
    fun progressFraction(e: PlayerAchievementEntry): Float? {
        val progress = e.progress ?: return null
        if (e.unlocked || progress.total <= 0) return null
        return progress.current.toFloat() / progress.total
    }

    /** The response's unlocked and total counts; when its total is 0, its entries' own. */
    fun summary(response: AchievementsResponse): AchievementSummary =
        if (response.totalCount > 0) {
            AchievementSummary(response.unlockedCount, response.totalCount)
        } else {
            AchievementSummary(response.achievements.count { it.unlocked }, response.achievements.size)
        }

    /** E.g. `12 of 50 Achievements`. */
    fun summaryText(unlocked: Int, total: Int): String = "$unlocked of $total Achievements"

    /** The unlocked share with one decimal, e.g. `24.0% complete`, as [MenuCollection.percentText] words it. */
    fun percentText(s: AchievementSummary): String = MenuCollection.percentText(asCollectionSummary(s))

    /** The unlocked share in percent (`0` to `100`), as [MenuCollection.percent] counts it. */
    fun percent(s: AchievementSummary): Double = MenuCollection.percent(asCollectionSummary(s))

    private fun asCollectionSummary(s: AchievementSummary) = MenuCollection.CollectionSummary(owned = s.unlocked, total = s.total)

    /** A locked secret, whose name and description are hidden. */
    private fun isMasked(e: PlayerAchievementEntry): Boolean = e.secret && !e.unlocked
}
