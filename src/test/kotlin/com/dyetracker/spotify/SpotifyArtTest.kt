package com.dyetracker.spotify

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit coverage for the album art's pure parts (task 76-5): which image is fetched, which hosts
 * it may come from, the rounded corners, and the snapshot's art URL.
 */
class SpotifyArtTest {

    private fun image(width: Int?, url: String = "$ART_BASE$width") = SpotifyImage(url, width, width)

    // chooseImage

    @Test
    fun `chooseImage takes the smallest image at least 64 wide`() {
        val small = image(64)
        assertSame(small, chooseImage(listOf(image(640), image(300), small)))
    }

    @Test
    fun `chooseImage skips images under 64 wide when a larger one qualifies`() {
        val mid = image(300)
        assertSame(mid, chooseImage(listOf(image(640), mid)))
    }

    @Test
    fun `chooseImage takes exactly 64 and skips 63`() {
        val exact = image(64)
        assertSame(exact, chooseImage(listOf(image(63), image(100), exact)))
        val hundred = image(100)
        assertSame(hundred, chooseImage(listOf(image(63), hundred)))
    }

    @Test
    fun `chooseImage takes the largest when none is 64 wide`() {
        val largest = image(32)
        assertSame(largest, chooseImage(listOf(largest, image(16))))
    }

    @Test
    fun `chooseImage takes the first image when no width is known`() {
        val first = image(null, "$ART_BASE/a")
        assertSame(first, chooseImage(listOf(first, image(null, "$ART_BASE/b"))))
    }

    @Test
    fun `chooseImage of no images is null`() {
        assertNull(chooseImage(emptyList()))
    }

    // artImagesOf

    @Test
    fun `artImagesOf takes the album's images first`() {
        val album = listOf(image(64, "$ART_BASE/album"))
        val item = PlayingItem(
            album = SpotifyAlbum(images = album),
            images = listOf(image(64, "$ART_BASE/item")),
            show = SpotifyShow(images = listOf(image(64, "$ART_BASE/show"))),
        )
        assertEquals(album, artImagesOf(item))
    }

    @Test
    fun `artImagesOf takes an episode's own images before its show's`() {
        val episode = listOf(image(64, "$ART_BASE/episode"))
        val item = PlayingItem(
            type = "episode",
            images = episode,
            show = SpotifyShow(images = listOf(image(64, "$ART_BASE/show"))),
        )
        assertEquals(episode, artImagesOf(item))
    }

    @Test
    fun `artImagesOf falls back to the show's images`() {
        val show = listOf(image(64, "$ART_BASE/show"))
        assertEquals(show, artImagesOf(PlayingItem(type = "episode", show = SpotifyShow(images = show))))
    }

    @Test
    fun `artImagesOf passes over an album with no images`() {
        val episode = listOf(image(64, "$ART_BASE/item"))
        assertEquals(episode, artImagesOf(PlayingItem(album = SpotifyAlbum(images = emptyList()), images = episode)))
    }

    @Test
    fun `artImagesOf of an item with no images is empty`() {
        assertEquals(emptyList(), artImagesOf(PlayingItem()))
    }

    // isAllowedArtUrl

    @Test
    fun `isAllowedArtUrl accepts Spotify's image hosts over https`() {
        assertTrue(isAllowedArtUrl("https://i.scdn.co/image/ab67616d00004851"))
        assertTrue(isAllowedArtUrl("https://mosaic.scdn.co/640/x"))
        assertTrue(isAllowedArtUrl("https://image-cdn-ak.spotifycdn.com/image/x"))
    }

    @Test
    fun `isAllowedArtUrl ignores the host's case`() {
        assertTrue(isAllowedArtUrl("https://I.SCDN.CO/image/x"))
    }

    @Test
    fun `isAllowedArtUrl rejects http`() {
        assertFalse(isAllowedArtUrl("http://i.scdn.co/x"))
    }

    @Test
    fun `isAllowedArtUrl rejects a host that only starts like Spotify's`() {
        assertFalse(isAllowedArtUrl("https://scdn.co.evil.com/x"))
    }

    @Test
    fun `isAllowedArtUrl rejects a bare suffix domain and other hosts`() {
        assertFalse(isAllowedArtUrl("https://scdn.co/x"))
        assertFalse(isAllowedArtUrl("https://example.com/x"))
        assertFalse(isAllowedArtUrl("https://i.scdn.co@evil.com/x"))
    }

    @Test
    fun `isAllowedArtUrl rejects text that isn't a url`() {
        assertFalse(isAllowedArtUrl("not a url"))
        assertFalse(isAllowedArtUrl(""))
    }

    // roundCorners

