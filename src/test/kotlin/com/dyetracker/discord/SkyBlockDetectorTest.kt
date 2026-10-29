package com.dyetracker.discord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [isHypixelAddress], [isSkyBlock] and the [SkyBlockSession] leave debounce. */
class SkyBlockDetectorTest {

    @Test
    fun `Hypixel addresses are recognised`() {
        for (address in listOf("mc.hypixel.net", "hypixel.net", "HYPIXEL.NET:25565", "mc.hypixel.net.", " mc.hypixel.net ")) {
            assertTrue(isHypixelAddress(address), "'$address' is Hypixel")
        }
    }

    @Test
    fun `other addresses are not Hypixel`() {
        for (address in listOf("notHypixel.net", "hypixel.net.evil.com", "localhost", null, "", "   ")) {
            assertFalse(isHypixelAddress(address), "'$address' is not Hypixel")
        }
    }

    @Test
    fun `a SKYBLOCK sidebar on Hypixel is SkyBlock`() {
        assertTrue(isSkyBlock("mc.hypixel.net", "§e§lSKYBLOCK"))
        assertTrue(isSkyBlock("mc.hypixel.net", "SKYBLOCK CO-OP"))
        assertTrue(isSkyBlock("mc.hypixel.net", "SkyBlock"), "the marker matches ignoring case")
        assertTrue(isSkyBlock("mc.hypixel.net", "§eSKY§lBLOCK"), "formatting codes inside the marker are stripped")
    }

    @Test
    fun `a live connection to SkyBlock counts`() {
        assertTrue(countsAsSkyBlock(connected = true, serverAddress = "mc.hypixel.net", sidebarTitle = "§e§lSKYBLOCK"))
    }

    @Test
    fun `a dropped connection never counts, whatever the sidebar still shows`() {
        assertFalse(countsAsSkyBlock(connected = false, serverAddress = "mc.hypixel.net", sidebarTitle = "§e§lSKYBLOCK"))
    }

    @Test
    fun `a live connection off SkyBlock does not count`() {
        assertFalse(countsAsSkyBlock(connected = true, serverAddress = "mc.hypixel.net", sidebarTitle = "§e§lHYPIXEL"))
        assertFalse(countsAsSkyBlock(connected = true, serverAddress = "play.example.net", sidebarTitle = "SKYBLOCK"))
    }

    @Test
    fun `another sidebar, another server or no sidebar is not SkyBlock`() {
        assertFalse(isSkyBlock("mc.hypixel.net", "§e§lHYPIXEL"))
        assertFalse(isSkyBlock("play.example.net", "SKYBLOCK"))
        assertFalse(isSkyBlock("mc.hypixel.net", null))
        assertFalse(isSkyBlock(null, "SKYBLOCK"))
    }

    @Test
    fun `a new session is inactive`() {
        val session = SkyBlockSession(DEBOUNCE_MS)
        assertFalse(session.active)
        assertNull(session.startedAtMs)
    }

    @Test
    fun `entering is immediate and leaving waits out the debounce`() {
        val session = SkyBlockSession(DEBOUNCE_MS)

        session.update(onSkyBlockNow = true, nowMs = 1_000)
        assertTrue(session.active)
        assertEquals(1_000, session.startedAtMs)

        session.update(onSkyBlockNow = false, nowMs = 2_000)
        session.update(onSkyBlockNow = false, nowMs = 11_999)
        assertTrue(session.active, "off SkyBlock for 9 999 ms keeps the card")
        assertEquals(1_000, session.startedAtMs)

        session.update(onSkyBlockNow = false, nowMs = 12_000)
        assertFalse(session.active, "off SkyBlock for the full debounce clears it")
        assertNull(session.startedAtMs)
    }

    @Test
    fun `coming back inside the debounce keeps the first start`() {
        val session = SkyBlockSession(DEBOUNCE_MS)
        session.update(onSkyBlockNow = true, nowMs = 1_000)
        session.update(onSkyBlockNow = false, nowMs = 2_000)
        session.update(onSkyBlockNow = true, nowMs = 5_000)

        // The leave timer restarted at 6 000, so 15 999 is still inside it.
        for (nowMs in (6_000L..15_000L step POLL_STEP_MS) + 15_999L) {
            session.update(onSkyBlockNow = false, nowMs = nowMs)
        }

        assertTrue(session.active)
        assertEquals(1_000, session.startedAtMs)
    }

    @Test
    fun `leaveNow ends the session at once`() {
        val session = SkyBlockSession(DEBOUNCE_MS)
        session.update(onSkyBlockNow = true, nowMs = 1_000)

        session.leaveNow()

        assertFalse(session.active)
        assertNull(session.startedAtMs)
    }

    @Test
    fun `rejoining after a leave starts a new timer`() {
        val session = SkyBlockSession(DEBOUNCE_MS)
        session.update(onSkyBlockNow = true, nowMs = 1_000)
        session.leaveNow()

        session.update(onSkyBlockNow = true, nowMs = 50_000)

        assertTrue(session.active)
        assertEquals(50_000, session.startedAtMs)
    }

    @Test
    fun `off SkyBlock while inactive stays inactive`() {
        val session = SkyBlockSession(DEBOUNCE_MS)
        session.update(onSkyBlockNow = false, nowMs = 1_000)
        session.update(onSkyBlockNow = false, nowMs = 100_000)
        assertFalse(session.active)
        assertNull(session.startedAtMs)
    }

    @Test
    fun `the default debounce is SKYBLOCK_LEAVE_DEBOUNCE_MS`() {
        val session = SkyBlockSession()
        session.update(onSkyBlockNow = true, nowMs = 0)
        session.update(onSkyBlockNow = false, nowMs = 1)
        session.update(onSkyBlockNow = false, nowMs = SKYBLOCK_LEAVE_DEBOUNCE_MS)
        assertTrue(session.active, "one ms short of the debounce")
        session.update(onSkyBlockNow = false, nowMs = SKYBLOCK_LEAVE_DEBOUNCE_MS + 1)
        assertFalse(session.active)
    }

    private companion object {
        const val DEBOUNCE_MS = 10_000L
        const val POLL_STEP_MS = 1_000L
    }
}
