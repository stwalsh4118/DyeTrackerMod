package com.dyetracker.discord

import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit tests for [ipcCandidatePaths]: where the Discord app's IPC socket or pipe may live. */
class IpcPathsTest {

    /** Plain, Flatpak and Snap: the sub-locations tried in each Unix dir. */
    private val perDir = 3 * IPC_PIPE_COUNT

    /** Every candidate for [dir], in the order they are tried. */
    private fun candidatesIn(dir: String) =
        List(IPC_PIPE_COUNT) { "$dir/discord-ipc-$it" } +
            List(IPC_PIPE_COUNT) { "$dir/app/com.discordapp.Discord/discord-ipc-$it" } +
            List(IPC_PIPE_COUNT) { "$dir/snap.discord/discord-ipc-$it" }

    @Test
    fun `Windows lists the ten named pipes in order`() {
        val paths = ipcCandidatePaths("Windows 11", emptyMap())
        assertEquals(IPC_PIPE_COUNT, paths.size)
        assertEquals("""\\.\pipe\discord-ipc-0""", paths.first())
        assertEquals("""\\.\pipe\discord-ipc-9""", paths.last())
    }

    @Test
    fun `the Windows check ignores case and the env`() {
        val paths = ipcCandidatePaths("windows 10", mapOf("TMPDIR" to "/x"))
        assertEquals(List(IPC_PIPE_COUNT) { """\\.\pipe\discord-ipc-$it""" }, paths)
    }

    @Test
    fun `Linux tries XDG_RUNTIME_DIR then the temp dirs, without duplicates`() {
        val paths = ipcCandidatePaths(
            "Linux",
            mapOf("XDG_RUNTIME_DIR" to "/run/user/1000/", "TMPDIR" to "/tmp"),
        )
        assertEquals(2 * perDir, paths.size)
        assertEquals("/run/user/1000/discord-ipc-0", paths[0])
        assertEquals("/run/user/1000/discord-ipc-9", paths[IPC_PIPE_COUNT - 1])
        assertEquals("/tmp/discord-ipc-0", paths[perDir])
        assertEquals(paths.distinct(), paths, "no duplicate /tmp entries")
    }

    @Test
    fun `dirs are tried in env order XDG_RUNTIME_DIR, TMPDIR, TMP, TEMP, then tmp`() {
        val paths = ipcCandidatePaths(
            "Mac OS X",
            mapOf("TEMP" to "/d", "TMP" to "/c", "TMPDIR" to "/b/", "XDG_RUNTIME_DIR" to "/a"),
        )
        val firstPerDir = paths.filterIndexed { i, _ -> i % perDir == 0 }
        assertEquals(
            listOf("/a/discord-ipc-0", "/b/discord-ipc-0", "/c/discord-ipc-0", "/d/discord-ipc-0", "/tmp/discord-ipc-0"),
            firstPerDir,
        )
    }

    @Test
    fun `blank env values are skipped`() {
        val paths = ipcCandidatePaths("Linux", mapOf("XDG_RUNTIME_DIR" to "  ", "TMPDIR" to ""))
        assertEquals(candidatesIn("/tmp"), paths)
    }

    @Test
    fun `empty env falls back to tmp`() {
        assertEquals(candidatesIn("/tmp"), ipcCandidatePaths("Linux", emptyMap()))
    }

    @Test
    fun `each dir tries plain, then Flatpak, then Snap, 0 to 9 within each`() {
        val paths = ipcCandidatePaths("Linux", mapOf("XDG_RUNTIME_DIR" to "/run/user/1000"))
        assertEquals(candidatesIn("/run/user/1000") + candidatesIn("/tmp"), paths)
        assertEquals("/run/user/1000/app/com.discordapp.Discord/discord-ipc-0", paths[IPC_PIPE_COUNT])
        assertEquals("/run/user/1000/snap.discord/discord-ipc-9", paths[perDir - 1])
    }
}
