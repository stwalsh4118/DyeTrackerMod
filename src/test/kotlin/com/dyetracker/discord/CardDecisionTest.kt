package com.dyetracker.discord

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [cardDecision]: what the presence worker does with the card each tick, including clearing the
 * last player's card while a new username's first stats load runs.
 */
class CardDecisionTest {

    @Test
    fun `the first load of a session holds the card back`() {
        assertEquals(CardDecision.SKIP, cardDecision(wanted = true, firstLoadPending = true, sentForUsername = null, username = STEVE))
    }

    @Test
    fun `a new username's pending first load clears the last player's card`() {
        assertEquals(CardDecision.CLEAR, cardDecision(wanted = true, firstLoadPending = true, sentForUsername = ALEX, username = STEVE))
    }

    @Test
    fun `a pending reload for the same username holds the card back`() {
        assertEquals(CardDecision.SKIP, cardDecision(wanted = true, firstLoadPending = true, sentForUsername = STEVE, username = STEVE))
    }

    @Test
    fun `loaded stats send the card`() {
        assertEquals(CardDecision.SEND, cardDecision(wanted = true, firstLoadPending = false, sentForUsername = ALEX, username = STEVE))
        assertEquals(CardDecision.SEND, cardDecision(wanted = true, firstLoadPending = false, sentForUsername = null, username = STEVE))
    }

    @Test
    fun `no card wanted clears`() {
        for (pending in listOf(true, false)) {
            assertEquals(CardDecision.CLEAR, cardDecision(wanted = false, firstLoadPending = pending, sentForUsername = STEVE, username = STEVE))
        }
    }

    private companion object {
        const val STEVE = "Steve"
        const val ALEX = "Alex"
    }
}
