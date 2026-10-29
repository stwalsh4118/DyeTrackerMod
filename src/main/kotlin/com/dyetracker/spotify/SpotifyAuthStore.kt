package com.dyetracker.spotify

import com.dyetracker.DyeTrackerMod
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/** Directory under the game's config dir, shared with `config.json`. */
private const val CONFIG_DIR_NAME = "dyetracker"

/** [SpotifyJson], pretty-printed for the file a player may open. */
private val authFileJson = Json(SpotifyJson) { prettyPrint = true }

/**
 * Reads and writes the Spotify token file at [path]. Never logs the file's contents: it holds the
 * player's tokens. [warn] receives the one warning a corrupt file produces.
 */
class SpotifyAuthFileStore(
    private val path: Path,
    private val warn: (String) -> Unit = { DyeTrackerMod.warn(it) },
) {

    /** The saved file, or the defaults when it is missing or can't be read. */
    fun load(): SpotifyAuthFile {
        if (!Files.exists(path)) return SpotifyAuthFile()
        return try {
            authFileJson.decodeFromString<SpotifyAuthFile>(Files.readString(path))
        } catch (e: Exception) {
            // The exception's message can quote the file, so only its type is logged.
            warn("Failed to read Spotify auth file ${path.toAbsolutePath()}, using defaults: ${e.javaClass.simpleName}")
            SpotifyAuthFile()
        }
    }

    /** Write [file], creating the parent directories. */
    fun save(file: SpotifyAuthFile) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, authFileJson.encodeToString(SpotifyAuthFile.serializer(), file))
    }
}

/**
 * The one Spotify token file, `config/dyetracker/spotify.json`. Loaded once, then served from
 * memory; [update] writes through under a lock.
 */
object SpotifyAuthStore {

    private val store: SpotifyAuthFileStore by lazy {
        SpotifyAuthFileStore(
            FabricLoader.getInstance().configDir.resolve(CONFIG_DIR_NAME).resolve(SpotifyConstants.AUTH_FILE_NAME),
        )
    }

    private val lock = Any()

    @Volatile
    private var cached: SpotifyAuthFile? = null

    /** The current auth file (loaded from disk on first use). */
    fun get(): SpotifyAuthFile = cached ?: synchronized(lock) {
        cached ?: store.load().also { cached = it }
    }

    /** Apply [change] to the current file and save it. A failed write is logged; memory keeps the change. */
    fun update(change: (SpotifyAuthFile) -> SpotifyAuthFile) {
        synchronized(lock) {
            val next = change(get())
            cached = next
            try {
                store.save(next)
            } catch (e: Exception) {
                DyeTrackerMod.error("Failed to save Spotify auth file", e)
            }
        }
    }
}
