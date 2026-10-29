package com.dyetracker.discord

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PresenceSync.reconcile] against [FakeDiscordServer]: sends only on change, clears and closes on null. */
@EnabledOnOs(OS.LINUX, OS.MAC)
@Timeout(TEST_TIMEOUT_SECONDS)
class PresenceSyncTest {

    private lateinit var dir: Path
    private lateinit var socket: Path
    private lateinit var server: FakeDiscordServer
    private lateinit var client: DiscordIpcClient
    private lateinit var sync: PresenceSync

    @BeforeEach
    fun setUp() {
        dir = Files.createTempDirectory("dt-sync")
        socket = dir.resolve(SOCKET_NAME)
        server = FakeDiscordServer(socket)
        client = DiscordIpcClient(UnixIpcConnector { listOf(socket.toString()) }, APP_ID)
        sync = PresenceSync(client, PID)
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `the same card twice is sent once`() {
        sync.reconcile(TRACER_CARD)
        sync.reconcile(TRACER_CARD)

        val sent = server.setActivityArgs()
        assertEquals(1, sent.size)
        assertEquals(TRACER_CARD.toActivityJson(), sent.single().getValue("activity").jsonObject)
        assertEquals(TRACER_CARD, sync.lastSent)
    }

    @Test
    fun `a changed card is sent again`() {
        sync.reconcile(TRACER_CARD)
        val next = TRACER_CARD.copy(state = "Fishing")
        sync.reconcile(next)

        val sent = server.setActivityArgs()
        assertEquals(2, sent.size)
        assertEquals("Fishing", sent[1].getValue("activity").jsonObject.getValue("state").jsonPrimitive.content)
        assertEquals(next, sync.lastSent)
    }

    @Test
    fun `null clears the card once and closes the connection`() {
        sync.reconcile(TRACER_CARD)
        sync.reconcile(null)
        sync.reconcile(null)

        val sent = server.setActivityArgs()
        assertEquals(2, sent.size, "one card, one clear")
        assertEquals(JsonNull, sent[1]["activity"])
        assertFalse(client.isConnected)
        assertTrue(server.awaitClientClosed(), "the server saw the socket close")
        assertNull(sync.lastSent)
    }

    @Test
    fun `null while nothing was sent never connects`() {
        sync.reconcile(null)
        assertFalse(client.isConnected)
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun `a failed send forgets the card so the next reconcile resends`() {
        server.replyError = true
        sync.reconcile(TRACER_CARD)
        assertNull(sync.lastSent, "an ERROR reply is not remembered")

        server.replyError = false
        sync.reconcile(TRACER_CARD)

        assertEquals(2, server.setActivityArgs().size)
        assertEquals(TRACER_CARD, sync.lastSent)
    }

    @Test
    fun `no Discord means nothing is remembered and the next call retries`() {
        var attempts = 0
        val missing = UnixIpcConnector { listOf(dir.resolve("missing").toString()) }
        val offline = DiscordIpcClient(IpcConnector { attempts++; missing.connect() }, APP_ID)
        val offlineSync = PresenceSync(offline, PID)

        offlineSync.reconcile(TRACER_CARD)
        offlineSync.reconcile(TRACER_CARD)

        assertEquals(2, attempts, "each reconcile tries to connect again")
        assertNull(offlineSync.lastSent)
        assertFalse(offline.isConnected)
    }
}
