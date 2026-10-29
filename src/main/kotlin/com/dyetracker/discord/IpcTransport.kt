package com.dyetracker.discord

import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.Objects

/** File name of the Discord IPC socket or pipe, before its index `0`..`9`. */
private const val IPC_NAME = "discord-ipc-"

/**
 * Windows named-pipe namespace the Discord app listens in. The `\\.\` device form, not `\\?\`:
 * Java on Windows rewrites `\\?\pipe\x` to the relative path `pipe\x`, so that form never opens.
 */
private const val WINDOWS_PIPE_DIR = """\\.\pipe\"""

/** Prefix of `os.name` on every Windows JVM. */
private const val WINDOWS_OS_PREFIX = "Windows"

/** Env vars that may hold the Discord socket's dir on Linux/macOS, in the order they are tried. */
private val UNIX_DIR_ENV_VARS = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")

/** The last dir tried on Linux/macOS. */
private const val UNIX_FALLBACK_DIR = "/tmp"

/**
 * Where in each Unix dir the socket may be, in the order tried: the dir itself, then where the
 * Flatpak and the Snap Discord put it.
 */
private val UNIX_SUB_LOCATIONS = listOf("", "app/com.discordapp.Discord/", "snap.discord/")

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
 * gets, for each non-blank dir from [UNIX_DIR_ENV_VARS] (trailing `/` removed) and then `/tmp`
 * with duplicate dirs dropped, `discord-ipc-0..9` in the dir, then in its Flatpak
 * (`app/com.discordapp.Discord/`) and Snap (`snap.discord/`) sub-dirs.
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
    return dirs.flatMap { dir ->
        UNIX_SUB_LOCATIONS.flatMap { sub -> List(IPC_PIPE_COUNT) { "$dir/$sub$IPC_NAME$it" } }
    }
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

/**
 * Connects to a Windows named pipe, trying each of [paths] in order; reads and writes share one
 * file. The pipe is synchronous I/O: closing it from another thread does not cancel a pending
 * read (and the close waits for that read). So the input never blocks in a read: it waits for
 * bytes with [PollingPipeInputStream] and gives up with an [IOException] after
 * [responseTimeoutMs].
 */
class WindowsPipeConnector(
    private val paths: () -> List<String>,
    private val responseTimeoutMs: Long = IPC_RESPONSE_TIMEOUT_MS,
) : IpcConnector {
    override fun connect(): IpcChannel? = paths().firstNotNullOfOrNull(::open)

    private fun open(path: String): IpcChannel? = try {
        val pipe = RandomAccessFile(path, "rw")
        val pipeInput = FileInputStream(pipe.fd)
        object : IpcChannel {
            // FileInputStream.available() on a pipe is PeekNamedPipe: the bytes waiting, without
            // blocking. A broken pipe reports 0 there, so a Discord quit surfaces as the timeout.
            override val input: InputStream =
                PollingPipeInputStream({ pipeInput.available().toLong() }, pipeInput::read, responseTimeoutMs)
            override val output: OutputStream = FileOutputStream(pipe.fd)
            override fun close() = pipe.close()
        }
    } catch (_: Exception) {
        // No pipe with this index: try the next one.
        null
    }
}

/**
 * Reads up to [maxBytes] once bytes are waiting, never blocking in [read]: polls [available]
 * every [IPC_PIPE_POLL_MS] until it reports bytes, then reads no more than it reported. Throws
 * [IOException] when nothing arrived by [deadlineMs] (on the [nowMs] clock), and passes on one
 * from [available] (e.g. the pipe was closed). [maxBytes] must be above 0.
 */
internal fun readWithDeadline(
    available: () -> Long,
    read: (maxBytes: Int) -> Int,
    maxBytes: Int,
    deadlineMs: Long,
    nowMs: () -> Long = System::currentTimeMillis,
    sleep: (Long) -> Unit = Thread::sleep,
): Int {
    while (true) {
        val waiting = available()
        if (waiting > 0) return read(waiting.coerceAtMost(maxBytes.toLong()).toInt())
        if (nowMs() >= deadlineMs) throw IOException("No reply from Discord by the deadline")
        try {
            sleep(IPC_PIPE_POLL_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted waiting for Discord").apply { initCause(e) }
        }
    }
}

/**
 * An [InputStream] over a Windows pipe whose every read goes through [readWithDeadline], so it
 * waits at most [timeoutMs] per read and never blocks in the pipe read itself.
 *
 * @param bytesWaiting bytes waiting in the pipe, without blocking.
 * @param readPipe the pipe's own read; only called for bytes [bytesWaiting] reported.
 */
internal class PollingPipeInputStream(
    private val bytesWaiting: () -> Long,
    private val readPipe: (ByteArray, Int, Int) -> Int,
    private val timeoutMs: Long,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : InputStream() {

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and BYTE_MASK
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        Objects.checkFromIndexSize(off, len, b.size)
        if (len == 0) return 0
        return readWithDeadline(bytesWaiting, { max -> readPipe(b, off, max) }, len, nowMs() + timeoutMs, nowMs, sleep)
    }

    private companion object {
        /** Keeps the low 8 bits, so a byte reads as 0..255. */
        const val BYTE_MASK = 0xFF
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
