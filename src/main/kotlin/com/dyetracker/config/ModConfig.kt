package com.dyetracker.config

import kotlinx.serialization.Serializable

/**
 * Configuration data class for the DyeTracker mod.
 *
 * This class defines all configurable options for the mod.
 * The configuration is stored as JSON in the config directory.
 */
@Serializable
data class ModConfig(
    /**
     * The URL of the DyeTracker API server.
     */
    val apiUrl: String = DEFAULT_API_URL,

    /**
     * Authentication token for API requests.
     * Leave empty if not authenticated.
     */
    val authToken: String = DEFAULT_AUTH_TOKEN
) {
    companion object {
        const val DEFAULT_API_URL = "https://api.dyetracker.com"
        const val DEFAULT_AUTH_TOKEN = ""
    }
}
