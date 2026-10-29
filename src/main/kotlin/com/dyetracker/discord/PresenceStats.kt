package com.dyetracker.discord

import com.dyetracker.api.ApiClient.ApiResult
import com.dyetracker.api.CollectionResponse
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.DyeProgressResponse
import com.dyetracker.api.PlayerProfilesResponse
import com.dyetracker.menu.MenuCollection
import com.dyetracker.menu.MenuData

/**
 * What the Discord card shows about the player: whose page the button opens ([uuid], [profileId],
 * [profileName]), the profile's [dyes] in API order, and how many of the [total] dyes they [owned]
 * and how many they [dropped] in all. A number is `null` when the data behind it is missing, so the
 * card leaves its line out rather than showing a wrong one.
 */
data class PresenceStats(
    val uuid: String,
    val profileId: String,
    val profileName: String,
    val dyes: List<DyeProgressEntry>,
    val owned: Int?,
    val total: Int?,
    val dropped: Int?,
)

/**
 * Loads [username]'s card stats: their profiles, the one [MenuData.selectProfile] picks, its dyes
 * and the dyes they own on it. Blocking (the fetchers are the [com.dyetracker.api.ApiClient]
 * calls), so only the presence worker calls it.
 *
 * `null` for a blank [username] (nothing is fetched), a profiles error or no profile. A dyes error
 * leaves [PresenceStats.dyes] empty and all three numbers `null`; a collection error leaves owned
 * and dropped `null`. Only dyes in the API's list count, and owning none of them also leaves owned
 * and dropped `null` (no Vincent's Dye Guide scan yet), so the card never shows `0/42`.
 */
fun loadPresenceStats(
    username: String,
    fetchProfiles: (String) -> ApiResult<PlayerProfilesResponse>,
    fetchDyes: (String, String) -> ApiResult<DyeProgressResponse>,
    fetchCollection: (String, String) -> ApiResult<CollectionResponse>,
): PresenceStats? {
    if (username.isBlank()) return null
    val profiles = when (val result = fetchProfiles(username)) {
        is ApiResult.Error -> return null
        is ApiResult.Success -> result.data
    }
    val profile = MenuData.selectProfile(profiles.profiles) ?: return null
    val noNumbers = PresenceStats(
        uuid = profiles.uuid,
        profileId = profile.id,
        profileName = profile.cuteName,
        dyes = emptyList(),
        owned = null,
        total = null,
        dropped = null,
    )
    val dyes = when (val result = fetchDyes(username, profile.id)) {
        is ApiResult.Error -> return noNumbers
        is ApiResult.Success -> result.data.dyes
    }
    val totalOnly = noNumbers.copy(dyes = dyes, total = dyes.size)
    val collection = when (val result = fetchCollection(username, profile.id)) {
        is ApiResult.Error -> return totalOnly
        is ApiResult.Success -> result.data.dyes
    }
    val cells = MenuCollection.cells(dyes, collection)
    val owned = cells.count { it.owned }
    if (owned == 0) return totalOnly
    // One cell per listed dye, so the total stays dyes.size.
    return totalOnly.copy(owned = owned, dropped = cells.sumOf { it.count })
}

/**
 * The presence worker's copy of the card's stats. [get] loads them when none were loaded yet for
 * this username, [STATS_REFRESH_MS] after the last load, or at once when the username changed (the
 * old player's stats are dropped then, never shown for the new one). A failed reload keeps the last
 * good stats; a failed load is tried again after [STATS_REFRESH_MS], not every tick. Not
 * thread-safe: only the presence worker calls it, and only while the card is wanted, so nothing is
 * fetched while it is not and the stats are kept for the next time.
 *
 * @param load blocking stats load for a username, e.g. [loadPresenceStats] over the API.
 * @param clock the current time in ms.
 */
class PresenceStatsCache(
    private val load: (String) -> PresenceStats?,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var username: String? = null
    private var stats: PresenceStats? = null

    /** When [load] last ran for [username], or `null` before the first. */
    private var loadedAtMs: Long? = null

    /** [username]'s stats, loaded first when due; `null` while none were ever loaded for them. */
    fun get(username: String): PresenceStats? {
        if (username != this.username) {
            this.username = username
            stats = null
            loadedAtMs = null
        }
        val now = clock()
        val last = loadedAtMs
        if (last == null || now - last >= STATS_REFRESH_MS) {
            loadedAtMs = now
            load(username)?.let { stats = it }
        }
        return stats
    }
}
