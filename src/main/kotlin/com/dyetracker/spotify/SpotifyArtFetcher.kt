package com.dyetracker.spotify

import com.dyetracker.DyeTrackerMod
import com.dyetracker.ui.texture.ImageFrame
import com.dyetracker.ui.texture.ImageFrames
import com.dyetracker.ui.texture.ImageTextureManager
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import javax.imageio.ImageReader

/** [ArtFetchResult.Failed.reason]s, logged once per URL. Never a body or a server message. */
internal const val ART_NOT_ALLOWED = "host not allowed"
internal const val ART_NOT_AN_IMAGE = "not an image"
internal const val ART_TOO_LARGE = "too large"
internal const val ART_UNDECODABLE = "undecodable"
internal const val ART_DIMENSIONS_TOO_LARGE = "dimensions too large"

private const val HEADER_CONTENT_TYPE = "Content-Type"
private const val HEADER_CONTENT_LENGTH = "Content-Length"
private const val IMAGE_CONTENT_TYPE_PREFIX = "image/"
private const val ART_THREAD_NAME = "DyeTracker-SpotifyArt"

/** Delay marking a single static frame, matching the GIF decoder's convention. */
private const val STATIC_FRAME_DELAY_MS = Int.MAX_VALUE

private val artTimeout: Duration = Duration.ofSeconds(SpotifyConstants.HTTP_TIMEOUT_SECONDS)

/** The outcome of one art fetch. */
sealed interface ArtFetchResult {
    data class Loaded(val image: BufferedImage) : ArtFetchResult
    data class Failed(val reason: String) : ArtFetchResult
}

/** At most [cap] bytes of [input]; null as soon as it holds more (the rest is never read). */
internal fun readCapped(input: InputStream, cap: Int): ByteArray? =
    input.readNBytes(cap + 1).takeIf { it.size <= cap }

/**
 * The art's HTTP client. Redirects are never followed, so a fetch can't leave the host
 * [isAllowedArtUrl] checked.
 */
internal fun artHttpClient(): HttpClient = HttpClient.newBuilder()
    .connectTimeout(artTimeout)
    .followRedirects(HttpClient.Redirect.NEVER)
    .build()

/**
 * Fetches and decodes one image, in memory and bounded (task 76-5): only a URL [allow] accepts
 * (production: [isAllowedArtUrl]; tests swap it for a local fake), status 200, an `image/...`
 * content type, a body of at most [SpotifyConstants.ART_MAX_BYTES] (a larger `Content-Length` is
 * refused before reading, and the read stops past the cap either way), and a header no wider or
 * taller than [SpotifyConstants.ART_MAX_DIM_PX] ([decodeBounded], task 76-6). Blocking; never
 * throws.
 */
internal class ArtFetchCore(
    private val client: HttpClient = artHttpClient(),
    private val allow: (String) -> Boolean = ::isAllowedArtUrl,
) {

    /** The decoded image at [url], or why there is none. */
    fun fetch(url: String): ArtFetchResult {
        if (!allow(url)) return ArtFetchResult.Failed(ART_NOT_ALLOWED)
        return try {
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(artTimeout).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body -> read(response, body) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            ArtFetchResult.Failed(e.javaClass.simpleName)
        } catch (e: IOException) {
            // The JDK client's IOException messages never carry a body.
            ArtFetchResult.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            ArtFetchResult.Failed(e.javaClass.simpleName)
        }
    }

    private fun read(response: HttpResponse<InputStream>, body: InputStream): ArtFetchResult {
        val status = response.statusCode()
        if (status != SpotifyConstants.HTTP_OK) return ArtFetchResult.Failed("HTTP $status")
        val headers = response.headers()
        val contentType = headers.firstValue(HEADER_CONTENT_TYPE).orElse(null)?.trim()?.lowercase(Locale.ROOT)
        if (contentType == null || !contentType.startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
            return ArtFetchResult.Failed(ART_NOT_AN_IMAGE)
        }
        val length = headers.firstValueAsLong(HEADER_CONTENT_LENGTH)
        if (length.isPresent && length.asLong > SpotifyConstants.ART_MAX_BYTES) {
            return ArtFetchResult.Failed(ART_TOO_LARGE)
        }
        val bytes = readCapped(body, SpotifyConstants.ART_MAX_BYTES) ?: return ArtFetchResult.Failed(ART_TOO_LARGE)
        return decodeBounded(bytes)
    }
}

/**
 * [bytes] decoded, unless its header declares a width or height past
 * [SpotifyConstants.ART_MAX_DIM_PX]: the size is read from the header before [ImageReader.read],
 * so an oversized image is never decoded. No reader for the format is [ART_UNDECODABLE].
 */
