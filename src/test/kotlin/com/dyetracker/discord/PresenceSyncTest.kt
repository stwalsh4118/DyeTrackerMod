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

/**
 * [PresenceSync.reconcile] against [FakeDiscordServer]: sends only on change, clears and closes on
 * null, backs off failed connects, pings an idle connection and resends the card after a
 * reconnect. Time is an injected clock ([now]).
 */
@EnabledOnOs(OS.LINUX, OS.MAC)
@Timeout(TEST_TIMEOUT_SECONDS)
class PresenceSyncTest {

    private lateinit var dir: Path
    private lateinit var socket: Path
    private lateinit var server: FakeDiscordServer
    private lateinit var client: DiscordIpcClient
    private lateinit var sync: PresenceSync
    private var now = 0L
    private val logged = mutableListOf<String>()
    private val extraClients = mutableListOf<DiscordIpcClient>()

    @BeforeEach
    fun setUp() {
        dir = Files.createTempDirectory("dt-sync")
        socket = dir.resolve(SOCKET_NAME)
        server = FakeDiscordServer(socket)
        client = DiscordIpcClient(UnixIpcConnector { listOf(socket.toString()) }, APP_ID, SHORT_TIMEOUT_MS)
        sync = syncFor(client)
    }

    @AfterEach
    fun tearDown() {
        (extraClients + client).forEach { it.close(); it.shutdownWatchdog() }
        server.close()
        dir.toFile().deleteRecursively()
    }

    private fun syncFor(client: DiscordIpcClient) =
        PresenceSync(client, PID, ReconnectBackoff(), clock = { now }, log = { logged += it })

    /** A client whose connector finds no Discord, counting how often it is asked. */
    private class Offline(val client: DiscordIpcClient, val attempts: () -> Int)

    private fun offline(): Offline {
        var attempts = 0
        val client = DiscordIpcClient(IpcConnector { attempts++; null }, APP_ID).also { extraClients += it }
        return Offline(client) { attempts }
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
    fun `no Discord means nothing is remembered and the client stays disconnected`() {
        val offline = offline()
        val offlineSync = syncFor(offline.client)

        offlineSync.reconcile(TRACER_CARD)

        assertEquals(1, offline.attempts())
        assertNull(offlineSync.lastSent)
        assertFalse(offline.client.isConnected)
    }

    @Test
    fun `failed connects back off 5 s then 10 s`() {
        val offline = offline()
        val offlineSync = syncFor(offline.client)
        val attemptsAt = mutableMapOf<Long, Int>()
        for (t in listOf(0L, 1_000L, 4_999L, 5_000L, 6_000L, 14_999L, 15_000L)) {
            now = t
            val before = offline.attempts()
            offlineSync.reconcile(TRACER_CARD)
            attemptsAt[t] = offline.attempts() - before
        }

        assertEquals(
            mapOf(0L to 1, 1_000L to 0, 4_999L to 0, 5_000L to 1, 6_000L to 0, 14_999L to 0, 15_000L to 1),
            attemptsAt,
        )
    }

    @Test
    fun `null while disconnected never calls the connector and resets the backoff`() {
        val offline = offline()
        val offlineSync = syncFor(offline.client)
        offlineSync.reconcile(TRACER_CARD)
        assertEquals(1, offline.attempts())

        now = 1_000
        offlineSync.reconcile(null)
        offlineSync.reconcile(null)
        assertEquals(1, offline.attempts(), "no connect just to clear")

        now = 1_001
        offlineSync.reconcile(TRACER_CARD)
        assertEquals(2, offline.attempts(), "the backoff was reset, so a card connects at once")
    }

    @Test
    fun `the first failure of an outage logs once and the reconnect logs connected`() {
        server.close()
        sync.reconcile(TRACER_CARD)
        now = 5_000
        sync.reconcile(TRACER_CARD)
        now = 15_000
        sync.reconcile(TRACER_CARD)
        assertEquals(listOf("Discord not reachable; will keep retrying quietly"), logged)

        server = FakeDiscordServer(socket)
        now = 35_000
        sync.reconcile(TRACER_CARD)

        assertEquals(
            listOf("Discord not reachable; will keep retrying quietly", "Discord presence connected"),
            logged,
        )
        assertEquals(TRACER_CARD, sync.lastSent)
    }

    @Test
    fun `a first connect without a failure logs nothing`() {
        sync.reconcile(TRACER_CARD)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `Discord quitting and coming back later resends the card once after the backoff`() {
        sync.reconcile(TRACER_CARD)
        server.close()

        now = IPC_HEALTH_CHECK_MS
        sync.reconcile(TRACER_CARD)
        assertFalse(client.isConnected, "the health ping noticed the quit")
        assertNull(sync.lastSent)

        server = FakeDiscordServer(socket)
        now = IPC_HEALTH_CHECK_MS + RECONNECT_INITIAL_MS - 1
        sync.reconcile(TRACER_CARD)
        assertEquals(0, server.connections, "still backing off")

        now = IPC_HEALTH_CHECK_MS + RECONNECT_INITIAL_MS
        sync.reconcile(TRACER_CARD)
        now += 1
        sync.reconcile(TRACER_CARD)

        assertEquals(TRACER_CARD.toActivityJson(), server.setActivityArgs().single().getValue("activity").jsonObject)
        assertEquals(TRACER_CARD, sync.lastSent)
    }

    @Test
    fun `an idle connection is pinged once per 30 s`() {
        sync.reconcile(TRACER_CARD)

        now = IPC_HEALTH_CHECK_MS - 1
        sync.reconcile(TRACER_CARD)
        assertTrue(server.payloads(IpcOp.PING).isEmpty(), "not idle long enough")

        now = IPC_HEALTH_CHECK_MS
        sync.reconcile(TRACER_CARD)
        now += 1
        sync.reconcile(TRACER_CARD)

        assertEquals(1, server.payloads(IpcOp.PING).size)
        assertEquals(TRACER_CARD, sync.lastSent, "a PONG keeps the card")
        assertEquals(1, server.setActivityArgs().size, "nothing resent")
    }

    @Test
    fun `a send counts as contact and delays the ping`() {
        sync.reconcile(TRACER_CARD)
        now = IPC_HEALTH_CHECK_MS - 1
        sync.reconcile(TRACER_CARD.copy(state = "Fishing"))

        now = IPC_HEALTH_CHECK_MS
        sync.reconcile(TRACER_CARD.copy(state = "Fishing"))

        assertTrue(server.payloads(IpcOp.PING).isEmpty())
    }

    @Test
    fun `a dropped connection fails the ping and the card is resent after reconnect`() {
        sync.reconcile(TRACER_CARD)
        server.dropClient()

        now = IPC_HEALTH_CHECK_MS
        sync.reconcile(TRACER_CARD)

        assertEquals(2, server.connections)
        assertEquals(2, server.setActivityArgs().size, "the card was sent again on the new connection")
        assertEquals(TRACER_CARD, sync.lastSent)
        assertTrue(client.isConnected)
    }

    @Test
    fun `a send on a dropped connection forgets the card and the next reconcile reconnects and resends`() {
        sync.reconcile(TRACER_CARD)
        server.dropClient()
        val next = TRACER_CARD.copy(state = "Fishing")

        sync.reconcile(next)
        assertNull(sync.lastSent)
        assertFalse(client.isConnected)

        sync.reconcile(next)
        assertEquals(next, sync.lastSent)
        assertEquals(2, server.connections)
    }

    private companion object {
        const val SHORT_TIMEOUT_MS = 500L
    }
}
