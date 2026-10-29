package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit coverage for the PKCE helpers (task 76-1): the S256 challenge (RFC 7636 appendix B), the
 * verifier's length and alphabet, the authorize URL's parameter order and encoding, and how a
 * loopback callback query is read.
 */
class SpotifyPkceTest {

    @Test
    fun `challengeOf matches the RFC 7636 appendix B example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `generateVerifier is 64 chars from the verifier alphabet`() {
        val verifier = generateVerifier()
        assertEquals(64, verifier.length)
        assertTrue(verifier.all { it in SpotifyConstants.VERIFIER_ALPHABET }, verifier)
    }

    @Test
    fun `generateVerifier differs between calls`() {
        assertNotEquals(generateVerifier(), generateVerifier())
    }

    @Test
    fun `generateState is 32 chars from the verifier alphabet`() {
        val state = generateState()
        assertEquals(32, state.length)
        assertTrue(state.all { it in SpotifyConstants.VERIFIER_ALPHABET }, state)
    }

    @Test
    fun `authorizeUrl puts the params in order and encodes the redirect URI`() {
        assertEquals(
            "https://accounts.spotify.com/authorize?response_type=code&client_id=abc" +
                "&scope=user-read-currently-playing&code_challenge_method=S256&code_challenge=CH" +
                "&redirect_uri=http%3A%2F%2F127.0.0.1%3A5000%2Fcallback&state=ST",
            authorizeUrl("abc", "http://127.0.0.1:5000/callback", "CH", "ST"),
        )
    }

    @Test
    fun `parseCallback reads the code when the state matches`() {
        assertEquals(CallbackResult.Code("XY"), parseCallback("code=XY&state=ST", "ST"))
    }

    @Test
    fun `parseCallback reports a denial`() {
        assertEquals(CallbackResult.Denied("access_denied"), parseCallback("error=access_denied&state=ST", "ST"))
    }

    @Test
    fun `parseCallback reports a denial even when the state differs`() {
        assertEquals(CallbackResult.Denied("access_denied"), parseCallback("error=access_denied&state=NO", "ST"))
    }

    @Test
    fun `parseCallback rejects a state mismatch`() {
        assertEquals(CallbackResult.StateMismatch, parseCallback("code=XY&state=NO", "ST"))
    }

    @Test
    fun `parseCallback rejects a missing state`() {
        assertEquals(CallbackResult.StateMismatch, parseCallback("code=XY", "ST"))
    }

    @Test
    fun `parseCallback with no code is Missing`() {
        assertEquals(CallbackResult.Missing, parseCallback("state=ST", "ST"))
    }

    @Test
    fun `parseCallback with no query is Missing`() {
        assertEquals(CallbackResult.Missing, parseCallback(null, "ST"))
    }

    @Test
    fun `parseCallback decodes percent-encoded values`() {
        assertEquals(CallbackResult.Code("a b/c"), parseCallback("code=a%20b%2Fc&state=ST", "ST"))
    }

    @Test
    fun `parseCallback with a bad percent escape is Missing`() {
        assertEquals(CallbackResult.Missing, parseCallback("code=%ZZ&state=ST", "ST"))
    }
}
