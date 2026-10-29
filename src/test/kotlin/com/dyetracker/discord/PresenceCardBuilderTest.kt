package com.dyetracker.discord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [buildPresenceCard]: the stats line, the profile button and the session timer. */
class PresenceCardBuilderTest {

    private val stats = PresenceStats(
        uuid = "abc",
        profileId = "p1",
        profileName = "Banana",
        dyes = emptyList(),
        owned = 17,
        total = 42,
        dropped = 23,
    )

    private val profileButton = PresenceButton("View on DyeTracker", "https://dyetracker.xyz/player/abc/profile/p1")

    @Test
    fun `full stats show the stats line and the profile button`() {
        val card = buildPresenceCard(stats, sessionStartMs = null)

        assertEquals("17/42 dyes · 23 dropped", card.state)
        assertEquals(listOf(profileButton), card.buttons)
        assertNull(card.details)
        assertNull(card.startEpochSeconds)
    }

    @Test
    fun `a missing number drops the stats line but keeps the button`() {
        for (partial in listOf(stats.copy(owned = null), stats.copy(total = null), stats.copy(dropped = null))) {
            val card = buildPresenceCard(partial, sessionStartMs = null)

            assertNull(card.state, "$partial")
            assertEquals(listOf(profileButton), card.buttons)
        }
    }

    @Test
    fun `a blank uuid or profile id drops the button`() {
        for (partial in listOf(stats.copy(uuid = ""), stats.copy(profileId = " "))) {
            assertTrue(buildPresenceCard(partial, sessionStartMs = null).buttons.isEmpty(), "$partial")
        }
    }

    @Test
    fun `no stats is the bare logo card`() {
        val card = buildPresenceCard(stats = null, sessionStartMs = null)
        assertNull(card.state)
        assertTrue(card.buttons.isEmpty())
        assertEquals("dyetracker", card.largeImage)
        assertEquals("DyeTracker", card.largeText)
        assertNull(card.smallImage)
        assertNull(card.smallText)
    }

    @Test
    fun `the session start becomes whole epoch seconds`() {
        assertEquals(1_761_700_000, buildPresenceCard(stats, sessionStartMs = 1_761_700_000_123).startEpochSeconds)
    }
}
