package com.dyetracker.dyeprogress

import com.dyetracker.api.DyeRotationStats
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the single-dye widget's rotation line (task 72-4): the rotation chance
 * percent, the "since" age text, and the composed line for each combination of a known chance
 * (with/without `progress`) and a full or partial rotation (`since` null or set).
 */
class RotationLineTest {

    private val now = 1_800_000_000_000L
    private val second = 1_000L
    private val minute = 60 * second
    private val hour = 60 * minute
    private val day = 24 * hour

    // --- rotationPercentText -------------------------------------------------

    @Test
    fun `rotationPercentText shows two decimals below one percent`() {
        assertEquals("0.04%", rotationPercentText(0.04))
    }

    @Test
    fun `rotationPercentText rounds just under one percent to two decimals`() {
        assertEquals("1.00%", rotationPercentText(0.999))
    }

    @Test
    fun `rotationPercentText shows one decimal at or above one percent`() {
        assertEquals("1.2%", rotationPercentText(1.234))
    }

    // --- sinceAgoText --------------------------------------------------------

    @Test
    fun `sinceAgoText under a minute is just now`() {
        assertEquals("just now", sinceAgoText(now - 59 * second, now))
    }

    @Test
    fun `sinceAgoText at one minute is 1m ago`() {
        assertEquals("1m ago", sinceAgoText(now - 60 * second, now))
    }

    @Test
    fun `sinceAgoText at 59 minutes is 59m ago`() {
        assertEquals("59m ago", sinceAgoText(now - 59 * minute, now))
    }

    @Test
    fun `sinceAgoText at one hour is 1h ago`() {
        assertEquals("1h ago", sinceAgoText(now - hour, now))
    }

    @Test
    fun `sinceAgoText at 23 hours is 23h ago`() {
        assertEquals("23h ago", sinceAgoText(now - 23 * hour, now))
    }

    @Test
    fun `sinceAgoText at 24 hours is 1d ago`() {
        assertEquals("1d ago", sinceAgoText(now - day, now))
    }

    @Test
    fun `sinceAgoText with a since in the future is just now`() {
        assertEquals("just now", sinceAgoText(now + hour, now))
    }

    // --- rotationLineText ----------------------------------------------------

    @Test
    fun `rotationLineText with progress over the full rotation`() {
        val r = DyeRotationStats(493, "1,234 skeletons", 0.04, 2, null)
        assertEquals("1,234 skeletons · 0.04% this rotation", rotationLineText(r, now))
    }

    @Test
    fun `rotationLineText with progress over a partial rotation`() {
        val r = DyeRotationStats(493, "1,234 skeletons", 1.234, 2, now - 2 * day)
        assertEquals("1,234 skeletons · 1.2% since 2d ago", rotationLineText(r, now))
    }

    @Test
    fun `rotationLineText without progress over the full rotation`() {
        val r = DyeRotationStats(493, "12 runs", null, 1, null)
        assertEquals("12 runs this rotation", rotationLineText(r, now))
    }

    @Test
    fun `rotationLineText without progress over a partial rotation`() {
        val r = DyeRotationStats(493, "12 runs", null, 1, now - 3 * hour)
        assertEquals("12 runs since 3h ago", rotationLineText(r, now))
    }
}
