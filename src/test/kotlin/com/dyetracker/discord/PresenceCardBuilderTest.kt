package com.dyetracker.discord

import com.dyetracker.api.DyeProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [buildPresenceCard]: the stats line, the profile button, the session timer and the hunt dye. */
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

    private fun nadeshiko(progress: Double?) = DyeProgressEntry(
        dyeId = NADESHIKO,
        name = "Nadeshiko",
        color = "#F6ADC6",
        category = "rng",
        progress = progress,
    )

    private fun huntCard(progress: Double?) =
        buildPresenceCard(stats.copy(dyes = listOf(nadeshiko(progress))), sessionStartMs = null, huntDyeId = NADESHIKO)

    @Test
    fun `a hunted dye with odds shows the hunt line, its art and the logo`() {
        val card = huntCard(0.026)

        assertEquals("Hunting Nadeshiko · 0.03%", card.details)
        assertEquals("https://dyetracker.xyz/dyes/nadeshiko.webp", card.largeImage)
        assertEquals("Nadeshiko", card.largeText)
        assertEquals("dyetracker", card.smallImage)
        assertEquals("Banana", card.smallText)
        assertEquals("17/42 dyes · 23 dropped", card.state)
        assertEquals(listOf(profileButton), card.buttons)
    }

    @Test
    fun `the hunt odds use the rotation line's precision`() {
        assertEquals("Hunting Nadeshiko · 0.0027%", huntCard(0.0027).details)
        assertEquals("Hunting Nadeshiko · 42.0%", huntCard(42.04).details)
    }

    @Test
    fun `complete odds read 100 percent`() {
        assertEquals("Hunting Nadeshiko · 100%", huntCard(100.0).details)
        assertEquals("Hunting Nadeshiko · 100%", huntCard(130.0).details)
    }

    @Test
    fun `a hunted dye without odds shows just its name, still with its art`() {
        val card = huntCard(null)

        assertEquals("Hunting Nadeshiko", card.details)
        assertEquals("https://dyetracker.xyz/dyes/nadeshiko.webp", card.largeImage)
        assertEquals("dyetracker", card.smallImage)
    }

    @Test
    fun `a hunted dye missing from the stats uses the catalog name and the logo`() {
        val card = buildPresenceCard(stats.copy(dyes = listOf(nadeshiko(0.026))), sessionStartMs = null, huntDyeId = "mango")

        assertEquals("Hunting Mango", card.details)
        assertEquals("dyetracker", card.largeImage)
        assertEquals("DyeTracker", card.largeText)
        assertNull(card.smallImage)
        assertNull(card.smallText)
    }

    @Test
    fun `a hunt with no stats yet shows the catalog name and the logo`() {
        val card = buildPresenceCard(stats = null, sessionStartMs = null, huntDyeId = NADESHIKO)

        assertEquals("Hunting Nadeshiko", card.details)
        assertEquals("dyetracker", card.largeImage)
        assertNull(card.smallImage)
    }

    @Test
    fun `a hunted id known to neither the stats nor the catalog shows the id`() {
        assertEquals("Hunting not_a_dye", buildPresenceCard(stats, sessionStartMs = null, huntDyeId = "not_a_dye").details)
    }

    @Test
    fun `no hunt leaves the card as before`() {
        val withDyes = stats.copy(dyes = listOf(nadeshiko(0.026)))

        assertEquals(buildPresenceCard(withDyes, sessionStartMs = null), buildPresenceCard(withDyes, null, huntDyeId = ""))
        val card = buildPresenceCard(withDyes, sessionStartMs = null)
        assertNull(card.details)
        assertEquals("dyetracker", card.largeImage)
        assertNull(card.smallImage)
    }

    @Test
    fun `huntOddsText is null without odds and caps at 100 percent`() {
        assertNull(huntOddsText(null))
        assertEquals("100%", huntOddsText(100.0))
        assertEquals("99.9%", huntOddsText(99.94))
        assertEquals("0.00%", huntOddsText(0.0))
    }

    @Test
    fun `dyeImageUrl points at the website's dye art`() {
        assertEquals("https://dyetracker.xyz/dyes/brick_red.webp", dyeImageUrl("brick_red"))
    }

    private companion object {
        const val NADESHIKO = "nadeshiko"
    }
}
