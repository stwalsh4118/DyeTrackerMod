package com.dyetracker.discord

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Discord IPC opcodes, the first header field of every frame. */
enum class IpcOp(val code: Int) {
    HANDSHAKE(0), FRAME(1), CLOSE(2), PING(3), PONG(4);

    companion object {
        /** The opcode for [code], or `null` for one this client doesn't know. */
        fun of(code: Int): IpcOp? = entries.firstOrNull { it.code == code }
    }
}

/** One decoded IPC frame: the raw opcode (it may be unknown, see [IpcOp.of]) and its JSON payload. */
data class IpcFrame(val op: Int, val json: String)

/**
 * Discord IPC framing: opcode and payload length as little-endian int32s, then the UTF-8 JSON
 * payload. Pure byte work, no I/O of its own beyond the stream it is given.
 */
object IpcCodec {

    /** Two int32 fields: opcode and payload length. */
    private const val HEADER_BYTES = Int.SIZE_BYTES * 2

    /** [json] as one frame with opcode [op]. */
    fun encode(op: IpcOp, json: String): ByteArray {
        val payload = json.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(op.code)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    /**
     * Reads exactly one frame from [input], blocking until it is complete. Returns `null` on a
     * clean EOF before the header; throws [IOException] on EOF mid-frame or a length that is
     * negative or above [IPC_MAX_FRAME_BYTES].
     */
    fun read(input: InputStream): IpcFrame? {
        val header = ByteArray(HEADER_BYTES)
        val headerRead = readFully(input, header)
        if (headerRead == 0) return null
        if (headerRead < HEADER_BYTES) throw EOFException("EOF inside an IPC frame header ($headerRead of $HEADER_BYTES bytes)")

        val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val op = fields.int
        val length = fields.int
        if (length < 0 || length > IPC_MAX_FRAME_BYTES) {
            throw IOException("IPC frame length $length is outside 0..$IPC_MAX_FRAME_BYTES")
        }

        val payload = ByteArray(length)
        val payloadRead = readFully(input, payload)
        if (payloadRead < length) throw EOFException("EOF inside an IPC frame payload ($payloadRead of $length bytes)")
        return IpcFrame(op, String(payload, Charsets.UTF_8))
    }

    /** Fills [buffer] from [input] until it is full or EOF; returns how many bytes were read. */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
