package com.dyetracker.spotify

import java.awt.image.BufferedImage
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/*
 * The album art's pure parts (PBI 76, task 76-5): which image to fetch, which hosts it may come
 * from, and its rounded corners. Pure, so it is unit-tested without a client.
 */

private const val HTTPS_SCHEME = "https"
private const val RGB_MASK = 0x00_FF_FF_FF

/** Offset from a pixel's corner to its center, for the corner circle test. */
private const val PIXEL_CENTER = 0.5

/**
 * The cover images of [item], first non-empty of: the track's album images, the episode's own
 * images, the episode's show images. Empty when it has none.
 */
fun artImagesOf(item: PlayingItem): List<SpotifyImage> =
    item.album?.images?.takeIf { it.isNotEmpty() }
        ?: item.images.takeIf { it.isNotEmpty() }
        ?: item.show?.images
        ?: emptyList()

/**
 * The image to fetch: the smallest at least [SpotifyConstants.ART_MIN_WIDTH_PX] wide, else the
 * largest (a missing width counts as 0; on a tie the first wins). Null when there are none.
 */
fun chooseImage(images: List<SpotifyImage>): SpotifyImage? =
    images.filter { (it.width ?: 0) >= SpotifyConstants.ART_MIN_WIDTH_PX }.minByOrNull { it.width ?: 0 }
        ?: images.maxByOrNull { it.width ?: 0 }

/**
 * Whether art may be fetched from [url]: an https URL whose host ends with one of
 * [SpotifyConstants.ART_ALLOWED_HOST_SUFFIXES] (so `i.scdn.co` passes and `scdn.co.evil.com`
 * doesn't). Anything that won't parse is refused.
 */
fun isAllowedArtUrl(url: String): Boolean {
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return false
    }
    if (!HTTPS_SCHEME.equals(uri.scheme, ignoreCase = true)) return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    return SpotifyConstants.ART_ALLOWED_HOST_SUFFIXES.any { host.endsWith(it) }
}

/** The art URL of [item]: the [chooseImage] of its [artImagesOf], or null when it isn't allowed. */
fun artUrlOf(item: PlayingItem): String? =
    chooseImage(artImagesOf(item))?.url?.takeIf(::isAllowedArtUrl)

/**
 * A new `TYPE_INT_ARGB` copy of [src] with its corners rounded to [radiusPx]: a pixel in a
 * corner's `r × r` square whose center lies outside the quarter circle of radius `r` gets alpha 0
 * (top-left: `(x + 0.5 - r)² + (y + 0.5 - r)² > r²`, mirrored for the other three). Every other
 * pixel is copied unchanged. Nothing is cropped, scaled or recolored.
 */
fun roundCorners(src: BufferedImage, radiusPx: Int): BufferedImage {
    val w = src.width
    val h = src.height
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val r = radiusPx.toDouble()
    val rSquared = r * r
    for (y in 0 until h) {
        // Distance from the nearest horizontal edge, so one test covers all four corners.
        val cy = minOf(y, h - 1 - y)
        for (x in 0 until w) {
            val cx = minOf(x, w - 1 - x)
            val argb = src.getRGB(x, y)
            val outside = cx < radiusPx && cy < radiusPx && run {
                val dx = cx + PIXEL_CENTER - r
                val dy = cy + PIXEL_CENTER - r
                dx * dx + dy * dy > rSquared
            }
            out.setRGB(x, y, if (outside) argb and RGB_MASK else argb)
        }
    }
    return out
}
