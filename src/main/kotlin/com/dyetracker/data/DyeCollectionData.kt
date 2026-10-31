package com.dyetracker.data

import kotlinx.serialization.Serializable

/**
 * A single dye that a player has obtained (dropped) from Vincent NPC.
 */
@Serializable
data class DroppedDye(
    /** Dye ID matching the website's dye constants (e.g., "matcha", "jade") */
    val dyeId: String,
    /** Number of times the player has dropped this dye */
    val count: Int = 0,
    /** Unix timestamp in ms when the dye was obtained, null if unknown */
    val obtainedAt: Long? = null,
    /** Additional metadata from item NBT, null if none */
    val metadata: Map<String, String>? = null
)

/**
 * A player's full dye collection captured from Vincent NPC inventory.
 */
@Serializable
data class DyeCollection(
    /** SkyBlock profile ID this collection belongs to */
    val profileId: String,
    /** List of dropped dyes found in Vincent's inventory */
    val dyes: List<DroppedDye> = emptyList(),
    /** Unix timestamp in ms when this collection was last scanned */
    val lastUpdated: Long = 0
)

/**
 * Dyes to delete from one SkyBlock profile's collection on the server: the dyes the Dye Guide
 * showed at 0 owned, and the profile it showed them on.
 */
data class DyeRemovals(
    /** Hypixel profile ID, from the "Profile ID:" chat line */
    val profileId: String,
    val dyeIds: Set<String>
) {
    /** Add [next] to these. Another profile's scan replaces them; reopening the Guide re-queues. */
    fun merge(next: DyeRemovals): DyeRemovals =
        if (next.profileId == profileId) copy(dyeIds = dyeIds + next.dyeIds) else next
}
