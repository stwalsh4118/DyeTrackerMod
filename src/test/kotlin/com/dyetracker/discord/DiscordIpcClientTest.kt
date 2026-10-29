package com.dyetracker.discord

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [DiscordIpcClient] against [FakeDiscordServer] over a real Unix socket in a temp dir. */
@EnabledOnOs(OS.LINUX, OS.MAC)
@Timeout(TEST_TIMEOUT_SECONDS)
class DiscordIpcClientTest {

    private lateinit var dir: Path
    private lateinit var socket: Path
    private lateinit var server: FakeDiscordServer

    @BeforeEach
    fun setUp() {
        dir = Files.createTempDirectory("dt-ipc")
        socket = dir.resolve(SOCKET_NAME)
        server = FakeDiscordServer(socket)
    }

    private val clients = mutableListOf<DiscordIpcClient>()

    @AfterEach
    fun tearDown() {
        clients.forEach { it.shutdown() }
        server.close()
        dir.toFile().deleteRecursively()
    }

    private fun client(appId: String = APP_ID, responseTimeoutMs: Long = IPC_RESPONSE_TIMEOUT_MS) =
        DiscordIpcClient(UnixIpcConnector { listOf(socket.toString()) }, appId, responseTimeoutMs).also { clients += it }

    @Test
    fun `connect handshakes with version 1 and the client id`() {
        val client = client()
        assertTrue(client.connect())
        assertTrue(client.isConnected)

        val handshake = server.payloads(IpcOp.HANDSHAKE).single()
        assertEquals(IPC_VERSION.toLong(), handshake.getValue("v").jsonPrimitive.long)
        assertEquals(APP_ID, handshake.getValue("client_id").jsonPrimitive.content)
        client.close()
    }

    @Test
    fun `setActivity sends the pid and the activity and returns true`() {
        val client = client()
        assertTrue(client.connect())

        assertTrue(client.setActivity(PID, buildJsonObject { put("details", "Tracking dyes") }))

        val args = server.setActivityArgs().single()
        assertEquals(PID, args.getValue("pid").jsonPrimitive.long)
        assertEquals("Tracking dyes", args.getValue("activity").jsonObject.getValue("details").jsonPrimitive.content)
        client.close()
    }

    @Test
    fun `setActivity with null sends activity null`() {
        val client = client()
        assertTrue(client.connect())

        assertTrue(client.setActivity(PID, null))

        assertEquals(JsonNull, server.setActivityArgs().single()["activity"])
        client.close()
    }

    @Test
    fun `each setActivity carries a fresh nonce`() {
        val client = client()
        assertTrue(client.connect())
        assertTrue(client.setActivity(PID, null))
        assertTrue(client.setActivity(PID, null))

        val nonces = server.payloads(IpcOp.FRAME).map { it.getValue("nonce").jsonPrimitive.content }
        assertEquals(2, nonces.toSet().size)
        client.close()
    }

    @Test
    fun `an ERROR reply makes setActivity return false`() {
        server.replyError = true
        val client = client()
        assertTrue(client.connect())

        assertFalse(client.setActivity(PID, buildJsonObject { put("details", "x") }))
        client.close()
    }

    @Test
    fun `an ERROR reply leaves Discord's message in lastError`() {
        server.replyError = true
        server.errorMessage = "Invalid button URL"
        val client = client()
        assertTrue(client.connect())
        assertNull(client.lastError, "nothing was rejected yet")

        assertFalse(client.setActivity(PID, buildJsonObject { put("details", "x") }))

        assertEquals("Invalid button URL", client.lastError)
        client.close()
    }

    @Test
    fun `an accepted setActivity clears lastError`() {
        server.replyError = true
        val client = client()
        assertTrue(client.connect())
        assertFalse(client.setActivity(PID, buildJsonObject { put("details", "x") }))

        server.replyError = false
        assertTrue(client.setActivity(PID, buildJsonObject { put("details", "x") }))

        assertNull(client.lastError)
        client.close()
    }

