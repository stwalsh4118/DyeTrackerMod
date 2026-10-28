package com.dyetracker.menu

/** A menu load result kept in [MenuCache], with when it was fetched (epoch ms). */
data class CachedMenu(val result: MenuLoadResult.Loaded, val fetchedAtMs: Long)

/**
 * The in-game menu's last loaded dyes per player and profile, kept in memory for the game
 * session (never persisted) so reopening the menu or switching back to a profile shows data at
 * once. Usernames are matched case-insensitively. Not synchronized: only touched on the render
 * thread.
 */
class MenuCache {
    private val entries = HashMap<String, CachedMenu>()

    /** The id of each player's (lowercased username) last viewed profile. */
    private val lastProfileId = HashMap<String, String>()

    /** Stores [result] as fetched at [nowMs] and makes its profile the player's last viewed one. */
    fun put(result: MenuLoadResult.Loaded, nowMs: Long) {
        val user = result.username.lowercase()
        entries[key(user, result.profile.id)] = CachedMenu(result, nowMs)
        lastProfileId[user] = result.profile.id
    }

    /** The entry for [username]'s last viewed profile, or `null` when none is cached. */
    fun lastFor(username: String): CachedMenu? {
        val user = username.lowercase()
        val profileId = lastProfileId[user] ?: return null
        return entries[key(user, profileId)]
    }

    /** The entry for [username]'s profile [profileId], or `null` when it isn't cached. */
    fun get(username: String, profileId: String): CachedMenu? = entries[key(username.lowercase(), profileId)]

    /** Forgets every entry. */
    fun clear() {
        entries.clear()
        lastProfileId.clear()
    }

    private fun key(lowercaseUsername: String, profileId: String): String = "$lowercaseUsername|$profileId"
}

/** The menu cache for the whole game session. */
val MENU_CACHE = MenuCache()

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** Ages under this many seconds read "just now". */
private const val JUST_NOW_SECONDS = 5L

/**
 * How old data fetched at [fetchedAtMs] is at [nowMs]: `updated just now` under 5 s, then
 * `updated 12s ago`, `updated 3m ago`, `updated 2h ago` (each rounded down). A negative age
 * (clock moved back) counts as 0.
 */
fun formatAge(nowMs: Long, fetchedAtMs: Long): String {
    val seconds = (nowMs - fetchedAtMs).coerceAtLeast(0L) / MS_PER_SECOND
    return when {
        seconds < JUST_NOW_SECONDS -> "updated just now"
        seconds < SECONDS_PER_MINUTE -> "updated ${seconds}s ago"
        seconds < SECONDS_PER_HOUR -> "updated ${seconds / SECONDS_PER_MINUTE}m ago"
        else -> "updated ${seconds / SECONDS_PER_HOUR}h ago"
    }
}

/**
 * Whether the menu pushes the player's mod data before fetching: only when the account
 * [isLinked], a sync is [syncPending] and there is mod data to send ([hasData]).
 */
fun shouldSyncFirst(isLinked: Boolean, syncPending: Boolean, hasData: Boolean): Boolean =
    isLinked && syncPending && hasData
