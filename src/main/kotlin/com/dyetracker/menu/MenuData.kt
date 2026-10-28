package com.dyetracker.menu

import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.DyeProgressResponse
import com.dyetracker.api.PlayerProfilesResponse
import com.dyetracker.api.ProfileSummary

/** Outcome of loading the in-game menu's dyes for one player. */
sealed class MenuLoadResult {
    /** The player's [profiles], the [profile] shown, and its [dyes] in API order. */
    data class Loaded(
        val username: String,
        val profiles: List<ProfileSummary>,
        val profile: ProfileSummary,
        val dyes: List<DyeProgressEntry>,
    ) : MenuLoadResult()

    /** The player exists but has no SkyBlock profiles. */
    data class NoProfiles(val username: String) : MenuLoadResult()

    /** A call failed; [message] is the API or network error text. */
    data class Failed(val message: String) : MenuLoadResult()
}

/** Totals over all of a profile's dyes: how many there are and how many are [complete] (at 100%). */
data class MenuTotals(val dyes: Int, val complete: Int)

/**
 * Pure data load for the in-game menu: the player's profiles, then the dyes of the profile
 * selected in-game. The fetchers are injected so this is testable without the network; the
 * screen passes the blocking [ApiClient] calls and runs [load] off the render thread.
 */
object MenuData {

    /** The profile selected in-game, else the first, else null for an empty list. */
    fun selectProfile(profiles: List<ProfileSummary>): ProfileSummary? =
        profiles.firstOrNull { it.selected } ?: profiles.firstOrNull()

    /** Fetch [username]'s profiles, pick one with [selectProfile], then fetch its dyes. */
    fun load(
        username: String,
        fetchProfiles: (String) -> ApiClient.ApiResult<PlayerProfilesResponse>,
        fetchDyes: (String, String) -> ApiClient.ApiResult<DyeProgressResponse>,
    ): MenuLoadResult {
        val profilesResponse = when (val result = fetchProfiles(username)) {
            is ApiClient.ApiResult.Error -> return MenuLoadResult.Failed(result.message)
            is ApiClient.ApiResult.Success -> result.data
        }
        val profile = selectProfile(profilesResponse.profiles)
            ?: return MenuLoadResult.NoProfiles(username)
        val dyesResponse = when (val result = fetchDyes(username, profile.id)) {
            is ApiClient.ApiResult.Error -> return MenuLoadResult.Failed(result.message)
            is ApiClient.ApiResult.Success -> result.data
        }
        return MenuLoadResult.Loaded(
            username = profilesResponse.username,
            profiles = profilesResponse.profiles,
            profile = profile,
            dyes = dyesResponse.dyes,
        )
    }

    /**
     * The profile after [current] (matched by `id`) in [profiles], wrapping from the last to the
     * first; [current] itself when it is the only profile or isn't in [profiles].
     */
    fun nextProfile(profiles: List<ProfileSummary>, current: ProfileSummary): ProfileSummary {
        val index = profiles.indexOfFirst { it.id == current.id }
        if (index < 0) return current
        return profiles[(index + 1) % profiles.size]
    }

    /**
     * Fetch the dyes of [profile] for a player whose [username] and [profiles] are already known
     * (the profile switcher); no profiles call.
     */
    fun loadProfile(
        username: String,
        profiles: List<ProfileSummary>,
        profile: ProfileSummary,
        fetchDyes: (String, String) -> ApiClient.ApiResult<DyeProgressResponse>,
    ): MenuLoadResult = when (val result = fetchDyes(username, profile.id)) {
        is ApiClient.ApiResult.Error -> MenuLoadResult.Failed(result.message)
        is ApiClient.ApiResult.Success -> MenuLoadResult.Loaded(username, profiles, profile, result.data.dyes)
    }

    /** Totals over every one of [dyes] (not a filtered list); a dye is complete at 100% or more. */
    fun totals(dyes: List<DyeProgressEntry>): MenuTotals = MenuTotals(
        dyes = dyes.size,
        complete = dyes.count { it.progress != null && it.progress >= COMPLETE_PROGRESS },
    )

    /** The strip's totals line, e.g. `64 dyes · 3 at 100%`. */
    fun totalsText(t: MenuTotals): String = "${t.dyes} dyes · ${t.complete} at 100%"

    /** Progress (percent) at which a dye counts as complete. */
    private const val COMPLETE_PROGRESS = 100.0
}
