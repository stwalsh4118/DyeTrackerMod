package com.dyetracker.menu

import com.dyetracker.api.DyeProgressEntry
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit coverage for the Collection tab's drop timeline (68-4). */
class MenuTimelineTest {

    private fun cell(dyeId: String, owned: Boolean = true, obtainedAt: Long? = null) = CollectionCell(
        entry = DyeProgressEntry(dyeId = dyeId, name = "$dyeId Dye", color = "#$dyeId", category = "mob_drop"),
        owned = owned,
        count = if (owned) 1 else 0,
        obtainedAt = obtainedAt,
    )

    /** 2026-10-26T12:00:00Z. */
    private val oct26Noon = 1_793_016_000_000L

    private val day = 86_400_000L

    @Test
    fun `timeline keeps owned dated cells newest first`() {
        // Cells order is neither newest first nor oldest first.
        val cells = listOf(
            cell("a", obtainedAt = 2_000L),
            cell("b", owned = false),
            cell("c", obtainedAt = 3_000L),
            cell("d", obtainedAt = null),
            cell("e", obtainedAt = 1_000L),
        )

        assertEquals(
            listOf(
                TimelineRow("c Dye", "#c", 3_000L),
                TimelineRow("a Dye", "#a", 2_000L),
                TimelineRow("e Dye", "#e", 1_000L),
            ),
            MenuTimeline.timeline(cells),
        )
    }

    @Test
    fun `timeline drops a not-owned cell even with a date`() {
        val cells = listOf(cell("a", owned = false, obtainedAt = 5_000L), cell("b", obtainedAt = 1_000L))

        assertEquals(listOf("b Dye"), MenuTimeline.timeline(cells).map { it.name })
    }

    @Test
    fun `timeline ties keep the cells order`() {
        val cells = listOf(
            cell("x", obtainedAt = 1_000L),
            cell("m", obtainedAt = 2_000L),
            cell("z", obtainedAt = 2_000L),
            cell("a", obtainedAt = 2_000L),
        )

        assertEquals(listOf("m Dye", "z Dye", "a Dye", "x Dye"), MenuTimeline.timeline(cells).map { it.name })
    }

    @Test
    fun `timeline of no dated cells is empty`() {
        assertEquals(emptyList(), MenuTimeline.timeline(listOf(cell("a"), cell("b", owned = false))))
        assertEquals(emptyList(), MenuTimeline.timeline(emptyList()))
    }

    @Test
    fun `drop date is month day and year in English`() {
        assertEquals("Oct 26, 2026", MenuTimeline.formatDropDate(oct26Noon, ZoneOffset.UTC))
    }

    @Test
    fun `drop date is the day in the given zone`() {
        val lateOct26Utc = 1_793_057_400_000L // 2026-10-26T23:30:00Z

        assertEquals("Oct 26, 2026", MenuTimeline.formatDropDate(lateOct26Utc, ZoneOffset.UTC))
        assertEquals("Oct 27, 2026", MenuTimeline.formatDropDate(lateOct26Utc, ZoneOffset.ofHours(2)))
    }

    @Test
    fun `relative time follows the website's whole-day rules`() {
        val cases = listOf(
            0L to "today",
            1L to "yesterday",
            6L to "6 days ago",
            7L to "1 weeks ago",
            29L to "4 weeks ago",
            30L to "1 months ago",
            364L to "12 months ago",
            365L to "1 years ago",
            800L to "2 years ago",
        )
        for ((days, expected) in cases) {
            assertEquals(expected, MenuTimeline.formatRelative(oct26Noon + days * day, oct26Noon), "$days days")
        }
    }

    @Test
    fun `relative time counts whole days only`() {
        assertEquals("today", MenuTimeline.formatRelative(oct26Noon + day - 1, oct26Noon))
        assertEquals("yesterday", MenuTimeline.formatRelative(oct26Noon + 2 * day - 1, oct26Noon))
    }

    @Test
    fun `a future drop is today`() {
        assertEquals("today", MenuTimeline.formatRelative(oct26Noon, oct26Noon + 3 * day))
    }

    @Test
    fun `timeline text joins the date and relative time with a middle dot`() {
        val row = TimelineRow("Matcha Dye", "#7cb342", oct26Noon)

        assertEquals(
            "Oct 26, 2026 · 2 days ago",
            MenuTimeline.timelineText(row, oct26Noon + 2 * day, ZoneOffset.UTC),
        )
    }
}
