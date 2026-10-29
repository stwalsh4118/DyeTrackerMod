package com.dyetracker.spotify

import com.dyetracker.DyeTrackerMod
import com.dyetracker.ui.texture.ImageFrame
import com.dyetracker.ui.texture.ImageFrames
import com.dyetracker.ui.texture.ImageTextureManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import javax.imageio.ImageIO

/**
 * Loads the bundled Spotify icon PNGs (green and white, 32×32) into the shared
 * [ImageTextureManager] at client startup, so the now-playing widget can draw them with
 * `SpriteWidget(SpotifyConstants.ICON_GREEN_ID)`. Mirrors `DyeSpriteLoader`: read from the
 * classpath, decoded off the main thread, uploaded through [ImageTextureManager.upload]. A missing
 * or undecodable icon is logged and skipped; the sprite then measures to zero and draws nothing.
 *
 * Texture release is central (`ImageTextureManager.releaseAll()` on `CLIENT_STOPPING`); this
 * loader only cancels its decode scope on shutdown.
 */
object SpotifyIconLoader {

    /** Delay marking a single static (non-animated) frame, matching the GIF decoder's convention. */
    private const val STATIC_FRAME_DELAY_MS = Int.MAX_VALUE

    private const val FILE_EXTENSION = ".png"

    /** Texture ids to load; each file is named after the part of its id after the `/`. */
    private val ICON_IDS = listOf(SpotifyConstants.ICON_GREEN_ID, SpotifyConstants.ICON_WHITE_ID)

    // Single-use: cancelled on CLIENT_STOPPING and never recreated (re-entry is blocked by loadInvoked).
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loadInvoked = false

    /** Wire startup load + shutdown cancellation. Call once from `onInitializeClient()`. */
    fun register() {
        ClientLifecycleEvents.CLIENT_STARTED.register { _ -> loadAll() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { _ -> loadScope.cancel() }
    }

    private fun loadAll() {
        if (loadInvoked) {
            DyeTrackerMod.debug("Spotify icon load already invoked; skipping re-entry")
            return
        }
        loadInvoked = true
        loadScope.launch {
            var dispatched = 0
            for (iconId in ICON_IDS) {
                ensureActive() // cooperate with shutdown cancellation
                if (loadOne(iconId)) dispatched++
            }
            DyeTrackerMod.info("Dispatched {} of {} Spotify icon uploads", dispatched, ICON_IDS.size)
        }
    }

    private fun loadOne(iconId: String): Boolean {
        val resourcePath = "/${SpotifyConstants.ICON_RESOURCE_DIR}/${iconId.substringAfter('/')}$FILE_EXTENSION"
        return try {
            val image = SpotifyIconLoader::class.java.getResourceAsStream(resourcePath)?.use { ImageIO.read(it) }
            if (image == null) {
                DyeTrackerMod.warn("Spotify icon missing or undecodable: {}", resourcePath)
                return false
            }
            val frames = ImageFrames(
                frames = listOf(ImageFrame(image, STATIC_FRAME_DELAY_MS)),
                width = image.width,
                height = image.height,
            )
            ImageTextureManager.upload(iconId, frames)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            DyeTrackerMod.warn("Failed to load Spotify icon '{}': {}", iconId, e.javaClass.simpleName)
            false
        }
    }
}
