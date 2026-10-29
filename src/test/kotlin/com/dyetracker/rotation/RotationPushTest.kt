package com.dyetracker.rotation

import com.dyetracker.data.DyeRotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers [rotationPushRequestOf]: a captured rotation becomes a `POST /api/v1/rotation` body only
 * when the player is linked and the capture is complete (dyes, boosts and SkyBlock year present).
 */
class RotationPushTest {

    private val fullRotation = DyeRotation(
        dyeIds = listOf("bone", "celeste", "flame"),
        capturedAt = 1761700000000L,
        boosts = mapOf("bone" to 2, "celeste" to 2, "flame" to 3),
        skyblockYear = 493,
    )

    @Test
    fun `linked player with a full rotation gets a request with the rotation's values`() {
        val request = assertNotNull(rotationPushRequestOf(fullRotation, linked = true))

        assertEquals(listOf("bone", "celeste", "flame"), request.dyeIds)
        assertEquals(mapOf("bone" to 2, "celeste" to 2, "flame" to 3), request.boosts)
        assertEquals(493, request.skyblockYear)
        assertEquals(1761700000000L, request.capturedAt)
    }

    @Test
    fun `unlinked player gets no request`() {
        assertNull(rotationPushRequestOf(fullRotation, linked = false))
    }

    @Test
    fun `null boosts gives no request`() {
        assertNull(rotationPushRequestOf(fullRotation.copy(boosts = null), linked = true))
    }

    @Test
    fun `empty boosts gives no request`() {
        assertNull(rotationPushRequestOf(fullRotation.copy(boosts = emptyMap()), linked = true))
    }

    @Test
    fun `null skyblock year gives no request`() {
        assertNull(rotationPushRequestOf(fullRotation.copy(skyblockYear = null), linked = true))
    }

    @Test
    fun `empty dye ids gives no request`() {
        assertNull(rotationPushRequestOf(fullRotation.copy(dyeIds = emptyList()), linked = true))
    }
}
