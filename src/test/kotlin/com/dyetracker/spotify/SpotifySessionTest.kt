package com.dyetracker.spotify

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit coverage for [SpotifySession] (task 76-2) against a local JDK [HttpServer] fake, an
 * in-memory store and a fake clock: refresh before expiry and on a 401 (once, no loop), the
 * rotated refresh token, invalid_grant ending the session, other refresh failures keeping the
 * tokens, logout, and the status.
 */
class SpotifySessionTest {

    private class MemoryStore(@Volatile var file: SpotifyAuthFile) : AuthStorePort {
        override fun get(): SpotifyAuthFile = file
        override fun update(f: (SpotifyAuthFile) -> SpotifyAuthFile) {
            file = f(file)
        }
    }

    /**
     * One fake endpoint: answers with queued (status, body) pairs, the last one repeating; a 500
     * when none were given (an endpoint the test expects untouched). [beforeReply] runs once the
     * request has arrived, before the reply: a store change made while the call is in flight.
     */
    private class Endpoint(vararg replies: Pair<Int, String?>) {
        val replies = ConcurrentLinkedQueue(replies.toList())
        val calls = AtomicInteger()
        val authorizations = CopyOnWriteArrayList<String?>()
        val bodies = CopyOnWriteArrayList<String>()

        @Volatile var beforeReply: () -> Unit = {}

        fun next(): Pair<Int, String?> = (if (replies.size > 1) replies.poll() else replies.peek()) ?: (500 to null)
    }

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    private val base: String get() = "http://127.0.0.1:${server.address.port}"
    private var nowMs = NOW
    private val store = MemoryStore(CONNECTED_FILE)
    private val session: SpotifySession by lazy {
        SpotifySession(SpotifyHttp(tokenUrl = "$base$TOKEN_PATH", apiBaseUrl = "$base/v1"), store) { nowMs }
    }

    @AfterTest
    fun stop() = server.stop(0)

