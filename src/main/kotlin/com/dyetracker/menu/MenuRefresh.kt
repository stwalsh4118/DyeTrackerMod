package com.dyetracker.menu

/** What one click on the strip's Refresh pill fetches again: the [dyes], the [collection], the [achievements]. */
data class RefreshPlan(val dyes: Boolean, val collection: Boolean, val achievements: Boolean)

/** What Refresh fetches on each menu tab, and when it is ignored. */
object MenuRefresh {
    /** On the Dyes tab: its dyes, and the collection behind their OBTAINED badges and totals. */
    val DYES_TAB = RefreshPlan(dyes = true, collection = true, achievements = false)

    /** On the Achievements tab: only the achievements. */
    val ACHIEVEMENTS_TAB = RefreshPlan(dyes = false, collection = false, achievements = true)

    /**
     * On the Collection tab, built from the dyes and the collection: the collection, and the dyes
     * too unless [dyesLoaded], so a failed dyes load can be retried from this tab.
     */
    fun collectionTab(dyesLoaded: Boolean): RefreshPlan =
        RefreshPlan(dyes = !dyesLoaded, collection = true, achievements = false)

    /**
     * Whether Refresh on the Collection tab is ignored: while a collection fetch runs
     * ([collectionBusy]) or a dyes load runs behind Loading ([dyesLoading]).
     */
    fun collectionTabBlocked(collectionBusy: Boolean, dyesLoading: Boolean): Boolean = collectionBusy || dyesLoading
}
