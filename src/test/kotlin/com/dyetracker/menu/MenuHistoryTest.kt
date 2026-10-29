package com.dyetracker.menu

import com.dyetracker.api.HistoryDyeGain
import com.dyetracker.api.HistoryResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit coverage for the History tab's periods, chance text, state messages and rows (75-1). */
class MenuHistoryTest {

    private val bone = HistoryDyeGain("bone", "Bone Dye", "#e3dac9", "1,000 skeletons", 0.0027)

    /** A linked response with [snapshotCount] snapshots up to [to] and the [dyes] gained. */
    private fun response(
        linked: Boolean = true,
        to: Long? = TO,
        snapshotCount: Int = 14,
        dyes: List<HistoryDyeGain> = listOf(bone),
    ) = HistoryResponse(linked = linked, from = FROM, to = to, snapshotCount = snapshotCount, dyes = dyes)

    @Test
    fun `the default period is 24h`() {
        assertEquals(HistoryPeriod.DAY, HistoryPeriod.DEFAULT)
    }

    @Test
    fun `the periods are 1h, 24h, 7d and 30d in that order, labelled as sent`() {
        assertEquals(listOf("1h", "24h", "7d", "30d"), HistoryPeriod.entries.map { it.apiValue })
        assertEquals(listOf("1h", "24h", "7d", "30d"), HistoryPeriod.entries.map { it.label })
    }

    @Test
    fun `a null chance reads as a dash`() {
        assertEquals("—", historyChanceText(null))
    }

    @Test
    fun `a tiny chance keeps two significant digits`() {
        assertEquals("0.0027%", historyChanceText(0.0027))
    }

    @Test
    fun `a chance from one percent up has one decimal`() {
        assertEquals("1.2%", historyChanceText(1.234))
    }

    @Test
    fun `an unlinked player gets the link hint`() {
        assertEquals(
            "Link the mod to start recording history",
            historyStateMessage(response(linked = false, to = null, snapshotCount = 0), HistoryPeriod.DAY),
        )
    }

    @Test
    fun `the link hint wins even when an unlinked response has snapshots`() {
        assertEquals("Link the mod to start recording history", historyStateMessage(response(linked = false), HistoryPeriod.DAY))
    }

    @Test
    fun `a linked player without any snapshot yet is told how history gets saved`() {
        assertEquals(
            "No history yet. Snapshots are saved while your dyes are being viewed.",
            historyStateMessage(response(to = null, snapshotCount = 0), HistoryPeriod.DAY),
        )
    }

    @Test
    fun `no snapshot in the period says nothing was gained in it`() {
        assertEquals("Nothing gained in the last 24h.", historyStateMessage(response(snapshotCount = 0), HistoryPeriod.DAY))
        assertEquals("Nothing gained in the last 7d.", historyStateMessage(response(snapshotCount = 0), HistoryPeriod.WEEK))
    }

    @Test
    fun `snapshots in the period without any dye work say nothing was gained in it`() {
        assertEquals(
            "Nothing gained in the last 30d.",
            historyStateMessage(response(snapshotCount = 3, dyes = emptyList()), HistoryPeriod.MONTH),
        )
    }

    @Test
    fun `snapshots in the period show the list`() {
        assertNull(historyStateMessage(response(snapshotCount = 1), HistoryPeriod.HOUR))
    }

    @Test
    fun `a row reads the name and the work, with the chance on the right`() {
        assertEquals("Bone Dye · 1,000 skeletons" to "0.0027%", historyRowText(bone))
    }

    @Test
    fun `a row without a chance shows a dash on the right`() {
        val gain = HistoryDyeGain("livid", "Livid Dye", "#c0c0c0", "3 completions", null)

        assertEquals("Livid Dye · 3 completions" to "—", historyRowText(gain))
    }

    @Test
    fun `the cache key is the profile and the period`() {
        assertEquals("p1|24h", historyKey("p1", HistoryPeriod.DAY))
        assertEquals("p1|30d", historyKey("p1", HistoryPeriod.MONTH))
    }

    private companion object {
        const val FROM = 1_761_700_000_000L
        const val TO = 1_761_786_000_000L
    }
}
