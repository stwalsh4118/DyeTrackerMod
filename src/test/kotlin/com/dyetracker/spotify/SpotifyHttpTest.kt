package com.dyetracker.spotify

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Unit coverage for [SpotifyHttp] (task 76-1) against a local JDK [HttpServer] fake: the token
 * exchange's form body and decoding, Spotify's error_description on a failure, and the
 * currently-playing request's header, query, 204 and track decoding. Task 76-2 adds the
 * refresh form, the kept token error code, and 429's Retry-After.
 */
class SpotifyHttpTest {

    /** What the fake saw on its last request. */
    private class Seen {
        @Volatile var body: String = ""
        @Volatile var query: String? = null
        @Volatile var authorization: String? = null
        @Volatile var contentType: String? = null
        @Volatile var method: String = ""
    }

    private val seen = Seen()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    private val base: String get() = "http://127.0.0.1:${server.address.port}"
    private val http: SpotifyHttp by lazy { SpotifyHttp(tokenUrl = "$base/api/token", apiBaseUrl = "$base/v1") }

    @AfterTest
    fun stop() = server.stop(0)

    /**
     * Serve [status] with [body] (null = no body) and [headers] on [path], recording the request
     * in [seen].
     */
    private fun serve(path: String, status: Int, body: String?, headers: Map<String, String> = emptyMap()) {
        server.createContext(path) { exchange: HttpExchange ->
            headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            seen.method = exchange.requestMethod
            seen.body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            seen.query = exchange.requestURI.rawQuery
            seen.authorization = exchange.requestHeaders.getFirst("Authorization")
            seen.contentType = exchange.requestHeaders.getFirst("Content-Type")
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
        server.start()
    }

    private fun form(body: String): Map<String, String> = body.split('&').associate { pair ->
        val (k, v) = pair.split('=', limit = 2)
        URLDecoder.decode(k, StandardCharsets.UTF_8) to URLDecoder.decode(v, StandardCharsets.UTF_8)
    }

    @Test
    fun `exchangeCode posts the form and decodes the tokens`() {
        serve(
            "/api/token",
            200,
            """{"access_token":"A","token_type":"Bearer","expires_in":3600,"refresh_token":"R","scope":"user-read-currently-playing"}""",
        )

        val result = http.exchangeCode("cid", "the code", "http://127.0.0.1:5000/callback", "ver")

        assertEquals("POST", seen.method)
        assertEquals("application/x-www-form-urlencoded", seen.contentType)
        assertEquals(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to "the code",
                "redirect_uri" to "http://127.0.0.1:5000/callback",
                "client_id" to "cid",
                "code_verifier" to "ver",
            ),
            form(seen.body),
        )
        assertEquals(
            SpotifyResult.Success(TokenResponse("A", "Bearer", 3600, "R", "user-read-currently-playing")),
            result,
        )
    }

    @Test
    fun `exchangeCode failure uses Spotify's error_description`() {
        serve("/api/token", 400, """{"error":"invalid_grant","error_description":"Invalid authorization code"}""")

        assertEquals(
            SpotifyResult.Failure(400, "Invalid authorization code", error = "invalid_grant"),
            http.exchangeCode("cid", "c", "r", "v"),
        )
    }

    @Test
    fun `exchangeCode failure falls back to error`() {
        serve("/api/token", 400, """{"error":"invalid_client"}""")

        assertEquals(
            SpotifyResult.Failure(400, "invalid_client", error = "invalid_client"),
            http.exchangeCode("cid", "c", "r", "v"),
        )
    }

    @Test
    fun `exchangeCode failure with no JSON body falls back to the status`() {
        serve("/api/token", 500, "oops")

        assertEquals(SpotifyResult.Failure(500, "HTTP 500"), http.exchangeCode("cid", "c", "r", "v"))
    }

    @Test
    fun `a network failure is status 0`() {
        // Nothing listens on this context's port once the server is stopped.
        server.start()
        val deadBase = base
        server.stop(0)
        val dead = SpotifyHttp(tokenUrl = "$deadBase/api/token", apiBaseUrl = "$deadBase/v1")

        val result = dead.exchangeCode("cid", "c", "r", "v")

        assertIs<SpotifyResult.Failure>(result)
        assertEquals(0, result.status)
    }

    @Test
    fun `currentlyPlaying sends the bearer token and additional_types, and 204 is null`() {
        serve("/v1/me/player/currently-playing", 204, null)

        val result = http.currentlyPlaying("A")

        assertEquals("GET", seen.method)
        assertEquals("Bearer A", seen.authorization)
        assertEquals("additional_types=track,episode", seen.query)
        assertEquals(SpotifyResult.Success(null), result)
    }

