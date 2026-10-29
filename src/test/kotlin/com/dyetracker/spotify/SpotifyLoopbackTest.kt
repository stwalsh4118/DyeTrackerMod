package com.dyetracker.spotify

import java.net.BindException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit coverage for the one-shot loopback listener (task 76-1): a real GET on the callback path
 * completes the result and gets the "connected" page; no request within the timeout is Timeout; a
 * port already in use is a BindException. Every listener binds `port = 0` (a free port).
 */
class SpotifyLoopbackTest {

    private val timeout: Duration = Duration.ofSeconds(5)
    private val client = HttpClient.newBuilder().connectTimeout(timeout).build()

    private fun get(url: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun `a callback with the code completes the result and shows the connected page`() {
        val loopback = SpotifyLoopback(port = 0)
        val port = loopback.start("ST")

        val response = get("http://127.0.0.1:$port/callback?code=XY&state=ST")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Spotify connected"), response.body())
        assertEquals(CallbackResult.Code("XY"), loopback.await())
    }

    @Test
    fun `a denied callback shows the failed page`() {
        val loopback = SpotifyLoopback(port = 0)
        val port = loopback.start("ST")

        val response = get("http://127.0.0.1:$port/callback?error=access_denied&state=ST")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Spotify login failed"), response.body())
        assertEquals(CallbackResult.Denied("access_denied"), loopback.await())
    }

    @Test
    fun `no callback within the timeout is Timeout`() {
        val loopback = SpotifyLoopback(timeoutMs = 200, port = 0)
        loopback.start("ST")

        assertEquals(CallbackResult.Timeout, loopback.await())
    }

    @Test
    fun `a port already in use throws BindException`() {
        val first = SpotifyLoopback(timeoutMs = 200, port = 0)
        val port = first.start("ST")

        assertFailsWith<BindException> { SpotifyLoopback(port = port).start("ST") }
        first.await()
    }

    @Test
    fun `the default port is the registered redirect URI's`() {
        assertEquals(SpotifyConstants.REGISTERED_REDIRECT_URI, SpotifyLoopback().redirectUri(SpotifyConstants.LOOPBACK_PORT))
    }

    @Test
    fun `redirectUri uses the loopback IP and callback path`() {
        assertEquals("http://127.0.0.1:5000/callback", SpotifyLoopback(port = 0).redirectUri(5000))
    }
}
