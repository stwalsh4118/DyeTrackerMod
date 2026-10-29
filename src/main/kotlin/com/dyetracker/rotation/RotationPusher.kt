package com.dyetracker.rotation

import com.dyetracker.DyeTrackerMod
import com.dyetracker.api.ApiClient
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.DyeRotation
import java.util.concurrent.Executors

/**
 * Sends each captured Vincent rotation to the API in the background when the player is linked.
 * No retry or offline queue: the rotation is pushed again every time the "Dyes" screen opens.
 */
object RotationPusher {

    // Dedicated executor so the blocking ApiClient call never runs on the client thread
    // (same pattern as AccountVerification's verifyExecutor).
    private val pushExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DyeTracker-RotationPush").apply { isDaemon = true }
    }

    /** Push [rotation] if it is complete and the player is linked; otherwise log why not. */
    fun push(rotation: DyeRotation) {
        val linked = ConfigManager.config.isLinked()
        val request = rotationPushRequestOf(rotation, linked)
        if (request == null) {
            DyeTrackerMod.debug(
                "Rotation not pushed (linked={}, dyes={}, boosts={}, year={})",
                linked,
                rotation.dyeIds.size,
                rotation.boosts?.size ?: 0,
                rotation.skyblockYear,
            )
            return
        }

        pushExecutor.execute {
            when (val result = ApiClient.pushRotation(request)) {
                is ApiClient.ApiResult.Success ->
                    DyeTrackerMod.info(
                        "Rotation pushed for year {} (stored={})",
                        result.data.skyblockYear,
                        result.data.stored,
                    )
                is ApiClient.ApiResult.Error ->
                    DyeTrackerMod.warn("Rotation push failed: {} ({})", result.message, result.statusCode)
            }
        }
    }
}
