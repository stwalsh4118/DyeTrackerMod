package com.dyetracker.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [InventoryUtils.detectInventoryType] on Vincent's screens. Hypixel renamed the
 * collection screen from "Dye Compendium" to "Dye Guide" (PBI 62); both titles must be detected.
 */
class InventoryTypeDetectionTest {

    @Test
    fun `Dye Guide is the Vincent dye collection`() {
        assertEquals(InventoryType.VincentDyeCollection, InventoryUtils.detectInventoryType("Dye Guide"))
    }

    @Test
    fun `formatted Dye Guide title is detected after stripping`() {
        assertEquals(InventoryType.VincentDyeCollection, InventoryUtils.detectInventoryType("§8Dye Guide"))
    }

    @Test
    fun `legacy Dye Compendium title is still the Vincent dye collection`() {
        assertEquals(InventoryType.VincentDyeCollection, InventoryUtils.detectInventoryType("Dye Compendium"))
    }

    @Test
    fun `Dyes is still the Vincent rotation screen`() {
        assertEquals(InventoryType.VincentDyeRotation, InventoryUtils.detectInventoryType("Dyes"))
    }

    @Test
    fun `unrelated title is not detected`() {
        assertNull(InventoryUtils.detectInventoryType("Dye Guide Shop"))
    }
}