    private fun opaqueRed(size: Int): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until size) for (x in 0 until size) img.setRGB(x, y, RED)
        return img
    }

    private fun alpha(img: BufferedImage, x: Int, y: Int): Int = img.getRGB(x, y) ushr ALPHA_SHIFT

    @Test
    fun `roundCorners clears the four corner pixels`() {
        val out = roundCorners(opaqueRed(SIZE), RADIUS)
        assertEquals(0, alpha(out, 0, 0))
        assertEquals(0, alpha(out, SIZE - 1, 0))
        assertEquals(0, alpha(out, 0, SIZE - 1))
        assertEquals(0, alpha(out, SIZE - 1, SIZE - 1))
    }

    @Test
    fun `roundCorners leaves the middle and the edges past the radius unchanged`() {
        val out = roundCorners(opaqueRed(SIZE), RADIUS)
        assertEquals(RED, out.getRGB(32, 32))
        assertEquals(RED, out.getRGB(RADIUS, 0))
        assertEquals(RED, out.getRGB(0, 32))
        assertEquals(RED, out.getRGB(SIZE - 1 - RADIUS, SIZE - 1))
    }

    @Test
    fun `roundCorners follows the quarter circle inside the corner square`() {
        val out = roundCorners(opaqueRed(SIZE), RADIUS)
        // (5,5): (5.5-6)² * 2 = 0.5 <= 36, inside the circle.
        assertEquals(RED, out.getRGB(RADIUS - 1, RADIUS - 1))
        // (1,1): (1.5-6)² * 2 = 40.5 > 36, outside.
        assertEquals(0, alpha(out, 1, 1))
        // (2,1): (2.5-6)² + (1.5-6)² = 32.5 <= 36, inside.
        assertEquals(RED, out.getRGB(2, 1))
    }

    @Test
    fun `roundCorners returns a new image of the same size`() {
        val src = opaqueRed(SIZE)
        val out = roundCorners(src, RADIUS)
        assertEquals(SIZE, out.width)
        assertEquals(SIZE, out.height)
        assertEquals(BufferedImage.TYPE_INT_ARGB, out.type)
        assertEquals(RED, src.getRGB(0, 0))
    }

    @Test
    fun `roundCorners with radius zero changes nothing`() {
        val out = roundCorners(opaqueRed(SIZE), 0)
        assertEquals(RED, out.getRGB(0, 0))
    }

    // snapshotOf's art URL

    @Test
    fun `a track's snapshot carries the chosen album image url`() {
        val cp = CurrentlyPlaying(
            isPlaying = true,
            item = PlayingItem(
                name = "Song",
                album = SpotifyAlbum(images = listOf(image(640, I_640), image(300, I_300), image(64, I_64))),
            ),
        )
        assertEquals(I_64, snapshotOf(cp, NOW)?.artUrl)
    }

    @Test
    fun `an episode's snapshot carries its image url`() {
        val cp = CurrentlyPlaying(
            isPlaying = true,
            type = "episode",
            item = PlayingItem(name = "Ep", type = "episode", images = listOf(image(300, I_300))),
        )
        assertEquals(I_300, snapshotOf(cp, NOW)?.artUrl)
    }

    @Test
    fun `an image off Spotify's hosts is no art`() {
        val cp = CurrentlyPlaying(
            isPlaying = true,
            item = PlayingItem(
                name = "Song",
                album = SpotifyAlbum(images = listOf(image(64, "https://example.com/a.jpg"))),
            ),
        )
        assertNull(assertNotNull(snapshotOf(cp, NOW)).artUrl)
    }

    @Test
    fun `a track with no images has no art`() {
        val cp = CurrentlyPlaying(isPlaying = true, item = PlayingItem(name = "Song"))
        assertNull(assertNotNull(snapshotOf(cp, NOW)).artUrl)
    }

    @Test
    fun `an ad has no art even with an item`() {
        val cp = CurrentlyPlaying(
            isPlaying = true,
            type = SpotifyConstants.AD_TYPE,
            item = PlayingItem(name = "Ad", album = SpotifyAlbum(images = listOf(image(64, I_64)))),
        )
        assertNull(assertNotNull(snapshotOf(cp, NOW)).artUrl)
    }

    private companion object {
        const val ART_BASE = "https://i.scdn.co/image/"
        const val I_640 = "https://i.scdn.co/image/640"
        const val I_300 = "https://i.scdn.co/image/300"
        const val I_64 = "https://i.scdn.co/image/64"
        const val NOW = 10_000L
        const val SIZE = 64
        const val RADIUS = 6
        const val ALPHA_SHIFT = 24
        const val RED = 0xFF_FF_00_00.toInt()
    }
}
