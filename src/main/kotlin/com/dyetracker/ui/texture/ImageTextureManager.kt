package com.dyetracker.ui.texture

import com.dyetracker.DyeTrackerMod
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Feature-agnostic GPU texture lifecycle + frame-animation backend for the UI toolkit.
 * Converts an [ImageFrames] (one or more frames + intrinsic size) into per-frame textures
 * registered under stable `Identifier`s keyed by an arbitrary string id, then exposes a
 * wall-clock-based [currentFrameIdentifier] selector.
 *
 * TODO(PBI-52): the real GPU texture pipeline is stubbed for the 26.1 compile-green milestone
 * (PBI-51 task 51-8). 26.1 retires Yarn's `NativeImage`/`NativeImageBackedTexture` for the
 * Mojmap `com.mojang.blaze3d.platform.NativeImage` + `net.minecraft.client.renderer.texture.DynamicTexture`
 * pipeline (and `TextureManager.register`/`release`, `NativeImage.setPixelABGR`). Porting + in-client
 * verification is PBI-52's scope. Here, [upload] allocates the per-frame `Identifier`s and keeps all
 * the pure frame-timing logic, but performs **no** native-image construction or texture registration,
 * so nothing is drawn until PBI-52 (the `UiDraw` blit is likewise stubbed).
 */
object ImageTextureManager {

    private const val FRAME_ID_PREFIX = "img_"
    private const val FRAME_ID_INFIX = "_frame_"
    private const val BYTES_PER_PIXEL = 4
    private const val HEX_RADIX = 16
    private val ID_SANITIZER_REGEX = Regex("[^a-z0-9_]")

    private data class ImageState(
        val frameIds: List<Identifier>,
        // TODO(PBI-52): re-add the GPU texture handles (was `List<NativeImageBackedTexture>`),
        // held to release on teardown, once the DynamicTexture pipeline lands.
        val cumulativeDelaysMs: LongArray,
        val totalDurationMs: Long,
        val width: Int,
        val height: Int,
        @Volatile var lastFrameIndex: Int = 0,
    )

    private val images = ConcurrentHashMap<String, ImageState>()
    private val warnedMissing = ConcurrentHashMap.newKeySet<String>()
    private val releaseListeners = CopyOnWriteArrayList<(String) -> Unit>()

    /**
     * Register [listener] to be invoked with an image id immediately after its textures are
     * released (on the render thread). Lets features hook release without the toolkit depending
     * on feature-specific code. The same listener added twice fires twice.
     */
    fun addReleaseListener(listener: (String) -> Unit) {
        releaseListeners.add(listener)
    }

