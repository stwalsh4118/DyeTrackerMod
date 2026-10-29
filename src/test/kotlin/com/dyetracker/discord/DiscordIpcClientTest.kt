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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @AfterEach
    fun tearDown() {
        server.close()
        dir.toFile().deleteRecursively()
    }

    private fun client(appId: String = APP_ID) =
        DiscordIpcClient(UnixIpcConnector { listOf(socket.toString()) }, appId)

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
}
