package com.dyetracker.rotation

import com.dyetracker.api.RotationPushRequest
import com.dyetracker.data.DyeRotation

/**
 * Builds the `POST /api/v1/rotation` body for a captured [rotation], or null when it shouldn't be
 * sent: the player isn't [linked], or the capture lacks dyes, boosts or the SkyBlock year.
 */
fun rotationPushRequestOf(rotation: DyeRotation, linked: Boolean): RotationPushRequest? {
    val boosts = rotation.boosts
    val skyblockYear = rotation.skyblockYear
    if (!linked || rotation.dyeIds.isEmpty() || boosts.isNullOrEmpty() || skyblockYear == null) {
        return null
    }
    return RotationPushRequest(
        dyeIds = rotation.dyeIds,
        boosts = boosts,
        skyblockYear = skyblockYear,
        capturedAt = rotation.capturedAt,
    )
}
