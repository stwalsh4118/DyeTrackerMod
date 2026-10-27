package com.dyetracker.events

import com.dyetracker.data.PotionTier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure [BrewingStandTracker] (PBI 66): it counts each finished potion when it
 * leaves the Brewing Stand, and never counts a potion the player put in and took out unchanged.
 */
class BrewingStandTrackerTest {

    private val slot = 0

    private fun empty(index: Int = slot): Map<Int, String?> = mapOf(index to null)

    private fun item(name: String, index: Int = slot): Map<Int, String?> = mapOf(index to name)

    @Test
    fun `a brewed potion present at open is counted when removed`() {
        val tracker = BrewingStandTracker()

        assertEquals(emptyList(), tracker.observe(item("Regeneration VII Potion")))
        assertEquals(listOf(PotionTier.VII), tracker.observe(empty()))
    }

    @Test
    fun `a potion placed and removed unchanged is not counted`() {
        val tracker = BrewingStandTracker()

        assertEquals(emptyList(), tracker.observe(empty()))
        assertEquals(emptyList(), tracker.observe(item("Speed V Potion")))
        assertEquals(emptyList(), tracker.observe(empty()))
    }

    @Test
    fun `a potion placed, brewed in place and removed is counted once`() {
        val tracker = BrewingStandTracker()
        val emitted = listOf(
            empty(),
            item("Water Bottle"),
            item("Awkward Potion"),
            item("Speed I Potion"),
            empty()
        ).flatMap { tracker.observe(it) }

        assertEquals(listOf(PotionTier.I), emitted)
    }

    @Test
    fun `an upgraded potion counts its final tier`() {
        val tracker = BrewingStandTracker()
        val emitted = listOf(
            empty(),
            item("Regeneration VI Potion"),
            item("Regeneration VII Potion"),
            empty()
        ).flatMap { tracker.observe(it) }

        assertEquals(listOf(PotionTier.VII), emitted)
    }

    @Test
    fun `three slots emptied in one tick count three potions`() {
        val tracker = BrewingStandTracker()
        tracker.observe(
            mapOf(
                0 to "Regeneration VII Potion",
                1 to "Speed VIII Potion",
                2 to "Fishing XP Boost III Potion"
            )
        )

        val emitted = tracker.observe(mapOf(0 to null, 1 to null, 2 to null))

        assertEquals(listOf(PotionTier.VII, PotionTier.VIII, PotionTier.III), emitted)
    }

    @Test
    fun `non-potions never count`() {
        val tracker = BrewingStandTracker()
        val ingredientSlot = 1
        val emitted = listOf(
            // Nether Wart placed in the ingredient slot, then consumed by the brew.
            mapOf(slot to null, ingredientSlot to null),
            mapOf(slot to "Water Bottle", ingredientSlot to "Nether Wart"),
            mapOf(slot to "Awkward Potion", ingredientSlot to null),
            // The Awkward Potion is taken out.
            mapOf(slot to null, ingredientSlot to null),
            // A Water Bottle changed in place, then taken out.
            mapOf(slot to "Awkward Potion", ingredientSlot to null),
            mapOf(slot to "Water Bottle", ingredientSlot to null),
            mapOf(slot to null, ingredientSlot to null)
        ).flatMap { tracker.observe(it) }

        assertEquals(emptyList(), emitted)
    }

    @Test
    fun `a reopened stand counts the remaining potions once, on removal`() {
        val first = BrewingStandTracker()
        first.observe(empty())
        first.observe(item("Water Bottle"))
        first.observe(item("Speed I Potion"))

        val reopened = BrewingStandTracker()
        assertEquals(emptyList(), reopened.observe(item("Speed I Potion")))
        assertEquals(emptyList(), reopened.observe(item("Speed I Potion")))
        assertEquals(listOf(PotionTier.I), reopened.observe(empty()))
        assertEquals(emptyList(), reopened.observe(empty()))
    }

    @Test
    fun `a slot missing from a later observation is left as it was`() {
        val tracker = BrewingStandTracker()
        tracker.observe(mapOf(0 to "Speed I Potion", 1 to "Speed II Potion"))

        assertEquals(listOf(PotionTier.II), tracker.observe(mapOf(1 to null)))
        assertEquals(listOf(PotionTier.I), tracker.observe(mapOf(0 to null)))
    }
}
