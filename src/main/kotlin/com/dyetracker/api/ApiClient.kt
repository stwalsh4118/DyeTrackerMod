package com.dyetracker.api

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP client for communicating with the DyeTracker API.
 */
object ApiClient {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val CONTENT_TYPE_JSON = "application/json"

    /**
     * Result of an API call.
     */
    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String, val statusCode: Int = 0) : ApiResult<Nothing>()
    }

    /**
     * Start the verification process with a link code.
     * Returns a serverId challenge for Mojang verification.
     */
    fun startVerify(code: String): ApiResult<StartVerifyResponse> {
        val url = "${ConfigManager.config.apiUrl}/api/v1/auth/start-verify"
        val body = """{"code":"$code"}"""

        DyeTrackerMod.info("API: POST {} (code={})", url, code)

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            DyeTrackerMod.info("API: Response status={}", response.statusCode())

            if (response.statusCode() == 200) {
                val data = json.decodeFromString<StartVerifyResponse>(response.body())
                DyeTrackerMod.info("API: start-verify success, received serverId")
                ApiResult.Success(data)
            } else {
                val error = parseError(response.body())
                DyeTrackerMod.warn("start-verify failed: {} ({})", error, response.statusCode())
                ApiResult.Error(error, response.statusCode())
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("start-verify exception", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Complete the verification process after calling Mojang's joinServer.
     */
    fun completeVerify(code: String, username: String): ApiResult<CompleteVerifyResponse> {
        val url = "${ConfigManager.config.apiUrl}/api/v1/auth/complete-verify"
        val body = """{"code":"$code","username":"$username"}"""

        DyeTrackerMod.info("API: POST {} (code={}, username={})", url, code, username)

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            DyeTrackerMod.info("API: Response status={}", response.statusCode())

            if (response.statusCode() == 200) {
                val data = json.decodeFromString<CompleteVerifyResponse>(response.body())
                DyeTrackerMod.info("API: complete-verify success, uuid={}", data.uuid)
                ApiResult.Success(data)
            } else {
                val error = parseError(response.body())
                DyeTrackerMod.warn("complete-verify failed: {} ({})", error, response.statusCode())
                ApiResult.Error(error, response.statusCode())
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("complete-verify exception", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    private fun parseError(body: String): String {
        return try {
            val errorResponse = json.decodeFromString<ApiErrorResponse>(body)
            errorResponse.message
        } catch (e: Exception) {
            "Unknown error"
        }
    }
}
