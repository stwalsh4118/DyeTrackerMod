package com.dyetracker.discord

/** What the presence worker does with the card on a tick. */
enum class CardDecision {
    /** Build the card from the stats known now and reconcile it. */
    SEND,

    /** Leave Discord as it is: the stats aren't known yet and nothing needs taking down. */
    SKIP,

    /** Reconcile `null`: take the card down. */
    CLEAR,
}

/**
 * What to do with the card this tick. Not [wanted] clears. While [username]'s first stats load is
 * pending ([firstLoadPending]) the card is held back, so a bare card doesn't hold the stats card
 * behind the send throttle; but when the last card went out for another player
 * ([sentForUsername], `null` when none is up) it is cleared instead, so their numbers and button
 * never stay up for [username]. Otherwise the card is sent.
 */
fun cardDecision(wanted: Boolean, firstLoadPending: Boolean, sentForUsername: String?, username: String): CardDecision = when {
    !wanted -> CardDecision.CLEAR
    !firstLoadPending -> CardDecision.SEND
    sentForUsername != null && sentForUsername != username -> CardDecision.CLEAR
    else -> CardDecision.SKIP
}
