package com.dyetracker.menu

import com.dyetracker.api.AchievementsResponse
import com.dyetracker.api.CollectionResponse
import com.dyetracker.api.HistoryDyeSeriesResponse
import com.dyetracker.api.HistoryResponse

/** A value kept in a [SessionCache], with when it was fetched (epoch ms). */
data class Cached<T>(val value: T, val fetchedAtMs: Long)

/**
 * A menu tab's last fetched data per player and profile, kept in memory for the game session
 * (never persisted) so reopening the tab or switching back to a profile shows data at once, like
 * [MenuCache] does for the Dyes tab. Usernames are matched case-insensitively. Not synchronized:
 * only touched on the render thread.
 * The History tab's "profile" is its profile and period ([historyKey]), its chart's also the dye
 * ([historySeriesKey]).
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

/**
 * The History tab's history for the whole game session, per profile and period: the profile id
 * passed in is [historyKey]'s `"$profileId|${period.apiValue}"`.
 */
val HISTORY_CACHE = SessionCache<HistoryResponse>()

/**
 * The History tab's chart series for the whole game session, per profile, period and dye: the
 * profile id passed in is [historySeriesKey]'s `"$profileId|${period.apiValue}|$dyeId"`.
 */
val HISTORY_SERIES_CACHE = SessionCache<HistoryDyeSeriesResponse>()
