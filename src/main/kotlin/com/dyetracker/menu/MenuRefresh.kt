package com.dyetracker.menu

/** What one click on the strip's Refresh pill fetches again: the [dyes], the [collection], the [achievements]. */
data class RefreshPlan(val dyes: Boolean, val collection: Boolean, val achievements: Boolean)

/** What a landed dyes load does with one tab's data ([LoadFollowUp]). */
enum class FollowUp {
    /** Nothing. */
    NONE,

    /** Fetches it only when not loaded or loading for the profile ([TabFetch.needsFetch]). */
    ENSURE,

    /** Fetches it again in any case, superseding a fetch that started before the sync. */
    FETCH,
}

/** What a landed dyes load does with the [collection] and the [achievements] of its profile. */
data class LoadFollowUp(val collection: FollowUp, val achievements: FollowUp)

/** What Refresh fetches on each menu tab, when it is ignored, and what a landed dyes load fetches next. */
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

    /**
     * What a loaded dyes result does next for its profile. Without a sync first: the collection is
     * fetched only when needed, the achievements likewise on their tab ([onAchievementsTab]). When
     * the load [syncedFirst], a collection or achievements fetched or cached before the sync may miss
     * what it pushed, so the collection is fetched again, and the achievements too when the tab
     * shows or holds them for that profile ([achievementsHeld]).
     */
    fun afterLoad(syncedFirst: Boolean, achievementsHeld: Boolean, onAchievementsTab: Boolean): LoadFollowUp = when {
        syncedFirst -> LoadFollowUp(
            collection = FollowUp.FETCH,
            achievements = if (achievementsHeld || onAchievementsTab) FollowUp.FETCH else FollowUp.NONE,
        )
        else -> LoadFollowUp(
            collection = FollowUp.ENSURE,
            achievements = if (onAchievementsTab) FollowUp.ENSURE else FollowUp.NONE,
        )
    }
}
