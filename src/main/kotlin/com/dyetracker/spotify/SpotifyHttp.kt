package com.dyetracker.spotify

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** A Spotify call's outcome. */
sealed interface SpotifyResult<out T> {
    data class Success<T>(val data: T) : SpotifyResult<T>

    /**
     * A failed call. [status] is the HTTP status, or [SpotifyConstants.STATUS_NO_RESPONSE] for a
     * network failure. [retryAfterSeconds] is set on a 429. [error] is the token endpoint's raw
     * `error` code (e.g. `invalid_grant`), null for the Web API and for failures with no such body.
     */
    data class Failure(
        val status: Int,
        val message: String,
        val retryAfterSeconds: Long? = null,
        val error: String? = null,
    ) : SpotifyResult<Nothing>
}

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204

private const val HEADER_CONTENT_TYPE = "Content-Type"
private const val HEADER_AUTHORIZATION = "Authorization"
private const val HEADER_RETRY_AFTER = "Retry-After"
private const val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"
private const val BEARER_PREFIX = "Bearer "
private const val GRANT_AUTHORIZATION_CODE = "authorization_code"
private const val GRANT_REFRESH_TOKEN = "refresh_token"

/** Token-request form keys shared by the code exchange and the refresh. */
private const val PARAM_GRANT_TYPE = "grant_type"
private const val PARAM_CLIENT_ID = "client_id"
private const val CURRENTLY_PLAYING_PATH = "/me/player/currently-playing?additional_types=track,episode"

/** Error body keys: the accounts service uses `error` + `error_description`, the Web API `error.message`. */
private const val ERROR_KEY = "error"
private const val ERROR_DESCRIPTION_KEY = "error_description"
private const val ERROR_MESSAGE_KEY = "message"

/**
 * [SpotifyResult.Failure.message] when a body won't decode. The decoder's own message quotes the
 * whole body, which for the token endpoint holds the tokens, so it is never used.
 */
private const val UNEXPECTED_RESPONSE = "Unexpected response from Spotify"

/** [SpotifyResult.Failure.message] for a 429, on every call. */
private const val RATE_LIMITED = "Rate limited"

private val httpTimeout: Duration = Duration.ofSeconds(SpotifyConstants.HTTP_TIMEOUT_SECONDS)

/**
 * Blocking Spotify HTTP calls (token exchange and refresh, currently playing). Never throws: every
 * outcome is a [SpotifyResult]. Never call it from the client thread. Never logs a token, code or
 * verifier.
 */
