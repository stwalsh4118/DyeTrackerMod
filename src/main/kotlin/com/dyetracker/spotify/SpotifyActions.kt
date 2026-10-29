package com.dyetracker.spotify

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.Util
import java.net.BindException
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Spotify Client ID from [raw]: trimmed, 32 hex characters, lowercased (Spotify IDs are
 * lowercase hex). Null when it isn't one.
 */
fun normalizeClientId(raw: String): String? =
    raw.trim().takeIf { SpotifyConstants.CLIENT_ID_REGEX.matches(it) }?.lowercase()

/**
 * Whether a finished login may save its tokens: only when the saved Client ID is still the one
 * the login [startedWith]. Case-insensitive, since IDs are stored lowercase.
 */
fun shouldSaveLogin(startedWith: String, current: String): Boolean = startedWith.equals(current, ignoreCase = true)

/**
 * The player-facing Spotify actions (Client ID, login, logout, now playing, tint, status), shared by the
 * `/dyetracker spotify` commands and the menu's Settings tab. Results go to a [feedback] function
 * on the client thread. A login runs on its own daemon thread, since it blocks on the browser;
 * Spotify API calls run on [SpotifyThreads.api], so they never queue behind an open login.
 */
object SpotifyActions {

    private const val LOGIN_THREAD_NAME = "DyeTracker-SpotifyLogin"

    private const val SET_CLIENT_ID_FIRST = "Set your Client ID first: /dyetracker spotify clientid <id>"
    private const val LOGIN_FAILED_PREFIX = "Spotify login failed: "
    private const val CLIENT_ID_CHANGED = "Your Client ID changed during login. Run /dyetracker spotify login again."

    /** Separates the title from the subtitle in the now-playing line. */
    private const val TITLE_SEPARATOR = " · "

    /** Runs logins only: one blocks on the browser for up to 5 minutes. */
    private val loginWorker: ExecutorService = SpotifyThreads.daemonWorker(LOGIN_THREAD_NAME)

    private val loginRunning = AtomicBoolean(false)

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
        loginWorker.execute {
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

    /**
     * Exchange the code, save the tokens, and say how it went. The tokens are saved only if the
     * Client ID the login started with is still the saved one.
     */
    private fun exchange(clientId: String, code: String, redirectUri: String, verifier: String): Component =
        when (val result = SpotifySessions.http.exchangeCode(clientId, code, redirectUri, verifier)) {
            is SpotifyResult.Success -> {
                val tokens = result.data
                val expiresAtMs = System.currentTimeMillis() + tokens.expiresIn * SpotifyConstants.MS_PER_SECOND
                var saved = false
                SpotifyAuthStore.update {
                    if (shouldSaveLogin(clientId, it.clientId)) {
                        saved = true
                        it.copy(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken.orEmpty(),
                            expiresAtMs = expiresAtMs,
                        )
                    } else {
                        it
                    }
                }
                if (saved) {
                    DyeTrackerMod.info(
                        "Spotify connected (access token present, refresh token {})",
                        if (tokens.refreshToken.isNullOrEmpty()) "absent" else "present",
                    )
                    Component.literal("Spotify connected.").withStyle(ChatFormatting.GREEN)
                } else {
                    Component.literal(CLIENT_ID_CHANGED).withStyle(ChatFormatting.YELLOW)
                }
            }
            is SpotifyResult.Failure -> red("$LOGIN_FAILED_PREFIX${result.message}")
        }

    /** Print what's playing, with a link back to Spotify. The session refreshes the token as needed. */
    fun now(feedback: (Component) -> Unit) {
        if (SpotifySessions.default.status() == SessionStatus.NOT_SET_UP) {
            feedback(Component.literal(SET_CLIENT_ID_FIRST).withStyle(ChatFormatting.YELLOW))
            return
        }
        SpotifyThreads.api.execute {
            val message = try {
                when (val result = SpotifySessions.default.currentlyPlaying()) {
                    is SpotifyResult.Success -> nowPlayingLine(result.data)?.let(::nowPlayingComponent)
                        ?: Component.literal("Nothing playing on Spotify.").withStyle(ChatFormatting.GRAY)
                    is SpotifyResult.Failure -> red(failureText(result))
                }
            } catch (e: Exception) {
                DyeTrackerMod.error("Spotify now-playing failed", e)
                red("Spotify error: ${e.javaClass.simpleName}")
            }
            post(feedback, message)
        }
    }

    /** Forget the Spotify tokens (the Client ID stays) and release the album art. */
    fun logout(feedback: (Component) -> Unit) {
        SpotifySessions.default.logout()
        SpotifyArtFetcher.ensure(null)
        feedback(Component.literal("Logged out of Spotify.").withStyle(ChatFormatting.GREEN))
    }

    /** Whether the now-playing widget shows: its saved placement's flag, else the default's. */
    fun isWidgetVisible(): Boolean =
        ConfigManager.spotifyPlacements.all().firstOrNull()?.visible ?: SpotifyWidgetConfig().visible

    /** Show or hide the now-playing widget; returns whether it is now shown. */
    fun toggleWidget(): Boolean {
        val visible = !isWidgetVisible()
        SpotifyPlacementEditor.setVisible(SpotifyConstants.WIDGET_ID, visible)
        return visible
    }

    /** Turn "Tint all widgets" on or off (saved in the config). */
    fun setTintAll(enabled: Boolean) {
        ConfigManager.setSpotifyTintAll(enabled)
    }

    /** Flip "Tint all widgets"; returns whether it is now on. */
    fun toggleTintAll(): Boolean {
        val enabled = !ConfigManager.isSpotifyTintAll()
        setTintAll(enabled)
        return enabled
    }

    /** The one-line Spotify status. A cheap read of the saved file, safe on the client thread. */
    fun status(): String = statusText(SpotifySessions.default.status())

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
