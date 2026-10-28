package com.dyetracker.menu

import com.dyetracker.api.CollectionEntry
import com.dyetracker.api.DyeProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit coverage for the Collection tab's cells, summary, badge and empty hint (task 68-2). */
class MenuCollectionTest {

    private fun dye(dyeId: String) = DyeProgressEntry(
        dyeId = dyeId,
        name = "$dyeId Dye",
        color = "#7cb342",
        category = "mob_drop",
    )

    private fun owned(dyeId: String, count: Int = 1, obtainedAt: Long? = null) =
        CollectionEntry(dyeId = dyeId, profileId = "p1", count = count, obtainedAt = obtainedAt)

    /** The Dyes tab's list, in the dyes endpoint's order. */
    private val dyes = listOf(dye("aquamarine"), dye("matcha"), dye("celeste"), dye("fossil"))

    @Test
    fun `cells keep the dyes order and mark which are owned`() {
        // Owned entries come back in another order than the dye list.
        val cells = MenuCollection.cells(dyes, listOf(owned("fossil"), owned("matcha")))

        assertEquals(listOf("aquamarine", "matcha", "celeste", "fossil"), cells.map { it.entry.dyeId })
        assertEquals(listOf(false, true, false, true), cells.map { it.owned })
    }

    @Test
    fun `a not-owned cell has count 0 and no obtainedAt`() {
        val cell = MenuCollection.cells(dyes, listOf(owned("matcha", count = 2, obtainedAt = 5L))).first()

        assertEquals("aquamarine", cell.entry.dyeId)
        assertFalse(cell.owned)
        assertEquals(0, cell.count)
        assertNull(cell.obtainedAt)
    }

    @Test
    fun `counts for the same dye sum across entries`() {
        val cells = MenuCollection.cells(dyes, listOf(owned("matcha", count = 2), owned("matcha", count = 3)))

        assertEquals(listOf(0, 5, 0, 0), cells.map { it.count })
    }

    @Test
    fun `obtainedAt is the earliest non-null across entries`() {
        val cells = MenuCollection.cells(
            dyes,
            listOf(
                owned("matcha", obtainedAt = 2_000L),
                owned("matcha", obtainedAt = null),
                owned("matcha", obtainedAt = 1_000L),
                owned("matcha", obtainedAt = 3_000L),
            ),
        )

        assertEquals(1_000L, cells[1].obtainedAt)
    }

    @Test
    fun `an owned dye with no obtainedAt on any entry has none`() {
        val cell = MenuCollection.cells(dyes, listOf(owned("celeste"))).single { it.entry.dyeId == "celeste" }

        assertTrue(cell.owned)
        assertEquals(1, cell.count)
        assertNull(cell.obtainedAt)
    }

    @Test
    fun `an owned dye not in the dye list is ignored`() {
        val cells = MenuCollection.cells(dyes, listOf(owned("not_a_dye", count = 9), owned("celeste")))

        assertEquals(dyes.map { it.dyeId }, cells.map { it.entry.dyeId })
        assertEquals(listOf(false, false, true, false), cells.map { it.owned })
        assertEquals(1, MenuCollection.summary(cells).owned)
    }

    @Test
    fun `no dyes gives no cells`() {
        assertTrue(MenuCollection.cells(emptyList(), listOf(owned("matcha"))).isEmpty())
    }

    @Test
    fun `summary counts owned cells against all cells`() {
        val cells = MenuCollection.cells(dyes, listOf(owned("matcha", count = 4), owned("fossil")))

        assertEquals(MenuCollection.CollectionSummary(owned = 2, total = 4), MenuCollection.summary(cells))
    }

    @Test
    fun `summary of no cells is 0 of 0`() {
        assertEquals(MenuCollection.CollectionSummary(owned = 0, total = 0), MenuCollection.summary(emptyList()))
    }

    @Test
    fun `summaryText reads owned of total Dyes`() {
        assertEquals("1 of 42 Dyes", MenuCollection.summaryText(MenuCollection.CollectionSummary(owned = 1, total = 42)))
    }

    @Test
    fun `percentText shows one decimal`() {
        assertEquals("2.4% complete", MenuCollection.percentText(MenuCollection.CollectionSummary(owned = 1, total = 42)))
    }

    @Test
    fun `percentText of a full collection is 100 point 0`() {
        assertEquals("100.0% complete", MenuCollection.percentText(MenuCollection.CollectionSummary(owned = 42, total = 42)))
    }

    @Test
    fun `percentText of 0 of 0 is 0 point 0`() {
        assertEquals("0.0% complete", MenuCollection.percentText(MenuCollection.CollectionSummary(owned = 0, total = 0)))
    }

    @Test
    fun `percent is the owned share out of 100 and 0 with no dyes`() {
        assertEquals(25.0, MenuCollection.percent(MenuCollection.CollectionSummary(owned = 1, total = 4)))
        assertEquals(100.0, MenuCollection.percent(MenuCollection.CollectionSummary(owned = 4, total = 4)))
        assertEquals(0.0, MenuCollection.percent(MenuCollection.CollectionSummary(owned = 0, total = 0)))
    }

    @Test
    fun `ownedBadge is a check mark for one`() {
        assertEquals("✓", MenuCollection.ownedBadge(1))
    }

    @Test
    fun `ownedBadge is x and the count for two or more`() {
        assertEquals("x2", MenuCollection.ownedBadge(2))
        assertEquals("x3", MenuCollection.ownedBadge(3))
    }

    @Test
    fun `showEmptyHint is true with no owned cell`() {
        assertTrue(MenuCollection.showEmptyHint(MenuCollection.cells(dyes, emptyList())))
    }

    @Test
    fun `showEmptyHint is false with one owned cell`() {
        assertFalse(MenuCollection.showEmptyHint(MenuCollection.cells(dyes, listOf(owned("matcha")))))
    }

    @Test
    fun `the empty hint says how to record a collection`() {
        assertEquals(
            "Link your account and open Vincent's Dye Guide to record your collection.",
            MenuCollection.EMPTY_HINT,
        )
    }
}