class SpotifyHttp(
    private val tokenUrl: String = SpotifyConstants.TOKEN_URL,
    private val apiBaseUrl: String = SpotifyConstants.API_BASE_URL,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(httpTimeout).build(),
) {

    /** Exchange an authorization [code] for tokens (PKCE: [verifier], no client secret). */
    fun exchangeCode(clientId: String, code: String, redirectUri: String, verifier: String): SpotifyResult<TokenResponse> {
        val body = formEncode(
            listOf(
                PARAM_GRANT_TYPE to GRANT_AUTHORIZATION_CODE,
                "code" to code,
                "redirect_uri" to redirectUri,
                PARAM_CLIENT_ID to clientId,
                "code_verifier" to verifier,
            ),
        )
        return postTokenForm(body)
    }

    /**
     * Trade a [refreshToken] for a new access token (PKCE: no client secret). The response may
     * carry a rotated refresh token. A failure keeps Spotify's `error` code in
     * [SpotifyResult.Failure.error].
     */
    fun refresh(clientId: String, refreshToken: String): SpotifyResult<TokenResponse> =
        postTokenForm(
            formEncode(
                listOf(
                    PARAM_GRANT_TYPE to GRANT_REFRESH_TOKEN,
                    "refresh_token" to refreshToken,
                    PARAM_CLIENT_ID to clientId,
                ),
            ),
        )

    /** POST the form [body] to the token endpoint and decode the tokens. */
    private fun postTokenForm(body: String): SpotifyResult<TokenResponse> {
        val request = HttpRequest.newBuilder(URI.create(tokenUrl))
            .timeout(httpTimeout)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return send(request) { status, text ->
            if (status == HTTP_OK) {
                SpotifyResult.Success(SpotifyJson.decodeFromString<TokenResponse>(text))
            } else {
                failure(status, text)
            }
        }
    }

    /** What the player is listening to; `Success(null)` when nothing is (Spotify's 204). */
    fun currentlyPlaying(accessToken: String): SpotifyResult<CurrentlyPlaying?> {
        val request = HttpRequest.newBuilder(URI.create("$apiBaseUrl$CURRENTLY_PLAYING_PATH"))
            .timeout(httpTimeout)
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$accessToken")
            .GET()
            .build()
        return send(request) { status, text ->
            when (status) {
                HTTP_OK -> SpotifyResult.Success(SpotifyJson.decodeFromString<CurrentlyPlaying>(text))
                HTTP_NO_CONTENT -> SpotifyResult.Success(null)
                else -> failure(status, text)
            }
        }
    }

    /**
     * Send [request] and map its response with [onResponse]. Never throws.
     *
     * A 429 is always a [RATE_LIMITED] failure carrying the `Retry-After` seconds, else
     * [SpotifyConstants.DEFAULT_RETRY_AFTER_SECONDS].
     *
     * A transport failure is a status-0 failure; only an [IOException]'s message is kept (the JDK
     * client's never carry a body). A body that won't decode is a failure with the real status and
     * [UNEXPECTED_RESPONSE]: never the decoder's message, which quotes the body (the tokens, for
     * the token endpoint). Any other exception is named by its type alone.
     */
    private fun <T> send(request: HttpRequest, onResponse: (Int, String) -> SpotifyResult<T>): SpotifyResult<T> {
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return SpotifyResult.Failure(SpotifyConstants.STATUS_NO_RESPONSE, e.javaClass.simpleName)
        } catch (e: IOException) {
            return SpotifyResult.Failure(SpotifyConstants.STATUS_NO_RESPONSE, e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            return SpotifyResult.Failure(SpotifyConstants.STATUS_NO_RESPONSE, e.javaClass.simpleName)
        }
        val status = response.statusCode()
        if (status == SpotifyConstants.HTTP_TOO_MANY_REQUESTS) {
            val retryAfter = response.headers().firstValue(HEADER_RETRY_AFTER).orElse(null)?.trim()?.toLongOrNull()
            return SpotifyResult.Failure(
                status,
                RATE_LIMITED,
                retryAfterSeconds = retryAfter ?: SpotifyConstants.DEFAULT_RETRY_AFTER_SECONDS,
            )
        }
        return try {
            onResponse(status, response.body())
        } catch (_: SerializationException) {
            SpotifyResult.Failure(status, UNEXPECTED_RESPONSE)
        } catch (_: IllegalArgumentException) {
            SpotifyResult.Failure(status, UNEXPECTED_RESPONSE)
        } catch (e: Exception) {
            SpotifyResult.Failure(status, e.javaClass.simpleName)
        }
    }
}

/**
 * A failure for [status], with Spotify's own message from [body] when it has one, and the token
 * endpoint's raw `error` code when the body has a string `error`.
 */
private fun failure(status: Int, body: String): SpotifyResult.Failure {
    val json = errorJson(body)
    return SpotifyResult.Failure(
        status,
        json?.let(::errorMessage) ?: "HTTP $status",
        error = json?.stringOrNull(ERROR_KEY),
    )
}

/** [body] as a JSON object, or null when it isn't one. */
private fun errorJson(body: String): JsonObject? = try {
    SpotifyJson.parseToJsonElement(body) as? JsonObject
} catch (_: Exception) {
    null
}

/**
 * Spotify's message from an error [json] body: the accounts service's `error_description`, else
 * its `error` string, else the Web API's `error.message`.
 */
private fun errorMessage(json: JsonObject): String? {
    json.stringOrNull(ERROR_DESCRIPTION_KEY)?.let { return it }
    json.stringOrNull(ERROR_KEY)?.let { return it }
    return (json[ERROR_KEY] as? JsonObject)?.stringOrNull(ERROR_MESSAGE_KEY)
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
