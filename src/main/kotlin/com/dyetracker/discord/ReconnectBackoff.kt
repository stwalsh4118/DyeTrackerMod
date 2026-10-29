package com.dyetracker.discord

/**
 * How long to wait before the next connect after failed ones: [initialMs] after the first failure,
 * doubling per further failure up to [maxMs]. Pure: callers pass the time in, so tests need no
 * clock. Not thread-safe; only the presence worker uses it (through [PresenceSync]).
 */
class ReconnectBackoff(private val initialMs: Long = RECONNECT_INITIAL_MS, private val maxMs: Long = RECONNECT_MAX_MS) {

    /** Consecutive failed connects since the last success (or since creation). */
    var failures: Int = 0
        private set

    /** The wait the next failure sets. */
    private var nextWaitMs = initialMs

    /** When the next connect may be tried; only meaningful while [failures] is above 0. */
    private var nextAttemptAtMs = 0L

    /** Whether a connect may be tried at [nowMs]: no failure is pending, or its wait is over. */
    fun canAttempt(nowMs: Long): Boolean = failures == 0 || nowMs >= nextAttemptAtMs

    /** Records a failed connect at [nowMs] and sets when the next one may be tried. */
    fun onFailure(nowMs: Long) {
        failures++
        nextAttemptAtMs = nowMs + nextWaitMs
        nextWaitMs = (nextWaitMs * 2).coerceAtMost(maxMs)
    }

    /** Records a successful connect (or that none is wanted): no failure is pending, and the next wait is [initialMs] again. */
    fun onSuccess() {
        failures = 0
        nextWaitMs = initialMs
        nextAttemptAtMs = 0L
    }
}
