package com.dyetracker.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit coverage for what the strip's Refresh pill fetches on each tab, and when it is ignored (68-8);
 * and what a landed dyes load fetches next, again after the menu synced first (PBI 68 branch review).
 */
class MenuRefreshTest {

    @Test
    fun `on the Dyes tab Refresh fetches the dyes and the collection`() {
        assertEquals(RefreshPlan(dyes = true, collection = true, achievements = false), MenuRefresh.DYES_TAB)
    }

    @Test
    fun `on the Achievements tab Refresh fetches only the achievements`() {
        assertEquals(RefreshPlan(dyes = false, collection = false, achievements = true), MenuRefresh.ACHIEVEMENTS_TAB)
    }

    @Test
    fun `on the History tab Refresh fetches only the history`() {
        assertEquals(
            RefreshPlan(dyes = false, collection = false, achievements = false, history = true),
            MenuRefresh.HISTORY_TAB,
        )
    }

    @Test
    fun `Refresh on the other tabs never fetches the history`() {
        assertFalse(MenuRefresh.DYES_TAB.history)
        assertFalse(MenuRefresh.ACHIEVEMENTS_TAB.history)
        assertFalse(MenuRefresh.collectionTab(dyesLoaded = true).history)
        assertFalse(MenuRefresh.collectionTab(dyesLoaded = false).history)
    }

    @Test
    fun `on the Collection tab with the dyes loaded Refresh fetches only the collection`() {
        assertEquals(
            RefreshPlan(dyes = false, collection = true, achievements = false),
            MenuRefresh.collectionTab(dyesLoaded = true),
        )
    }

    @Test
    fun `on the Collection tab without the dyes loaded Refresh also fetches the dyes`() {
        assertEquals(
            RefreshPlan(dyes = true, collection = true, achievements = false),
            MenuRefresh.collectionTab(dyesLoaded = false),
        )
    }

    @Test
    fun `Refresh on the Collection tab is ignored while the collection fetch or the dyes load behind Loading runs`() {
        assertFalse(MenuRefresh.collectionTabBlocked(collectionBusy = false, dyesLoading = false))

        assertTrue(MenuRefresh.collectionTabBlocked(collectionBusy = true, dyesLoading = false))
        assertTrue(MenuRefresh.collectionTabBlocked(collectionBusy = false, dyesLoading = true))
        assertTrue(MenuRefresh.collectionTabBlocked(collectionBusy = true, dyesLoading = true))
    }

    @Test
    fun `a load without a sync first only ensures the collection, and the achievements on their tab`() {
        assertEquals(
            LoadFollowUp(collection = FollowUp.ENSURE, achievements = FollowUp.NONE),
            MenuRefresh.afterLoad(syncedFirst = false, achievementsHeld = false, onAchievementsTab = false),
        )
        assertEquals(
            LoadFollowUp(collection = FollowUp.ENSURE, achievements = FollowUp.NONE),
            MenuRefresh.afterLoad(syncedFirst = false, achievementsHeld = true, onAchievementsTab = false),
        )
        assertEquals(
            LoadFollowUp(collection = FollowUp.ENSURE, achievements = FollowUp.ENSURE),
            MenuRefresh.afterLoad(syncedFirst = false, achievementsHeld = true, onAchievementsTab = true),
        )
        assertEquals(
            LoadFollowUp(collection = FollowUp.ENSURE, achievements = FollowUp.ENSURE),
            MenuRefresh.afterLoad(syncedFirst = false, achievementsHeld = false, onAchievementsTab = true),
        )
    }

    @Test
    fun `a load after a sync first fetches the collection again, and achievements held or shown`() {
        assertEquals(
            LoadFollowUp(collection = FollowUp.FETCH, achievements = FollowUp.NONE),
            MenuRefresh.afterLoad(syncedFirst = true, achievementsHeld = false, onAchievementsTab = false),
        )
        assertEquals(
            LoadFollowUp(collection = FollowUp.FETCH, achievements = FollowUp.FETCH),
            MenuRefresh.afterLoad(syncedFirst = true, achievementsHeld = true, onAchievementsTab = false),
        )
        assertEquals(
            LoadFollowUp(collection = FollowUp.FETCH, achievements = FollowUp.FETCH),
            MenuRefresh.afterLoad(syncedFirst = true, achievementsHeld = false, onAchievementsTab = true),
        )
        assertEquals(
            LoadFollowUp(collection = FollowUp.FETCH, achievements = FollowUp.FETCH),
            MenuRefresh.afterLoad(syncedFirst = true, achievementsHeld = true, onAchievementsTab = true),
        )
    }

    @Test
    fun `a tab's data after a load without a sync first is ensured only on its tab`() {
        assertEquals(FollowUp.NONE, MenuRefresh.tabFollowUp(syncedFirst = false, held = false, onTab = false))
        assertEquals(FollowUp.NONE, MenuRefresh.tabFollowUp(syncedFirst = false, held = true, onTab = false))
        assertEquals(FollowUp.ENSURE, MenuRefresh.tabFollowUp(syncedFirst = false, held = false, onTab = true))
        assertEquals(FollowUp.ENSURE, MenuRefresh.tabFollowUp(syncedFirst = false, held = true, onTab = true))
    }

    @Test
    fun `a tab's data after a sync first is fetched again when held or shown`() {
        assertEquals(FollowUp.NONE, MenuRefresh.tabFollowUp(syncedFirst = true, held = false, onTab = false))
        assertEquals(FollowUp.FETCH, MenuRefresh.tabFollowUp(syncedFirst = true, held = true, onTab = false))
        assertEquals(FollowUp.FETCH, MenuRefresh.tabFollowUp(syncedFirst = true, held = false, onTab = true))
        assertEquals(FollowUp.FETCH, MenuRefresh.tabFollowUp(syncedFirst = true, held = true, onTab = true))
    }
}
