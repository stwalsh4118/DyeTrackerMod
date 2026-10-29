package com.dyetracker.discord

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Discord activity type "Playing", shown as "Playing DyeTracker". */
private const val ACTIVITY_TYPE_PLAYING = 0

/**
 * What the Discord card should show. Every nullable field is left out of the activity when null,
 * so Discord shows nothing for it. [startEpochSeconds] makes Discord count up an elapsed timer.
 */
data class PresenceCard(
    val details: String? = null,
    val state: String? = null,
    val largeImage: String = DISCORD_LOGO_ASSET,
    val largeText: String? = DISCORD_LOGO_TEXT,
    val smallImage: String? = null,
    val smallText: String? = null,
    val startEpochSeconds: Long? = null,
    val buttons: List<PresenceButton> = emptyList(),
)

/** A link button on the card. */
data class PresenceButton(val label: String, val url: String)

/** This card as the `activity` object of a SET_ACTIVITY request. */
fun PresenceCard.toActivityJson(): JsonObject = buildJsonObject {
    put("type", ACTIVITY_TYPE_PLAYING)
    details?.let { put("details", it) }
    state?.let { put("state", it) }
    putJsonObject("assets") {
        put("large_image", largeImage)
        largeText?.let { put("large_text", it) }
        smallImage?.let { put("small_image", it) }
        smallText?.let { put("small_text", it) }
    }
    startEpochSeconds?.let { start -> putJsonObject("timestamps") { put("start", start) } }
    if (buttons.isNotEmpty()) {
        putJsonArray("buttons") {
            for (button in buttons) {
                addJsonObject {
                    put("label", button.label)
                    put("url", button.url)
                }
            }
        }
    }
}
