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

/** A Spotify call's outcome. [Failure.status] `0` means a network or decode failure. */
sealed interface SpotifyResult<out T> {
    data class Success<T>(val data: T) : SpotifyResult<T>
    data class Failure(val status: Int, val message: String, val retryAfterSeconds: Long? = null) : SpotifyResult<Nothing>
}

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204

/** [SpotifyResult.Failure.status] for a failure with no HTTP response (network, decode). */
private const val STATUS_NO_RESPONSE = 0

private const val HEADER_CONTENT_TYPE = "Content-Type"
private const val HEADER_AUTHORIZATION = "Authorization"
private const val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"
private const val BEARER_PREFIX = "Bearer "
private const val GRANT_AUTHORIZATION_CODE = "authorization_code"
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

private val httpTimeout: Duration = Duration.ofSeconds(SpotifyConstants.HTTP_TIMEOUT_SECONDS)

/**
 * Blocking Spotify HTTP calls (token exchange, currently playing). Never throws: every outcome is
 * a [SpotifyResult]. Never call it from the client thread. Never logs a token, code or verifier.
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
                "grant_type" to GRANT_AUTHORIZATION_CODE,
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to verifier,
            ),
        )
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
            return SpotifyResult.Failure(STATUS_NO_RESPONSE, e.javaClass.simpleName)
        } catch (e: IOException) {
            return SpotifyResult.Failure(STATUS_NO_RESPONSE, e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            return SpotifyResult.Failure(STATUS_NO_RESPONSE, e.javaClass.simpleName)
        }
        val status = response.statusCode()
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

/** A failure for [status], with Spotify's own message from [body] when it has one. */
private fun failure(status: Int, body: String): SpotifyResult.Failure =
    SpotifyResult.Failure(status, errorMessage(body) ?: "HTTP $status")

/**
 * Spotify's message from an error [body]: the accounts service's `error_description`, else its
 * `error` string, else the Web API's `error.message`. Null when the body isn't such JSON.
 */
private fun errorMessage(body: String): String? {
    val json = try {
        SpotifyJson.parseToJsonElement(body) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return null
    json.stringOrNull(ERROR_DESCRIPTION_KEY)?.let { return it }
    json.stringOrNull(ERROR_KEY)?.let { return it }
    return (json[ERROR_KEY] as? JsonObject)?.stringOrNull(ERROR_MESSAGE_KEY)
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
