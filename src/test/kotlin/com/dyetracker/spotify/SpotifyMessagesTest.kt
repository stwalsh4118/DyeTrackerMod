package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the player-facing Spotify text (task 76-2): each failure status maps to its
 * line, and each session status to its status line.
 */
class SpotifyMessagesTest {

    @Test
    fun `401 says not logged in`() {
        assertEquals(
            "Not logged in: /dyetracker spotify login",
            failureText(SpotifyResult.Failure(401, "Spotify session ended, log in again")),
        )
    }

    @Test
    fun `403 points at User Management`() {
        assertEquals(
            "Spotify refused: add your account under User Management in your Spotify app",
            failureText(SpotifyResult.Failure(403, "Forbidden")),
        )
    }

    @Test
    fun `429 says when it retries`() {
        assertEquals(
            "Spotify rate limit, retrying in 7s",
            failureText(SpotifyResult.Failure(429, "Rate limited", retryAfterSeconds = 7)),
        )
    }

    @Test
    fun `429 without a wait says the default`() {
        assertEquals(
            "Spotify rate limit, retrying in 5s",
            failureText(SpotifyResult.Failure(429, "Rate limited")),
        )
    }

    @Test
    fun `0 says Spotify can't be reached`() {
        assertEquals("Can't reach Spotify", failureText(SpotifyResult.Failure(0, "Connection refused")))
    }

    @Test
    fun `an undecodable body falls to the generic line with its real status`() {
        assertEquals(
            "Spotify error 200: Unexpected response from Spotify",
            failureText(SpotifyResult.Failure(200, "Unexpected response from Spotify")),
        )
    }

    @Test
    fun `any other status shows the status and message`() {
        assertEquals("Spotify error 500: HTTP 500", failureText(SpotifyResult.Failure(500, "HTTP 500")))
    }

    @Test
    fun `NOT_SET_UP status line`() {
        assertEquals(
            "Spotify: not set up (/dyetracker spotify clientid <id>)",
            statusText(SessionStatus.NOT_SET_UP),
        )
    }

    @Test
    fun `LOGGED_OUT status line`() {
        assertEquals("Spotify: logged out (/dyetracker spotify login)", statusText(SessionStatus.LOGGED_OUT))
    }

    @Test
    fun `CONNECTED status line`() {
        assertEquals("Spotify: connected", statusText(SessionStatus.CONNECTED))
    }
}
