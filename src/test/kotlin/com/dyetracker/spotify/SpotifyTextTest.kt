package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for the now-playing chat line (task 76-1): a track's artists, an episode's show,
 * and nothing to show when Spotify returned no item.
 */
class SpotifyTextTest {

    private val trackUrl = "https://open.spotify.com/track/1"

    @Test
    fun `a track reads its title, artists and link`() {
        val cp = CurrentlyPlaying(
            isPlaying = true,
            type = "track",
            item = PlayingItem(
                name = "Song",
                artists = listOf(SpotifyArtist("A1"), SpotifyArtist("A2")),
                externalUrls = ExternalUrls(trackUrl),
            ),
        )
        assertEquals(NowPlayingLine("Song", "A1, A2", trackUrl), nowPlayingLine(cp))
    }

    @Test
    fun `an episode reads its show name`() {
        val cp = CurrentlyPlaying(
            type = "episode",
            item = PlayingItem(name = "Ep", type = "episode", show = SpotifyShow(name = "Pod")),
        )
        assertEquals(NowPlayingLine("Ep", "Pod", ""), nowPlayingLine(cp))
    }

    @Test
    fun `an item with no artists or show has an empty subtitle`() {
        val cp = CurrentlyPlaying(item = PlayingItem(name = "Thing"))
        assertEquals(NowPlayingLine("Thing", "", ""), nowPlayingLine(cp))
    }

    @Test
    fun `no item is null`() {
        assertNull(nowPlayingLine(CurrentlyPlaying(item = null)))
    }

    @Test
    fun `null is null`() {
        assertNull(nowPlayingLine(null))
    }
}
