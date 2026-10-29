package com.dyetracker.discord

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A stand-in for the Discord app's IPC socket, for tests. Listens on the Unix socket at
 * [socketPath] and serves one connection at a time on a daemon thread, accepting the next once a
 * client goes away (so reconnects can be tested). Records every frame it receives, answers
 * HANDSHAKE with READY, each SET_ACTIVITY with a reply carrying the same nonce (an
 * `"evt":"ERROR"` reply while [replyError] is set) and each PING with a PONG of the same JSON.
 */
class FakeDiscordServer(private val socketPath: Path) : Closeable {

    /** Every frame received, in order. */
    val received: MutableList<IpcFrame> = CopyOnWriteArrayList()

    /** When set, SET_ACTIVITY is answered with `"evt":"ERROR"`. */
    @Volatile
    var replyError: Boolean = false

    /** When set, HANDSHAKE is answered with a CLOSE frame (what Discord does for a bad client id). */
    @Volatile
    var rejectHandshake: Boolean = false

    /** When set, nothing is ever answered (a Discord that hangs). */
    @Volatile
    var silent: Boolean = false

    /** When set, HANDSHAKE is answered with a PING carrying this JSON; READY follows the client's PONG. */
    @Volatile
    var pingBeforeReady: String? = null

    /** When set, SET_ACTIVITY is answered with a CLOSE frame instead of a reply. */
    @Volatile
    var closeOnSetActivity: Boolean = false

    /** When set, a PING from the client is answered with a CLOSE frame instead of a PONG. */
    @Volatile
    var closeOnPing: Boolean = false

    /** How many connections were accepted so far. */
    val connections: Int get() = accepted.get()

    private val accepted = AtomicInteger()
    private val ended = AtomicInteger()

    @Volatile
    private var current: SocketChannel? = null

    @Volatile
    private var awaitingPong = false

    private val server: ServerSocketChannel =
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socketPath))
    private val worker = thread(isDaemon = true, name = "fake-discord-ipc") { serve() }

    /** The JSON of every received frame with [op], parsed. */
    fun payloads(op: IpcOp): List<JsonObject> =
        received.filter { it.op == op.code }.map { Json.parseToJsonElement(it.json).jsonObject }

    /** The received SET_ACTIVITY commands' `args` objects, in order. */
    fun setActivityArgs(): List<JsonObject> =
        payloads(IpcOp.FRAME).filter { it.str("cmd") == "SET_ACTIVITY" }.map { it.getValue("args").jsonObject }

    /**
     * Waits until a connection was accepted and every accepted one has ended; true when that
     * happened within [timeoutMs].
     */
    fun awaitClientClosed(timeoutMs: Long = AWAIT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (accepted.get() == 0 || ended.get() < accepted.get()) {
            if (System.currentTimeMillis() >= deadline) return false
            Thread.sleep(POLL_MS)
        }
        return true
    }

    /** Drops the current connection (a Discord restart) and keeps listening for the next. */
    fun dropClient() {
        current?.close()
        awaitClientClosed()
    }

    /** Stops listening and drops the current connection (Discord quit). The socket file is removed. */
    override fun close() {
        server.close()
        current?.close()
        worker.join(AWAIT_MS)
        Files.deleteIfExists(socketPath)
    }

    private fun serve() {
        while (true) {
            val channel = try {
                server.accept()
            } catch (_: IOException) {
                // The server was closed.
                return
            }
            current = channel
            accepted.incrementAndGet()
            handle(channel)
        }
    }

    private fun handle(channel: SocketChannel) {
        try {
            channel.use {
                val input = Channels.newInputStream(channel)
                val output = Channels.newOutputStream(channel)
                while (true) {
                    val frame = IpcCodec.read(input) ?: break
                    received += frame
                    val (op, reply) = replyTo(frame) ?: continue
                    output.write(IpcCodec.encode(op, reply))
                    output.flush()
                }
            }
        } catch (_: IOException) {
            // The client went away mid-frame, or the connection was dropped from the test.
        } finally {
            current = null
            awaitingPong = false
            ended.incrementAndGet()
        }
    }

    /** The opcode and JSON to answer [frame] with, or `null` to send nothing. */
    private fun replyTo(frame: IpcFrame): Pair<IpcOp, String>? {
        if (silent) return null
        val json = Json.parseToJsonElement(frame.json).jsonObject
        val isSetActivity = frame.op == IpcOp.FRAME.code && json.str("cmd") == "SET_ACTIVITY"
        val pingJson = pingBeforeReady
        return when {
            frame.op == IpcOp.HANDSHAKE.code && rejectHandshake -> IpcOp.CLOSE to closeFrame(INVALID_CLIENT_ID_CODE, "Invalid Client ID")
            frame.op == IpcOp.HANDSHAKE.code && pingJson != null -> {
                awaitingPong = true
                IpcOp.PING to pingJson
            }
            frame.op == IpcOp.HANDSHAKE.code -> IpcOp.FRAME to READY_JSON
            frame.op == IpcOp.PONG.code && awaitingPong -> {
                awaitingPong = false
                IpcOp.FRAME to READY_JSON
            }
            frame.op == IpcOp.PING.code && closeOnPing -> IpcOp.CLOSE to closeFrame(NORMAL_CLOSE_CODE, "Closed")
            frame.op == IpcOp.PING.code -> IpcOp.PONG to frame.json
            isSetActivity && closeOnSetActivity -> IpcOp.CLOSE to closeFrame(NORMAL_CLOSE_CODE, "Closed")
            isSetActivity -> IpcOp.FRAME to buildJsonObject {
                put("cmd", "SET_ACTIVITY")
                put("nonce", json.getValue("nonce"))
                if (replyError) put("evt", "ERROR")
                putJsonObject("data") {}
            }.toString()
            else -> null
        }
    }

    private fun closeFrame(code: Int, message: String): String = buildJsonObject {
        put("code", code)
        put("message", message)
    }.toString()

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    private companion object {
        const val AWAIT_MS = 5_000L
        const val POLL_MS = 5L
        const val INVALID_CLIENT_ID_CODE = 4000
        const val NORMAL_CLOSE_CODE = 1000
        val READY_JSON = buildJsonObject {
            put("cmd", "DISPATCH")
            put("evt", "READY")
            putJsonObject("data") {}
        }.toString()
    }
}

/** Per-test timeout for the socket tests, so a stuck read fails the test instead of hanging the build. */
internal const val TEST_TIMEOUT_SECONDS = 10L

/** Socket file name the fake server listens on, inside a per-test temp dir. */
internal const val SOCKET_NAME = "discord-ipc-0"

/** A made-up application id for the socket tests. */
internal const val APP_ID = "123456789012345678"

/** A made-up process id for the socket tests. */
internal const val PID = 4242L
