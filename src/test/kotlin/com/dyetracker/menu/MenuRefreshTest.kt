package com.dyetracker.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unit coverage for what the strip's Refresh pill fetches on each tab, and when it is ignored (68-8). */
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
}
