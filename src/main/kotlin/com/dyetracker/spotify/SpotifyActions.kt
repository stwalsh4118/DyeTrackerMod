package com.dyetracker.spotify

import com.dyetracker.DyeTrackerMod
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.Util
import java.net.BindException
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Spotify Client ID from [raw]: trimmed, 32 hex characters, lowercased (Spotify IDs are
 * lowercase hex). Null when it isn't one.
 */
fun normalizeClientId(raw: String): String? =
    raw.trim().takeIf { SpotifyConstants.CLIENT_ID_REGEX.matches(it) }?.lowercase()

/**
 * The player-facing Spotify actions (Client ID, login, now playing), shared by the
 * `/dyetracker spotify` commands and, later, the Settings tab. Results go to a [feedback] function
 * on the client thread; every Spotify call runs on one daemon worker thread.
 */
object SpotifyActions {

    private const val WORKER_THREAD_NAME = "DyeTracker-SpotifyLogin"
    private const val MS_PER_SECOND = 1000L

    private const val SET_CLIENT_ID_FIRST = "Set your Client ID first: /dyetracker spotify clientid <id>"
    private const val LOGIN_FAILED_PREFIX = "Spotify login failed: "

    /** Separates the title from the subtitle in the now-playing line. */
    private const val TITLE_SEPARATOR = " · "

    /** One worker for the login (which blocks on the browser) and the now-playing call. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, WORKER_THREAD_NAME).apply { isDaemon = true }
    }

    private val loginRunning = AtomicBoolean(false)

    private val http = SpotifyHttp()

    sealed interface SetClientIdResult {
        data object Saved : SetClientIdResult
        data object Invalid : SetClientIdResult
    }

    /** Save the player's Client ID. A different ID than the saved one clears the saved tokens. */
    fun setClientId(raw: String): SetClientIdResult {
        val clientId = normalizeClientId(raw) ?: return SetClientIdResult.Invalid
        SpotifyAuthStore.update { current ->
            if (current.clientId == clientId) current else SpotifyAuthFile(clientId = clientId)
        }
        return SetClientIdResult.Saved
    }

    /**
     * Log in with Authorization Code + PKCE: listen on the loopback port, open the browser at
     * Spotify's authorize page, wait for the redirect, exchange the code and save the tokens.
     */
    fun login(feedback: (Component) -> Unit) {
        val clientId = SpotifyAuthStore.get().clientId
        if (clientId.isBlank()) {
            feedback(Component.literal(SET_CLIENT_ID_FIRST).withStyle(ChatFormatting.YELLOW))
            return
        }
        if (!loginRunning.compareAndSet(false, true)) {
            feedback(Component.literal("A Spotify login is already running.").withStyle(ChatFormatting.YELLOW))
            return
        }
        worker.execute {
            try {
                runLogin(clientId, feedback)
            } catch (e: Exception) {
                DyeTrackerMod.error("Spotify login failed", e)
                post(feedback, red("$LOGIN_FAILED_PREFIX${e.javaClass.simpleName}"))
            } finally {
                loginRunning.set(false)
            }
        }
    }

    private fun runLogin(clientId: String, feedback: (Component) -> Unit) {
        val verifier = generateVerifier()
        val state = generateState()
        val loopback = SpotifyLoopback()
        val port = try {
            loopback.start(state)
        } catch (_: BindException) {
            post(
                feedback,
                red(
                    "${LOGIN_FAILED_PREFIX}port ${SpotifyConstants.LOOPBACK_PORT} is in use. " +
                        "Close whatever is using it and try again.",
                ),
            )
            return
        }
        val redirectUri = loopback.redirectUri(port)
        val url = URI.create(authorizeUrl(clientId, redirectUri, challengeOf(verifier), state))
        Minecraft.getInstance().execute {
            Util.getPlatform().openUri(url)
            feedback(
                Component.literal("Opening Spotify login… click here if your browser didn't open")
                    .withStyle { it.withColor(ChatFormatting.YELLOW).withClickEvent(ClickEvent.OpenUrl(url)) },
            )
        }

        val message = when (val callback = loopback.await()) {
            is CallbackResult.Code -> exchange(clientId, callback.code, redirectUri, verifier)
            is CallbackResult.Denied -> red("Spotify login cancelled.")
            CallbackResult.StateMismatch, CallbackResult.Missing -> red("${LOGIN_FAILED_PREFIX}bad callback.")
            CallbackResult.Timeout -> red("Spotify login timed out after 5 minutes.")
        }
        post(feedback, message)
    }