internal fun decodeBounded(bytes: ByteArray): ArtFetchResult {
    val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        ?: return ArtFetchResult.Failed(ART_UNDECODABLE)
    stream.use {
        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) return ArtFetchResult.Failed(ART_UNDECODABLE)
        val reader = readers.next()
        try {
            reader.input = stream
            val max = SpotifyConstants.ART_MAX_DIM_PX
            if (reader.getWidth(0) > max || reader.getHeight(0) > max) {
                return ArtFetchResult.Failed(ART_DIMENSIONS_TOO_LARGE)
            }
            return ArtFetchResult.Loaded(reader.read(0))
        } finally {
            reader.dispose()
        }
    }
}

/**
 * Keeps one art texture in step with the wanted URL (task 76-5). [ensure] with a new URL fetches
 * it on [worker] and hands the rounded image to [upload], which replaces the old texture; the
 * same URL again (loaded, in flight or failed) does nothing; null calls [release] and forgets the
 * URL. A failure's reason goes to [warn] once per URL and releases the old art, so a new track
 * never shows the last one's cover. A load whose URL is no longer wanted is skipped before the
 * fetch, never uploaded after it, and released if its upload lands after the URL changed.
 *
 * [recolor] (task 76-6) is only ever called on [worker]: with the [paletteOf] of the decoded
 * image (before its corners are rounded) as it is uploaded, and with [FALLBACK_COLORS] on a
 * failure or after a null that is still wanted when its turn on [worker] comes.
 */
internal class ArtLoader(
    private val worker: Executor,
    private val fetch: (String) -> ArtFetchResult,
    private val upload: (BufferedImage) -> CompletableFuture<*>,
    private val release: () -> Unit,
    private val recolor: (SpotifyColors) -> Unit,
    private val warn: (String) -> Unit = { DyeTrackerMod.warn("Spotify album art not shown: {}", it) },
) {

    private val lock = Any()

    /** The URL last asked for (loaded, in flight or failed). Written under [lock]. */
    @Volatile
    private var wanted: String? = null

    private val warned = ConcurrentHashMap.newKeySet<String>()

    /** Load [url]'s art, or release the art when it is null. Cheap and non-blocking. */
    fun ensure(url: String?) {
        synchronized(lock) {
            if (url == wanted) return
            wanted = url
            if (url == null) release()
        }
        if (url == null) worker.execute(::clearColors) else worker.execute { load(url) }
    }

    /** No art: back to the fallback colors, unless a newer URL took over (its load recolors). */
    private fun clearColors() {
        if (wanted == null) recolor(FALLBACK_COLORS)
    }

    private fun load(url: String) {
        if (wanted != url) return
        DyeTrackerMod.debug("Spotify art: fetching {}", url)
        val result = try {
            fetch(url)
        } catch (e: Exception) {
            ArtFetchResult.Failed(e.javaClass.simpleName)
        }
        when (result) {
            is ArtFetchResult.Loaded -> show(url, result.image)
            is ArtFetchResult.Failed -> fail(url, result.reason)
        }
    }

    private fun show(url: String, image: BufferedImage) {
        val colors = paletteOf(image)
        val radius = SpotifyConstants.ART_CORNER_RADIUS_PX * image.width / SpotifyConstants.ART_MIN_WIDTH_PX
        val rounded = roundCorners(image, radius)
        // Under the lock: a newer ensure() can't slip between the check and the queued upload, so
        // its release is never queued ahead of this upload. One that lands later is released below.
        synchronized(lock) {
            if (wanted != url) return
            recolor(colors)
            upload(rounded).whenComplete { _, _ -> if (wanted != url) release() }
        }
    }

    private fun fail(url: String, reason: String) {
        if (warned.add(url)) warn(reason)
        synchronized(lock) {
            if (wanted == url) {
                release()
                recolor(FALLBACK_COLORS)
            }
        }
    }
}

/**
 * The now-playing widget's album art (PBI 76, task 76-5): fetched in memory, bounded, only from
 * Spotify's image hosts, once per URL, on its own daemon thread (`DyeTracker-SpotifyArt`) so a
 * slow image never delays polling, then uploaded with rounded corners as
 * [SpotifyConstants.ART_TEXTURE_ID]. Its palette (task 76-6) goes to [SpotifyColorStore] on the
 * same thread. No disk cache (unlike `OverlayDownloader`, whose cache would grow per track).
 * [SpotifyPoller] calls [ensure] after each tick; its stop and the logout call `ensure(null)`.
 */
object SpotifyArtFetcher {

    private val loader = ArtLoader(
        worker = SpotifyThreads.daemonWorker(ART_THREAD_NAME),
        fetch = ArtFetchCore()::fetch,
        upload = { image ->
            ImageTextureManager.upload(
                SpotifyConstants.ART_TEXTURE_ID,
                ImageFrames(listOf(ImageFrame(image, STATIC_FRAME_DELAY_MS)), image.width, image.height),
            )
        },
        release = { ImageTextureManager.release(SpotifyConstants.ART_TEXTURE_ID) },
        recolor = { SpotifyColorStore.recolor(it, System.currentTimeMillis()) },
    )

    /** Show [url]'s art (no-op when it already is, or is loading); null releases the art. */
    fun ensure(url: String?) = loader.ensure(url)
}
