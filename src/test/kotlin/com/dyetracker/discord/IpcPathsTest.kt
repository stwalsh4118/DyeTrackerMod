package com.dyetracker.discord

import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit tests for [ipcCandidatePaths]: where the Discord app's IPC socket or pipe may live. */
class IpcPathsTest {

    @Test
    fun `Windows lists the ten named pipes in order`() {
        val paths = ipcCandidatePaths("Windows 11", emptyMap())
        assertEquals(IPC_PIPE_COUNT, paths.size)
        assertEquals("""\\?\pipe\discord-ipc-0""", paths.first())
        assertEquals("""\\?\pipe\discord-ipc-9""", paths.last())
    }

    @Test
    fun `the Windows check ignores case and the env`() {
        val paths = ipcCandidatePaths("windows 10", mapOf("TMPDIR" to "/x"))
        assertEquals(List(IPC_PIPE_COUNT) { """\\?\pipe\discord-ipc-$it""" }, paths)
    }

    @Test
    fun `Linux tries XDG_RUNTIME_DIR then the temp dirs, without duplicates`() {
        val paths = ipcCandidatePaths(
            "Linux",
            mapOf("XDG_RUNTIME_DIR" to "/run/user/1000/", "TMPDIR" to "/tmp"),
        )
        assertEquals(2 * IPC_PIPE_COUNT, paths.size)
        assertEquals("/run/user/1000/discord-ipc-0", paths[0])
        assertEquals("/run/user/1000/discord-ipc-9", paths[IPC_PIPE_COUNT - 1])
        assertEquals("/tmp/discord-ipc-0", paths[IPC_PIPE_COUNT])
        assertEquals(paths.distinct(), paths, "no duplicate /tmp entries")
    }

    @Test
    fun `dirs are tried in env order XDG_RUNTIME_DIR, TMPDIR, TMP, TEMP, then tmp`() {
        val paths = ipcCandidatePaths(
            "Mac OS X",
            mapOf("TEMP" to "/d", "TMP" to "/c", "TMPDIR" to "/b/", "XDG_RUNTIME_DIR" to "/a"),
        )
        val firstPerDir = paths.filter { it.endsWith("discord-ipc-0") }
        assertEquals(
            listOf("/a/discord-ipc-0", "/b/discord-ipc-0", "/c/discord-ipc-0", "/d/discord-ipc-0", "/tmp/discord-ipc-0"),
            firstPerDir,
        )
    }

    @Test
    fun `blank env values are skipped`() {
        val paths = ipcCandidatePaths("Linux", mapOf("XDG_RUNTIME_DIR" to "  ", "TMPDIR" to ""))
        assertEquals(List(IPC_PIPE_COUNT) { "/tmp/discord-ipc-$it" }, paths)
    }

    @Test
    fun `empty env falls back to tmp`() {
        assertEquals(List(IPC_PIPE_COUNT) { "/tmp/discord-ipc-$it" }, ipcCandidatePaths("Linux", emptyMap()))
    }
}
