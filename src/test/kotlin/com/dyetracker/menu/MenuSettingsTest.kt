package com.dyetracker.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit coverage for the menu's Settings tab logic: the group toggle state of a set of widgets,
 * the two-click unlink confirm, and the last-sync text (task 67-7).
 */
class MenuSettingsTest {

    @Test
    fun `aggregate of no widgets is NONE`() {
        assertEquals(ToggleState.NONE, aggregate(emptyList()))
    }

    @Test
    fun `aggregate of all visible widgets is ON`() {
        assertEquals(ToggleState.ON, aggregate(listOf(true, true)))
    }

    @Test
    fun `aggregate of all hidden widgets is OFF`() {
        assertEquals(ToggleState.OFF, aggregate(listOf(false)))
    }

    @Test
    fun `aggregate of visible and hidden widgets is MIXED`() {
        assertEquals(ToggleState.MIXED, aggregate(listOf(true, false)))
    }

    @Test
    fun `targetVisibility is null for NONE`() {
        assertNull(targetVisibility(ToggleState.NONE))
    }

    @Test
    fun `targetVisibility hides everything from ON`() {
        assertEquals(false, targetVisibility(ToggleState.ON))
    }

    @Test
    fun `targetVisibility shows everything from OFF`() {
        assertEquals(true, targetVisibility(ToggleState.OFF))
    }

    @Test
    fun `targetVisibility shows everything from MIXED`() {
        assertEquals(true, targetVisibility(ToggleState.MIXED))
    }

    @Test
    fun `toggleLabel names each state`() {
        assertEquals("On", toggleLabel(ToggleState.ON))
        assertEquals("Off", toggleLabel(ToggleState.OFF))
        assertEquals("Mixed", toggleLabel(ToggleState.MIXED))
        assertEquals("None", toggleLabel(ToggleState.NONE))
    }

    @Test
    fun `the unlink confirm window is 3 seconds`() {
        assertEquals(3_000L, MenuLayout.UNLINK_CONFIRM_MS)
    }

    @Test
    fun `a first click only arms the confirm`() {
        val confirm = ConfirmClick()

        assertFalse(confirm.click(nowMs = 10_000L))
    }

    @Test
    fun `a second click within the window confirms`() {
        val confirm = ConfirmClick()
        confirm.click(nowMs = 10_000L)

        assertTrue(confirm.click(nowMs = 13_000L))
    }

    @Test
    fun `a second click after the window re-arms instead of confirming`() {
        val confirm = ConfirmClick()
        confirm.click(nowMs = 10_000L)

        assertFalse(confirm.click(nowMs = 13_001L))
        assertTrue(confirm.isArmed(nowMs = 13_001L), "the late click arms a new window")
        assertTrue(confirm.click(nowMs = 14_000L), "a click within the new window confirms")
    }

    @Test
    fun `a confirm disarms, so the next click only arms again`() {
        val confirm = ConfirmClick()
        confirm.click(nowMs = 10_000L)
        confirm.click(nowMs = 11_000L)

        assertFalse(confirm.isArmed(nowMs = 11_000L))
        assertFalse(confirm.click(nowMs = 11_500L))
    }

    @Test
    fun `a double click arms but never confirms`() {
        val confirm = ConfirmClick()
        confirm.click(nowMs = 10_000L)

        assertFalse(confirm.click(nowMs = 10_100L, doubled = true), "the second half of a double click does not unlink")
        assertTrue(confirm.isArmed(nowMs = 10_100L), "the confirm stays armed so it can be shown")
    }

    @Test
    fun `a double click on a disarmed confirm arms it`() {
        val confirm = ConfirmClick()

        assertFalse(confirm.click(nowMs = 10_000L, doubled = true))
        assertTrue(confirm.isArmed(nowMs = 10_000L))
    }

    @Test
    fun `a plain click after a double click confirms`() {
        val confirm = ConfirmClick()
        confirm.click(nowMs = 10_000L)
        confirm.click(nowMs = 10_100L, doubled = true)

        assertTrue(confirm.click(nowMs = 11_000L))
    }

    @Test
    fun `a double click keeps the first click's window`() {
        val confirm = ConfirmClick()
        confirm.click(nowMs = 10_000L)
        confirm.click(nowMs = 10_100L, doubled = true)

        assertFalse(confirm.isArmed(nowMs = 13_001L), "the window still ends 3s after the arming click")
    }

    @Test
    fun `isArmed is false before any click`() {
        assertFalse(ConfirmClick().isArmed(nowMs = 10_000L))
    }

    @Test
    fun `isArmed is true inside the window and false after it`() {
        val confirm = ConfirmClick()
        confirm.click(nowMs = 10_000L)

        assertTrue(confirm.isArmed(nowMs = 10_000L))
        assertTrue(confirm.isArmed(nowMs = 13_000L))
        assertFalse(confirm.isArmed(nowMs = 13_001L))
    }

    @Test
    fun `a custom window is honoured`() {
        val confirm = ConfirmClick(windowMs = 500L)
        confirm.click(nowMs = 10_000L)

        assertFalse(confirm.isArmed(nowMs = 10_501L))
        assertFalse(confirm.click(nowMs = 10_501L))
    }

    @Test
    fun `lastSyncText says never synced when there was no sync`() {
        assertEquals("Never synced", lastSyncText(180_000L, 0L))
    }

    @Test
    fun `lastSyncText gives the age of the last sync`() {
        assertEquals("Last sync: 2m ago", lastSyncText(180_000L, 60_000L))
    }

    @Test
    fun `lastSyncText says just now for a sync seconds ago`() {
        assertEquals("Last sync: just now", lastSyncText(63_000L, 60_000L))
    }
}