    /** Exchange the code, save the tokens, and say how it went. */
    private fun exchange(clientId: String, code: String, redirectUri: String, verifier: String): Component =
        when (val result = http.exchangeCode(clientId, code, redirectUri, verifier)) {
            is SpotifyResult.Success -> {
                val tokens = result.data
                val expiresAtMs = System.currentTimeMillis() + tokens.expiresIn * MS_PER_SECOND
                SpotifyAuthStore.update {
                    it.copy(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken.orEmpty(),
                        expiresAtMs = expiresAtMs,
                    )
                }
                DyeTrackerMod.info(
                    "Spotify connected (access token present, refresh token {})",
                    if (tokens.refreshToken.isNullOrEmpty()) "absent" else "present",
                )
                Component.literal("Spotify connected.").withStyle(ChatFormatting.GREEN)
            }
            is SpotifyResult.Failure -> red("$LOGIN_FAILED_PREFIX${result.message}")
        }

    /** Print what's playing, with a link back to Spotify. */
    fun now(feedback: (Component) -> Unit) {
        val auth = SpotifyAuthStore.get()
        val notReady = when {
            auth.clientId.isBlank() -> SET_CLIENT_ID_FIRST
            auth.accessToken.isBlank() -> "Not logged in: /dyetracker spotify login"
            auth.expiresAtMs <= System.currentTimeMillis() -> "Spotify session expired: /dyetracker spotify login"
            else -> null
        }
        if (notReady != null) {
            feedback(Component.literal(notReady).withStyle(ChatFormatting.YELLOW))
            return
        }
        worker.execute {
            val message = try {
                when (val result = http.currentlyPlaying(auth.accessToken)) {
                    is SpotifyResult.Success -> nowPlayingLine(result.data)?.let(::nowPlayingComponent)
                        ?: Component.literal("Nothing playing on Spotify.").withStyle(ChatFormatting.GRAY)
                    is SpotifyResult.Failure -> red("Spotify error: ${result.message}")
                }
            } catch (e: Exception) {
                DyeTrackerMod.error("Spotify now-playing failed", e)
                red("Spotify error: ${e.javaClass.simpleName}")
            }
            post(feedback, message)
        }
    }

    /** `<title> · <subtitle> [LISTEN ON SPOTIFY]`, the link only when Spotify gave a URL. */
    private fun nowPlayingComponent(line: NowPlayingLine): Component {
        val text: MutableComponent = Component.literal("${line.title}$TITLE_SEPARATOR${line.subtitle} ")
            .withStyle(ChatFormatting.WHITE)
        if (line.url.isNotBlank()) {
            val uri = URI.create(line.url)
            text.append(
                Component.literal("[${SpotifyConstants.LISTEN_ON_SPOTIFY}]")
                    .withStyle { it.withColor(ChatFormatting.GREEN).withClickEvent(ClickEvent.OpenUrl(uri)) },
            )
        }
        return text
    }

    private fun red(text: String): Component = Component.literal(text).withStyle(ChatFormatting.RED)

    /** Hand [message] to [feedback] on the client thread. */
    private fun post(feedback: (Component) -> Unit, message: Component) {
        Minecraft.getInstance().execute { feedback(message) }
    }
}
