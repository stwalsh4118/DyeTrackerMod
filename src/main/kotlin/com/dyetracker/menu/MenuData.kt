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
}
