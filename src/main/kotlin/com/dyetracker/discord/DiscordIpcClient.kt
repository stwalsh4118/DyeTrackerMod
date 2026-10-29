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
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** JSON keys and values of the Discord RPC messages this client sends and reads. */
private const val KEY_EVT = "evt"
private const val KEY_NONCE = "nonce"
private const val EVT_READY = "READY"
private const val EVT_ERROR = "ERROR"
private const val KEY_DATA = "data"
private const val KEY_MESSAGE = "message"
private const val CMD_SET_ACTIVITY = "SET_ACTIVITY"

/** Name of the client's watchdog thread, which closes a connection Discord stopped answering. */
private const val WATCHDOG_THREAD_NAME = "DyeTracker-DiscordWatchdog"

/**
 * A minimal Discord IPC client: handshake, then SET_ACTIVITY and PING requests, each answered
 * before the next is sent. Strictly request/response on the caller's thread and **not
 * thread-safe**: only the presence worker uses it (see [DiscordPresence]). Every failure comes
 * back as `false`, never as an exception; an I/O failure, a CLOSE from Discord or no answer within
 * [responseTimeoutMs] also drops the connection. A PING from Discord while a reply is awaited is
 * answered with a PONG.
 *
 * Every wait for a reply is guarded by a watchdog thread that closes the channel at
 * [responseTimeoutMs]; on Unix that unblocks the read. On Windows the pipe input itself gives up
 * by then (see [WindowsPipeConnector]), since closing a pipe there does not cancel a read.
 *
 * @param connector opens the channel to the Discord app.
 * @param applicationId the Discord application id; blank means [connect] always fails.
 * @param responseTimeoutMs how long a request may wait for Discord's answer.
 */
