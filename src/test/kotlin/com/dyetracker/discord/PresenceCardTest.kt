package com.dyetracker.discord

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Unit tests for [PresenceCard.toActivityJson]: the SET_ACTIVITY `activity` object Discord gets. */
class PresenceCardTest {

    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `the tracer card is a playing card with the logo`() {
        assertEquals(
            parse("""{"type":0,"details":"Tracking dyes","assets":{"large_image":"dyetracker","large_text":"DyeTracker"}}"""),
            TRACER_CARD.toActivityJson(),
        )
    }

    @Test
    fun `a full card carries state, small image, start time and buttons`() {
        val card = PresenceCard(
            details = "d",
            state = "s",
            smallImage = "small",
            smallText = "small text",
            startEpochSeconds = 1761700000,
            buttons = listOf(PresenceButton(label = "x", url = "https://e.x")),
        )
        assertEquals(
            parse(
                """
                {"type":0,"details":"d","state":"s",
                 "assets":{"large_image":"dyetracker","large_text":"DyeTracker","small_image":"small","small_text":"small text"},
                 "timestamps":{"start":1761700000},
                 "buttons":[{"label":"x","url":"https://e.x"}]}
                """,
            ),
            card.toActivityJson(),
        )
    }

    @Test
    fun `null fields are absent, not null`() {
        val json = PresenceCard(largeText = null).toActivityJson()
        assertEquals(parse("""{"type":0,"assets":{"large_image":"dyetracker"}}"""), json)
        assertFalse("null" in json.toString(), "no explicit nulls: $json")
    }
}
