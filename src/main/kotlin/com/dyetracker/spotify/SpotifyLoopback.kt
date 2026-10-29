package com.dyetracker.spotify

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val HTTP_OK = 200
private const val HEADER_CONTENT_TYPE = "Content-Type"
private const val CONTENT_TYPE_HTML = "text/html; charset=utf-8"
private const val CONNECTED_PAGE = "DyeTracker: Spotify connected. You can close this tab."
private const val FAILED_PAGE = "DyeTracker: Spotify login failed. Go back to Minecraft for details."
private const val LOOPBACK_THREAD_NAME = "DyeTracker-SpotifyLoopback"

/** Let the reply to the browser finish before the server goes away. */
private const val STOP_DELAY_SECONDS = 1

/**
 * A one-shot HTTP listener on [SpotifyConstants.LOOPBACK_HOST]:[port] that catches Spotify's
 * redirect back from the browser. [start] it, send the player to the authorize URL, then [await]
 * the result. It stops itself after the first callback or the timeout.
 */
class SpotifyLoopback(
    private val timeoutMs: Long = SpotifyConstants.LOGIN_TIMEOUT_MS,
    private val port: Int = SpotifyConstants.LOOPBACK_PORT,
) {

    private val result = CompletableFuture<CallbackResult>()
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    /**
     * Bind and start listening for the callback carrying [expectedState]. Returns the bound port
     * ([port], or a free one when [port] is 0).
     *
     * @throws java.net.BindException when the port is in use.
     */
    @Synchronized
    fun start(expectedState: String): Int {
        val httpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName(SpotifyConstants.LOOPBACK_HOST), port), 0)
        val daemonExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, LOOPBACK_THREAD_NAME).apply { isDaemon = true }
        }
        httpServer.executor = daemonExecutor
        httpServer.createContext(SpotifyConstants.CALLBACK_PATH) { exchange -> handle(exchange, expectedState) }
        server = httpServer
        executor = daemonExecutor
        httpServer.start()
        return httpServer.address.port
    }

    private fun handle(exchange: HttpExchange, expectedState: String) {
        val outcome = parseCallback(exchange.requestURI.rawQuery, expectedState)
        val page = if (outcome is CallbackResult.Code) CONNECTED_PAGE else FAILED_PAGE
        val bytes = page.toByteArray(StandardCharsets.UTF_8)
        exchange.use {
            it.responseHeaders.set(HEADER_CONTENT_TYPE, CONTENT_TYPE_HTML)
            it.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
            it.responseBody.use { body -> body.write(bytes) }
        }
        result.complete(outcome)
        // Stop on another thread: stop() waits for this handler's exchange to finish.
        Thread({ stop() }, LOOPBACK_THREAD_NAME).apply { isDaemon = true }.start()
    }

    /** Block until the callback arrives or [timeoutMs] passes ([CallbackResult.Timeout]). */
    fun await(): CallbackResult = try {
        result.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        stop()
        CallbackResult.Timeout
    }

    /** The redirect URI for a listener on [port]. */
    fun redirectUri(port: Int): String =
        "http://${SpotifyConstants.LOOPBACK_HOST}:$port${SpotifyConstants.CALLBACK_PATH}"

    @Synchronized
    private fun stop() {
        server?.stop(STOP_DELAY_SECONDS)
        server = null
        executor?.shutdown()
        executor = null
    }
}
