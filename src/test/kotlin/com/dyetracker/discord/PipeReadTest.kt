package com.dyetracker.discord

import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [readWithDeadline] and [PollingPipeInputStream], the Windows pipe input that waits
 * for bytes before reading so a read never blocks. A fake clock that [sleep] advances stands in
 * for time.
 */
class PipeReadTest {

    private var now = 0L
    private val sleeps = mutableListOf<Long>()
    private val sleep: (Long) -> Unit = { sleeps += it; now += it }

    @Test
    fun `bytes that arrive after 3 polls are read`() {
        var polls = 0
        var readWith = -1
        val n = readWithDeadline(
            available = { if (++polls > 3) 4L else 0L },
            read = { max -> readWith = max; max },
            maxBytes = 8,
            deadlineMs = 1_000,
            nowMs = { now },
            sleep = sleep,
        )

        assertEquals(4, n)
        assertEquals(4, readWith, "the read asks for no more than is waiting, so it can't block")
        assertEquals(List(3) { IPC_PIPE_POLL_MS }, sleeps)
    }

    @Test
    fun `the read never asks for more than the caller wants`() {
        var readWith = -1
        readWithDeadline({ 100L }, { max -> readWith = max; max }, maxBytes = 8, deadlineMs = 1_000, nowMs = { now }, sleep = sleep)
        assertEquals(8, readWith)
        assertTrue(sleeps.isEmpty(), "bytes already waiting are read without a poll")
    }

    @Test
    fun `no bytes by the deadline throws IOException without reading`() {
        var reads = 0
        assertFailsWith<IOException> {
            readWithDeadline({ 0L }, { reads++; 0 }, maxBytes = 8, deadlineMs = 100, nowMs = { now }, sleep = sleep)
        }
        assertEquals(0, reads)
        assertEquals(100L, now, "gave up at the deadline, not later")
    }

    @Test
    fun `an IOException from available propagates`() {
        assertFailsWith<IOException> {
            readWithDeadline({ throw IOException("Stream Closed") }, { 0 }, 8, 1_000, { now }, sleep)
        }
    }

    @Test
    fun `the stream reads a whole frame through the poll`() {
        val bytes = IpcCodec.encode(IpcOp.FRAME, """{"evt":"READY"}""")
        val source = ByteArrayInputStream(bytes)
        var polls = 0
        val input = PollingPipeInputStream(
            bytesWaiting = { if (++polls % 2 == 0) source.available().toLong() else 0L },
            readPipe = source::read,
            timeoutMs = 1_000,
            nowMs = { now },
            sleep = sleep,
        )

        assertEquals(IpcFrame(IpcOp.FRAME.code, """{"evt":"READY"}"""), IpcCodec.read(input))
    }

    @Test
    fun `the stream gives up when nothing arrives`() {
        val input = PollingPipeInputStream({ 0L }, { _, _, _ -> 0 }, timeoutMs = 300, nowMs = { now }, sleep = sleep)
        assertFailsWith<IOException> { input.read() }
        assertEquals(300L, now)
    }

    @Test
    fun `a zero-length read returns 0 without polling`() {
        val input = PollingPipeInputStream({ 0L }, { _, _, _ -> error("not read") }, 300, { now }, sleep)
        assertEquals(0, input.read(ByteArray(4), 0, 0))
        assertTrue(sleeps.isEmpty())
    }
}
