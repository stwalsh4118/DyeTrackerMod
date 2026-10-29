package com.dyetracker.discord

import com.dyetracker.api.ApiClient
import com.dyetracker.api.CollectionEntry
import com.dyetracker.api.CollectionResponse
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.DyeProgressResponse
import com.dyetracker.api.PlayerProfilesResponse
import com.dyetracker.api.ProfileSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/** [loadPresenceStats] with fake fetchers: the card's owned / total / dropped numbers and which profile they are for. */
class PresenceStatsTest {

    private val first = ProfileSummary(id = "p-first", cuteName = "Apple")
    private val selected = ProfileSummary(id = "p-selected", cuteName = "Banana", selected = true)

    private val catalog = listOf(
        DyeProgressEntry(dyeId = "a", name = "A Dye", color = "#000001", category = "Mining"),
        DyeProgressEntry(dyeId = "b", name = "B Dye", color = "#000002", category = "Slayer"),
        DyeProgressEntry(dyeId = "c", name = "C Dye", color = "#000003", category = "Fishing"),
    )

    private val owned = listOf(
        CollectionEntry(dyeId = "a", count = 1),
        CollectionEntry(dyeId = "b", count = 2),
        CollectionEntry(dyeId = "x", count = 5),
    )

    private fun profilesOk(vararg profiles: ProfileSummary) = { _: String ->
        ApiClient.ApiResult.Success(PlayerProfilesResponse(uuid = UUID, username = USERNAME, profiles = profiles.toList()))
    }

    private fun dyesOk(dyes: List<DyeProgressEntry> = catalog) = { _: String, profileId: String ->
        ApiClient.ApiResult.Success(DyeProgressResponse(username = USERNAME, profile = ProfileSummary(profileId, "x"), dyes = dyes))
    }

    private fun collectionOk(entries: List<CollectionEntry> = owned) = { _: String, _: String ->
        ApiClient.ApiResult.Success(CollectionResponse(uuid = UUID, username = USERNAME, dyes = entries))
    }

    private val failed = ApiClient.ApiResult.Error("boom", 500)

    @Test
    fun `owned and dropped count only the dyes in the list`() {
        val stats = loadPresenceStats(USERNAME, profilesOk(first), dyesOk(), collectionOk())

        assertEquals(
            PresenceStats(UUID, first.id, first.cuteName, catalog, owned = 2, total = 3, dropped = 3),
            stats,
        )
    }

    @Test
    fun `the profile selected in-game is used over the first`() {
        val asked = mutableListOf<String>()
        val stats = loadPresenceStats(
            USERNAME,
            profilesOk(first, selected),
            { user, profileId -> asked += profileId; dyesOk()(user, profileId) },
            { user, profileId -> asked += profileId; collectionOk()(user, profileId) },
        )

        assertEquals(selected.id, stats?.profileId)
        assertEquals(selected.cuteName, stats?.profileName)
        assertEquals(listOf(selected.id, selected.id), asked)
    }

    @Test
    fun `a collection error drops owned and dropped but keeps the total`() {
        val stats = loadPresenceStats(USERNAME, profilesOk(first), dyesOk(), { _, _ -> failed })

        assertEquals(PresenceStats(UUID, first.id, first.cuteName, catalog, owned = null, total = 3, dropped = null), stats)
    }

    @Test
    fun `a dyes error drops all three numbers`() {
        val stats = loadPresenceStats(USERNAME, profilesOk(first), { _, _ -> failed }, collectionOk())

        assertEquals(PresenceStats(UUID, first.id, first.cuteName, emptyList(), owned = null, total = null, dropped = null), stats)
    }

    @Test
    fun `an empty collection shows no owned count rather than 0`() {
        val stats = loadPresenceStats(USERNAME, profilesOk(first), dyesOk(), collectionOk(emptyList()))

        assertNull(stats?.owned)
        assertNull(stats?.dropped)
        assertEquals(3, stats?.total)
    }

    @Test
    fun `owned dyes none of which are in the list count as none`() {
        val stats = loadPresenceStats(USERNAME, profilesOk(first), dyesOk(), collectionOk(listOf(CollectionEntry(dyeId = "x"))))

        assertNull(stats?.owned)
        assertNull(stats?.dropped)
    }

    @Test
    fun `a profiles error gives no stats`() {
        assertNull(loadPresenceStats(USERNAME, { failed }, dyesOk(), collectionOk()))
    }

    @Test
    fun `no profiles gives no stats`() {
        assertNull(loadPresenceStats(USERNAME, profilesOk(), dyesOk(), collectionOk()))
    }

    @Test
    fun `a blank username gives no stats and fetches nothing`() {
        for (username in listOf("", "   ")) {
            assertNull(
                loadPresenceStats(
                    username,
                    { fail("fetchProfiles must not be called") },
                    { _, _ -> fail("fetchDyes must not be called") },
                    { _, _ -> fail("fetchCollection must not be called") },
                ),
            )
        }
    }

    private companion object {
        const val USERNAME = "Steve"
        const val UUID = "uuid-1"
    }
}
