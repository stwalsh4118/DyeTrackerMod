package com.dyetracker.menu

import com.dyetracker.api.HistoryBucket
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit coverage for the History tab's work bars: heights, rects, hover slots, labels and tooltips (75-2). */
class HistoryBarsTest {

    /** 2026-10-26T12:00:00Z. */
    private val oct26Noon = 1_793_016_000_000L

    /** 2026-10-26T00:00:00Z. */
    private val oct26Midnight = 1_792_972_800_000L

    private val hour = 3_600_000L

    private val day = 86_400_000L

    @Test
    fun `bar heights scale to the tallest bar and keep nulls`() {
        assertEquals(listOf(0, 20, 40, null), barHeights(listOf(0.0, 50.0, 100.0, null), 40))
    }

    @Test
    fun `a tiny but positive work still gets a one pixel bar`() {
        assertEquals(listOf(1, 40), barHeights(listOf(0.4, 1000.0), 40))
    }

    @Test
    fun `all zero work is all zero heights`() {
        assertEquals(listOf(0, 0, 0), barHeights(listOf(0.0, 0.0, 0.0), 40))
    }

    @Test
    fun `zero work beside nulls keeps the nulls`() {
        assertEquals(listOf(null, 0, null), barHeights(listOf(null, 0.0, null), 40))
    }

    @Test
    fun `no work at all is no heights`() {
        assertEquals(emptyList(), barHeights(emptyList(), 40))
        assertEquals(listOf(null, null), barHeights(listOf(null, null), 40))
    }

    @Test
    fun `bar heights round to the nearest pixel`() {
        // 1/3 of 40 is 13.33, 2/3 is 26.67.
        assertEquals(listOf(13, 27, 40), barHeights(listOf(1.0, 2.0, 3.0), 40))
    }

    @Test
    fun `twelve bars in 120 px with a gap of 2 are 8 wide and 10 apart`() {
        val heights = List<Int?>(12) { 5 }

        val rects = barRects(heights, left = 30, bottom = 100, width = 120, gapPx = 2)

        assertEquals(12, rects.size)
        rects.forEachIndexed { i, rect -> assertEquals(BarRect(30 + 10 * i, 95, 8, 5), rect) }
    }

    @Test
    fun `a zero height is a one pixel baseline tick and a null height no bar`() {
        val rects = barRects(listOf(0, null, 4), left = 0, bottom = 50, width = 30, gapPx = 2)

        assertEquals(BarRect(0, 49, 8, 1), rects[0])
        assertNull(rects[1])
        assertEquals(BarRect(20, 46, 8, 4), rects[2])
    }

    @Test
    fun `a bar is at least one pixel wide however big the gap`() {
        val rects = barRects(listOf(3, 3), left = 0, bottom = 10, width = 4, gapPx = 5)

        assertEquals(listOf(1, 1), rects.map { it?.width })
    }

    @Test
    fun `no heights are no rects`() {
        assertEquals(emptyList(), barRects(emptyList(), left = 0, bottom = 10, width = 120, gapPx = 2))
    }

    @Test
    fun `the slot under the mouse is its bar's index`() {
        // 12 slots of 10 px from x=30.
        assertEquals(0, barSlotAt(30.0, left = 30, width = 120, count = 12))
        assertEquals(0, barSlotAt(39.9, left = 30, width = 120, count = 12))
        assertEquals(1, barSlotAt(40.0, left = 30, width = 120, count = 12))
        assertEquals(11, barSlotAt(149.9, left = 30, width = 120, count = 12))
    }

    @Test
    fun `no slot left of the bars, past the last slot or without bars`() {
        assertNull(barSlotAt(29.9, left = 30, width = 120, count = 12))
        assertNull(barSlotAt(150.0, left = 30, width = 120, count = 12))
        assertNull(barSlotAt(50.0, left = 30, width = 120, count = 0))
        assertNull(barSlotAt(30.0, left = 30, width = 5, count = 12)) // narrower than a pixel per slot
    }

    @Test
    fun `slice labels name each period's bucket`() {
        assertEquals("5 min", sliceLabel(HistoryPeriod.HOUR))
        assertEquals("hour", sliceLabel(HistoryPeriod.DAY))
        assertEquals("6 h", sliceLabel(HistoryPeriod.WEEK))
        assertEquals("day", sliceLabel(HistoryPeriod.MONTH))
    }

    @Test
    fun `the chart title names the dye and its slice`() {
        assertEquals("Bone Dye · work per hour", chartTitle("Bone Dye", HistoryPeriod.DAY))
        assertEquals("Bone Dye · work per 5 min", chartTitle("Bone Dye", HistoryPeriod.HOUR))
    }

    @Test
    fun `an hour bucket's tooltip is its times and grouped work`() {
        val bucket = HistoryBucket(start = oct26Noon, end = oct26Noon + hour, work = 1234.0)

        assertEquals("12:00–13:00: 1,234", barTooltip(bucket, ZoneOffset.UTC))
    }

    @Test
    fun `a bucket's times are in the given zone`() {
        val bucket = HistoryBucket(start = oct26Noon, end = oct26Noon + hour, work = 5.0)

        assertEquals("14:00–15:00: 5", barTooltip(bucket, ZoneOffset.ofHours(2)))
    }

    @Test
    fun `a day bucket's tooltip is its date and work`() {
        val bucket = HistoryBucket(start = oct26Midnight, end = oct26Midnight + day, work = 1_000_000.0)

        assertEquals("Oct 26: 1,000,000", barTooltip(bucket, ZoneOffset.UTC))
    }

    @Test
    fun `a bucket without work says no data`() {
        val bucket = HistoryBucket(start = oct26Noon, end = oct26Noon + hour, work = null)

        assertEquals("12:00–13:00: no data", barTooltip(bucket, ZoneOffset.UTC))
    }

    @Test
    fun `fractional work keeps one decimal`() {
        val bucket = HistoryBucket(start = oct26Noon, end = oct26Noon + hour, work = 1234.56)

        assertEquals("12:00–13:00: 1,234.6", barTooltip(bucket, ZoneOffset.UTC))
    }

    @Test
    fun `a chart with any work has no message`() {
        val buckets = listOf(HistoryBucket(work = null), HistoryBucket(work = 0.0))

        assertNull(chartMessage(buckets))
    }

    @Test
    fun `a chart without any work says there aren't enough snapshots`() {
        assertEquals("Not enough snapshots for a chart", chartMessage(listOf(HistoryBucket(), HistoryBucket())))
        assertEquals("Not enough snapshots for a chart", chartMessage(emptyList()))
    }
}
