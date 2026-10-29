package com.dyetracker.spotify

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit coverage for the Spotify token file (task 76-1): a save round-trips, and a missing or
 * corrupt file reads as the defaults, and a corrupt one warns without echoing its contents.
 */
class SpotifyAuthStoreTest {

    private val dir: Path = createTempDirectory("spotify-auth-test")

    @AfterTest
    fun cleanup() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `save then load round-trips`() {
        val path = dir.resolve("nested").resolve(SpotifyConstants.AUTH_FILE_NAME)
        val store = SpotifyAuthFileStore(path)
        val file = SpotifyAuthFile(
            clientId = "0123456789abcdef0123456789abcdef",
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtMs = 1_800_000_000_000L,
        )

        store.save(file)

        assertEquals(file, SpotifyAuthFileStore(path).load())
    }

    @Test
    fun `missing file loads the defaults`() {
        assertEquals(SpotifyAuthFile(), SpotifyAuthFileStore(dir.resolve("absent.json")).load())
    }

    @Test
    fun `corrupt file loads the defaults and warns without the contents`() {
        val path = dir.resolve(SpotifyConstants.AUTH_FILE_NAME)
        Files.writeString(path, """{"accessToken": "SECRET-TOKEN", """)
        val warnings = mutableListOf<String>()

        assertEquals(SpotifyAuthFile(), SpotifyAuthFileStore(path) { warnings.add(it) }.load())
        assertEquals(1, warnings.size)
        assertFalse(warnings.single().contains("SECRET-TOKEN"), warnings.single())
    }

    @Test
    fun `missing file does not warn`() {
        val warnings = mutableListOf<String>()

        SpotifyAuthFileStore(dir.resolve("absent.json")) { warnings.add(it) }.load()

        assertTrue(warnings.isEmpty())
    }
}