class DiscordIpcClient(
    private val connector: IpcConnector,
    private val applicationId: String,
    private val responseTimeoutMs: Long = IPC_RESPONSE_TIMEOUT_MS,
) {

    /** Volatile only so [close] from another thread (game shutdown) is seen by the worker. */
    @Volatile
    private var channel: IpcChannel? = null

    /** Guards [channel] being set against [close], and [closes]. */
    private val channelLock = Any()

    /** Counts [close] calls, so a connect that a close overtook can tell (see [connect]). */
    private var closes = 0L

    /** Guards [watchdog]. */
    private val watchdogLock = Any()

    /** Created on first use; never again after [shutdown]. */
    private var watchdog: ScheduledExecutorService? = null

    /** Set for good by [shutdown]: from then on [connect] fails without calling the connector. */
    @Volatile
    private var shutDown = false

    /**
     * Why Discord rejected the last [setActivity]: the `data.message` of its ERROR answer (the
     * whole answer when it has none). `null` when the last [setActivity] was accepted, never
     * answered or never made. Like the rest of the client, only the presence worker reads it.
     */
    var lastError: String? = null
        private set

    /** Whether a handshaken connection is open. */
    val isConnected: Boolean get() = channel != null

    /**
     * Opens the connection and handshakes, returning `true` once Discord answers READY (or at once
     * when already connected). A blank [applicationId] returns `false` without calling the
     * connector; no Discord, a CLOSE, EOF, a timeout or an I/O error return `false` with nothing
     * left open. A [close] that lands while the connector runs (game shutdown) wins: the channel it
     * returns is closed and `false` returned. After [shutdown] it returns `false` without calling
     * the connector, and a shutdown that lands while the connector runs closes the late channel.
     */
    fun connect(): Boolean {
        if (applicationId.isBlank() || shutDown) return false
        if (isConnected) return true
        val closesBefore = synchronized(channelLock) { closes }
        val opened = connector.connect() ?: return false
        val kept = synchronized(channelLock) {
            (!shutDown && closes == closesBefore).also { if (it) channel = opened }
        }
        if (!kept) {
            closeQuietly(opened)
            return false
        }
        val handshake = buildJsonObject {
            put("v", IPC_VERSION)
            put("client_id", applicationId)
        }
        return exchange(opened, IpcOp.HANDSHAKE, handshake.toString(), IpcOp.FRAME) { it.string(KEY_EVT) == EVT_READY } != null
    }

    /**
     * Sets the card to [activity], or clears it when `null`, for process [pid]. Returns `true` when
     * Discord accepts it and `false` when it answers ERROR (its reason goes in [lastError]), when
     * not connected, or when the connection fails (which also closes it).
     */
    fun setActivity(pid: Long, activity: JsonObject?): Boolean {
        lastError = null
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
        val reply = exchange(open, IpcOp.FRAME, request.toString(), IpcOp.FRAME) { it.string(KEY_NONCE) == nonce } ?: return false
        if (reply.string(KEY_EVT) != EVT_ERROR) return true
        lastError = (reply[KEY_DATA] as? JsonObject)?.string(KEY_MESSAGE) ?: reply.toString()
        return false
    }

    /**
     * Checks the connection is alive: sends a PING carrying a fresh nonce and waits for the PONG,
     * skipping other frames. Returns `true` on the PONG and `false` when not connected or on a
     * CLOSE, EOF, timeout or I/O error (which also close the connection).
     */
    fun ping(): Boolean {
        val open = channel ?: return false
        val request = buildJsonObject { put(KEY_NONCE, UUID.randomUUID().toString()) }
        return exchange(open, IpcOp.PING, request.toString(), IpcOp.PONG) { true } != null
    }

    /** Clears the card for process [pid] when connected (a failure is ignored), then closes. */
    fun clearAndClose(pid: Long) {
        if (isConnected) setActivity(pid, null)
        close()
    }

    /**
     * Closes the connection, ignoring errors. [isConnected] is `false` afterwards, and a [connect]
     * whose connector is still running when this lands fails. The one call that may come from
     * another thread: on Unix closing the channel also unblocks a read the worker is in.
     */
    fun close() {
        val open = synchronized(channelLock) {
            closes++
            channel.also { channel = null }
        } ?: return
        closeQuietly(open)
    }

    /**
     * Shuts the client down for good (game shutdown): closes the connection and stops the watchdog
     * thread. Afterwards [connect] always fails without calling the connector, and the watchdog is
     * never started again. Safe from another thread, like [close].
     */
    fun shutdown() {
        shutDown = true
        close()
        synchronized(watchdogLock) {
            watchdog?.shutdownNow()
            watchdog = null
        }
    }

    /**
     * Sends [request] as an [op] frame, then reads frames until a [replyOp] frame whose JSON
     * [matches], answering PINGs and skipping the rest. Returns that JSON, or `null` after closing
     * the connection on a CLOSE frame, EOF, an I/O error or no match within [responseTimeoutMs].
     */
    private fun exchange(
        open: IpcChannel,
        op: IpcOp,
        request: String,
        replyOp: IpcOp,
        matches: (JsonObject) -> Boolean,
    ): JsonObject? {
        val timer = scheduleClose(open)
        if (timer == null) {
            close()
            return null
        }
        val reply = try {
            send(open, op, request)
            awaitReply(open, replyOp, matches)
        } catch (_: IOException) {
            // Broken pipe, a bad frame or the watchdog's close: handled as a dropped connection below.
            null
        }
        // A timer that can no longer be cancelled has fired: the channel is gone even if a reply slipped in.
        val timedOut = !timer.cancel(false)
        if (reply == null || timedOut) {
            close()
            return null
        }
        return reply
    }

    private fun send(open: IpcChannel, op: IpcOp, json: String) {
        open.output.write(IpcCodec.encode(op, json))
        open.output.flush()
    }

    /** Reads until a [replyOp] frame whose JSON [matches]; `null` on a CLOSE frame or EOF. */
    private fun awaitReply(open: IpcChannel, replyOp: IpcOp, matches: (JsonObject) -> Boolean): JsonObject? {
        while (true) {
            val frame = IpcCodec.read(open.input) ?: return null
            when (frame.op) {
                IpcOp.CLOSE.code -> return null
                IpcOp.PING.code -> send(open, IpcOp.PONG, frame.json)
                replyOp.code -> parseObject(frame.json)?.takeIf(matches)?.let { return it }
            }
        }
    }

    /** Closes [open] after [responseTimeoutMs] unless cancelled; `null` when the watchdog can't take it. */
    private fun scheduleClose(open: IpcChannel): ScheduledFuture<*>? = try {
        watchdog().schedule({ closeQuietly(open) }, responseTimeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: RejectedExecutionException) {
        // Shut down (game shutdown) before or during this request, so no request is guarded.
        null
    }

    /** The watchdog, started on first use; throws [RejectedExecutionException] after [shutdown]. */
    private fun watchdog(): ScheduledExecutorService = synchronized(watchdogLock) {
        if (shutDown) throw RejectedExecutionException("Discord IPC client is shut down")
        watchdog ?: Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, WATCHDOG_THREAD_NAME).apply { isDaemon = true }
        }.also { watchdog = it }
    }

    private fun closeQuietly(open: IpcChannel) {
        try {
            open.close()
        } catch (_: IOException) {
            // Already broken; nothing more to release.
        }
    }

    /** [json] as an object, or `null` when it isn't one (such a frame is skipped). */
    private fun parseObject(json: String): JsonObject? = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