    @Test
    fun `a setActivity that never reaches Discord clears lastError`() {
        server.replyError = true
        val client = client()
        assertTrue(client.connect())
        assertFalse(client.setActivity(PID, buildJsonObject { put("details", "x") }))
        client.close()

        assertFalse(client.setActivity(PID, buildJsonObject { put("details", "x") }))

        assertNull(client.lastError, "a dropped send is not a rejection")
    }

    @Test
    fun `an ERROR reply without a message leaves the whole reply in lastError`() {
        server.replyError = true
        server.errorMessage = null
        val client = client()
        assertTrue(client.connect())

        assertFalse(client.setActivity(PID, buildJsonObject { put("details", "x") }))

        val error = client.lastError
        assertTrue(error != null && "\"evt\":\"ERROR\"" in error, "lastError was $error")
        client.close()
    }

    @Test
    fun `setActivity without a connection returns false`() {
        assertFalse(client().setActivity(PID, null))
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun `close drops the connection`() {
        val client = client()
        assertTrue(client.connect())
        client.close()
        assertFalse(client.isConnected)
        assertTrue(server.awaitClientClosed(), "the server saw the socket close")
        assertFalse(client.setActivity(PID, null))
    }

    @Test
    fun `a blank app id never calls the connector`() {
        var calls = 0
        val client = DiscordIpcClient(IpcConnector { calls++; null }, " ")
        assertFalse(client.connect())
        assertEquals(0, calls)
        assertFalse(client.isConnected)
    }

    @Test
    fun `no socket file means connect fails`() {
        val client = DiscordIpcClient(UnixIpcConnector { listOf(dir.resolve("missing").toString()) }, APP_ID)
        assertFalse(client.connect())
        assertFalse(client.isConnected)
    }

    @Test
    fun `the connector skips candidates that do not open`() {
        val connector = UnixIpcConnector { listOf(dir.resolve("missing").toString(), socket.toString()) }
        val client = DiscordIpcClient(connector, APP_ID)
        assertTrue(client.connect())
        client.close()
    }

    @Test
    fun `a CLOSE reply to the handshake fails the connect and drops the socket`() {
        server.rejectHandshake = true
        val client = client()
        assertFalse(client.connect())
        assertFalse(client.isConnected)
        assertTrue(server.awaitClientClosed(), "the client closed its end")
    }

    @Test
    fun `a Discord that never answers the handshake times out`() {
        server.silent = true
        val client = client(responseTimeoutMs = SHORT_TIMEOUT_MS)

        val started = System.nanoTime()
        assertFalse(client.connect())
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertFalse(client.isConnected)
        assertTrue(elapsedMs < SHORT_TIMEOUT_MS + TIMEOUT_SLACK_MS, "gave up after ${elapsedMs}ms")
        assertTrue(server.awaitClientClosed(), "the timed-out socket was closed")
    }

    @Test
    fun `a Discord that never answers SET_ACTIVITY times out and disconnects`() {
        val client = client(responseTimeoutMs = SHORT_TIMEOUT_MS)
        assertTrue(client.connect())
        server.silent = true

        assertFalse(client.setActivity(PID, null))
        assertFalse(client.isConnected)
    }

    @Test
    fun `a PING before READY is answered with a PONG of the same JSON`() {
        server.pingBeforeReady = """{"x":1}"""
        val client = client(responseTimeoutMs = SHORT_TIMEOUT_MS)

        assertTrue(client.connect(), "READY only comes after the PONG")

        val pong = server.received.single { it.op == IpcOp.PONG.code }
        assertEquals("""{"x":1}""", pong.json)
    }

    @Test
    fun `a CLOSE instead of a SET_ACTIVITY reply returns false and disconnects`() {
        server.closeOnSetActivity = true
        val client = client()
        assertTrue(client.connect())

        assertFalse(client.setActivity(PID, buildJsonObject { put("details", "x") }))
        assertFalse(client.isConnected)
        assertTrue(server.awaitClientClosed())
    }

    @Test
    fun `ping is true when Discord answers PONG`() {
        val client = client()
        assertTrue(client.connect())

        assertTrue(client.ping())
        assertTrue(client.isConnected)
        val ping = server.payloads(IpcOp.PING).single()
        assertTrue(ping.getValue("nonce").jsonPrimitive.content.isNotBlank(), "the PING carries a nonce")
    }

    @Test
    fun `ping is false and disconnects when Discord answers CLOSE`() {
        server.closeOnPing = true
        val client = client()
        assertTrue(client.connect())

        assertFalse(client.ping())
        assertFalse(client.isConnected)
    }

    @Test
    fun `ping is false when Discord went away`() {
        val client = client(responseTimeoutMs = SHORT_TIMEOUT_MS)
        assertTrue(client.connect())
        server.dropClient()

        assertFalse(client.ping())
        assertFalse(client.isConnected)
    }

    @Test
    fun `ping is false without a connection`() {
        assertFalse(client().ping())
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun `clearAndClose clears the card then closes`() {
        val client = client()
        assertTrue(client.connect())

        client.clearAndClose(PID)

        assertEquals(JsonNull, server.setActivityArgs().single()["activity"])
        assertFalse(client.isConnected)
        assertTrue(server.awaitClientClosed())
    }

    @Test
    fun `clearAndClose without a connection sends nothing`() {
        client().clearAndClose(PID)
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun `a close during the connector call closes the socket it returns`() {
        lateinit var client: DiscordIpcClient
        val unix = UnixIpcConnector { listOf(socket.toString()) }
        // stop() lands while the worker is inside the connector.
        client = DiscordIpcClient(IpcConnector { client.close(); unix.connect() }, APP_ID).also { clients += it }

        assertFalse(client.connect())
        assertFalse(client.isConnected)
        assertTrue(server.awaitClientClosed(), "the late socket was closed")
        assertTrue(server.received.isEmpty(), "no handshake on the late socket")
    }

    @Test
    fun `the client reconnects after a close`() {
        val client = client()
        assertTrue(client.connect())
        client.close()
        assertTrue(server.awaitClientClosed())

        assertTrue(client.connect())
        assertEquals(2, server.connections)
    }

    @Test
    fun `after shutdown connect is false and opens no connection`() {
        var calls = 0
        val unix = UnixIpcConnector { listOf(socket.toString()) }
        val client = DiscordIpcClient(IpcConnector { calls++; unix.connect() }, APP_ID).also { clients += it }
        assertTrue(client.connect())
        assertEquals(1, calls)

        client.shutdown()
        assertTrue(server.awaitClientClosed(), "shutdown closed the connection")

        assertFalse(client.connect())
        assertFalse(client.isConnected)
        assertEquals(1, calls, "the connector is not called after shutdown")
        assertEquals(1, server.connections, "the server saw no new connection")
    }

    @Test
    fun `a shutdown during the connector call closes the socket it returns`() {
        lateinit var client: DiscordIpcClient
        val unix = UnixIpcConnector { listOf(socket.toString()) }
        client = DiscordIpcClient(IpcConnector { client.shutdown(); unix.connect() }, APP_ID).also { clients += it }

        assertFalse(client.connect())
        assertFalse(client.isConnected)
        assertTrue(server.awaitClientClosed(), "the late socket was closed")
        assertTrue(server.received.isEmpty(), "no handshake on the late socket")
        assertFalse(client.connect(), "still shut down")
        assertEquals(1, server.connections)
    }

    @Test
    fun `a failed ping after shutdown does not reconnect through reconcile`() {
        var now = 0L
        val client = client()
        val sync = PresenceSync(client, PID, ReconnectBackoff(), clock = { now }, log = {})
        sync.reconcile(TEST_CARD)
        assertTrue(client.isConnected)
        assertEquals(1, server.connections)

        client.shutdown()
        assertFalse(client.ping(), "no ping on a shut-down client")
        now += IPC_HEALTH_CHECK_MS + RECONNECT_MAX_MS
        sync.reconcile(TEST_CARD)

        assertFalse(client.isConnected)
        assertEquals(1, server.connections, "reconcile did not reconnect")
        assertEquals(1, server.setActivityArgs().size, "nothing more was sent")
    }

    private companion object {
        const val SHORT_TIMEOUT_MS = 300L
        const val TIMEOUT_SLACK_MS = 1_000L
    }
}
