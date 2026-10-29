package com.dyetracker.spotify

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/*
 * Authorization Code + PKCE helpers for the Spotify login (PBI 76, RFC 7636). Pure, so they are
 * unit-tested without a client or a network.
 */

private const val SHA256_ALGORITHM = "SHA-256"
private const val RESPONSE_TYPE_CODE = "code"
private const val CHALLENGE_METHOD_S256 = "S256"

private const val PARAM_CODE = "code"
private const val PARAM_STATE = "state"
private const val PARAM_ERROR = "error"

/** A random PKCE code verifier: [SpotifyConstants.VERIFIER_LENGTH] unreserved characters. */
fun generateVerifier(random: SecureRandom = SecureRandom()): String =
    randomString(random, SpotifyConstants.VERIFIER_LENGTH)

/** A random OAuth `state`: [SpotifyConstants.STATE_LENGTH] unreserved characters. */
fun generateState(random: SecureRandom = SecureRandom()): String =
    randomString(random, SpotifyConstants.STATE_LENGTH)

private fun randomString(random: SecureRandom, length: Int): String {
    val alphabet = SpotifyConstants.VERIFIER_ALPHABET
    return buildString(length) {
        repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
}

/** The S256 code challenge of [verifier]: base64url, no padding, of SHA-256 of its ASCII bytes. */
fun challengeOf(verifier: String): String {
    val digest = MessageDigest.getInstance(SHA256_ALGORITHM).digest(verifier.toByteArray(StandardCharsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

/** The Spotify authorize URL the browser opens, with every query value URL-encoded. */
fun authorizeUrl(
    clientId: String,
    redirectUri: String,
    challenge: String,
    state: String,
    base: String = SpotifyConstants.AUTHORIZE_URL,
): String {
    val params = listOf(
        "response_type" to RESPONSE_TYPE_CODE,
        "client_id" to clientId,
        "scope" to SpotifyConstants.SCOPE,
        "code_challenge_method" to CHALLENGE_METHOD_S256,
        "code_challenge" to challenge,
        "redirect_uri" to redirectUri,
        PARAM_STATE to state,
    )
    return "$base?${formEncode(params)}"
}

/** `k=v&k=v` with each key and value URL-encoded (UTF-8). Shared with the token request body. */
internal fun formEncode(params: List<Pair<String, String>>): String =
    params.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

/** What the browser brought back to the loopback listener. */
sealed interface CallbackResult {
    /** Spotify approved; exchange [code] for tokens. */
    data class Code(val code: String) : CallbackResult

    /** Spotify reported [error], e.g. `access_denied` when the player cancelled. */
    data class Denied(val error: String) : CallbackResult

    /** The `state` didn't match the one this login sent. */
    data object StateMismatch : CallbackResult

    /** Neither a code nor an error. */
    data object Missing : CallbackResult

    /** No callback arrived in time. */
    data object Timeout : CallbackResult
}

/**
 * Read the callback's raw query. An `error` wins over the state check so a denial is reported as
 * such; otherwise the state must match before a `code` is accepted. No query, or one that isn't
 * valid percent-encoding, is [CallbackResult.Missing].
 */
fun parseCallback(rawQuery: String?, expectedState: String): CallbackResult {
    if (rawQuery.isNullOrEmpty()) return CallbackResult.Missing
    val params = try {
        decodeQuery(rawQuery)
    } catch (_: IllegalArgumentException) {
        return CallbackResult.Missing
    }
    params[PARAM_ERROR]?.let { return CallbackResult.Denied(it) }
    if (params[PARAM_STATE] != expectedState) return CallbackResult.StateMismatch
    return params[PARAM_CODE]?.let { CallbackResult.Code(it) } ?: CallbackResult.Missing
}

/** Decode `k=v&k=v` into a map; the first value of a repeated key wins. Throws on bad `%` escapes. */
private fun decodeQuery(rawQuery: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (pair in rawQuery.split('&')) {
        if (pair.isEmpty()) continue
        val key = urlDecode(pair.substringBefore('='))
        val value = urlDecode(pair.substringAfter('=', ""))
        result.putIfAbsent(key, value)
    }
    return result
}

private fun urlDecode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