    /**
     * Upload all frames of [image] under [id]. Returns a future that completes after the per-frame
     * ids are registered on the render thread. Replaces any existing entry for [id]. Safe to call
     * from any thread.
     */
    fun upload(id: String, image: ImageFrames): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        val client = Minecraft.getInstance()
        if (image.frames.isEmpty()) {
            future.completeExceptionally(IllegalArgumentException("upload requires at least one frame"))
            return future
        }
        client.execute {
            try {
                releaseInternal(id) // replace if already uploaded

                val sanitized = sanitize(id)
                val frameIds = ArrayList<Identifier>(image.frames.size)
                for (index in image.frames.indices) {
                    val frameId = Identifier.fromNamespaceAndPath(
                        DyeTrackerMod.MOD_ID,
                        "${FRAME_ID_PREFIX}${sanitized}${FRAME_ID_INFIX}$index",
                    )
                    // TODO(PBI-52): build a NativeImage from image.frames[index].image and register a
                    // DynamicTexture under frameId via client.textureManager — deferred (real render).
                    frameIds.add(frameId)
                }

                val cumulative = LongArray(image.frames.size)
                var running = 0L
                for ((i, frame) in image.frames.withIndex()) {
                    // Static frame uses Int.MAX_VALUE as a sentinel; clamp to a sane long so
                    // wallClock % total doesn't overflow.
                    val d = frame.delayMs.toLong().coerceAtMost(Int.MAX_VALUE.toLong())
                    running += d
                    cumulative[i] = running
                }

                val state = ImageState(
                    frameIds = frameIds,
                    cumulativeDelaysMs = cumulative,
                    totalDurationMs = running.coerceAtLeast(1L),
                    width = image.width,
                    height = image.height,
                )
                images[id] = state
                warnedMissing.remove(id)

                val vramBytes = image.width.toLong() * image.height * BYTES_PER_PIXEL * image.frames.size
                DyeTrackerMod.info(
                    "Image '{}' registered ({} frames, {}x{}, ~{} KB VRAM when PBI-52 uploads it)",
                    id, image.frames.size, image.width, image.height, vramBytes / 1024
                )
                future.complete(Unit)
            } catch (e: Throwable) {
                DyeTrackerMod.error("Failed to upload image '$id'", e)
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /** Release an image's textures. Schedules to render thread if needed. */
    fun release(id: String) {
        val client = Minecraft.getInstance()
        if (client.isSameThread) {
            releaseInternal(id)
        } else {
            client.execute { releaseInternal(id) }
        }
    }

    /** Release every image; called on mod/client shutdown. */
    fun releaseAll() {
        val client = Minecraft.getInstance()
        if (client.isSameThread) {
            images.keys.toList().forEach(::releaseInternal)
        } else {
            client.execute { images.keys.toList().forEach(::releaseInternal) }
        }
    }

    private fun releaseInternal(id: String) {
        val state = images.remove(id) ?: return
        // TODO(PBI-52): destroy each frameId's registered DynamicTexture (was
        // client.textureManager.destroyTexture(frameId)) once the upload pipeline lands.
        warnedMissing.remove(id)
        releaseListeners.forEach { it(id) }
        DyeTrackerMod.info("Image '{}' released ({} frames)", id, state.frameIds.size)
    }

    /**
     * Identifier of the frame to draw at [wallClockMs]. Returns null if [id] has not been
     * uploaded yet. When [paused] is true, returns the frame previously selected (or frame 0
     * if none yet) so animation freezes on the pause screen.
     */
    fun currentFrameIdentifier(id: String, wallClockMs: Long, paused: Boolean): Identifier? {
        val state = images[id]
        if (state == null) {
            if (warnedMissing.add(id)) {
                DyeTrackerMod.warn("Render requested for image '{}' that is not uploaded yet", id)
            }
            return null
        }
        if (paused) {
            return state.frameIds[state.lastFrameIndex]
        }
        val total = state.totalDurationMs
        val t = if (total <= 0L) 0L else ((wallClockMs % total) + total) % total
        val idx = findFrameIndex(state.cumulativeDelaysMs, t)
        state.lastFrameIndex = idx
        return state.frameIds[idx]
    }

    /** Source width/height in pixels (the rendered size scales by the widget/placement scale). */
    fun dimensions(id: String): Pair<Int, Int>? =
        images[id]?.let { it.width to it.height }

    /** Allocation-free width accessor for the hot render loop. Returns -1 if missing. */
    fun widthOf(id: String): Int = images[id]?.width ?: -1

    /** Allocation-free height accessor for the hot render loop. Returns -1 if missing. */
    fun heightOf(id: String): Int = images[id]?.height ?: -1

    /**
     * Binary search for the smallest index `i` with `cumulative[i] > t`. The cumulative
     * array is strictly increasing (delays are clamped >= 1ms by the decoder), so this
     * always finds a unique index. For `t >= cumulative.last()`, returns `last()` —
     * caller is responsible for the wall-clock modulo.
     */
    internal fun findFrameIndex(cumulative: LongArray, t: Long): Int {
        if (cumulative.isEmpty()) return 0
        var lo = 0
        var hi = cumulative.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid] > t) {
                hi = mid
            } else {
                lo = mid + 1
            }
        }
        return lo
    }

    /**
     * Sanitize an arbitrary id into a unique Identifier path. Mojang allows `[a-z0-9_./-]+`;
     * we use a stricter `[a-z0-9_]` subset and append a stable hash suffix so distinct ids that
     * sanitize to the same prefix still produce distinct Identifiers — collisions would otherwise
     * cause one image's release to destroy another image's live textures.
     */
    private fun sanitize(id: String): String {
        val lower = id.lowercase()
        val cleaned = ID_SANITIZER_REGEX.replace(lower, "_").ifEmpty { "anon" }
        val suffix = id.hashCode().toUInt().toString(HEX_RADIX)
        return "${cleaned}_$suffix"
    }
}
