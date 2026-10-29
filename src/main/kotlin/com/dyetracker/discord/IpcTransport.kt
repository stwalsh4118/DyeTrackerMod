package com.dyetracker.discord

import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel

/** File name of the Discord IPC socket or pipe, before its index `0`..`9`. */
private const val IPC_NAME = "discord-ipc-"

/** Windows named-pipe namespace the Discord app listens in. */
private const val WINDOWS_PIPE_DIR = """\\?\pipe\"""

/** Prefix of `os.name` on every Windows JVM. */
private const val WINDOWS_OS_PREFIX = "Windows"

/** Env vars that may hold the Discord socket's dir on Linux/macOS, in the order they are tried. */
private val UNIX_DIR_ENV_VARS = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")

/** The last dir tried on Linux/macOS. */
private const val UNIX_FALLBACK_DIR = "/tmp"

/** An open connection to the Discord app. Closing it closes both streams. */
interface IpcChannel : Closeable {
    val input: InputStream
    val output: OutputStream
}

/** Opens a connection to the local Discord app. */
fun interface IpcConnector {
    /** An open channel, or `null` when no Discord app is listening. */
    fun connect(): IpcChannel?
}

/**
 * Where the Discord app's IPC endpoint may be, in the order to try them. Pure: [osName] is
 * `os.name` and [env] the process environment. Windows gets the ten named pipes; anything else
 * gets `discord-ipc-0..9` in each non-blank dir from [UNIX_DIR_ENV_VARS] (trailing `/` removed),
 * then `/tmp`, with duplicate dirs dropped.
 */
fun ipcCandidatePaths(osName: String, env: Map<String, String>): List<String> {
    if (osName.startsWith(WINDOWS_OS_PREFIX, ignoreCase = true)) {
        return List(IPC_PIPE_COUNT) { "$WINDOWS_PIPE_DIR$IPC_NAME$it" }
    }
    val dirs = UNIX_DIR_ENV_VARS
        .mapNotNull { env[it]?.trimEnd('/') }
        .filter { it.isNotBlank() }
        .plus(UNIX_FALLBACK_DIR)
        .distinct()
    return dirs.flatMap { dir -> List(IPC_PIPE_COUNT) { "$dir/$IPC_NAME$it" } }
}

/**
 * Connects over a Unix domain socket (Linux/macOS), trying each of [paths] in order. The streams
 * come from [Channels], which share one lock per channel, so the channel must only ever be read
 * and written from one thread (see [DiscordIpcClient]).
 */
class UnixIpcConnector(private val paths: () -> List<String>) : IpcConnector {
    override fun connect(): IpcChannel? = paths().firstNotNullOfOrNull(::open)

    private fun open(path: String): IpcChannel? {
        val channel = try {
            SocketChannel.open(StandardProtocolFamily.UNIX)
        } catch (_: Exception) {
            // This JVM can't open Unix sockets at all.
            return null
        }
        return try {
            channel.connect(UnixDomainSocketAddress.of(path))
            object : IpcChannel {
                override val input: InputStream = Channels.newInputStream(channel)
                override val output: OutputStream = Channels.newOutputStream(channel)
                override fun close() = channel.close()
            }
        } catch (_: Exception) {
            // Nothing listening at this path (or it isn't a socket): try the next one.
            runCatching { channel.close() }
            null
        }
    }
}

/** Connects to a Windows named pipe, trying each of [paths] in order; reads and writes share one file. */
class WindowsPipeConnector(private val paths: () -> List<String>) : IpcConnector {
    override fun connect(): IpcChannel? = paths().firstNotNullOfOrNull(::open)

    private fun open(path: String): IpcChannel? = try {
        val pipe = RandomAccessFile(path, "rw")
        object : IpcChannel {
            override val input: InputStream = FileInputStream(pipe.fd)
            override val output: OutputStream = FileOutputStream(pipe.fd)
            override fun close() = pipe.close()
        }
    } catch (_: Exception) {
        // No pipe with this index: try the next one.
        null
    }
}

/** The connector for this OS, over [ipcCandidatePaths] for this process's `os.name` and env. */
fun defaultIpcConnector(): IpcConnector {
    val osName = System.getProperty("os.name").orEmpty()
    val paths = { ipcCandidatePaths(osName, System.getenv()) }
    return if (osName.startsWith(WINDOWS_OS_PREFIX, ignoreCase = true)) {
        WindowsPipeConnector(paths)
    } else {
        UnixIpcConnector(paths)
    }
}
