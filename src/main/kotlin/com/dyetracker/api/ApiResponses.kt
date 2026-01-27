package com.dyetracker.api

import kotlinx.serialization.Serializable

/**
 * Response from POST /api/v1/auth/start-verify
 */
@Serializable
data class StartVerifyResponse(
    val serverId: String
)

/**
 * Response from POST /api/v1/auth/complete-verify
 */
@Serializable
data class CompleteVerifyResponse(
    val success: Boolean,
    val uuid: String,
    val username: String
)

/**
 * Error response from the API
 */
@Serializable
data class ApiErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)
