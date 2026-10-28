package com.dyetracker.menu

import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.DyeProgressResponse
import com.dyetracker.api.PlayerProfilesResponse
import com.dyetracker.api.ProfileSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail

/** Unit coverage for the menu's pure profile selection and two-call data load (task 67-1). */
class MenuDataTest {

    private val mango = ProfileSummary(id = "p-mango", cuteName = "Mango")
    private val banana = ProfileSummary(id = "p-banana", cuteName = "Banana", selected = true)
    private val apple = ProfileSummary(id = "p-apple", cuteName = "Apple")

    private val dyes = listOf(
        DyeProgressEntry(dyeId = "matcha", name = "Matcha Dye", color = "#7cb342", category = "Slayer", progress = 42.5),
        DyeProgressEntry(dyeId = "fossil", name = "Fossil Dye", color = "#5d4037", category = "Mining"),
        DyeProgressEntry(dyeId = "lava", name = "Lava Dye", color = "#ff5722", category = "Mining", progress = 100.0),
    )

    private fun profilesOk(vararg profiles: ProfileSummary) = { _: String ->
        ApiClient.ApiResult.Success(PlayerProfilesResponse(uuid = "uuid-1", username = "Steve", profiles = profiles.toList()))
    }

    private val dyesNeverCalled = { _: String, _: String ->
        fail("fetchDyes must not be called")
    }

    @Test
    fun `selectProfile picks the selected profile when it is not first`() {
        assertEquals(banana, MenuData.selectProfile(listOf(mango, banana, apple)))
    }

    @Test
    fun `selectProfile falls back to the first profile when none is selected`() {
        assertEquals(mango, MenuData.selectProfile(listOf(mango, apple)))
    }

    @Test
    fun `selectProfile returns null for an empty list`() {
        assertNull(MenuData.selectProfile(emptyList()))
    }

    @Test
    fun `load returns Failed with the message when the profiles call errors`() {
        val result = MenuData.load(
            "Steve",
            fetchProfiles = { ApiClient.ApiResult.Error("boom", 500) },
            fetchDyes = dyesNeverCalled,
        )

        assertEquals(MenuLoadResult.Failed("boom"), result)
    }

    @Test
    fun `load returns NoProfiles when the player has no profiles`() {
        val result = MenuData.load("Steve", fetchProfiles = profilesOk(), fetchDyes = dyesNeverCalled)

        assertEquals(MenuLoadResult.NoProfiles("Steve"), result)
    }

    @Test
    fun `load returns Failed with the message when the dyes call errors`() {
        val result = MenuData.load(
            "Steve",
            fetchProfiles = profilesOk(mango, banana),
            fetchDyes = { _, _ -> ApiClient.ApiResult.Error("dyes down", 503) },
        )

        assertEquals(MenuLoadResult.Failed("dyes down"), result)
    }

    @Test
    fun `load returns Loaded with the selected profile and the dyes in API order`() {
        val calls = mutableListOf<Pair<String, String>>()
        val result = MenuData.load(
            "steve",
            fetchProfiles = profilesOk(mango, banana, apple),
            fetchDyes = { username, profileId ->
                calls += username to profileId
                ApiClient.ApiResult.Success(DyeProgressResponse(username = "Steve", profile = banana, dyes = dyes))
            },
        )

        val loaded = assertIs<MenuLoadResult.Loaded>(result)
        assertEquals("Steve", loaded.username) // canonical name from the profiles response
        assertEquals(listOf(mango, banana, apple), loaded.profiles)
        assertEquals(banana, loaded.profile)
        assertEquals(listOf("matcha", "fossil", "lava"), loaded.dyes.map { it.dyeId })
        assertEquals(listOf("steve" to "p-banana"), calls)
    }
}
