package com.dyetracker.discord

import com.dyetracker.api.ApiClient.ApiResult
import com.dyetracker.api.CollectionResponse
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.DyeProgressResponse
import com.dyetracker.api.PlayerProfilesResponse
import com.dyetracker.menu.MenuCollection
import com.dyetracker.menu.MenuData
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

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

/** Runs a stats load somewhere other than the caller and hands back its pending result. */
typealias StatsLauncher = (() -> PresenceStats?) -> Deferred<PresenceStats?>

/**
 * The presence worker's copy of the card's stats. [get] never waits for a load: it starts one
 * through [launch] when none was started yet for this username or [STATS_REFRESH_MS] after the
 * last one started, never while one is pending, and picks up a finished one on a later call. A
 * username change drops the old stats and any pending load at once, so the old player's numbers
 * are never shown for the new one. A failed reload keeps the last good stats; a failed load is
 * tried again after [STATS_REFRESH_MS], not every call.
 *
 * Not thread-safe: only the presence worker calls it, and only while the card is wanted, so
 * nothing is fetched while it is not and the stats are kept for the next time. The load itself
 * runs wherever [launch] puts it; its result crosses back through the [Deferred].
 *
 * @param launch starts a load off the caller's thread, e.g. `async` on an IO scope.
 * @param load blocking stats load for a username, e.g. [loadPresenceStats] over the API.
 * @param clock the current time in ms.
 */
class PresenceStatsCache(
    private val launch: StatsLauncher,
    private val load: (String) -> PresenceStats?,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var username: String? = null
    private var stats: PresenceStats? = null

    /** When the last load for [username] started, or `null` before the first. */
    private var loadedAtMs: Long? = null

    /** The load still running for [username], if any. */
    private var pending: Deferred<PresenceStats?>? = null

    /** Whether a load for [username] has finished, successfully or not. */
    private var settled = false

    /**
     * Whether the first load for the current username is still running: the stats aren't known
     * yet, as opposed to known to be missing. The worker holds the card back then, so it doesn't
     * send a bare card that would hold the stats card back behind the send throttle.
     */
    val firstLoadPending: Boolean get() = !settled && pending != null

    /**
     * [username]'s stats as known now, without waiting: picks up a finished load, starts one when
     * due, and returns `null` while none ever finished for them with a result.
     */
    fun get(username: String): PresenceStats? {
        if (username != this.username) {
            pending?.cancel()
            this.username = username
            stats = null
            loadedAtMs = null
            pending = null
            settled = false
        }
        collect()
        val now = clock()
        val last = loadedAtMs
        if (pending == null && (last == null || now - last >= STATS_REFRESH_MS)) {
            loadedAtMs = now
            pending = launch { load(username) }
            // A launcher that finished already (no real I/O) needs no extra call.
            collect()
        }
        return stats
    }

    /** Takes a finished [pending] load's result; a load that threw counts as a failed one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collect() {
        val done = pending?.takeIf { it.isCompleted } ?: return
        pending = null
        settled = true
        runCatching { done.getCompleted() }.getOrNull()?.let { stats = it }
    }
}
