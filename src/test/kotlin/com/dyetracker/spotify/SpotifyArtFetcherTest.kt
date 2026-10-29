package com.dyetracker.spotify

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Unit coverage for the album art fetch (task 76-5): the capped read, the HTTP checks and decode
 * of [ArtFetchCore] against a local JDK [HttpServer] fake (the host check is bypassed only by the
 * injected allow function), and [ArtLoader]'s once-per-URL load, release and stale-upload rules.
 * Task 76-6 adds the dimension check before decode and the loader's recolor from the cover.
 */
class SpotifyArtFetcherTest {

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    private val base: String get() = "http://127.0.0.1:${server.address.port}"
    private val requests = AtomicInteger()

    /** The core under test, with the host check off for the `127.0.0.1` fake. */
    private val core = ArtFetchCore(allow = { true })

    @AfterTest
    fun stop() = server.stop(0)

    /**
     * Serve [status] with [body] and [contentType] (null = none) on `/art`. [chunked] sends no
     * `Content-Length`, so only the read cap can stop an oversized body.
     */
    private fun serve(
        status: Int,
        body: ByteArray,
        contentType: String? = PNG_TYPE,
        chunked: Boolean = false,
        headers: Map<String, String> = emptyMap(),
    ) {
        server.createContext(ART_PATH) { exchange: HttpExchange ->
            requests.incrementAndGet()
            contentType?.let { exchange.responseHeaders.add("Content-Type", it) }
            headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            try {
                exchange.sendResponseHeaders(status, if (chunked) 0 else body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } catch (_: java.io.IOException) {
                // The client stopped reading past the cap.
            }
            exchange.close()
        }
        server.start()
    }

    private val url: String get() = "$base$ART_PATH"

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out)
        return out.toByteArray()
    }

    // readCapped

    @Test
    fun `readCapped returns a body of exactly the cap`() {
        val bytes = ByteArray(CAP) { it.toByte() }
        assertContentEquals(bytes, readCapped(ByteArrayInputStream(bytes), CAP))
    }

    @Test
    fun `readCapped returns null one byte past the cap`() {
        assertNull(readCapped(ByteArrayInputStream(ByteArray(CAP + 1)), CAP))
    }

    @Test
    fun `readCapped of an empty body is empty`() {
        assertContentEquals(ByteArray(0), readCapped(ByteArrayInputStream(ByteArray(0)), CAP))
    }

    // ArtFetchCore

    @Test
    fun `a valid PNG decodes to an image of its size`() {
        serve(200, png(64, 48))
        val result = assertIs<ArtFetchResult.Loaded>(core.fetch(url))
        assertEquals(64, result.image.width)
        assertEquals(48, result.image.height)
    }

    @Test
    fun `the image content type is matched case-insensitively`() {
        serve(200, png(8, 8), contentType = "IMAGE/PNG")
        assertIs<ArtFetchResult.Loaded>(core.fetch(url))
    }

    @Test
    fun `a body over the cap with a Content-Length is too large`() {
        serve(200, ByteArray(SpotifyConstants.ART_MAX_BYTES + 1))
        assertEquals(ArtFetchResult.Failed(ART_TOO_LARGE), core.fetch(url))
    }

    @Test
    fun `a chunked body over the cap is too large`() {
        serve(200, ByteArray(SpotifyConstants.ART_MAX_BYTES + 1), chunked = true)
        assertEquals(ArtFetchResult.Failed(ART_TOO_LARGE), core.fetch(url))
    }

    @Test
    fun `a text-html response is rejected`() {
        serve(200, "<html></html>".toByteArray(), contentType = "text/html")
        assertEquals(ArtFetchResult.Failed(ART_NOT_AN_IMAGE), core.fetch(url))
    }

    @Test
    fun `a response with no content type is rejected`() {
        serve(200, png(8, 8), contentType = null)
        assertEquals(ArtFetchResult.Failed(ART_NOT_AN_IMAGE), core.fetch(url))
    }

    @Test
    fun `a non-200 status fails with the status`() {
        serve(404, ByteArray(0))
        assertEquals(ArtFetchResult.Failed("HTTP 404"), core.fetch(url))
    }

    @Test
    fun `a redirect is not followed`() {
        serve(302, ByteArray(0), headers = mapOf("Location" to "https://example.com/x"))
        assertEquals(ArtFetchResult.Failed("HTTP 302"), core.fetch(url))
        assertEquals(1, requests.get())
    }

    @Test
    fun `image bytes that won't decode are undecodable`() {
        serve(200, "not a png".toByteArray())
        assertEquals(ArtFetchResult.Failed(ART_UNDECODABLE), core.fetch(url))
    }

    @Test
    fun `a header wider than the max dimension is refused before decoding`() {
        serve(200, png(OVERSIZE_PX, 1))
        assertEquals(ArtFetchResult.Failed(ART_DIMENSIONS_TOO_LARGE), core.fetch(url))
    }

    @Test
    fun `a header taller than the max dimension is refused before decoding`() {
        serve(200, png(1, OVERSIZE_PX))
        assertEquals(ArtFetchResult.Failed(ART_DIMENSIONS_TOO_LARGE), core.fetch(url))
    }

    @Test
    fun `an image exactly the max dimension decodes`() {
        serve(200, png(SpotifyConstants.ART_MAX_DIM_PX, 1))
        assertEquals(SpotifyConstants.ART_MAX_DIM_PX, assertIs<ArtFetchResult.Loaded>(core.fetch(url)).image.width)
    }

    @Test
    fun `a 64 by 64 image passes the dimension check`() {
        serve(200, png(64, 64))
        val image = assertIs<ArtFetchResult.Loaded>(core.fetch(url)).image
        assertEquals(64, image.width)
        assertEquals(64, image.height)
    }

    @Test
    fun `the default host check refuses the fake without a request`() {
        serve(200, png(8, 8))
        assertEquals(ArtFetchResult.Failed(ART_NOT_ALLOWED), ArtFetchCore().fetch(url))
        assertEquals(0, requests.get())
    }

    // ArtLoader

    /** Records the loader's uploads and releases; [futures] lets a test finish an upload late. */
    private class Textures {
        val uploads = mutableListOf<BufferedImage>()
        val futures = mutableListOf<CompletableFuture<Unit>>()
        var releases = 0
        var completeAtOnce = true

        fun upload(image: BufferedImage): CompletableFuture<Unit> {
            uploads.add(image)
            val future = CompletableFuture<Unit>()
            futures.add(future)
            if (completeAtOnce) future.complete(Unit)
            return future
        }
    }

    private val textures = Textures()
    private val fetched = mutableListOf<String>()
    private val warnings = mutableListOf<String>()
    private val recolors = mutableListOf<SpotifyColors>()
    private val direct = Executor { it.run() }

    private fun blank(size: Int) = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)

    private fun solid(argb: Int) = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB).apply {
        for (y in 0 until 64) for (x in 0 until 64) setRGB(x, y, argb)
    }

    private fun loader(
        worker: Executor = direct,
        fetch: (String) -> ArtFetchResult = { ArtFetchResult.Loaded(blank(64)) },
    ) = ArtLoader(
        worker = worker,
        fetch = { fetched.add(it); fetch(it) },
        upload = textures::upload,
        release = { textures.releases++ },
        recolor = { recolors.add(it) },
        warn = { warnings.add(it) },
    )

    @Test
    fun `a new url is fetched and uploaded once`() {
        val loader = loader()
        loader.ensure(A)
        loader.ensure(A)
        assertEquals(listOf(A), fetched)
        assertEquals(1, textures.uploads.size)
        assertEquals(0, textures.releases)
    }

    @Test
    fun `the uploaded image has rounded corners`() {
        val opaque = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until 64) for (x in 0 until 64) setRGB(x, y, OPAQUE_WHITE)
        }
        loader(fetch = { ArtFetchResult.Loaded(opaque) }).ensure(A)
        val uploaded = textures.uploads.single()
        assertEquals(0, uploaded.getRGB(0, 0) ushr ALPHA_SHIFT)
        assertEquals(OPAQUE_WHITE, uploaded.getRGB(32, 32))
    }

    @Test
    fun `a loaded cover recolors the widget from the image before its corners are rounded`() {
        // Only the corner pixel is opaque: rounding clears it, so the rounded image has no palette.
        val corner = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, OPAQUE_RED) }
        loader(fetch = { ArtFetchResult.Loaded(corner) }).ensure(A)
        assertEquals(listOf(paletteOf(corner)), recolors)
        assertNotEquals(FALLBACK_COLORS, recolors.single())
    }

    @Test
    fun `a failed fetch recolors the widget to the fallback`() {
        loader(fetch = { ArtFetchResult.Failed("HTTP 404") }).ensure(A)
        assertEquals(listOf(FALLBACK_COLORS), recolors)
    }

    @Test
    fun `null recolors the widget to the fallback`() {
        val loader = loader(fetch = { ArtFetchResult.Loaded(solid(OPAQUE_RED)) })
        loader.ensure(A)
        loader.ensure(null)
        assertEquals(listOf(paletteOf(solid(OPAQUE_RED)), FALLBACK_COLORS), recolors)
    }

    @Test
    fun `a null replaced before its turn on the worker leaves the colors to the next load`() {
        val queued = mutableListOf<Runnable>()
        val loader = loader(worker = Executor { queued.add(it) }, fetch = { ArtFetchResult.Loaded(solid(OPAQUE_RED)) })
        loader.ensure(A)
        loader.ensure(null)
        loader.ensure(B)
        queued.forEach(Runnable::run)
        assertEquals(listOf(paletteOf(solid(OPAQUE_RED))), recolors)
    }

    @Test
    fun `a load whose url is no longer wanted does not recolor`() {
        val queued = mutableListOf<Runnable>()
        val loader = loader(worker = Executor { queued.add(it) })
        loader.ensure(A)
        loader.ensure(B)
        queued.first().run()
        assertEquals(emptyList(), recolors)
    }

    @Test
    fun `a changed url fetches again and the upload replaces the old art`() {
        val loader = loader()
        loader.ensure(A)
        loader.ensure(B)
        assertEquals(listOf(A, B), fetched)
        assertEquals(2, textures.uploads.size)
        assertEquals(0, textures.releases)
    }

    @Test
    fun `null releases the art once and forgets the url`() {
        val loader = loader()
        loader.ensure(A)
        loader.ensure(null)
        loader.ensure(null)
        assertEquals(1, textures.releases)
        loader.ensure(A)
        assertEquals(listOf(A, A), fetched)
    }

    @Test
    fun `null before any art is a no-op`() {
        loader().ensure(null)
        assertEquals(0, textures.releases)
    }

    @Test
    fun `a failed fetch releases the old art and is not retried for the same url`() {
        val loader = loader(fetch = { if (it == A) ArtFetchResult.Loaded(blank(8)) else ArtFetchResult.Failed("HTTP 404") })
        loader.ensure(A)
        loader.ensure(B)
        loader.ensure(B)
        assertEquals(listOf(A, B), fetched)
        assertEquals(1, textures.uploads.size)
        assertEquals(1, textures.releases)
    }

    @Test
    fun `a fetch that throws counts as a failure named by its class`() {
        val loader = loader(fetch = { throw IllegalStateException("boom") })
        loader.ensure(A)
        assertEquals(0, textures.uploads.size)
        assertEquals(1, textures.releases)
        assertEquals(listOf("IllegalStateException"), warnings)
    }

    @Test
    fun `a failing url is warned about once`() {
        val loader = loader(fetch = { ArtFetchResult.Failed("HTTP 404") })
        loader.ensure(A)
        loader.ensure(B)
        loader.ensure(A)
        assertEquals(listOf(A, B, A), fetched)
        assertEquals(listOf("HTTP 404", "HTTP 404"), warnings)
    }

    @Test
    fun `an upload that lands after the url changed is released`() {
        textures.completeAtOnce = false
        val loader = loader()
        loader.ensure(A)
        loader.ensure(null)
        assertEquals(1, textures.releases)
        textures.futures.single().complete(Unit)
        assertEquals(2, textures.releases)
    }

    @Test
    fun `an upload that lands while its url is still wanted is kept`() {
        textures.completeAtOnce = false
        loader().ensure(A)
        textures.futures.single().complete(Unit)
        assertEquals(0, textures.releases)
    }

    @Test
    fun `a url replaced before its load starts is never fetched`() {
        val queued = mutableListOf<Runnable>()
        val loader = loader(worker = Executor { queued.add(it) })
        loader.ensure(A)
        loader.ensure(B)
        queued.forEach(Runnable::run)
        assertEquals(listOf(B), fetched)
        assertEquals(1, textures.uploads.size)
    }

    private companion object {
        const val ART_PATH = "/art"
        const val PNG_TYPE = "image/png"
        const val CAP = 16
        const val A = "https://i.scdn.co/image/a"
        const val B = "https://i.scdn.co/image/b"
        const val ALPHA_SHIFT = 24
        const val OPAQUE_WHITE = 0xFF_FF_FF_FF.toInt()
        const val OPAQUE_RED = 0xFF_C0_39_2B.toInt()

        /** Past [SpotifyConstants.ART_MAX_DIM_PX]; a 4096×1 PNG is tiny but declares it. */
        const val OVERSIZE_PX = 4096
    }
}