    @Test
    fun `currentlyPlaying decodes a track and ignores unknown fields`() {
        serve(
            "/v1/me/player/currently-playing",
            200,
            """
            {
              "is_playing": true,
              "progress_ms": 1000,
              "currently_playing_type": "track",
              "not_a_field": {"x": 1},
              "item": {
                "name": "Song",
                "duration_ms": 200000,
                "type": "track",
                "artists": [{"name": "A1"}, {"name": "A2"}],
                "album": {"name": "Al", "images": [{"url": "https://i.scdn.co/image/1", "width": 640, "height": 640}]},
                "external_urls": {"spotify": "https://open.spotify.com/track/1"}
              }
            }
            """.trimIndent(),
        )

        val result = http.currentlyPlaying("A")

        assertIs<SpotifyResult.Success<CurrentlyPlaying?>>(result)
        val cp = result.data
        assertEquals(true, cp?.isPlaying)
        assertEquals(1000L, cp?.progressMs)
        assertEquals("track", cp?.type)
        val item = cp?.item
        assertEquals("Song", item?.name)
        assertEquals(200_000L, item?.durationMs)
        assertEquals(listOf("A1", "A2"), item?.artists?.map { it.name })
        assertEquals(listOf(SpotifyImage("https://i.scdn.co/image/1", 640, 640)), item?.album?.images)
        assertEquals("https://open.spotify.com/track/1", item?.externalUrls?.spotify)
        assertNull(item?.show)
    }

    @Test
    fun `currentlyPlaying failure uses the Web API's error message`() {
        serve("/v1/me/player/currently-playing", 401, """{"error":{"status":401,"message":"The access token expired"}}""")

        assertEquals(SpotifyResult.Failure(401, "The access token expired"), http.currentlyPlaying("A"))
    }

    @Test
    fun `a 200 token body that won't decode fails without echoing the tokens`() {
        serve(
            "/api/token",
            200,
            """{"access_token":"SECRETA","token_type":"Bearer","expires_in":"soon","refresh_token":"SECRETR"}""",
        )

        val result = http.exchangeCode("cid", "c", "r", "v")

        assertIs<SpotifyResult.Failure>(result)
        assertEquals(200, result.status)
        assertFalse(result.message.contains("SECRETA"), result.message)
        assertFalse(result.message.contains("SECRETR"), result.message)
    }

    @Test
    fun `a 200 currently-playing body that won't decode fails without echoing the body`() {
        serve("/v1/me/player/currently-playing", 200, """{"is_playing":"SECRETBODY","item":{"name":"Song"}}""")

        val result = http.currentlyPlaying("A")

        assertIs<SpotifyResult.Failure>(result)
        assertEquals(200, result.status)
        assertFalse(result.message.contains("SECRETBODY"), result.message)
    }

    @Test
    fun `a malformed error body falls back to the status without echoing it`() {
        serve("/api/token", 400, """{"error":"invalid_grant","error_description":"SECRETX" """)

        assertEquals(SpotifyResult.Failure(400, "HTTP 400"), http.exchangeCode("cid", "c", "r", "v"))
    }

    @Test
    fun `refresh posts the refresh_token form and decodes the tokens`() {
        serve("/api/token", 200, """{"access_token":"A2","token_type":"Bearer","expires_in":3600}""")

        val result = http.refresh("cid", "R1")

        assertEquals("POST", seen.method)
        assertEquals("application/x-www-form-urlencoded", seen.contentType)
        assertEquals(
            mapOf("grant_type" to "refresh_token", "refresh_token" to "R1", "client_id" to "cid"),
            form(seen.body),
        )
        assertEquals(SpotifyResult.Success(TokenResponse("A2", "Bearer", 3600, null)), result)
    }

    @Test
    fun `refresh invalid_grant without a description keeps the code as message and error`() {
        serve("/api/token", 400, """{"error":"invalid_grant"}""")

        assertEquals(
            SpotifyResult.Failure(400, "invalid_grant", error = "invalid_grant"),
            http.refresh("cid", "R1"),
        )
    }

    @Test
    fun `refresh invalid_grant with a description uses it and keeps the code`() {
        serve("/api/token", 400, """{"error":"invalid_grant","error_description":"Refresh token revoked"}""")

        assertEquals(
            SpotifyResult.Failure(400, "Refresh token revoked", error = "invalid_grant"),
            http.refresh("cid", "R1"),
        )
    }

    @Test
    fun `a 200 refresh body that won't decode fails without echoing the tokens`() {
        serve(
            "/api/token",
            200,
            """{"access_token":"SECRETA","token_type":"Bearer","expires_in":"soon","refresh_token":"SECRETR"}""",
        )

        val result = http.refresh("cid", "R1")

        assertIs<SpotifyResult.Failure>(result)
        assertEquals(200, result.status)
        assertFalse(result.message.contains("SECRETA"), result.message)
        assertFalse(result.message.contains("SECRETR"), result.message)
        assertNull(result.error)
    }

    @Test
    fun `a 429 with Retry-After 7 waits 7 seconds`() {
        serve("/v1/me/player/currently-playing", 429, null, mapOf("Retry-After" to "7"))

        assertEquals(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 7), http.currentlyPlaying("A"))
    }

    @Test
    fun `a 429 without Retry-After waits the default`() {
        serve("/v1/me/player/currently-playing", 429, """{"error":{"status":429,"message":"API rate limit exceeded"}}""")

        assertEquals(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 5), http.currentlyPlaying("A"))
    }

    @Test
    fun `a 429 with a non-numeric Retry-After waits the default`() {
        serve("/v1/me/player/currently-playing", 429, null, mapOf("Retry-After" to "Wed, 21 Oct 2026 07:28:00 GMT"))

        assertEquals(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 5), http.currentlyPlaying("A"))
    }

    @Test
    fun `a 429 from the token endpoint is rate limited too`() {
        serve("/api/token", 429, null, mapOf("Retry-After" to "3"))

        assertEquals(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 3), http.refresh("cid", "R1"))
    }
}
