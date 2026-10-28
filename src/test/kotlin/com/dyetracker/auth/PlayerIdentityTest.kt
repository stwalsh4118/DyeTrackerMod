package com.dyetracker.auth

import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit coverage for [PlayerIdentity.effectiveUsername]: whose name the HUD widgets poll under (task 67-6). */
class PlayerIdentityTest {

    @Test
    fun `linked name wins over the in-game name`() {
        assertEquals("Notch", PlayerIdentity.effectiveUsername("Notch", "Steve"))
    }

    @Test
    fun `blank linked name falls back to the trimmed in-game name`() {
        assertEquals("Steve", PlayerIdentity.effectiveUsername("", "  Steve "))
        assertEquals("Steve", PlayerIdentity.effectiveUsername("   ", "Steve"))
    }

    @Test
    fun `both blank or null gives an empty name`() {
        assertEquals("", PlayerIdentity.effectiveUsername("", null))
        assertEquals("", PlayerIdentity.effectiveUsername("  ", "   "))
    }
}
