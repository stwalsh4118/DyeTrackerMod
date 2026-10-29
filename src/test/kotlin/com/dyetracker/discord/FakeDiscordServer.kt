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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A stand-in for the Discord app's IPC socket, for tests. Listens on the Unix socket at
 * [socketPath], accepts one connection on a daemon thread, records every frame it receives, and
 * answers HANDSHAKE with READY and each SET_ACTIVITY with a reply carrying the same nonce (an
 * `"evt":"ERROR"` reply while [replyError] is set).
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

    private val clientGone = CountDownLatch(1)
    private val server: ServerSocketChannel =
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socketPath))
    private val worker = thread(isDaemon = true, name = "fake-discord-ipc") { serve() }

    /** The JSON of every received frame with [op], parsed. */
    fun payloads(op: IpcOp): List<JsonObject> =
        received.filter { it.op == op.code }.map { Json.parseToJsonElement(it.json).jsonObject }

    /** The received SET_ACTIVITY commands' `args` objects, in order. */
    fun setActivityArgs(): List<JsonObject> =
        payloads(IpcOp.FRAME).filter { it.str("cmd") == "SET_ACTIVITY" }.map { it.getValue("args").jsonObject }

    /** Waits until the client has closed its end; true when it did within [timeoutMs]. */
    fun awaitClientClosed(timeoutMs: Long = AWAIT_MS): Boolean = clientGone.await(timeoutMs, TimeUnit.MILLISECONDS)

    override fun close() {
        server.close()
        worker.join(AWAIT_MS)
        Files.deleteIfExists(socketPath)
    }

    private fun serve() {
        try {
            server.accept().use { channel ->
                val input = Channels.newInputStream(channel)
                val output = Channels.newOutputStream(channel)
                while (true) {
                    val frame = IpcCodec.read(input) ?: break
                    received += frame
                    val (op, reply) = replyTo(frame) ?: continue
                    output.write(IpcCodec.encode(op, reply.toString()))
                    output.flush()
                }
            }
        } catch (_: IOException) {
            // Server closed before a client came, or the client went away mid-frame.
        } finally {
            clientGone.countDown()
        }
    }

    private fun replyTo(frame: IpcFrame): Pair<IpcOp, JsonObject>? {
        val json = Json.parseToJsonElement(frame.json).jsonObject
        return when {
            frame.op == IpcOp.HANDSHAKE.code && rejectHandshake -> IpcOp.CLOSE to buildJsonObject {
                put("code", INVALID_CLIENT_ID_CODE)
                put("message", "Invalid Client ID")
            }
            frame.op == IpcOp.HANDSHAKE.code -> IpcOp.FRAME to buildJsonObject {
                put("cmd", "DISPATCH")
                put("evt", "READY")
                putJsonObject("data") {}
            }
            frame.op == IpcOp.FRAME.code && json.str("cmd") == "SET_ACTIVITY" -> IpcOp.FRAME to buildJsonObject {
                put("cmd", "SET_ACTIVITY")
                put("nonce", json.getValue("nonce"))
                if (replyError) put("evt", "ERROR")
                putJsonObject("data") {}
            }
            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    private companion object {
        const val AWAIT_MS = 5_000L
        const val INVALID_CLIENT_ID_CODE = 4000
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