    /** Serve [token] on the token path and [api] on the currently-playing path, then start. */
    private fun serve(token: Endpoint = Endpoint(500 to "{}"), api: Endpoint = Endpoint(204 to null)) {
        for ((path, endpoint) in listOf(TOKEN_PATH to token, PLAYING_PATH to api)) {
            server.createContext(path) { exchange ->
                endpoint.calls.incrementAndGet()
                endpoint.authorizations.add(exchange.requestHeaders.getFirst("Authorization"))
                endpoint.bodies.add(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8))
                endpoint.beforeReply()
                val (status, body) = endpoint.next()
                if (body == null) {
                    exchange.sendResponseHeaders(status, -1)
                } else {
                    val bytes = body.toByteArray(StandardCharsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                exchange.close()
            }
        }
        server.start()
    }

    private fun form(body: String): Map<String, String> = body.split('&').associate { pair ->
        val (k, v) = pair.split('=', limit = 2)
        URLDecoder.decode(k, StandardCharsets.UTF_8) to URLDecoder.decode(v, StandardCharsets.UTF_8)
    }

    @Test
    fun `a valid token makes one API call and no refresh`() {
        val token = Endpoint()
        val api = Endpoint(200 to PLAYING_BODY)
        serve(token, api)

        val result = session.currentlyPlaying()

        assertIs<SpotifyResult.Success<CurrentlyPlaying?>>(result)
        assertEquals("Song", result.data?.item?.name)
        assertEquals(1, api.calls.get())
        assertEquals(0, token.calls.get())
        assertEquals(listOf<String?>("Bearer OLD_ACCESS"), api.authorizations)
    }

    @Test
    fun `a token just outside the refresh margin is not refreshed`() {
        store.file = CONNECTED_FILE.copy(expiresAtMs = NOW + SpotifyConstants.REFRESH_MARGIN_MS + 1)
        val token = Endpoint()
        val api = Endpoint(204 to null)
        serve(token, api)

        assertEquals(SpotifyResult.Success(null), session.currentlyPlaying())
        assertEquals(0, token.calls.get())
    }

    @Test
    fun `a token inside the refresh margin is refreshed before the API call`() {
        store.file = CONNECTED_FILE.copy(expiresAtMs = NOW + 60_000)
        val token = Endpoint(200 to REFRESH_NO_ROTATION)
        val api = Endpoint(204 to null)
        serve(token, api)

        val result = session.currentlyPlaying()

        assertEquals(SpotifyResult.Success(null), result)
        assertEquals(1, token.calls.get())
        assertEquals(
            mapOf("grant_type" to "refresh_token", "refresh_token" to "OLD_REFRESH", "client_id" to CLIENT_ID),
            form(token.bodies.single()),
        )
        assertEquals(listOf<String?>("Bearer NEW_ACCESS"), api.authorizations)
        assertEquals("NEW_ACCESS", store.file.accessToken)
        assertEquals(NOW + 3_600_000, store.file.expiresAtMs)
    }

    @Test
    fun `a refresh without refresh_token keeps the old one`() {
        store.file = CONNECTED_FILE.copy(expiresAtMs = NOW)
        serve(token = Endpoint(200 to REFRESH_NO_ROTATION))

        session.currentlyPlaying()

        assertEquals("OLD_REFRESH", store.file.refreshToken)
    }

    @Test
    fun `a refresh with refresh_token stores the new one`() {
        store.file = CONNECTED_FILE.copy(expiresAtMs = NOW)
        serve(token = Endpoint(200 to REFRESH_ROTATED))

        session.currentlyPlaying()

        assertEquals("NEW_REFRESH", store.file.refreshToken)
        assertEquals("NEW_ACCESS", store.file.accessToken)
    }

    @Test
    fun `an API 401 refreshes once and retries once`() {
        val token = Endpoint(200 to REFRESH_NO_ROTATION)
        val api = Endpoint(401 to EXPIRED_BODY, 200 to PLAYING_BODY)
        serve(token, api)

        val result = session.currentlyPlaying()

        assertIs<SpotifyResult.Success<CurrentlyPlaying?>>(result)
        assertEquals("Song", result.data?.item?.name)
        assertEquals(1, token.calls.get())
        assertEquals(listOf<String?>("Bearer OLD_ACCESS", "Bearer NEW_ACCESS"), api.authorizations)
    }

    @Test
    fun `a second API 401 is returned without looping`() {
        val token = Endpoint(200 to REFRESH_NO_ROTATION)
        val api = Endpoint(401 to EXPIRED_BODY)
        serve(token, api)

        val result = session.currentlyPlaying()

        assertEquals(SpotifyResult.Failure(401, "The access token expired"), result)
        assertEquals(2, api.calls.get())
        assertEquals(1, token.calls.get())
    }

    @Test
    fun `a refresh failure after an API 401 is returned without a retry`() {
        val token = Endpoint(500 to """{"error":"server_error"}""")
        val api = Endpoint(401 to EXPIRED_BODY)
        serve(token, api)

        val result = session.currentlyPlaying()

        assertIs<SpotifyResult.Failure>(result)
        assertEquals(500, result.status)
        assertEquals(1, api.calls.get())
        assertEquals(1, token.calls.get())
    }

    @Test
    fun `refresh invalid_grant clears the tokens and keeps the client ID`() {
        store.file = CONNECTED_FILE.copy(expiresAtMs = NOW)
        val api = Endpoint(204 to null)
        serve(token = Endpoint(400 to """{"error":"invalid_grant","error_description":"Refresh token revoked"}"""), api = api)

        val result = session.currentlyPlaying()

        assertEquals(SpotifyResult.Failure(401, "Spotify session ended, log in again"), result)
        assertEquals(SpotifyAuthFile(clientId = CLIENT_ID), store.file)
        assertEquals(SessionStatus.LOGGED_OUT, session.status())
        assertEquals(0, api.calls.get())
    }

    @Test
    fun `refresh 500 keeps the tokens and returns the failure`() {
        val expired = CONNECTED_FILE.copy(expiresAtMs = NOW)
        store.file = expired
        val api = Endpoint(204 to null)
        serve(token = Endpoint(500 to "oops"), api = api)

        val result = session.currentlyPlaying()

        assertEquals(SpotifyResult.Failure(500, "HTTP 500"), result)
        assertEquals(expired, store.file)
        assertEquals(SessionStatus.CONNECTED, session.status())
        assertEquals(0, api.calls.get())
    }

    @Test
    fun `a logout during a refresh stays logged out`() {
        store.file = CONNECTED_FILE.copy(expiresAtMs = NOW)
        val token = Endpoint(200 to REFRESH_ROTATED).apply {
            beforeReply = { store.file = SpotifyAuthFile(clientId = CLIENT_ID) }
        }
        val api = Endpoint(204 to null)
        serve(token, api)

        val result = session.currentlyPlaying()

        assertEquals(SpotifyResult.Failure(401, "Not logged in"), result)
        assertEquals(SpotifyAuthFile(clientId = CLIENT_ID), store.file)
        assertEquals(SessionStatus.LOGGED_OUT, session.status())
        assertEquals(0, api.calls.get())
    }

    @Test
    fun `a Client ID change during a refresh keeps the new ID and no tokens`() {
        store.file = CONNECTED_FILE.copy(expiresAtMs = NOW)
        val token = Endpoint(200 to REFRESH_ROTATED).apply {
            beforeReply = { store.file = SpotifyAuthFile(clientId = OTHER_CLIENT_ID) }
        }
        val api = Endpoint(204 to null)
        serve(token, api)

        val result = session.currentlyPlaying()

        assertEquals(SpotifyResult.Failure(401, "Not logged in"), result)
        assertEquals(SpotifyAuthFile(clientId = OTHER_CLIENT_ID), store.file)
        assertEquals(0, api.calls.get())
    }

    @Test
    fun `a stale refresh's invalid_grant leaves a fresh login's tokens alone`() {
        store.file = CONNECTED_FILE.copy(expiresAtMs = NOW)
        val freshLogin = CONNECTED_FILE.copy(
            accessToken = "FRESH_ACCESS",
            refreshToken = "FRESH_REFRESH",
            expiresAtMs = NOW + 3_600_000,
        )
        val token = Endpoint(400 to """{"error":"invalid_grant","error_description":"Refresh token revoked"}""").apply {
            beforeReply = { store.file = freshLogin }
        }
        serve(token = token)

        val result = session.currentlyPlaying()

        assertIs<SpotifyResult.Failure>(result)
        assertEquals(freshLogin, store.file)
        assertEquals(SessionStatus.CONNECTED, session.status())
    }

    @Test
    fun `a blank refresh token clears the tokens without calling Spotify`() {
        store.file = CONNECTED_FILE.copy(refreshToken = "", expiresAtMs = NOW)
        val token = Endpoint(200 to REFRESH_NO_ROTATION)
        val api = Endpoint(204 to null)
        serve(token, api)

        val result = session.currentlyPlaying()

        assertEquals(SpotifyResult.Failure(401, "Not logged in"), result)
        assertEquals(SpotifyAuthFile(clientId = CLIENT_ID), store.file)
        assertEquals(0, token.calls.get())
        assertEquals(0, api.calls.get())
    }

    @Test
    fun `not connected fails with Not logged in without calling Spotify`() {
        store.file = SpotifyAuthFile(clientId = CLIENT_ID)
        val token = Endpoint()
        val api = Endpoint(204 to null)
        serve(token, api)

        assertEquals(SpotifyResult.Failure(401, "Not logged in"), session.currentlyPlaying())
        assertEquals(0, token.calls.get())
        assertEquals(0, api.calls.get())
    }

    @Test
    fun `two refreshes from the same seen token make one token POST`() {
        val token = Endpoint(200 to REFRESH_ROTATED)
        serve(token = token)

        assertEquals(null, session.refresh(seen = "OLD_ACCESS"))
        assertEquals(null, session.refresh(seen = "OLD_ACCESS"))

        assertEquals(1, token.calls.get())
        assertEquals("NEW_ACCESS", store.file.accessToken)
        assertEquals("NEW_REFRESH", store.file.refreshToken)
    }

    @Test
    fun `a refresh from a stale seen token after a logout reports not logged in`() {
        store.file = SpotifyAuthFile(clientId = CLIENT_ID)
        val token = Endpoint(200 to REFRESH_ROTATED)
        serve(token = token)

        assertEquals(SpotifyResult.Failure(401, "Not logged in"), session.refresh(seen = "OLD_ACCESS"))
        assertEquals(0, token.calls.get())
    }

    @Test
    fun `logout clears the tokens and keeps the client ID`() {
        session.logout()

        assertEquals(SpotifyAuthFile(clientId = CLIENT_ID), store.file)
        assertEquals(SessionStatus.LOGGED_OUT, session.status())
    }

    @Test
    fun `no client ID is NOT_SET_UP`() {
        store.file = SpotifyAuthFile()

        assertEquals(SessionStatus.NOT_SET_UP, session.status())
    }

    @Test
    fun `tokens with a client ID are CONNECTED`() {
        assertEquals(SessionStatus.CONNECTED, session.status())
    }

    @Test
    fun `an access token alone is CONNECTED`() {
        store.file = CONNECTED_FILE.copy(refreshToken = "")

        assertEquals(SessionStatus.CONNECTED, session.status())
    }

    @Test
    fun `a refresh token alone is CONNECTED`() {
        store.file = CONNECTED_FILE.copy(accessToken = "")

        assertEquals(SessionStatus.CONNECTED, session.status())
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val CLIENT_ID = "0123456789abcdef0123456789abcdef"
        const val OTHER_CLIENT_ID = "fedcba9876543210fedcba9876543210"
        const val TOKEN_PATH = "/api/token"
        const val PLAYING_PATH = "/v1/me/player/currently-playing"

        val CONNECTED_FILE = SpotifyAuthFile(
            clientId = CLIENT_ID,
            accessToken = "OLD_ACCESS",
            refreshToken = "OLD_REFRESH",
            expiresAtMs = NOW + 3_600_000,
        )

        const val REFRESH_NO_ROTATION = """{"access_token":"NEW_ACCESS","token_type":"Bearer","expires_in":3600}"""
        const val REFRESH_ROTATED =
            """{"access_token":"NEW_ACCESS","token_type":"Bearer","expires_in":3600,"refresh_token":"NEW_REFRESH"}"""
        const val EXPIRED_BODY = """{"error":{"status":401,"message":"The access token expired"}}"""
        const val PLAYING_BODY = """{"is_playing":true,"item":{"name":"Song","type":"track"}}"""
    }
}
