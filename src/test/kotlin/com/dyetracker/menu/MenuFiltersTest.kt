package com.dyetracker.menu

import com.dyetracker.api.DyeProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit coverage for the menu's category filter and sort (task 67-3). */
class MenuFiltersTest {

    private fun entry(name: String, category: String, progress: Double?) = DyeProgressEntry(
        dyeId = name.lowercase(),
        name = name,
        color = "#ffffff",
        category = category,
        progress = progress,
        trackable = true,
    )

    private val celeste = entry("Celeste", "slayer", 80.0)
    private val aurora = entry("Aurora", "slayer", 50.0)
    private val dusk = entry("dusk", "nucleus", 50.0)
    private val bubblegum = entry("bubblegum", "dungeon", null)

    /** API order; deliberately not sorted by name or progress. */
    private val dyes = listOf(celeste, aurora, dusk, bubblegum)

    @Test
    fun `menu categories follow the website order`() {
        assertEquals(
            listOf("slayer", "dungeon", "nucleus", "experimentation", "kuudra", "mob_drop", "skill_action", "purchase"),
            MENU_CATEGORIES,
        )
    }

    @Test
    fun `the null category is All Dyes`() {
        assertEquals("All Dyes", categoryLabel(null))
    }

    @Test
    fun `known categories use the website labels`() {
        assertEquals("Crystal Nucleus", categoryLabel("nucleus"))
        assertEquals("Skills", categoryLabel("skill_action"))
        assertEquals(
            listOf("Slayer", "Dungeon", "Crystal Nucleus", "Experimentation", "Kuudra", "Mob Drop", "Skills", "Purchase"),
            MENU_CATEGORIES.map { categoryLabel(it) },
        )
    }

    @Test
    fun `an unknown category is title-cased from its id`() {
        assertEquals("New Thing", categoryLabel("new_thing"))
    }

    @Test
    fun `sort labels match the brief`() {
        assertEquals(
            listOf("Default", "Progress ↓", "Progress ↑", "Name A→Z", "Name Z→A"),
            MenuSort.entries.map { it.label },
        )
    }

    @Test
    fun `next cycles in declaration order`() {
        assertEquals(MenuSort.PROGRESS_DESC, MenuSort.DEFAULT.next())
        assertEquals(MenuSort.PROGRESS_ASC, MenuSort.PROGRESS_DESC.next())
        assertEquals(MenuSort.NAME_ASC, MenuSort.PROGRESS_ASC.next())
        assertEquals(MenuSort.NAME_DESC, MenuSort.NAME_ASC.next())
    }

    @Test
    fun `next wraps from the last sort to the first`() {
        assertEquals(MenuSort.DEFAULT, MenuSort.NAME_DESC.next())
    }

    @Test
    fun `a category filter keeps only matching entries in API order`() {
        assertEquals(listOf(celeste, aurora), applyFilters(dyes, "slayer", MenuSort.DEFAULT))
    }

    @Test
    fun `the null category keeps every entry`() {
        assertEquals(dyes, applyFilters(dyes, null, MenuSort.DEFAULT))
    }

    @Test
    fun `a category with no entries yields an empty list`() {
        assertTrue(applyFilters(dyes, "kuudra", MenuSort.DEFAULT).isEmpty())
    }

    @Test
    fun `an empty input yields an empty list`() {
        assertTrue(applyFilters(emptyList(), null, MenuSort.NAME_ASC).isEmpty())
    }

    @Test
    fun `default keeps the input order`() {
        assertEquals(listOf(celeste, aurora, dusk, bubblegum), applyFilters(dyes, null, MenuSort.DEFAULT))
    }

    @Test
    fun `progress descending puts null progress last`() {
        assertEquals(listOf(celeste, aurora, dusk, bubblegum), applyFilters(dyes, null, MenuSort.PROGRESS_DESC))
    }

    @Test
    fun `progress ascending puts null progress first`() {
        assertEquals(listOf(bubblegum, aurora, dusk, celeste), applyFilters(dyes, null, MenuSort.PROGRESS_ASC))
    }

    @Test
    fun `name ascending ignores case`() {
        assertEquals(listOf(aurora, bubblegum, celeste, dusk), applyFilters(dyes, null, MenuSort.NAME_ASC))
    }

    @Test
    fun `name descending ignores case`() {
        assertEquals(listOf(dusk, celeste, bubblegum, aurora), applyFilters(dyes, null, MenuSort.NAME_DESC))
    }

    @Test
    fun `equal progress keeps input order`() {
        val duskFirst = listOf(dusk, aurora)
        assertEquals(duskFirst, applyFilters(duskFirst, null, MenuSort.PROGRESS_DESC))
        assertEquals(duskFirst, applyFilters(duskFirst, null, MenuSort.PROGRESS_ASC))
    }

    @Test
    fun `filter and sort combine`() {
        assertEquals(listOf(aurora, celeste), applyFilters(dyes, "slayer", MenuSort.PROGRESS_ASC))
    }
}
