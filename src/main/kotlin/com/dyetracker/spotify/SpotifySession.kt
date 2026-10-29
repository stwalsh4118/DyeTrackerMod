package com.dyetracker.spotify

/** The token file as [SpotifySession] sees it: the real [SpotifyAuthStore], or an in-memory one in tests. */
interface AuthStorePort {
    fun get(): SpotifyAuthFile
    fun update(f: (SpotifyAuthFile) -> SpotifyAuthFile)
}

/** Where the player is with Spotify, for the status line, the HUD and the menu. */
enum class SessionStatus { NOT_SET_UP, LOGGED_OUT, CONNECTED }

/** [SpotifyResult.Failure.message] when there are no tokens to use. */
private const val NOT_LOGGED_IN = "Not logged in"

/** [SpotifyResult.Failure.message] when Spotify refused the refresh token (`invalid_grant`). */
private const val SESSION_ENDED = "Spotify session ended, log in again"

/** The token endpoint's `error` code for a refresh token Spotify no longer accepts. */
private const val ERROR_INVALID_GRANT = "invalid_grant"

/**
 * A logged-in Spotify session over [store]: refreshes the access token before it expires and once
 * on a 401, keeps a rotated refresh token, and ends the session when Spotify refuses the refresh
 * token. [currentlyPlaying] blocks: call it only from the Spotify API thread. [status] reads the
 * store and is safe from any thread.
 */
class SpotifySession(
    private val http: SpotifyHttp,
    private val store: AuthStorePort,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** No Client ID → [SessionStatus.NOT_SET_UP]; no tokens → [SessionStatus.LOGGED_OUT]. */
    fun status(): SessionStatus {
        val auth = store.get()
        return when {
            auth.clientId.isBlank() -> SessionStatus.NOT_SET_UP
            auth.refreshToken.isBlank() && auth.accessToken.isBlank() -> SessionStatus.LOGGED_OUT
            else -> SessionStatus.CONNECTED
        }
    }

    /**
     * What the player is listening to. Refreshes first when the token is within
     * [SpotifyConstants.REFRESH_MARGIN_MS] of expiry, and refreshes then retries once on a 401.
     */
    fun currentlyPlaying(): SpotifyResult<CurrentlyPlaying?> {
        if (status() != SessionStatus.CONNECTED) {
            return SpotifyResult.Failure(SpotifyConstants.HTTP_UNAUTHORIZED, NOT_LOGGED_IN)
        }
        if (clock() >= store.get().expiresAtMs - SpotifyConstants.REFRESH_MARGIN_MS) {
            refresh()?.let { return it }
        }
        val first = http.currentlyPlaying(store.get().accessToken)
        if (first !is SpotifyResult.Failure || first.status != SpotifyConstants.HTTP_UNAUTHORIZED) return first
        refresh()?.let { return it }
        return http.currentlyPlaying(store.get().accessToken)
    }

    /** Forget the tokens; the Client ID stays. */
    fun logout() {
        store.update { it.withoutTokens() }
    }

    /**
     * Refresh the access token and save it, keeping the old refresh token unless Spotify rotated
     * it. Null on success, else the failure to return. `invalid_grant` (or no refresh token) ends
     * the session; any other failure keeps the tokens.
     */
    @Synchronized
    private fun refresh(): SpotifyResult.Failure? {
        val auth = store.get()
        if (auth.refreshToken.isBlank()) {
            store.update { it.withoutTokens() }
            return SpotifyResult.Failure(SpotifyConstants.HTTP_UNAUTHORIZED, NOT_LOGGED_IN)
        }
        return when (val result = http.refresh(auth.clientId, auth.refreshToken)) {
            is SpotifyResult.Success -> {
                val tokens = result.data
                val expiresAtMs = clock() + tokens.expiresIn * SpotifyConstants.MS_PER_SECOND
                store.update {
                    it.copy(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken ?: it.refreshToken,
                        expiresAtMs = expiresAtMs,
                    )
                }
                null
            }
            is SpotifyResult.Failure -> if (result.error == ERROR_INVALID_GRANT) {
                store.update { it.withoutTokens() }
                SpotifyResult.Failure(SpotifyConstants.HTTP_UNAUTHORIZED, SESSION_ENDED)
            } else {
                result
            }
        }
    }
}

/** This file with the tokens cleared and the Client ID kept. */
private fun SpotifyAuthFile.withoutTokens(): SpotifyAuthFile =
    copy(accessToken = "", refreshToken = "", expiresAtMs = 0)

/** The one session the mod uses, over the real token file. */
object SpotifySessions {
    val default: SpotifySession by lazy { SpotifySession(SpotifyHttp(), SpotifyAuthStore) }
}
