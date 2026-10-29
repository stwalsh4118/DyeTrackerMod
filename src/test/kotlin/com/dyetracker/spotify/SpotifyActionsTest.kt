package com.dyetracker.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit coverage for the Client ID check behind `/dyetracker spotify clientid` (task 76-1):
 * 32 hex characters, trimmed, saved lowercase; anything else is rejected. Task 76-2 adds the
 * check that a finished login still matches the saved Client ID.
 */
class SpotifyActionsTest {

    @Test
    fun `an uppercase hex ID is lowercased`() {
        assertEquals("0123456789abcdef0123456789abcdef", normalizeClientId("0123456789ABCDEF0123456789ABCDEF"))
    }

    @Test
    fun `a lowercase hex ID is kept`() {
        assertEquals("0123456789abcdef0123456789abcdef", normalizeClientId("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `surrounding spaces are trimmed`() {
        assertEquals("0123456789abcdef0123456789abcdef", normalizeClientId("  0123456789abcdef0123456789abcdef \t"))
    }

    @Test
    fun `31 chars is rejected`() {
        assertNull(normalizeClientId("0123456789abcdef0123456789abcde"))
    }

    @Test
    fun `33 chars is rejected`() {
        assertNull(normalizeClientId("0123456789abcdef0123456789abcdef0"))
    }

    @Test
    fun `non-hex is rejected`() {
        assertNull(normalizeClientId("0123456789abcdef0123456789abcdeg"))
    }

    @Test
    fun `empty is rejected`() {
        assertNull(normalizeClientId(""))
    }

    @Test
    fun `a login saves when the Client ID is unchanged`() {
        assertTrue(shouldSaveLogin("0123456789abcdef0123456789abcdef", "0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `a login doesn't save when the Client ID changed`() {
        assertFalse(shouldSaveLogin("0123456789abcdef0123456789abcdef", "fedcba9876543210fedcba9876543210"))
    }

    @Test
    fun `a login doesn't save when the Client ID was cleared`() {
        assertFalse(shouldSaveLogin("0123456789abcdef0123456789abcdef", ""))
    }

    @Test
    fun `the Client ID comparison ignores case`() {
        assertTrue(shouldSaveLogin("0123456789ABCDEF0123456789ABCDEF", "0123456789abcdef0123456789abcdef"))
    }
}
