package com.dyetracker.discord

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.util.UUID

/** JSON keys and values of the Discord RPC messages this client sends and reads. */
private const val KEY_EVT = "evt"
private const val KEY_NONCE = "nonce"
private const val EVT_READY = "READY"
private const val EVT_ERROR = "ERROR"
private const val CMD_SET_ACTIVITY = "SET_ACTIVITY"

/**
 * A minimal Discord IPC client: handshake, then SET_ACTIVITY requests, each answered before the
 * next is sent. Strictly request/response on the caller's thread and **not thread-safe**: only the
 * presence worker uses it (see [DiscordPresence]). Every failure comes back as `false`, never as
 * an exception; an I/O failure or a CLOSE from Discord also drops the connection.
 *
 * @param connector opens the channel to the Discord app.
 * @param applicationId the Discord application id; blank means [connect] always fails.
 */
class DiscordIpcClient(private val connector: IpcConnector, private val applicationId: String) {

    /** Volatile only so [close] from another thread (game shutdown) is seen by the worker. */
    @Volatile
    private var channel: IpcChannel? = null

    /** Whether a handshaken connection is open. */
    val isConnected: Boolean get() = channel != null

    /**
     * Opens the connection and handshakes, returning `true` once Discord answers READY (or at once
     * when already connected). A blank [applicationId] returns `false` without calling the
     * connector; no Discord, a CLOSE, EOF or an I/O error return `false` with nothing left open.
     */
    fun connect(): Boolean {
        if (applicationId.isBlank()) return false
        if (isConnected) return true
        val opened = connector.connect() ?: return false
        channel = opened
        val handshake = buildJsonObject {
            put("v", IPC_VERSION)
            put("client_id", applicationId)
        }
        return exchange(opened, IpcOp.HANDSHAKE, handshake) { it.string(KEY_EVT) == EVT_READY } != null
    }

    /**
     * Sets the card to [activity], or clears it when `null`, for process [pid]. Returns `true` when
     * Discord accepts it and `false` when it answers ERROR, when not connected, or when the
     * connection fails (which also closes it).
     */
    fun setActivity(pid: Long, activity: JsonObject?): Boolean {
        val open = channel ?: return false
        val nonce = UUID.randomUUID().toString()
        val request = buildJsonObject {
            put("cmd", CMD_SET_ACTIVITY)
            putJsonObject("args") {
                put("pid", pid)
                put("activity", activity ?: JsonNull)
            }
            put(KEY_NONCE, nonce)
        }
        val reply = exchange(open, IpcOp.FRAME, request) { it.string(KEY_NONCE) == nonce } ?: return false
        return reply.string(KEY_EVT) != EVT_ERROR
    }

    /**
     * Closes the connection, ignoring errors. [isConnected] is `false` afterwards. The one call that
     * may come from another thread: closing the channel unblocks a read the worker is stuck in.
     */
    fun close() {
        val open = channel ?: return
        channel = null
        try {
            open.close()
        } catch (_: IOException) {
            // Already broken; nothing more to release.
        }
    }

    /**
     * Sends [request] as an [op] frame, then reads frames until a FRAME whose JSON [matches],
     * skipping the rest. Returns that JSON, or `null` after closing the connection on a CLOSE
     * frame, EOF or an I/O error.
     */
    private fun exchange(open: IpcChannel, op: IpcOp, request: JsonObject, matches: (JsonObject) -> Boolean): JsonObject? {
        try {
            open.output.write(IpcCodec.encode(op, request.toString()))
            open.output.flush()
            while (true) {
                val frame = IpcCodec.read(open.input) ?: break
                if (frame.op == IpcOp.CLOSE.code) break
                if (frame.op != IpcOp.FRAME.code) continue
                val json = parseObject(frame.json) ?: continue
                if (matches(json)) return json
            }
        } catch (_: IOException) {
            // Broken pipe or a bad frame: handled as a dropped connection below.
        }
        close()
        return null
    }

    /** [json] as an object, or `null` when it isn't one (such a frame is skipped). */
    private fun parseObject(json: String): JsonObject? = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
