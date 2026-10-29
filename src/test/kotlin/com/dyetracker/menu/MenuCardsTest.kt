package com.dyetracker.menu

import com.dyetracker.api.DyeProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit coverage for the menu card's badge, labels and text fitting (task 67-2). */
class MenuCardsTest {

    private fun entry(progress: Double? = null, trackable: Boolean = false, isVerified: Boolean = false) =
        DyeProgressEntry(
            dyeId = "matcha",
            name = "Matcha Dye",
            color = "#7cb342",
            category = "mob_drop",
            progress = progress,
            trackable = trackable,
            isVerified = isVerified,
        )

    private val modVerified = entry(progress = 42.0, trackable = true, isVerified = true)
    private val modOnly = entry(progress = null, trackable = true)
    private val estimated = entry(progress = 42.0, trackable = true)
    private val estimatedComplete = entry(progress = 100.0, trackable = true)
    private val notTrackable = entry(progress = null, trackable = false)

    /** Every character measures 6px, like a fixed-width font. */
    private val measure: (String) -> Int = { it.length * 6 }

    @Test
    fun `a verified entry gets the MOD badge`() {
        assertEquals(CardBadge.MOD, MenuCards.badgeFor(modVerified))
    }

    @Test
    fun `a verified entry with null progress still gets the MOD badge`() {
        assertEquals(CardBadge.MOD, MenuCards.badgeFor(entry(progress = null, trackable = true, isVerified = true)))
    }

    @Test
    fun `a trackable entry with null progress gets the MOD ONLY badge`() {
        assertEquals(CardBadge.MOD_ONLY, MenuCards.badgeFor(modOnly))
    }

    @Test
    fun `a trackable entry with progress gets the ESTIMATED badge`() {
        assertEquals(CardBadge.ESTIMATED, MenuCards.badgeFor(estimated))
    }

    @Test
    fun `an untrackable entry gets the NOT TRACKABLE badge`() {
        assertEquals(CardBadge.NOT_TRACKABLE, MenuCards.badgeFor(notTrackable))
    }

    @Test
    fun `badge labels match the website`() {
        assertEquals(
            listOf("MOD", "MOD ONLY", "ESTIMATED", "NOT TRACKABLE"),
            CardBadge.entries.map { it.label },
        )
    }

    @Test
    fun `status label is Progress for a trackable entry with progress`() {
        assertEquals("Progress", MenuCards.statusLabel(modVerified))
        assertEquals("Progress", MenuCards.statusLabel(estimated))
    }

    @Test
    fun `status label is Status when progress is null or the dye is untrackable`() {
        assertEquals("Status", MenuCards.statusLabel(modOnly))
        assertEquals("Status", MenuCards.statusLabel(notTrackable))
        assertEquals("Status", MenuCards.statusLabel(entry(progress = 42.0, trackable = false)))
    }

    @Test
    fun `value text for MOD and ESTIMATED is the formatted percent`() {
        assertEquals("42.0%", MenuCards.valueText(modVerified))
        assertEquals("42.0%", MenuCards.valueText(estimated))
        assertEquals("100%", MenuCards.valueText(estimatedComplete))
    }

    @Test
    fun `value text for MOD ONLY asks to sync via the mod`() {
        assertEquals("Sync via mod", MenuCards.valueText(modOnly))
    }

    @Test
    fun `value text for NOT TRACKABLE says so`() {
        assertEquals("Not trackable", MenuCards.valueText(notTrackable))
    }

    @Test
    fun `format category capitalises each underscore-separated word`() {
        assertEquals("Mob Drop", MenuCards.formatCategory("mob_drop"))
        assertEquals("Skill Action", MenuCards.formatCategory("skill_action"))
    }

    @Test
    fun `format category capitalises a single word`() {
        assertEquals("Nucleus", MenuCards.formatCategory("nucleus"))
    }

    @Test
    fun `format category of an empty string is empty`() {
        assertEquals("", MenuCards.formatCategory(""))
    }

    @Test
    fun `fit text returns text that fits unchanged`() {
        assertEquals("Matcha", MenuCards.fitText("Matcha", 100, measure))
    }

    @Test
    fun `fit text keeps text that exactly fills the width`() {
        assertEquals("Matcha", MenuCards.fitText("Matcha", 36, measure))
        assertEquals("Matcha", MenuCards.fitText("Matcha", 37, measure))
    }

    @Test
    fun `fit text truncates to the longest prefix plus ellipsis that fits`() {
        assertEquals("Necron'…", MenuCards.fitText("Necron's Abyss", 48, measure))
    }

    @Test
    fun `fit text is empty when not even the ellipsis fits`() {
        assertEquals("", MenuCards.fitText("Necron's Abyss", 5, measure))
    }

    @Test
    fun `fit text is just the ellipsis when only it fits`() {
        assertEquals("…", MenuCards.fitText("Necron's Abyss", 6, measure))
    }

    @Test
    fun `fit text never cuts a surrogate pair in half`() {
        // "ab" + one emoji (a surrogate pair, 2 chars) + "cd". At 24px (4 chars) a cut after 3
        // chars would leave the emoji's lone high surrogate, so the fit steps back to "ab…".
        val text = "ab\uD83C\uDFB5cd"
        assertEquals("ab\u2026", MenuCards.fitText(text, 24, measure))
        assertEquals("ab\uD83C\uDFB5\u2026", MenuCards.fitText(text, 30, measure))
    }

    @Test
    fun `fit text of an empty string is empty`() {
        assertEquals("", MenuCards.fitText("", 0, measure))
    }
}
