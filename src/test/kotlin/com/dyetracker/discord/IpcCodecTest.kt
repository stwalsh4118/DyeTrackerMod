package com.dyetracker.discord

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Unit tests for [IpcCodec]: the Discord IPC frame layout (two little-endian int32s, then UTF-8 JSON). */
class IpcCodecTest {

    private fun header(op: Int, length: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES * 2).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(length).array()

    @Test
    fun `encode writes a little-endian header then the UTF-8 payload`() {
        val bytes = IpcCodec.encode(IpcOp.FRAME, """{"a":1}""")
        val expected = byteArrayOf(1, 0, 0, 0, 7, 0, 0, 0) + """{"a":1}""".toByteArray(Charsets.UTF_8)
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `encode counts the payload length in UTF-8 bytes, not chars`() {
        val json = """{"s":"é"}"""
        val bytes = IpcCodec.encode(IpcOp.HANDSHAKE, json)
        val length = ByteBuffer.wrap(bytes, Int.SIZE_BYTES, Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(json.toByteArray(Charsets.UTF_8).size, length)
        assertEquals(json.length + 1, length)
    }

    @Test
    fun `a frame round-trips through read`() {
        val json = """{"cmd":"DISPATCH","evt":"READY"}"""
        val frame = IpcCodec.read(ByteArrayInputStream(IpcCodec.encode(IpcOp.FRAME, json)))
        assertEquals(IpcFrame(IpcOp.FRAME.code, json), frame)
    }

    @Test
    fun `two frames back to back are read in order`() {
        val stream = ByteArrayInputStream(
            IpcCodec.encode(IpcOp.HANDSHAKE, """{"v":1}""") + IpcCodec.encode(IpcOp.CLOSE, """{"code":4000}"""),
        )
        assertEquals(IpcFrame(IpcOp.HANDSHAKE.code, """{"v":1}"""), IpcCodec.read(stream))
        assertEquals(IpcFrame(IpcOp.CLOSE.code, """{"code":4000}"""), IpcCodec.read(stream))
        assertNull(IpcCodec.read(stream), "clean EOF after the last frame")
    }

    @Test
    fun `read assembles a frame delivered a few bytes at a time`() {
        val bytes = IpcCodec.encode(IpcOp.FRAME, """{"a":1}""")
        val trickle = object : java.io.InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= bytes.size) return -1
                b[off] = bytes[pos++]
                return 1
            }
        }
        assertEquals(IpcFrame(IpcOp.FRAME.code, """{"a":1}"""), IpcCodec.read(trickle))
    }

    @Test
    fun `empty stream reads as null`() {
        assertNull(IpcCodec.read(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `EOF inside the header throws`() {
        assertFailsWith<IOException> { IpcCodec.read(ByteArrayInputStream(byteArrayOf(1, 0, 0))) }
    }

    @Test
    fun `truncated payload throws`() {
        val full = IpcCodec.encode(IpcOp.FRAME, """{"a":1}""")
        assertFailsWith<IOException> { IpcCodec.read(ByteArrayInputStream(full.copyOf(full.size - 2))) }
    }

    @Test
    fun `length above the frame limit throws`() {
        val stream = ByteArrayInputStream(header(IpcOp.FRAME.code, IPC_MAX_FRAME_BYTES + 1))
        assertFailsWith<IOException> { IpcCodec.read(stream) }
    }

    @Test
    fun `negative length throws`() {
        assertFailsWith<IOException> { IpcCodec.read(ByteArrayInputStream(header(IpcOp.FRAME.code, -1))) }
    }

    @Test
    fun `opcodes map to and from their codes`() {
        assertEquals(listOf(0, 1, 2, 3, 4), IpcOp.entries.map { it.code })
        assertEquals(IpcOp.PONG, IpcOp.of(4))
        assertEquals(IpcOp.HANDSHAKE, IpcOp.of(0))
        assertNull(IpcOp.of(5))
    }
}
