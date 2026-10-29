package com.dyetracker.dyeprogress

import com.dyetracker.api.DyeRotationStats
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the single-dye widget's rotation line (tasks 72-4, 72-7): the rotation chance
 * percent (two significant digits below 0.01%) and the composed line, which always reads
 * `this rotation` (the API's `since` is ignored) and tags a boosted chance with its multiplier,
 * e.g. `(2x)`.
 */
class RotationLineTest {

    /** A `since` timestamp (ms): counting began partway through the rotation. */
    private val since = 1_800_000_000_000L

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

    @Test
    fun `rotationPercentText shows two significant digits below a hundredth of a percent`() {
        assertEquals("0.0027%", rotationPercentText(0.0027))
    }

    @Test
    fun `rotationPercentText keeps a trailing zero as the second significant digit`() {
        assertEquals("0.000040%", rotationPercentText(0.00004))
    }

    @Test
    fun `rotationPercentText just under a hundredth of a percent keeps two significant digits`() {
        assertEquals("0.0099%", rotationPercentText(0.0099))
    }

    @Test
    fun `rotationPercentText at zero is two decimals`() {
        assertEquals("0.00%", rotationPercentText(0.0))
    }

    // --- rotationLineText ----------------------------------------------------

    @Test
    fun `rotationLineText with progress at a 2x boost tags the chance`() {
        val r = DyeRotationStats(523, "16 skeletons", 0.0027, 2, null)
        assertEquals("16 skeletons · 0.0027% this rotation (2x)", rotationLineText(r))
    }

    @Test
    fun `rotationLineText with progress at a 3x boost tags the chance`() {
        val r = DyeRotationStats(523, "16 skeletons", 0.0027, 3, null)
        assertEquals("16 skeletons · 0.0027% this rotation (3x)", rotationLineText(r))
    }

    @Test
    fun `rotationLineText with progress and no boost has no tag`() {
        val r = DyeRotationStats(523, "16 skeletons", 0.0027, 1, null)
        assertEquals("16 skeletons · 0.0027% this rotation", rotationLineText(r))
    }

    @Test
    fun `rotationLineText ignores since`() {
        val partial = DyeRotationStats(523, "16 skeletons", 0.0027, 2, since)
        val full = DyeRotationStats(523, "16 skeletons", 0.0027, 2, null)
        assertEquals(rotationLineText(full), rotationLineText(partial))
        assertEquals("16 skeletons · 0.0027% this rotation (2x)", rotationLineText(partial))
    }

    @Test
    fun `rotationLineText without progress shows the count only`() {
        val r = DyeRotationStats(523, "12 runs", null, 1, 123L)
        assertEquals("12 runs this rotation", rotationLineText(r))
    }

    @Test
    fun `rotationLineText without progress has no boost tag`() {
        val r = DyeRotationStats(523, "12 runs", null, 2, 123L)
        assertEquals("12 runs this rotation", rotationLineText(r))
    }
}
