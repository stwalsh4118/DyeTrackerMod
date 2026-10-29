package com.dyetracker.menu

import com.dyetracker.api.AchievementsResponse
import com.dyetracker.api.CollectionResponse

/** A value kept in a [SessionCache], with when it was fetched (epoch ms). */
data class Cached<T>(val value: T, val fetchedAtMs: Long)

/**
 * A menu tab's last fetched data per player and profile, kept in memory for the game session
 * (never persisted) so reopening the tab or switching back to a profile shows data at once, like
 * [MenuCache] does for the Dyes tab. Usernames are matched case-insensitively. Not synchronized:
 * only touched on the render thread.
 */
class SessionCache<T> {
    private val entries = HashMap<String, Cached<T>>()

    /** Stores [value] for [username]'s profile [profileId] as fetched at [nowMs], replacing any entry. */
    fun put(username: String, profileId: String, value: T, nowMs: Long) {
        entries[key(username, profileId)] = Cached(value, nowMs)
    }

    /** The entry for [username]'s profile [profileId], or `null` when it isn't cached. */
    fun get(username: String, profileId: String): Cached<T>? = entries[key(username, profileId)]

    /** Forgets every entry. */
    fun clear() {
        entries.clear()
    }

    private fun key(username: String, profileId: String): String = "${username.lowercase()}|$profileId"
}

/** The Collection tab's (and the Dyes tab's OBTAINED badges') collections for the whole game session. */
val COLLECTION_CACHE = SessionCache<CollectionResponse>()

/** The Achievements tab's achievements for the whole game session. */
val ACHIEVEMENTS_CACHE = SessionCache<AchievementsResponse>()
