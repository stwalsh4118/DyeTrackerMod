package com.dyetracker.menu

import com.dyetracker.api.AchievementsResponse
import com.dyetracker.api.ApiClient
import com.dyetracker.api.CollectionResponse
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.HistoryDyeGain
import com.dyetracker.api.HistoryDyeSeriesResponse
import com.dyetracker.api.HistoryResponse
import com.dyetracker.api.PlayerAchievementEntry
import com.dyetracker.api.ProfileSummary
import com.dyetracker.auth.AccountVerification
import com.dyetracker.client.showScreen
import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.PlayerRngData
import com.dyetracker.data.RngDataStore
import com.dyetracker.discord.DiscordHuntPickerScreen
import com.dyetracker.discord.disableDiscordPresence
import com.dyetracker.discord.enableDiscordPresence
import com.dyetracker.dyeprogress.DyeCatalog
import com.dyetracker.dyeprogress.DyeProgressPlacementEditor
import com.dyetracker.dyeprogress.DyeProgressPoller
import com.dyetracker.dyeprogress.DyeProgressWidgetConfig
import com.dyetracker.dyeprogress.DyeProgressWidgetView
import com.dyetracker.overlay.GifPlacementEditor
import com.dyetracker.rotation.DyeSprites
import com.dyetracker.rotation.RotationPlacementEditor
import com.dyetracker.rotation.RotationWidgetConfig
import com.dyetracker.spotify.SessionStatus
import com.dyetracker.spotify.SpotifyActions
import com.dyetracker.spotify.SpotifyAuthStore
import com.dyetracker.spotify.SpotifyConstants
import com.dyetracker.spotify.SpotifySessions
import com.dyetracker.spotify.statusText
import com.dyetracker.sync.SyncManager
import com.dyetracker.ui.bounce.BounceController
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Rect
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.texture.ImageTextureManager
import com.dyetracker.ui.theme.UiTheme
import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.util.Util
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The in-game DyeTracker menu (`/dyetracker menu`), styled after the website: a header bar with
 * the title and a tab bar, a profile strip (the player's name, a profile pill that cycles their
 * SkyBlock profiles, how old the data is, a Refresh pill, and totals), category filter pills and
 * a sort pill (like the website's `CategoryFilters`), over a scrolling grid of the player's own
 * dye cards (styled like the website's `DyeCard`), first for the profile selected in-game.
 * Always loads by the in-game name, linked or not. The filter and sort last while the screen is
 * open, across profile switches.
 *
 * Every loaded profile goes into [MENU_CACHE] for the game session. Opening always loads via
 * [MenuData.load] (profiles, then the one selected in-game), in the background behind the
 * player's last viewed profile from the cache when there is one. A profile switch or Refresh
 * loads that profile via [MenuData.loadProfile], in the background behind its cache entry when
 * there is one. A linked player's pending mod data is synced first ([shouldSyncFirst]).
 * Loads run on [Dispatchers.IO]; each result is posted back to the render thread and dropped if
 * the screen was closed or another load started meanwhile ([epoch] guard).
 *
 * Clicking a dye card pins that dye, for the profile shown, as a HUD dye progress widget
 * ([pinDecision]); a short message at the bottom says what happened, and the hovered card hints
 * whether a click would pin it.
 *
 * The header's tab pills switch between the Dyes tab (all of the above), the Collection tab, the
 * Achievements tab, the History tab and the Settings tab. The Collection tab keeps the profile strip and shows which of the Dyes tab's
 * dyes the player owns on that profile, like the website's Collection page ([CollectionTabView]),
 * with category chips (owned/total each) and an All / Owned / Unowned filter of its own, kept while
 * the screen is open. The collection is fetched alongside the dyes (cached dyes shown on opening,
 * each load landing on a profile without one, each profile switch, Refresh on the Dyes or
 * Collection tab; and opening the Collection tab after a failure), dropping a result for another profile or one a newer request
 * superseded ([collectionFetch]).
 * Once loaded, the Dyes tab's cards of owned dyes show an OBTAINED badge and the strip's totals
 * count the obtained dyes; until then (or after a collection error) they show as without it.
 * The Achievements tab keeps the profile strip and shows that profile's achievements in category
 * sections, like the website's Achievements page ([AchievementsTabView]), scrolling on their own,
 * with an All / Unlocked / Locked status filter of its own, kept while the screen is open.
 * They are fetched only for that tab: on opening it, on a profile switch or Refresh while it shows,
 * and when a load lands while it shows ([achievementsFetch], which drops a stale result likewise).
 * The History tab keeps the profile strip and shows what that profile gained over a rolling period
 * ([HistoryTabView]), picked with its 1h / 24h / 7d / 30d pills (24h first, kept while the screen
 * is open). It is fetched like the achievements, per profile and period ([historyFetch]); picking
 * a period fetches that period. Clicking a row selects its dye (the first row until one is
 * clicked, or when the selected dye isn't listed for the period), whose work per time slice shows
 * as bars in a chart above the list, fetched per profile, period and dye ([historySeriesFetch]).
 * Like the dyes, every fetched collection, achievements and history goes into a session cache
 * ([COLLECTION_CACHE], [ACHIEVEMENTS_CACHE], [HISTORY_CACHE], [HISTORY_SERIES_CACHE]); a profile's
 * cached entry shows at once while it is fetched again in the background, and a failed background
 * fetch keeps it on screen.
 * The strip's status text is the shown tab's: how old its data is, "refreshing…" or "refresh failed". Refresh
 * fetches the shown tab's data again ([MenuRefresh]): on the Dyes tab the dyes and the collection,
 * on the Collection tab the collection (and the dyes while they aren't loaded).
 * The Settings tab is one panel of rows (account, sync, the HUD widget toggles, bounce, Discord
 * presence and its hunt dye, Spotify, the layout editor), each control calling what its
 * `/dyetracker` command calls. Settings state is read fresh every frame, so a change made by a
 * command meanwhile shows at once. The panel scrolls when its rows don't fit ([settingsScroll]).
 * The Discord hunt row opens [DiscordHuntPickerScreen], which comes back to a new menu opened on
 * the Settings tab ([openOnSettings]). The Spotify Client ID row holds a vanilla [EditBox]
 * ([clientIdField]), there only while the Settings tab shows. The menu has no key handling of its
 * own: vanilla routes keys to the focused field first, its Escape still closes the menu, and the
 * open-menu keybind only acts while no screen is open ([MenuKeybind]).
 *
 * @param openOnSettings open on the Settings tab instead of the Dyes tab.
 */
class DyeTrackerMenuScreen(openOnSettings: Boolean = false) : Screen(Component.literal(TITLE)) {

    /** The menu's tabs, in header order; [label] is the tab pill's text. */
    private enum class MenuTab(val label: String) {
        DYES("Dyes"),
        COLLECTION("Collection"),
        ACHIEVEMENTS("Achievements"),
        HISTORY("History"),
        SETTINGS("Settings"),
    }

    /** How a pill is drawn, beyond [MenuPill.selected]'s two website `Button` variants. */
    internal enum class PillStyle {
        /** The website primary `Button`: white with black text. */
        SELECTED,

        /** The website secondary `Button`: card fill, brightening on hover. */
        UNSELECTED,

        /** A toggle that is on: the website `success` Badge colors. */
        TOGGLE_ON,

        /** An action waiting for its confirming click: unselected, with error-colored text. */
        CONFIRM,

        /** Shown but not clickable: muted text, no hover. */
        INACTIVE,

        /** A Collection category chip whose dyes are all owned: unselected, with emerald text. */
        COMPLETE,
    }

    /** What a Settings row is about; picks what a click on its pill does. */
    private enum class SettingsKey {
        ACCOUNT,
        SYNC,
        ROTATION,
        DYE_WIDGETS,
        GIFS,
        BOUNCE,
        DISCORD,
        DISCORD_HUNT,
        SPOTIFY,
        SPOTIFY_CLIENT_ID,
        SPOTIFY_WIDGET,
        SPOTIFY_TINT,
        EDIT_LAYOUT,
    }

    /**
     * One Settings row as of now: its [label], an optional [detail] line under it, and an optional
     * control pill showing [pillLabel] drawn in [pillStyle].
     */
    private data class SettingsRow(
        val key: SettingsKey,
        val label: String,
        val detail: String? = null,
        val pillLabel: String? = null,
        val pillStyle: PillStyle = PillStyle.UNSELECTED,
    )

    /** A [row] placed in the Settings panel at [rect], its control [pill] right-aligned in it. */
    private data class PlacedRow(val row: SettingsRow, val rect: Rect, val pill: MenuPill?)

    /**
     * The Settings [panel] and its [rows] at least partly inside it after [settingsScroll], scrolled
     * content [contentHeight] tall.
     */
    private data class SettingsLayout(val panel: Rect, val rows: List<PlacedRow>, val contentHeight: Int)

    /** A message replacing row [key]'s detail line until [untilMs] ([Util.getMillis] time). */
    private data class RowMessage(val key: SettingsKey, val text: String, val argb: Int, val untilMs: Long)

    /** What the content area shows. */
    private sealed class ViewState {
        data object Loading : ViewState()
        data class Loaded(val result: MenuLoadResult.Loaded) : ViewState()
        data class NoProfiles(val username: String) : ViewState()
        data class Failed(val message: String) : ViewState()
    }

    /**
     * What the profile strip shows: kept apart from [ViewState] so the strip stays up, showing the
     * profile being switched to, while the grid area shows a profile switch's Loading or failure.
     */
    private data class StripState(
        val username: String,
        val profiles: List<ProfileSummary>,
        val profile: ProfileSummary,
    ) {
        /** Whether the profile pill cycles: only with more than one profile. */
        val canSwitch: Boolean get() = profiles.size > 1
    }

    /** The message after a card click, shown at the bottom until [untilMs] ([Util.getMillis] time). */
    private data class PinMessage(val text: String, val argb: Int, val untilMs: Long)

    /** A hovered card's pin hint, drawn bottom-right in [argb]. */
    private data class PinHint(val text: String, val argb: Int)

    /** One piece of text in the profile strip, drawn at [x] in [argb]. */
    private data class StripText(val text: String, val x: Int, val argb: Int)

    /**
     * Where the profile strip's parts sit: the profile pill, then right-aligned the status text
     * (age, refreshing or refresh failed), the Refresh pill and the totals. An optional part is
     * `null` when it doesn't apply or doesn't fit.
     */
    private data class StripLayout(
        val profilePill: MenuPill,
        val status: StripText?,
        val refreshPill: MenuPill?,
        val totals: StripText?,
    )

    /**
     * Where the profile strip's row, the control pills below it and the card grid below them sit
     * for the current screen size, and how tall the grid's content is.
     */
    private data class GridGeometry(
        val stripRow: Rect,
        val pills: List<MenuPill>,
        val columns: Int,
        val left: Int,
        val top: Int,
        val viewport: Rect,
        val contentHeight: Int,
    )

    private var state: ViewState = ViewState.Loading

    /** The profile strip; `null` until the first load succeeds (then never cleared). */
    private var strip: StripState? = null

    /** Pixels the card grid is scrolled up by, kept within [MenuLayout.clampScroll]. */
    private var scroll = 0

    /** Category the grid is filtered to; `null` shows every dye. */
    private var category: String? = null

    /** Order of the grid's cards; the sort pill cycles it. */
    private var sort = MenuSort.DEFAULT

    /** Set on the first [init]; resize re-inits must not reload. */
    private var started = false

    /** Bumped per load and on [removed]; a posted result whose epoch is stale is dropped. */
    private var epoch = 0

    /** The running load; cancelled when another starts, so a superseded sync-then-fetch stops early. */
    private var loadJob: Job? = null

    /** When the shown dyes were fetched (epoch ms); meaningful while [state] is Loaded. */
    private var fetchedAtMs = 0L

    /** A background fetch is running while cached dyes show. */
    private var refreshing = false

    /** The last background fetch failed; cleared by the next successful load. */
    private var refreshFailed = false

    /** The last card click's message; `null` before the first click. */
    private var pinMessage: PinMessage? = null

    /** The tab shown; kept while the screen is open. */
    private var tab = if (openOnSettings) MenuTab.SETTINGS else MenuTab.DYES

    /** The Unlink pill's two-click confirm. */
    private val unlinkConfirm = ConfirmClick()

    /** The last Settings click's message; `null` before one says anything. */
    private var rowMessage: RowMessage? = null

    /** Pixels the Settings panel is scrolled up by, kept within [maxSettingsScroll]. */
    private var settingsScroll = 0

    /**
     * The Spotify Client ID field, a widget only while the Settings tab shows ([addClientIdField],
     * [removeClientIdField]); placed on its row every frame ([placeClientIdField]).
     */
    private var clientIdField: EditBox? = null

    /** Set by [removed]; a sync completing after the screen closed posts nothing. */
    private var closed = false

    /** A load is running, in the background or behind Loading; Refresh is ignored meanwhile. */
    private val fetchRunning: Boolean get() = refreshing || state is ViewState.Loading

    /** Pixels the Collection grid is scrolled up by; apart from the Dyes grid's [scroll]. */
    private var collectionScroll = 0

    /** Category the Collection grid is filtered to; `null` shows every dye. Apart from the Dyes tab's [category]. */
    private var collectionCategory: String? = null

    /** Which Collection cards show by ownership. */
    private var ownedFilter = OwnedFilter.ALL

    /** Pixels the Achievements list is scrolled up by; apart from the card grids' scrolls. */
    private var achievementsScroll = 0

    /** Which Achievements show by status; apart from the Collection tab's [ownedFilter]. */
    private var statusFilter = StatusFilter.ALL

    /** Pixels the History list is scrolled up by; apart from the other tabs' scrolls. */
    private var historyScroll = 0

    /** The period the History tab shows gains over. */
    private var historyPeriod = HistoryPeriod.DEFAULT

    /**
     * The dye whose History chart shows, as last resolved against the list ([syncHistorySelection]);
     * `null` until a list shows.
     */
    private var historyDye: String? = null

    /**
     * Refresh is ignored while the shown tab's fetch runs: on the Collection tab a collection fetch
     * or a dyes load behind Loading ([MenuRefresh.collectionTabBlocked]), on the Achievements tab an
     * achievements fetch, on the History tab a history fetch, else a dyes load ([fetchRunning]).
     */
    private val refreshBlocked: Boolean
        get() = when (tab) {
            MenuTab.COLLECTION -> MenuRefresh.collectionTabBlocked(collectionFetch.busy, state is ViewState.Loading)
            MenuTab.ACHIEVEMENTS -> achievementsFetch.busy
            MenuTab.HISTORY -> historyFetch.busy
            MenuTab.DYES, MenuTab.SETTINGS -> fetchRunning
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The collection for the profile the strip shows, one result shared by the Dyes tab's OBTAINED
     * badges and totals and the Collection tab, cached in [COLLECTION_CACHE]; a result posted after
     * the screen closed, a newer collection fetch started or the strip moved to another profile is
     * dropped.
     */
    private val collectionFetch = TabFetch<CollectionResponse>(
        scope,
        post = { Minecraft.getInstance().execute(it) },
        shownProfileId = { strip?.profile?.id },
        cache = COLLECTION_CACHE,
    )

    /**
     * The achievements of the profile the strip shows, for the Achievements tab only, cached in
     * [ACHIEVEMENTS_CACHE]; a result posted after the screen closed, a newer achievements fetch
     * started or the strip moved to another profile is dropped.
     */
    private val achievementsFetch = TabFetch<AchievementsResponse>(
        scope,
        post = { Minecraft.getInstance().execute(it) },
        shownProfileId = { strip?.profile?.id },
        cache = ACHIEVEMENTS_CACHE,
    )

    /**
     * The history of the profile the strip shows over [historyPeriod], for the History tab only,
     * keyed by both ([historyKey]) and cached in [HISTORY_CACHE]; a result posted after the screen
     * closed, a newer history fetch started, or the strip moved to another profile or period is dropped.
     */
    private val historyFetch = TabFetch<HistoryResponse>(
        scope,
        post = { Minecraft.getInstance().execute(it) },
        shownProfileId = { strip?.profile?.id?.let { historyKey(it, historyPeriod) } },
        cache = HISTORY_CACHE,
    )

    /**
     * The selected History dye's work per time slice on the profile the strip shows over
     * [historyPeriod], keyed by all three ([historySeriesKey]) and cached in [HISTORY_SERIES_CACHE];
     * a result posted after the screen closed, a newer series fetch started, or the selection,
     * profile or period moved on is dropped.
     */
    private val historySeriesFetch = TabFetch<HistoryDyeSeriesResponse>(
        scope,
        post = { Minecraft.getInstance().execute(it) },
        shownProfileId = ::shownHistorySeriesKey,
        cache = HISTORY_SERIES_CACHE,
    )

    override fun init() {
        super.init()
        clampScrollToContent() // a resize changes the viewport and column count
        // A resize rebuilds the widgets, clearing them first: put the field back, with what was typed.
        clientIdField?.let { addRenderableWidget(it) }
        // Opened on the Settings tab ([openOnSettings]): no tab click has added the field yet.
        if (tab == MenuTab.SETTINGS && clientIdField == null) addClientIdField()
        if (started) return
        started = true
        startLoad()
    }

    /**
     * Loads the player's profiles and the selected profile's dyes ([MenuData.load]). When the
     * player's last viewed profile is in [MENU_CACHE] it shows at once and this full load runs in
     * the background, so a profile switched or created in-game since is still picked up.
     */
    private fun startLoad() {
        val username = Minecraft.getInstance().user.name
        val cached = MENU_CACHE.lastFor(username)
        if (cached != null) showCached(cached)
        launchLoad(background = cached != null) {
            MenuData.load(username, ApiClient::fetchProfiles, ApiClient::fetchDyeProgress)
        }
    }

    /**
     * Shows [cached]'s dyes and profile, and its fetch time for the age text; its collection too
     * ([ensureCollection]), at once when [COLLECTION_CACHE] has it, for the OBTAINED badges and totals.
     */
    private fun showCached(cached: CachedMenu) {
        val result = cached.result
        strip = StripState(result.username, result.profiles, result.profile)
        state = ViewState.Loaded(result)
        fetchedAtMs = cached.fetchedAtMs
        ensureCollection()
    }

    /** Fetches again what Refresh fetches on the shown tab ([MenuRefresh]), for [current]'s profile. */
    private fun refresh(current: StripState) {
        val plan = when (tab) {
            MenuTab.COLLECTION -> MenuRefresh.collectionTab(dyesLoaded = state is ViewState.Loaded)
            MenuTab.ACHIEVEMENTS -> MenuRefresh.ACHIEVEMENTS_TAB
            MenuTab.HISTORY -> MenuRefresh.HISTORY_TAB
            MenuTab.DYES, MenuTab.SETTINGS -> MenuRefresh.DYES_TAB
        }
        if (plan.dyes) fetchProfile(current)
        if (plan.collection) fetchCollection()
        if (plan.achievements) fetchAchievements()
        if (plan.history) fetchHistory()
    }

    /**
     * Cycles the strip to the profile after [current]'s and loads its dyes ([fetchProfile]); the
     * filter and sort are kept.
     */
    private fun switchProfile(current: StripState) {
        scroll = 0
        fetchProfile(current.copy(profile = MenuData.nextProfile(current.profiles, current.profile)))
    }

    /**
     * Shows [target] in the strip and fetches its profile's dyes. A profile in [MENU_CACHE] shows
     * at once and refreshes in the background; else the grid area shows Loading.
     */
    private fun fetchProfile(target: StripState) {
        strip = target
        val cached = MENU_CACHE.get(target.username, target.profile.id)
        if (cached != null) {
            state = ViewState.Loaded(cached.result)
            fetchedAtMs = cached.fetchedAtMs
        }
        launchLoad(background = cached != null) {
            MenuData.loadProfile(target.username, target.profiles, target.profile, ApiClient::fetchDyeProgress)
        }
    }

    /**
     * Runs [load] on [Dispatchers.IO], after syncing the player's pending mod data when
     * [shouldSyncFirst], then posts its result to the render thread. A [background] load keeps
     * the shown dyes (and shows "refreshing…"); otherwise the grid area shows Loading. A newer
     * load cancels this one and, like [removed], bumps [epoch] first, so a stale result is
     * dropped even when its blocking call was already in flight. The result is told whether this
     * load synced first, so [applyResult] fetches the tabs' data again after the sync.
     */
    private fun launchLoad(background: Boolean, load: () -> MenuLoadResult) {
        val myEpoch = ++epoch
        loadJob?.cancel()
        refreshing = background
        if (!background) state = ViewState.Loading
        val modData = RngDataStore.getData()
        val syncFirst = shouldSyncFirst(ConfigManager.config.isLinked(), SyncManager.isSyncPending(), modData.hasData())
        loadJob = scope.launch {
            if (syncFirst) {
                syncThenWait(modData)
                ensureActive() // superseded or closed while syncing: skip the fetch
            }
            val result = try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                MenuLoadResult.Failed(unexpectedErrorMessage(e))
            }
            Minecraft.getInstance().execute {
                if (myEpoch != epoch) return@execute // stale — screen closed or another load started
                applyResult(result, background, syncedFirst = syncFirst)
            }
        }
    }

    /**
     * Fetches the collection of the profile the strip shows ([ApiClient.fetchCollection]) on
     * [Dispatchers.IO] via [collectionFetch], which drops a stale result on the render thread and
     * shows a [COLLECTION_CACHE] entry meanwhile.
     * Before the first load gives the strip a profile there is nothing to fetch; [applyResult]
     * fetches once it lands. The Collection grid's scroll resets for another profile.
     */
    private fun fetchCollection() {
        val target = strip ?: return
        val profileId = target.profile.id
        if (collectionFetch.state?.profileId != profileId) collectionScroll = 0
        collectionFetch.fetch(target.username, profileId, { ApiClient.fetchCollection(target.username, profileId) }) {
            clampCollectionScroll()
        }
    }

    /** [fetchCollection] unless the strip's profile has its collection loaded or loading ([TabFetch.needsFetch]). */
    private fun ensureCollection() {
        val profileId = strip?.profile?.id ?: return
        if (collectionFetch.needsFetch(profileId)) fetchCollection()
    }

    /**
     * Fetches the achievements of the profile the strip shows ([ApiClient.fetchAchievements]) on
     * [Dispatchers.IO] via [achievementsFetch], which drops a stale result on the render thread and
     * shows an [ACHIEVEMENTS_CACHE] entry meanwhile.
     * Before the first load gives the strip a profile there is nothing to fetch; [applyResult]
     * fetches once it lands. The Achievements list's scroll resets for another profile.
     */
    private fun fetchAchievements() {
        val target = strip ?: return
        val profileId = target.profile.id
        if (achievementsFetch.state?.profileId != profileId) achievementsScroll = 0
        achievementsFetch.fetch(target.username, profileId, { ApiClient.fetchAchievements(target.username, profileId) }) {
            clampAchievementsScroll()
        }
    }

    /** [fetchAchievements] unless the strip's profile has its achievements loaded or loading ([TabFetch.needsFetch]). */
    private fun ensureAchievements() {
        val profileId = strip?.profile?.id ?: return
        if (achievementsFetch.needsFetch(profileId)) fetchAchievements()
    }

    /**
     * Fetches the history of the profile the strip shows over [historyPeriod]
     * ([ApiClient.fetchHistory]) on [Dispatchers.IO] via [historyFetch], which drops a stale result
     * on the render thread and shows a [HISTORY_CACHE] entry meanwhile.
     * Before the first load gives the strip a profile there is nothing to fetch; [applyResult]
     * fetches once it lands. The History list's scroll resets for another profile or period.
     * The selected dye's chart is fetched again with it when a cached list shows; once the list
     * lands, the (maybe new) selection's chart unless it is loaded or loading ([ensureHistorySeries]).
     */
    private fun fetchHistory() {
        val target = strip ?: return
        val profileId = target.profile.id
        val period = historyPeriod
        val key = historyKey(profileId, period)
        if (historyFetch.state?.profileId != key) historyScroll = 0
        historyFetch.fetch(target.username, key, { ApiClient.fetchHistory(target.username, profileId, period.apiValue) }) {
            clampHistoryScroll()
            ensureHistorySeries()
        }
        fetchHistorySeries()
    }

    /**
     * [fetchHistory] unless the strip's profile has [historyPeriod]'s history loaded or loading
     * ([TabFetch.needsFetch]); then the selected dye's chart unless it is ([ensureHistorySeries]).
     */
    private fun ensureHistory() {
        val profileId = strip?.profile?.id ?: return
        if (historyFetch.needsFetch(historyKey(profileId, historyPeriod))) fetchHistory() else ensureHistorySeries()
    }

    /**
     * Fetches the selected History dye's work per time slice ([ApiClient.fetchHistoryDyeSeries]) on
     * the profile the strip shows over [historyPeriod], on [Dispatchers.IO] via [historySeriesFetch],
     * which drops a stale result on the render thread and shows a [HISTORY_SERIES_CACHE] entry
     * meanwhile. Nothing to fetch while no History list shows (no selection).
     */
    private fun fetchHistorySeries() {
        val target = strip ?: return
        val dyeId = syncHistorySelection()?.dyeId ?: return
        val profileId = target.profile.id
        val period = historyPeriod
        historySeriesFetch.fetch(target.username, historySeriesKey(profileId, period, dyeId), {
            ApiClient.fetchHistoryDyeSeries(target.username, profileId, dyeId, period.apiValue)
        })
    }

    /**
     * [fetchHistorySeries] unless the selected dye's chart is loaded or loading
     * ([TabFetch.needsFetch]); either way the selection is resolved against the list shown first.
     */
    private fun ensureHistorySeries() {
        val profileId = strip?.profile?.id ?: return
        val dyeId = syncHistorySelection()?.dyeId ?: return
        if (historySeriesFetch.needsFetch(historySeriesKey(profileId, historyPeriod, dyeId))) fetchHistorySeries()
    }

    /**
     * Resolves [historyDye] against the History list shown ([historySelection]) and keeps the
     * result, so a dye no longer listed gives way to the first row; `null` while no list shows,
     * leaving [historyDye] for the next list.
     */
    private fun syncHistorySelection(): HistoryDyeGain? {
        val gain = historySelection(historyRows(), historyDye) ?: return null
        historyDye = gain.dyeId
        return gain
    }

    /** The [historySeriesKey] of the chart the History tab shows, else `null` (no list shows). */
    private fun shownHistorySeriesKey(): String? {
        val profileId = strip?.profile?.id ?: return null
        val dyeId = historySelection(historyRows(), historyDye)?.dyeId ?: return null
        return historySeriesKey(profileId, historyPeriod, dyeId)
    }

    /**
     * Syncs [data] now and suspends until the sync is done, succeeded or not. A failed sync
     * re-arms the normal debounced sync, since `syncNow` cancelled its pending retry. [attachRearm]
     * hangs the re-arm on the sync's own future and hands back a separate stage to wait on, so
     * when this load is cancelled `await()` cancels only that stage: the sync and the re-arm
     * still run.
     */
    private suspend fun syncThenWait(data: PlayerRngData) {
        try {
            // Re-arm with the newest data, so a change made during the sync isn't overwritten.
            attachRearm(SyncManager.syncNow(data), { it.success }) {
                SyncManager.onDataChanged(RngDataStore.getData())
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DyeTrackerMod.warn("Menu sync before fetch failed: {}", e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * A loaded result replaces the shown dyes and strip and goes into [MENU_CACHE]; the scroll
     * resets when it is another profile than the strip showed (a background full load picked the
     * profile selected in-game). A failed [background] load (or one finding no profiles) keeps the
     * cached dyes on screen and shows "refresh failed"; any other failure replaces the grid area.
     * A loaded profile without its collection loaded or loading gets a collection fetch, on any tab;
     * on the Achievements tab, likewise its achievements, and on the History tab its history. When
     * the load [syncedFirst], its collection is fetched again in any case, and its achievements and
     * history when held or shown ([MenuRefresh.afterLoad], [MenuRefresh.tabFollowUp]), so data
     * fetched or cached before the sync doesn't stay.
     */
    private fun applyResult(result: MenuLoadResult, background: Boolean, syncedFirst: Boolean) {
        refreshing = false
        when {
            result is MenuLoadResult.Loaded -> {
                val now = System.currentTimeMillis()
                MENU_CACHE.put(result, now)
                fetchedAtMs = now
                refreshFailed = false
                state = ViewState.Loaded(result)
                if (strip?.profile?.id != result.profile.id) scroll = 0
                strip = StripState(result.username, result.profiles, result.profile)
                clampScrollToContent() // a refresh can change how many dyes there are
                // A first load or one landing on another profile needs that profile's collection;
                // after a sync, data fetched before it may miss what the sync pushed.
                val followUp = MenuRefresh.afterLoad(
                    syncedFirst,
                    achievementsHeld = achievementsFetch.stateFor(result.profile.id) != null,
                    onAchievementsTab = tab == MenuTab.ACHIEVEMENTS,
                )
                runFollowUp(followUp.collection, ::ensureCollection, ::fetchCollection)
                runFollowUp(followUp.achievements, ::ensureAchievements, ::fetchAchievements)
                val historyFollowUp = MenuRefresh.tabFollowUp(
                    syncedFirst,
                    held = historyFetch.stateFor(historyKey(result.profile.id, historyPeriod)) != null,
                    onTab = tab == MenuTab.HISTORY,
                )
                runFollowUp(historyFollowUp, ::ensureHistory, ::fetchHistory)
            }
            background -> refreshFailed = true
            else -> state = result.toViewState()
        }
    }

    /** Does what [followUp] says with one tab's data: nothing, [ensure] it, or [fetch] it again. */
    private fun runFollowUp(followUp: FollowUp, ensure: () -> Unit, fetch: () -> Unit) {
        when (followUp) {
            FollowUp.NONE -> Unit
            FollowUp.ENSURE -> ensure()
            FollowUp.FETCH -> fetch()
        }
    }

    private fun MenuLoadResult.toViewState(): ViewState = when (this) {
        is MenuLoadResult.Loaded -> ViewState.Loaded(this)
        is MenuLoadResult.NoProfiles -> ViewState.NoProfiles(username)
        is MenuLoadResult.Failed -> ViewState.Failed(message)
    }

    override fun removed() {
        epoch++
        closed = true
        removeClientIdField()
        collectionFetch.close()
        achievementsFetch.close()
        historyFetch.close()
        historySeriesFetch.close()
        scope.cancel()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (super.mouseClicked(click, doubled)) return true
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        val tabPill = pillAt(tabPills(), click.x(), click.y())
        if (tabPill != null) {
            val next = MenuTab.valueOf(tabPill.key)
            if (next == MenuTab.SETTINGS && clientIdField == null) addClientIdField()
            if (next != MenuTab.SETTINGS) removeClientIdField()
            tab = next
            when (tab) {
                MenuTab.COLLECTION -> ensureCollection() // reuses the Dyes tab's collection; retries a failed one
                MenuTab.ACHIEVEMENTS -> ensureAchievements() // fetched once per profile; retries a failed one
                MenuTab.HISTORY -> ensureHistory() // fetched once per profile and period; retries a failed one
                MenuTab.DYES, MenuTab.SETTINGS -> Unit
            }
            return true
        }
        if (tab == MenuTab.SETTINGS) return clickSettings(click.x(), click.y(), doubled)
        val dyes = visibleDyes()
        val grid = gridGeometry(dyes.size)
        val currentStrip = strip
        if (currentStrip != null) {
            val layout = stripLayout(currentStrip, grid.stripRow)
            // The profile pill also works while a switch loads or after it failed, so the player can switch again.
            if (currentStrip.canSwitch && layout.profilePill.rect.contains(click.x(), click.y())) {
                switchProfile(currentStrip)
                ensureCollection()
                if (tab == MenuTab.ACHIEVEMENTS) ensureAchievements()
                if (tab == MenuTab.HISTORY) ensureHistory()
                return true
            }
            if (layout.refreshPill?.rect?.contains(click.x(), click.y()) == true) {
                if (!refreshBlocked) refresh(currentStrip)
                return true
            }
        }
        if (tab == MenuTab.COLLECTION) return clickCollectionPill(click.x(), click.y()) // its cards aren't clickable
        if (tab == MenuTab.ACHIEVEMENTS) return clickStatusPill(click.x(), click.y()) // never the hidden Dyes grid
        if (tab == MenuTab.HISTORY) return clickPeriodPill(click.x(), click.y()) || clickHistoryRow(click.x(), click.y())
        val loaded = state as? ViewState.Loaded ?: return false // control pills and cards only show over loaded dyes
        val pill = pillAt(grid.pills, click.x(), click.y())
        if (pill != null) {
            when (pill.key) {
                SORT_PILL_KEY -> sort = sort.next()
                ALL_PILL_KEY -> category = null
                else -> category = pill.key
            }
            scroll = 0
            return true
        }
        val index = MenuLayout.cardIndexAt(
            click.x(),
            click.y(),
            dyes.size,
            grid.columns,
            grid.left,
            grid.top,
            scroll,
            grid.viewport,
        ) ?: return false
        pinCard(dyes[index], loaded.result.profile)
        return true
    }

    /**
     * Applies the Collection filter pill at ([mouseX], [mouseY]): a category chip ("All Dyes" for
     * none) or an owned-filter pill. The Collection grid scrolls back to the top. Returns whether
     * a pill took the click; none shows until the collection is loaded.
     */
    private fun clickCollectionPill(mouseX: Double, mouseY: Double): Boolean {
        val cells = collectionCells() ?: return false
        val pill = pillAt(collectionPills(cells, collectionGeometry(cells)), mouseX, mouseY) ?: return false
        val filter = OwnedFilter.entries.firstOrNull { ownedFilterKey(it) == pill.key }
        when {
            filter != null -> ownedFilter = filter
            pill.key == ALL_PILL_KEY -> collectionCategory = null
            else -> collectionCategory = pill.key
        }
        collectionScroll = 0
        return true
    }

    /**
     * Applies the Achievements status pill at ([mouseX], [mouseY]); the list scrolls back to the
     * top. Returns whether a pill took the click; its rows aren't clickable, and no pill shows
     * until the achievements are loaded.
     */
    private fun clickStatusPill(mouseX: Double, mouseY: Double): Boolean {
        val entries = loadedAchievements()?.achievements ?: return false
        val pill = pillAt(statusPills(entries, achievementsGeometry(entries)), mouseX, mouseY) ?: return false
        statusFilter = StatusFilter.valueOf(pill.key)
        achievementsScroll = 0
        return true
    }

    /**
     * Picks the History period pill at ([mouseX], [mouseY]) and fetches that period unless it is
     * loaded or loading ([ensureHistory]); a cached period shows at once. Returns whether a pill
     * took the click; no pill shows before the strip has a profile.
     */
    private fun clickPeriodPill(mouseX: Double, mouseY: Double): Boolean {
        if (strip == null) return false
        val pill = pillAt(periodPills(historyGeometry()), mouseX, mouseY) ?: return false
        historyPeriod = HistoryPeriod.valueOf(pill.key)
        ensureHistory()
        return true
    }

    /**
     * Selects the dye of the History row at ([mouseX], [mouseY]) and fetches its chart unless it is
     * loaded or loading ([ensureHistorySeries]). Returns whether a row took the click; none shows
     * until the history is loaded with gains.
     */
    private fun clickHistoryRow(mouseX: Double, mouseY: Double): Boolean {
        val rows = historyRows()
        val index = MenuLayout.historyRowIndexAt(mouseX, mouseY, historyGeometry().viewport, rows.size, historyScroll)
            ?: return false
        historyDye = rows[index].dyeId
        ensureHistorySeries()
        return true
    }

    /**
     * Pins [entry], for [profile], as a HUD dye progress widget when [pinDecision] says so, then
     * polls at once so it fills in; either way shows what happened in [pinMessage].
     */
    private fun pinCard(entry: DyeProgressEntry, profile: ProfileSummary) {
        val (text, argb) = when (val decision = currentPinDecision(entry, profile)) {
            is PinDecision.Add -> {
                ConfigManager.dyeProgressPlacements.add(
                    DyeProgressWidgetConfig(
                        id = DyeProgressWidgetConfig.newId(),
                        dyeId = decision.dyeId,
                        profileName = decision.profileName,
                        profileId = decision.profileId,
                    ),
                )
                DyeProgressPoller.pollOnce()
                "$PINNED_PREFIX${entry.name}$PINNED_SUFFIX" to UiTheme.Colors.ACCENT_SECONDARY
            }
            PinDecision.AlreadyPinned -> "${entry.name}$ALREADY_PINNED_SUFFIX" to UiTheme.Colors.TEXT_SUBTLE
            PinDecision.NotPinnable -> "${entry.name}$NOT_PINNABLE_SUFFIX" to UiTheme.Colors.STATUS_ERROR
        }
        pinMessage = PinMessage(text, argb, Util.getMillis() + MenuLayout.PIN_MESSAGE_MS)
    }

    /** [pinDecision] for [entry] on [profile] against the HUD widgets configured now. */
    private fun currentPinDecision(entry: DyeProgressEntry, profile: ProfileSummary): PinDecision =
        pinDecision(ConfigManager.dyeProgressPlacements.all(), entry, profile, DyeSprites::has)

    /** The hovered card's hint: `+ HUD` when a click would pin it, `On HUD` when pinned, none when it can't be. */
    private fun pinHint(entry: DyeProgressEntry, profile: ProfileSummary): PinHint? =
        when (currentPinDecision(entry, profile)) {
            is PinDecision.Add -> PinHint(PIN_HINT_TEXT, UiTheme.Colors.ACCENT_SECONDARY)
            PinDecision.AlreadyPinned -> PinHint(ON_HUD_HINT_TEXT, UiTheme.Colors.TEXT_MUTED)
            PinDecision.NotPinnable -> null
        }

    /**
     * Runs the action of the Settings pill at ([mouseX], [mouseY]), as its `/dyetracker` command
     * does; an inactive pill does nothing, and neither does the part of a pill scrolled out of the
     * panel. A [doubled] click never confirms the unlink. Returns whether a pill took the click.
     */
    private fun clickSettings(mouseX: Double, mouseY: Double, doubled: Boolean): Boolean {
        val now = Util.getMillis()
        val layout = settingsLayout(now)
        if (!layout.panel.contains(mouseX, mouseY)) return false
        val clicked = layout.rows.firstOrNull { placed ->
            placed.row.pillStyle != PillStyle.INACTIVE && placed.pill?.rect?.contains(mouseX, mouseY) == true
        } ?: return false
        when (clicked.row.key) {
            SettingsKey.ACCOUNT -> if (unlinkConfirm.click(now, doubled)) {
                AccountVerification.unlink()
                showRowMessage(SettingsKey.ACCOUNT, UNLINKED_TEXT, UiTheme.Colors.TEXT_SUBTLE)
            }
            SettingsKey.SYNC -> syncFromSettings()
            SettingsKey.ROTATION ->
                RotationPlacementEditor.setVisible(RotationWidgetConfig.WIDGET_ID, !rotationVisible())
            SettingsKey.DYE_WIDGETS -> {
                val widgets = ConfigManager.dyeProgressPlacements.all()
                // NONE's pill is inactive, so a click only gets here with widgets to switch.
                val target = targetVisibility(aggregate(widgets.map { it.visible }))!!
                for (widget in widgets) DyeProgressPlacementEditor.setVisible(widget.id, target)
            }
            SettingsKey.GIFS -> {
                val overlays = ConfigManager.gifPlacements.all()
                // NONE's pill is inactive, so a click only gets here with overlays to switch.
                val target = targetVisibility(aggregate(overlays.map { it.visible }))!!
                for (overlay in overlays) GifPlacementEditor.setVisible(overlay.id, target)
            }
            SettingsKey.BOUNCE -> {
                val enabled = !BounceController.isEnabled()
                BounceController.setEnabled(enabled)
                ConfigManager.setBounceEnabled(enabled)
            }
            SettingsKey.DISCORD -> if (ConfigManager.isDiscordEnabled()) {
                disableDiscordPresence()
            } else {
                enableDiscordPresence { hint -> Minecraft.getInstance().player?.sendSystemMessage(hint) }
            }
            // This screen's loads stop once it closes, so the picker comes back to a new one.
            SettingsKey.DISCORD_HUNT -> Minecraft.getInstance().showScreen(
                DiscordHuntPickerScreen(DyeTrackerMenuScreen(openOnSettings = true)),
            )
            // NOT_SET_UP's pill is inactive, so a click only gets here logged out or connected.
            SettingsKey.SPOTIFY -> if (SpotifySessions.default.status() == SessionStatus.CONNECTED) {
                SpotifyActions.logout(::showSpotifyMessage)
            } else {
                SpotifyActions.login(::loginFeedback)
            }
            SettingsKey.SPOTIFY_CLIENT_ID -> saveClientId()
            SettingsKey.SPOTIFY_WIDGET -> SpotifyActions.toggleWidget()
            SettingsKey.SPOTIFY_TINT -> SpotifyActions.toggleTintAll()
            SettingsKey.EDIT_LAYOUT -> Minecraft.getInstance().showScreen(WidgetEditScreen())
        }
        return true
    }

    /**
     * Syncs the player's mod data now, as `/dyetracker sync` does, and shows the outcome in the
     * Sync row once done: "Synced." or the failure's message. With no mod data yet it says so
     * instead. The outcome is posted to the render thread and dropped once the screen closed.
     */
    private fun syncFromSettings() {
        val data = RngDataStore.getData()
        if (!data.hasData()) {
            showRowMessage(SettingsKey.SYNC, NOTHING_TO_SYNC_TEXT, UiTheme.Colors.TEXT_SUBTLE)
            return
        }
        SyncManager.syncNow(data).whenComplete { result, error ->
            Minecraft.getInstance().execute {
                if (closed) return@execute
                when {
                    error != null -> showRowMessage(
                        SettingsKey.SYNC,
                        unexpectedErrorMessage(error),
                        UiTheme.Colors.STATUS_ERROR,
                    )
                    result.success -> showRowMessage(SettingsKey.SYNC, SYNCED_TEXT, UiTheme.Colors.ACCENT_SECONDARY)
                    else -> showRowMessage(SettingsKey.SYNC, result.message, UiTheme.Colors.STATUS_ERROR)
                }
            }
        }
    }

    /** Shows [text] in row [key]'s detail line for [MenuLayout.SETTINGS_MESSAGE_MS]. */
    private fun showRowMessage(key: SettingsKey, text: String, argb: Int) {
        rowMessage = RowMessage(key, text, argb, Util.getMillis() + MenuLayout.SETTINGS_MESSAGE_MS)
    }

    /**
     * A Spotify login's [message], on the client thread: to chat, where its "click here" link
     * works, and as the Spotify row's message unless the screen has closed meanwhile.
     */
    private fun loginFeedback(message: Component) {
        Minecraft.getInstance().player?.sendSystemMessage(message)
        if (!closed) showSpotifyMessage(message)
    }

    /** Shows a Spotify action's chat [message] as plain text in the Spotify row ([rowMessageArgb]). */
    private fun showSpotifyMessage(message: Component) {
        showRowMessage(SettingsKey.SPOTIFY, message.string, rowMessageArgb(message))
    }

    /** A row message's color for a chat [message]: a red one error-colored, a green one the success accent, else subtle. */
    private fun rowMessageArgb(message: Component): Int = when (message.style.color) {
        TextColor.fromLegacyFormat(ChatFormatting.RED) -> UiTheme.Colors.STATUS_ERROR
        TextColor.fromLegacyFormat(ChatFormatting.GREEN) -> UiTheme.Colors.ACCENT_SECONDARY
        else -> UiTheme.Colors.TEXT_SUBTLE
    }

    /** Saves the Client ID field's text, as `/dyetracker spotify clientid` does, and says how it went. */
    private fun saveClientId() {
        // The field exists while the Settings tab shows, and only that tab's pills are clicked.
        when (SpotifyActions.setClientId(clientIdField!!.value)) {
            SpotifyActions.SetClientIdResult.Saved ->
                showRowMessage(SettingsKey.SPOTIFY_CLIENT_ID, CLIENT_ID_SAVED_TEXT, UiTheme.Colors.ACCENT_SECONDARY)
            SpotifyActions.SetClientIdResult.Invalid ->
                showRowMessage(SettingsKey.SPOTIFY_CLIENT_ID, CLIENT_ID_INVALID_TEXT, UiTheme.Colors.STATUS_ERROR)
        }
    }

    /**
     * Adds the Spotify Client ID field, prefilled with the saved ID; hidden until
     * [placeClientIdField] puts it on its row.
     */
    private fun addClientIdField() {
        val field = EditBox(font, 0, 0, 0, MenuLayout.SETTINGS_FIELD_HEIGHT, Component.literal(SPOTIFY_CLIENT_ID_LABEL))
        field.setMaxLength(SpotifyConstants.CLIENT_ID_LENGTH)
        field.setValue(SpotifyAuthStore.get().clientId)
        field.setHint(Component.literal(CLIENT_ID_HINT))
        field.visible = false
        clientIdField = addRenderableWidget(field)
    }

    /** Removes the Client ID field, if it is there; vanilla drops its focus with it. */
    private fun removeClientIdField() {
        val field = clientIdField ?: return
        removeWidget(field)
        clientIdField = null
    }

    /**
     * Puts the Client ID field where the Spotify Client ID row's detail line would be, left of its
     * Save pill ([clientIdFieldRect]). While the field isn't wholly inside the panel it is hidden,
     * so it takes no clicks, and unfocused, so keys stop going to it.
     */
    private fun placeClientIdField(layout: SettingsLayout) {
        val field = clientIdField ?: return
        val rect = layout.rows.firstOrNull { it.row.key == SettingsKey.SPOTIFY_CLIENT_ID }?.let(::clientIdFieldRect)
        if (rect == null || rect.width <= 0 || rect.y < layout.panel.y || rect.bottom > layout.panel.bottom) {
            field.visible = false
            if (field.isFocused) setFocused(null)
            return
        }
        field.visible = true
        field.setX(rect.x)
        field.setY(rect.y)
        field.setWidth(rect.width)
    }

    /** Where the Client ID field goes in its [placed] row: under the label, from the text's left to [settingsTextRight]. */
    private fun clientIdFieldRect(placed: PlacedRow): Rect {
        val x = placed.rect.x + MenuLayout.CARD_PADDING
        val y = settingsLabelY(placed.rect, MenuLayout.SETTINGS_FIELD_HEIGHT) + font.lineHeight
        return Rect(x, y, settingsTextRight(placed) - x, MenuLayout.SETTINGS_FIELD_HEIGHT)
    }

    /** The right end of a [placed] Settings row's text: a gap before its pill, else its padding. */
    private fun settingsTextRight(placed: PlacedRow): Int =
        placed.pill?.let { it.rect.x - MenuLayout.TEXT_GAP } ?: (placed.rect.right - MenuLayout.CARD_PADDING)

    /** The label's top in a Settings row at [rect] whose label has [belowHeight] of detail or field under it, centred. */
    private fun settingsLabelY(rect: Rect, belowHeight: Int): Int =
        rect.y + (rect.height - font.lineHeight - belowHeight) / 2

    /** Whether the dye rotation widget shows, read as `/dyetracker rotation toggle` reads it. */
    private fun rotationVisible(): Boolean =
        ConfigManager.rotationPlacements.all().firstOrNull()?.visible ?: RotationWidgetConfig().visible

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (tab == MenuTab.SETTINGS) { // one row per notch; the hidden card grids keep their scroll
            settingsScroll = MenuLayout.clampScroll(
                settingsScroll - (vertical * MenuLayout.SETTINGS_ROW_HEIGHT).toInt(),
                maxSettingsScroll(settingsRows(Util.getMillis()).size),
            )
            return true
        }
        if (tab == MenuTab.COLLECTION) {
            val geometry = collectionCells()?.let(::collectionGeometry) ?: return false // no grid shows
            collectionScroll = MenuLayout.clampScroll(
                collectionScroll - (vertical * MenuLayout.SCROLL_STEP).toInt(),
                geometry.contentHeight,
                geometry.viewport.height,
            )
            return true
        }
        if (tab == MenuTab.ACHIEVEMENTS) {
            val geometry = loadedAchievements()?.let { achievementsGeometry(it.achievements) } ?: return false // no list shows
            achievementsScroll = MenuLayout.clampScroll(
                achievementsScroll - (vertical * MenuLayout.SCROLL_STEP).toInt(),
                geometry.contentHeight,
                geometry.viewport.height,
            )
            return true
        }
        if (tab == MenuTab.HISTORY) {
            if (historyRows().isEmpty()) return false // no list shows
            val geometry = historyGeometry()
            historyScroll = MenuLayout.clampScroll(
                historyScroll - (vertical * MenuLayout.SCROLL_STEP).toInt(),
                geometry.contentHeight,
                geometry.viewport.height,
            )
            return true
        }
        val grid = gridGeometry(visibleDyes().size)
        scroll = MenuLayout.clampScroll(
            scroll - (vertical * MenuLayout.SCROLL_STEP).toInt(),
            grid.contentHeight,
            grid.viewport.height,
        )
        return true
    }

    private fun clampScrollToContent() {
        val grid = gridGeometry(visibleDyes().size)
        scroll = MenuLayout.clampScroll(scroll, grid.contentHeight, grid.viewport.height)
        clampCollectionScroll()
        clampAchievementsScroll()
        clampHistoryScroll()
        settingsScroll = MenuLayout.clampScroll(settingsScroll, maxSettingsScroll(settingsRows(Util.getMillis()).size))
    }

    /** Keeps [achievementsScroll] within the Achievements list; leaves it while no list shows. */
    private fun clampAchievementsScroll() {
        val response = loadedAchievements() ?: return
        val geometry = achievementsGeometry(response.achievements)
        achievementsScroll = MenuLayout.clampScroll(achievementsScroll, geometry.contentHeight, geometry.viewport.height)
    }

    /** The achievements loaded for the profile the strip shows, else `null` (none shown, loading, failed). */
    private fun loadedAchievements(): AchievementsResponse? = strip?.profile?.id?.let(achievementsFetch::loadedFor)

    /** The sections of [entries] that [statusFilter] keeps: what the Achievements list shows. */
    private fun visibleAchievementSections(entries: List<PlayerAchievementEntry>): List<AchievementSection> =
        MenuAchievements.filteredSections(entries, statusFilter)

    /**
     * Where the Achievements tab's parts sit for [entries] (all of them) on the current screen
     * size: below the profile strip, as wide as it, with the status pills and the
     * [visibleAchievementSections].
     */
    private fun achievementsGeometry(entries: List<PlayerAchievementEntry>): AchievementsGeometry {
        val stripRow = gridGeometry(0).stripRow
        return MenuLayout.achievementsGeometry(
            top = stripRow.bottom + MenuLayout.STRIP_GAP,
            left = stripRow.x,
            width = stripRow.width,
            screenHeight = height,
            lineHeight = font.lineHeight,
            pillLabels = statusPillLabels(entries),
            measure = { font.width(it) },
            sectionSizes = visibleAchievementSections(entries).map { it.entries.size },
        )
    }

    /**
     * The All / Unlocked / Locked pills as `key to label` (the key is the [StatusFilter] name), each
     * with its count over all [entries], so a click never reflows the row.
     */
    private fun statusPillLabels(entries: List<PlayerAchievementEntry>): List<Pair<String, String>> =
        StatusFilter.entries.map { it.name to MenuAchievements.statusPillLabel(it, entries) }

    /** [statusPillLabels] at [geometry]'s rects; the selected [statusFilter]'s pill is selected. */
    private fun statusPills(entries: List<PlayerAchievementEntry>, geometry: AchievementsGeometry): List<MenuPill> =
        statusPillLabels(entries).zip(geometry.pills) { (key, label), (_, rect) ->
            MenuPill(key, label, rect, selected = key == statusFilter.name)
        }

    /** Keeps [historyScroll] within the History list; leaves it while no list shows. */
    private fun clampHistoryScroll() {
        if (historyRows().isEmpty()) return
        val geometry = historyGeometry()
        historyScroll = MenuLayout.clampScroll(historyScroll, geometry.contentHeight, geometry.viewport.height)
    }

    /**
     * The rows the History list shows: the dyes of [historyPeriod]'s history loaded for the profile
     * the strip shows, unless [historyStateMessage] shows instead; else none.
     */
    private fun historyRows(): List<HistoryDyeGain> {
        val profileId = strip?.profile?.id ?: return emptyList()
        val response = historyFetch.loadedFor(historyKey(profileId, historyPeriod)) ?: return emptyList()
        return if (historyStateMessage(response, historyPeriod) == null) response.dyes else emptyList()
    }

    /**
     * Where the History tab's parts sit on the current screen size: below the profile strip, as
     * wide as it, with the period pills, the chart (with rows only) and the [historyRows].
     */
    private fun historyGeometry(): HistoryGeometry {
        val stripRow = gridGeometry(0).stripRow
        return MenuLayout.historyGeometry(
            top = stripRow.bottom + MenuLayout.STRIP_GAP,
            left = stripRow.x,
            width = stripRow.width,
            screenHeight = height,
            pillLabels = periodPillLabels(),
            measure = { font.width(it) },
            rowCount = historyRows().size,
        )
    }

    /** The 1h / 24h / 7d / 30d pills as `key to label` (the key is the [HistoryPeriod] name). */
    private fun periodPillLabels(): List<Pair<String, String>> = HistoryPeriod.entries.map { it.name to it.label }

    /** [periodPillLabels] at [geometry]'s rects; the selected [historyPeriod]'s pill is selected. */
    private fun periodPills(geometry: HistoryGeometry): List<MenuPill> =
        periodPillLabels().zip(geometry.pills) { (key, label), (_, rect) ->
            MenuPill(key, label, rect, selected = key == historyPeriod.name)
        }

    /** Keeps [collectionScroll] within the Collection grid; leaves it while no grid shows. */
    private fun clampCollectionScroll() {
        val geometry = collectionCells()?.let(::collectionGeometry) ?: return
        collectionScroll = MenuLayout.clampScroll(collectionScroll, geometry.contentHeight, geometry.viewport.height)
    }

    /**
     * The Collection tab's cards: every loaded dye, owned or not, once both the Dyes tab's dyes
     * and the collection are loaded for the same profile; else `null`. The grid shows
     * [visibleCollectionCells] of them.
     */
    private fun collectionCells(): List<CollectionCell>? {
        val loaded = state as? ViewState.Loaded ?: return null
        val owned = collectionFetch.loadedFor(loaded.result.profile.id) ?: return null
        return MenuCollection.cells(loaded.result.dyes, owned.dyes)
    }

    /** How many of each dye (by `dyeId`) the player owns on the shown profile; empty until [collectionCells] loads. */
    private fun obtainedCounts(): Map<String, Int> = collectionCells()?.associate { it.entry.dyeId to it.count }.orEmpty()

    /** The Collection [cells] in the selected [collectionCategory] that [ownedFilter] keeps: what its grid shows. */
    private fun visibleCollectionCells(cells: List<CollectionCell>): List<CollectionCell> =
        MenuCollection.filterCells(cells, collectionCategory, ownedFilter)

    /**
     * Where the Collection tab's parts sit for [cells] (all of them) on the current screen size,
     * the drop timeline included.
     */
    private fun collectionGeometry(cells: List<CollectionCell>): CollectionGeometry {
        val grid = gridGeometry(cells.size)
        return MenuLayout.collectionGeometry(
            top = grid.stripRow.bottom + MenuLayout.STRIP_GAP,
            left = grid.left,
            gridWidth = grid.stripRow.width,
            screenHeight = height,
            lineHeight = font.lineHeight,
            showHint = MenuCollection.showEmptyHint(cells),
            pillLabels = collectionPillLabels(cells),
            measure = { font.width(it) },
            itemCount = visibleCollectionCells(cells).size,
            columns = grid.columns,
            timelineRows = MenuTimeline.timeline(cells).size,
        )
    }

    /**
     * The Collection tab's pills as `key to label`, one wrapping block: the "All Dyes" chip, a chip
     * per category of [cells] with its owned/total, then the All / Owned / Unowned pills with their
     * counts. Every count is over all [cells], so a click never reflows the block.
     */
    private fun collectionPillLabels(cells: List<CollectionCell>): List<Pair<String, String>> =
        listOf(ALL_PILL_KEY to categoryLabel(null)) +
            MenuCollection.categoryCounts(cells).map { it.category to MenuCollection.categoryChipLabel(it) } +
            OwnedFilter.entries.map { ownedFilterKey(it) to MenuCollection.ownedFilterLabel(it, cells) }

    /** [collectionPillLabels] at [geometry]'s rects; the selected category's chip and owned filter's pill are selected. */
    private fun collectionPills(cells: List<CollectionCell>, geometry: CollectionGeometry): List<MenuPill> {
        val selectedKeys = setOf(collectionCategory ?: ALL_PILL_KEY, ownedFilterKey(ownedFilter))
        return collectionPillLabels(cells).zip(geometry.pills) { (key, label), (_, rect) ->
            MenuPill(key, label, rect, selected = key in selectedKeys)
        }
    }

    /** [MenuPill.key] of [filter]'s pill, apart from the category chips' keys. */
    private fun ownedFilterKey(filter: OwnedFilter): String = "$OWNED_FILTER_KEY_PREFIX${filter.name}"

    /** The loaded dyes, or none while loading or after a failure. */
    private fun loadedDyes(): List<DyeProgressEntry> = (state as? ViewState.Loaded)?.result?.dyes.orEmpty()

    /** The loaded dyes in the selected [category], ordered by [sort]: what the grid shows. */
    private fun visibleDyes(): List<DyeProgressEntry> = applyFilters(loadedDyes(), category, sort)

    /**
     * The profile strip's row below the header, the control pills below it and the grid for
     * [itemCount] cards below them, all centred horizontally and as wide as the grid.
     */
    private fun gridGeometry(itemCount: Int): GridGeometry {
        val columns = MenuLayout.columns(width - 2 * MenuLayout.OUTER_MARGIN)
        val gridWidth = MenuLayout.gridWidth(columns)
        val left = (width - gridWidth) / 2
        val stripTop = MenuLayout.HEADER_HEIGHT + MenuLayout.OUTER_MARGIN
        val stripRow = Rect(left, stripTop, gridWidth, MenuLayout.STRIP_HEIGHT)
        val pills = controlPills(left, gridWidth, stripRow.bottom + MenuLayout.STRIP_GAP)
        val top = pills.maxOf { it.rect.bottom } + MenuLayout.OUTER_MARGIN
        val viewport = Rect(left, top, gridWidth, height - top - MenuLayout.OUTER_MARGIN)
        return GridGeometry(stripRow, pills, columns, left, top, viewport, MenuLayout.contentHeight(itemCount, columns))
    }

    /**
     * The strip's parts in [row], vertically centred, [MenuLayout.STRIP_ITEM_GAP] apart.
     *
     * After the username, the profile pill: `Profile: <name> ▸` when [strip] can cycle, else
     * without the arrow, its name shortened with `…` so it ends before the Refresh pill (or the
     * row's end). Right-aligned: the status text, the Refresh pill, then the totals. The Refresh
     * pill always shows unless not even `Profile:  ▸` fits beside it; the status text shows over the
     * shown tab's loaded data ([stripStatus]) and the totals only over loaded dyes, and when space is
     * short the totals go first, then the status text.
     * No pill is selected-styled.
     */
    private fun stripLayout(strip: StripState, row: Rect): StripLayout {
        val measure: (String) -> Int = { font.width(it) }
        val gap = MenuLayout.STRIP_ITEM_GAP
        val pillY = row.y + (row.height - MenuLayout.PILL_HEIGHT) / 2
        val profileX = row.x + font.width(strip.username) + gap
        val profileFrame = PROFILE_LABEL_PREFIX + if (strip.canSwitch) PROFILE_CYCLE_SUFFIX else ""
        val frameWidth = pillWidth(profileFrame, measure) // the profile pill with an empty name
        val refreshWidth = pillWidth(REFRESH_LABEL, measure)
        val showRefresh = profileX + frameWidth + gap + refreshWidth <= row.right
        val profileMaxRight = if (showRefresh) row.right - refreshWidth - gap else row.right
        val name = MenuCards.fitText(strip.profile.cuteName, profileMaxRight - profileX - frameWidth, measure)
        val profileLabel = buildString {
            append(PROFILE_LABEL_PREFIX).append(name)
            if (strip.canSwitch) append(PROFILE_CYCLE_SUFFIX)
        }
        val profilePill = MenuPill(
            PROFILE_PILL_KEY,
            profileLabel,
            Rect(profileX, pillY, pillWidth(profileLabel, measure), MenuLayout.PILL_HEIGHT),
            selected = false,
        )
        if (!showRefresh) return StripLayout(profilePill, status = null, refreshPill = null, totals = null)

        val loaded = state as? ViewState.Loaded
        var spare = row.right - (profilePill.rect.right + gap + refreshWidth)
        val status = stripStatus()?.takeIf { (text, _) -> font.width(text) + gap <= spare }
        if (status != null) spare -= font.width(status.first) + gap
        val totals = loaded?.let { stripTotalsText(it.result.dyes) }?.takeIf { font.width(it) + gap <= spare }

        var right = row.right
        val totalsText = totals?.let { StripText(it, right - font.width(it), UiTheme.Colors.TEXT_SUBTLE) }
        if (totalsText != null) right = totalsText.x - gap
        val refreshPill = MenuPill(
            REFRESH_PILL_KEY,
            REFRESH_LABEL,
            Rect(right - refreshWidth, pillY, refreshWidth, MenuLayout.PILL_HEIGHT),
            selected = false,
        )
        val statusText = status?.let { (text, argb) ->
            StripText(text, refreshPill.rect.x - gap - font.width(text), argb)
        }
        return StripLayout(profilePill, statusText, refreshPill, totalsText)
    }

    /**
     * The strip's totals over every one of the loaded [dyes]: obtained of all and how many at 100%
     * once the shown profile has its collection loaded ([collectionCells],
     * [MenuCollection.obtainedTotalsText]), else how many dyes and how many at 100% ([MenuData.totalsText]).
     */
    private fun stripTotalsText(dyes: List<DyeProgressEntry>): String {
        val totals = MenuData.totals(dyes)
        val cells = collectionCells() ?: return MenuData.totalsText(totals)
        return MenuCollection.obtainedTotalsText(MenuCollection.summary(cells).owned, totals.dyes, totals.complete)
    }

    /**
     * The strip's status text and its color for the shown tab's data: on the Collection tab the
     * collection of the loaded dyes' profile, on the Achievements tab the achievements of the strip's
     * profile, on the History tab its history over [historyPeriod], else the loaded dyes. `null`
     * while that data isn't loaded.
     */
    private fun stripStatus(): Pair<String, Int>? {
        val loaded = state as? ViewState.Loaded
        val tabState = when (tab) {
            MenuTab.COLLECTION -> loaded?.let { collectionFetch.stateFor(it.result.profile.id) }
            MenuTab.ACHIEVEMENTS -> strip?.profile?.id?.let(achievementsFetch::stateFor)
            MenuTab.HISTORY -> strip?.profile?.id?.let { historyFetch.stateFor(historyKey(it, historyPeriod)) }
            MenuTab.DYES, MenuTab.SETTINGS -> return loaded?.let { statusText(fetchedAtMs, dyesRefresh) }
        }
        return (tabState as? FetchState.Loaded)?.let { statusText(it.fetchedAtMs, it.refresh) }
    }

    /** The loaded dyes' background load, as [RefreshStatus]: running, else failed, else idle. */
    private val dyesRefresh: RefreshStatus
        get() = when {
            refreshing -> RefreshStatus.REFRESHING
            refreshFailed -> RefreshStatus.FAILED
            else -> RefreshStatus.IDLE
        }

    /** A status text and its color: "refreshing…", else "refresh failed", else the age of data fetched at [fetchedAtMs]. */
    private fun statusText(fetchedAtMs: Long, refresh: RefreshStatus): Pair<String, Int> = when (refresh) {
        RefreshStatus.REFRESHING -> REFRESHING_TEXT to UiTheme.Colors.TEXT_MUTED
        RefreshStatus.FAILED -> REFRESH_FAILED_TEXT to UiTheme.Colors.STATUS_ERROR
        RefreshStatus.IDLE -> formatAge(System.currentTimeMillis(), fetchedAtMs) to UiTheme.Colors.TEXT_MUTED
    }

    /**
     * The category pills ("All Dyes" first), wrapping within [gridWidth] from ([left], [top]), and
     * the sort pill right-aligned on the first row. The first category row leaves room for the
     * widest sort label, so cycling the sort never reflows the pills; later rows use the full grid
     * width.
     */
    private fun controlPills(left: Int, gridWidth: Int, top: Int): List<MenuPill> {
        val measure: (String) -> Int = { font.width(it) }
        val categoryLabels = (listOf<String?>(null) + MENU_CATEGORIES)
            .map { (it ?: ALL_PILL_KEY) to categoryLabel(it) }
        val sortReserve = MenuSort.entries.maxOf { pillWidth(sortLabel(it), measure) } + MenuLayout.PILL_GAP
        val selectedKey = category ?: ALL_PILL_KEY
        val placed = layoutPills(
            categoryLabels,
            measure,
            left,
            top,
            maxWidth = gridWidth,
            firstRowMaxWidth = gridWidth - sortReserve,
        )
        val categoryPills = categoryLabels.zip(placed) { (key, label), (_, rect) ->
            MenuPill(key, label, rect, selected = key == selectedKey)
        }
        val sortText = sortLabel(sort)
        val sortWidth = pillWidth(sortText, measure)
        val sortRect = Rect(left + gridWidth - sortWidth, top, sortWidth, MenuLayout.PILL_HEIGHT)
        return categoryPills + MenuPill(SORT_PILL_KEY, sortText, sortRect, selected = false)
    }

    /** The sort pill's text for [option], e.g. `Sort: Default`. */
    private fun sortLabel(option: MenuSort): String = "$SORT_LABEL_PREFIX${option.label}"

    /** The header's tab pills, one per [MenuTab], after the title; the active tab's is selected. */
    private fun tabPills(): List<MenuPill> {
        var x = MenuLayout.OUTER_MARGIN + font.width(TITLE) + MenuLayout.TAB_GAP
        val y = (MenuLayout.HEADER_HEIGHT - MenuLayout.PILL_HEIGHT) / 2
        return MenuTab.entries.map { entry ->
            val pillWidth = pillWidth(entry.label) { font.width(it) }
            val pill = MenuPill(entry.name, entry.label, Rect(x, y, pillWidth, MenuLayout.PILL_HEIGHT), entry == tab)
            x += pillWidth + MenuLayout.PILL_GAP
            pill
        }
    }

    /** The Settings rows as of now ([nowMs] is [Util.getMillis] time), read fresh from the live state. */
    private fun settingsRows(nowMs: Long): List<SettingsRow> {
        val config = ConfigManager.config
        val linked = config.isLinked()
        val account = if (linked) {
            val confirming = unlinkConfirm.isArmed(nowMs)
            SettingsRow(
                SettingsKey.ACCOUNT,
                ACCOUNT_LABEL,
                "$LINKED_AS_PREFIX${config.linkedUsername}",
                if (confirming) CONFIRM_UNLINK_LABEL else UNLINK_LABEL,
                if (confirming) PillStyle.CONFIRM else PillStyle.UNSELECTED,
            )
        } else {
            SettingsRow(SettingsKey.ACCOUNT, ACCOUNT_LABEL, NOT_LINKED_TEXT)
        }
        val syncDetail = lastSyncText(System.currentTimeMillis(), SyncManager.getLastSyncTime()) +
            if (SyncManager.isSyncPending()) SYNC_PENDING_SUFFIX else ""
        return listOf(
            account,
            SettingsRow(
                SettingsKey.SYNC,
                SYNC_LABEL,
                syncDetail,
                SYNC_NOW_LABEL,
                if (linked) PillStyle.UNSELECTED else PillStyle.INACTIVE,
            ),
            switchRow(SettingsKey.ROTATION, ROTATION_LABEL, rotationVisible()),
            groupRow(
                SettingsKey.DYE_WIDGETS,
                DYE_WIDGETS_LABEL,
                aggregate(ConfigManager.dyeProgressPlacements.all().map { it.visible }),
                NO_DYE_WIDGETS_TEXT,
            ),
            groupRow(
                SettingsKey.GIFS,
                GIFS_LABEL,
                aggregate(ConfigManager.gifPlacements.all().map { it.visible }),
                NO_GIFS_TEXT,
            ),
            switchRow(SettingsKey.BOUNCE, BOUNCE_LABEL, BounceController.isEnabled()),
            switchRow(SettingsKey.DISCORD, DISCORD_LABEL, ConfigManager.isDiscordEnabled()),
            SettingsRow(
                SettingsKey.DISCORD_HUNT,
                DISCORD_HUNT_LABEL,
                detail = discordHuntName(config.discordHuntDyeId),
                pillLabel = DISCORD_HUNT_CHANGE,
            ),
            spotifyRow(),
            SettingsRow(SettingsKey.SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_ID_LABEL, pillLabel = SAVE_LABEL),
            switchRow(SettingsKey.SPOTIFY_WIDGET, SPOTIFY_WIDGET_LABEL, SpotifyActions.isWidgetVisible()),
            switchRow(SettingsKey.SPOTIFY_TINT, SPOTIFY_TINT_LABEL, ConfigManager.isSpotifyTintAll()),
            SettingsRow(SettingsKey.EDIT_LAYOUT, EDIT_LAYOUT_LABEL, pillLabel = EDIT_LAYOUT_LABEL),
        )
    }

    /** The Discord hunt row's detail: [huntDyeId]'s [DyeCatalog] name (the id itself when unknown), or [DISCORD_HUNT_NONE]. */
    private fun discordHuntName(huntDyeId: String): String = if (huntDyeId.isBlank()) {
        DISCORD_HUNT_NONE
    } else {
        DyeCatalog.displayNameOr(huntDyeId)
    }

    /**
     * The Spotify row: the status line ([statusText]) without its `Spotify: ` prefix, and the
     * [spotifyRowPill] for the same status, read once so the two agree.
     */
    private fun spotifyRow(): SettingsRow {
        val status = SpotifySessions.default.status()
        val (pillLabel, active) = spotifyRowPill(status)
        return SettingsRow(
            SettingsKey.SPOTIFY,
            SPOTIFY_LABEL,
            statusText(status).removePrefix(SPOTIFY_STATUS_PREFIX),
            pillLabel,
            if (active) PillStyle.UNSELECTED else PillStyle.INACTIVE,
        )
    }

    /** A row with an On/Off pill for one setting that is [on] or not. */
    private fun switchRow(key: SettingsKey, label: String, on: Boolean): SettingsRow =
        groupRow(key, label, if (on) ToggleState.ON else ToggleState.OFF, noneDetail = null)

    /**
     * A row with a toggle pill for a group of widgets in [state]: On-styled when on; inactive, with
     * [noneDetail] as the detail, when there are none.
     */
    private fun groupRow(key: SettingsKey, label: String, state: ToggleState, noneDetail: String?): SettingsRow =
        SettingsRow(
            key,
            label,
            detail = if (state == ToggleState.NONE) noneDetail else null,
            pillLabel = toggleLabel(state),
            pillStyle = when (state) {
                ToggleState.ON -> PillStyle.TOGGLE_ON
                ToggleState.NONE -> PillStyle.INACTIVE
                ToggleState.OFF, ToggleState.MIXED -> PillStyle.UNSELECTED
            },
        )

    /** Top of the Settings panel: [MenuLayout.OUTER_MARGIN] below the header. */
    private val settingsTop: Int get() = MenuLayout.HEADER_HEIGHT + MenuLayout.OUTER_MARGIN

    /** How tall the Settings panel may get: down to [MenuLayout.OUTER_MARGIN] above the screen's bottom. */
    private val settingsAvailableHeight: Int get() = height - settingsTop - MenuLayout.OUTER_MARGIN

    /** How far [rowCount] Settings rows scroll on the current screen size ([MenuLayout.settingsMaxScroll]). */
    private fun maxSettingsScroll(rowCount: Int): Int =
        MenuLayout.settingsMaxScroll(rowCount, MenuLayout.SETTINGS_ROW_HEIGHT, settingsAvailableHeight)

    /**
     * The Settings panel: [MenuLayout.SETTINGS_WIDTH] wide (narrower on a narrow screen), centred,
     * below the header, as tall as [rowCount] [MenuLayout.SETTINGS_ROW_HEIGHT] rows or
     * [settingsAvailableHeight], whichever is less.
     */
    private fun settingsPanel(rowCount: Int): Rect {
        val panelWidth = minOf(MenuLayout.SETTINGS_WIDTH, width - 2 * MenuLayout.OUTER_MARGIN)
        val panelHeight = minOf(rowCount * MenuLayout.SETTINGS_ROW_HEIGHT, settingsAvailableHeight).coerceAtLeast(0)
        return Rect((width - panelWidth) / 2, settingsTop, panelWidth, panelHeight)
    }

    /**
     * [settingsRows] placed top to bottom in [settingsPanel], scrolled up by [settingsScroll], each
     * pill right-aligned and vertically centred; rows wholly outside the panel are left out.
     */
    private fun settingsLayout(nowMs: Long): SettingsLayout {
        val rows = settingsRows(nowMs)
        val panel = settingsPanel(rows.size)
        val placed = rows.mapIndexed { index, row ->
            val rowY = panel.y + index * MenuLayout.SETTINGS_ROW_HEIGHT - settingsScroll
            val rect = Rect(panel.x, rowY, panel.width, MenuLayout.SETTINGS_ROW_HEIGHT)
            val pill = row.pillLabel?.let { label ->
                val pillWidth = pillWidth(label) { font.width(it) }
                MenuPill(
                    row.key.name,
                    label,
                    Rect(
                        rect.right - MenuLayout.CARD_PADDING - pillWidth,
                        rect.y + (rect.height - MenuLayout.PILL_HEIGHT) / 2,
                        pillWidth,
                        MenuLayout.PILL_HEIGHT,
                    ),
                    selected = false,
                )
            }
            PlacedRow(row, rect, pill)
        }
        val shown = placed.filter { it.rect.bottom > panel.y && it.rect.y < panel.bottom }
        return SettingsLayout(panel, shown, rows.size * MenuLayout.SETTINGS_ROW_HEIGHT)
    }

    override fun onClose() {
        Minecraft.getInstance().showScreen(null)
    }

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(context, mouseX, mouseY, delta)
        UiDraw.fillRect(context, 0, 0, width, height, UiTheme.Colors.MENU_BACKGROUND)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        drawHeader(context, mouseX, mouseY)
        when (tab) {
            MenuTab.DYES -> drawDyesTab(context, mouseX, mouseY)
            MenuTab.COLLECTION -> drawCollectionTab(context, mouseX, mouseY)
            MenuTab.ACHIEVEMENTS -> drawAchievementsTab(context, mouseX, mouseY)
            MenuTab.HISTORY -> drawHistoryTab(context, mouseX, mouseY)
            MenuTab.SETTINGS -> drawSettings(context, mouseX, mouseY)
        }
        // The widgets (the Settings tab's Client ID field) go over the panel, once it has placed them.
        super.extractRenderState(context, mouseX, mouseY, delta)
        drawPinMessage(context)
    }

    /** The Dyes tab: the profile strip, then the dyes or what the content area says instead. */
    private fun drawDyesTab(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val dyes = visibleDyes()
        val grid = gridGeometry(dyes.size)
        val messageArea = drawStripArea(context, grid.stripRow, mouseX, mouseY)
        when (val current = state) {
            is ViewState.Loaded -> drawDyes(context, dyes, current.result.profile, grid, mouseX, mouseY)
            else -> drawStateMessage(context, current, messageArea)
        }
    }

    /**
     * The Collection tab: the profile strip, then the collection ([CollectionTabView]) once the
     * Dyes tab's dyes and the collection are loaded for the strip's profile. Until the dyes are,
     * the Dyes tab's loading or failure message shows; then the collection's.
     */
    private fun drawCollectionTab(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val grid = gridGeometry(loadedDyes().size)
        val messageArea = drawStripArea(context, grid.stripRow, mouseX, mouseY)
        val loaded = state as? ViewState.Loaded
        if (loaded == null) {
            drawStateMessage(context, state, messageArea)
            return
        }
        when (val current = collectionFetch.stateFor(loaded.result.profile.id)) {
            null, is FetchState.Loading ->
                drawMessage(context, COLLECTION_LOADING_TEXT, UiTheme.Colors.TEXT_SUBTLE, messageArea)
            is FetchState.Failed ->
                drawMessage(context, "$COLLECTION_FAILED_PREFIX${current.message}", UiTheme.Colors.STATUS_ERROR, messageArea)
            is FetchState.Loaded -> {
                val cells = MenuCollection.cells(loaded.result.dyes, current.data.dyes)
                val geometry = collectionGeometry(cells)
                CollectionTabView.draw(
                    this,
                    context,
                    font,
                    cells,
                    visibleCollectionCells(cells),
                    collectionPills(cells, geometry),
                    ownedFilter,
                    geometry,
                    grid.columns,
                    collectionScroll,
                    mouseX,
                    mouseY,
                )
            }
        }
    }

    /**
     * The Achievements tab: the profile strip, then the strip profile's achievements
     * ([AchievementsTabView]) once loaded, else their loading or failure message. Before the first
     * load gives the strip a profile, the Dyes tab's loading or failure message shows.
     */
    private fun drawAchievementsTab(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val messageArea = drawStripArea(context, gridGeometry(0).stripRow, mouseX, mouseY)
        val profileId = strip?.profile?.id
        if (profileId == null) {
            drawStateMessage(context, state, messageArea)
            return
        }
        when (val current = achievementsFetch.stateFor(profileId)) {
            null, is FetchState.Loading ->
                drawMessage(context, ACHIEVEMENTS_LOADING_TEXT, UiTheme.Colors.TEXT_SUBTLE, messageArea)
            is FetchState.Failed ->
                drawMessage(context, "$ACHIEVEMENTS_FAILED_PREFIX${current.message}", UiTheme.Colors.STATUS_ERROR, messageArea)
            is FetchState.Loaded -> {
                val entries = current.data.achievements
                val geometry = achievementsGeometry(entries)
                AchievementsTabView.draw(
                    this,
                    context,
                    font,
                    current.data,
                    visibleAchievementSections(entries),
                    statusPills(entries, geometry),
                    statusFilter,
                    geometry,
                    achievementsScroll,
                    mouseX,
                    mouseY,
                )
            }
        }
    }

    /**
     * The History tab: the profile strip, then the period pills and the strip profile's history over
     * [historyPeriod] ([HistoryTabView]) once loaded, with the selected dye's chart ([historyChart]),
     * else its loading or failure message under the pills, so another period can still be picked.
     * Before the first load gives the strip a profile, the Dyes tab's loading or failure message shows.
     */
    private fun drawHistoryTab(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val messageArea = drawStripArea(context, gridGeometry(0).stripRow, mouseX, mouseY)
        val profileId = strip?.profile?.id
        if (profileId == null) {
            drawStateMessage(context, state, messageArea)
            return
        }
        val message = when (val current = historyFetch.stateFor(historyKey(profileId, historyPeriod))) {
            null, is FetchState.Loading -> HistoryTabView.Message(HISTORY_LOADING_TEXT, UiTheme.Colors.TEXT_SUBTLE)
            is FetchState.Failed ->
                HistoryTabView.Message("$HISTORY_FAILED_PREFIX${current.message}", UiTheme.Colors.STATUS_ERROR)
            is FetchState.Loaded ->
                historyStateMessage(current.data, historyPeriod)?.let { HistoryTabView.Message(it, UiTheme.Colors.TEXT_SUBTLE) }
        }
        val rows = historyRows()
        val selected = historySelection(rows, historyDye)
        val geometry = historyGeometry()
        HistoryTabView.draw(
            this,
            context,
            font,
            periodPills(geometry),
            message,
            selected?.let { historyChart(profileId, it) },
            rows,
            selected?.dyeId,
            geometry,
            historyScroll,
            mouseX,
            mouseY,
        )
    }

    /**
     * The chart of [gain]'s work on [profileId] over [historyPeriod], in its color (the theme's
     * accent when it doesn't parse): its bars once loaded, else its loading or failure message, or
     * [chartMessage] when no slice has work.
     */
    private fun historyChart(profileId: String, gain: HistoryDyeGain): HistoryTabView.Chart {
        val title = chartTitle(gain.name, historyPeriod)
        val argb = DyeProgressWidgetView.parseAccentColor(gain.color)
        val key = historySeriesKey(profileId, historyPeriod, gain.dyeId)
        val (message, buckets) = when (val current = historySeriesFetch.stateFor(key)) {
            null, is FetchState.Loading ->
                HistoryTabView.Message(HISTORY_CHART_LOADING_TEXT, UiTheme.Colors.TEXT_SUBTLE) to emptyList()
            is FetchState.Failed ->
                HistoryTabView.Message(HISTORY_CHART_FAILED_TEXT, UiTheme.Colors.STATUS_ERROR) to emptyList()
            is FetchState.Loaded -> {
                val text = chartMessage(current.data.buckets)
                text?.let { HistoryTabView.Message(it, UiTheme.Colors.TEXT_SUBTLE) } to current.data.buckets
            }
        }
        return HistoryTabView.Chart(title, message, buckets, argb)
    }

    /**
     * The profile strip in [stripRow] when there is one; returns where a message goes: below the
     * strip when it shows (a profile switch loading or failing), else below the header.
     */
    private fun drawStripArea(context: GuiGraphicsExtractor, stripRow: Rect, mouseX: Int, mouseY: Int): Rect {
        val currentStrip = strip ?: return Rect(0, MenuLayout.HEADER_HEIGHT, width, height - MenuLayout.HEADER_HEIGHT)
        drawStrip(context, currentStrip, stripRow, mouseX, mouseY)
        return Rect(0, stripRow.bottom, width, height - stripRow.bottom)
    }

    /** What the content area says for a [current] Dyes state without loaded dyes, centred in [area]. */
    private fun drawStateMessage(context: GuiGraphicsExtractor, current: ViewState, area: Rect) {
        when (current) {
            ViewState.Loading -> drawMessage(context, LOADING_TEXT, UiTheme.Colors.TEXT_SUBTLE, area)
            is ViewState.NoProfiles ->
                drawMessage(context, "$NO_PROFILES_PREFIX${current.username}.", UiTheme.Colors.TEXT_SUBTLE, area)
            is ViewState.Failed ->
                drawMessage(context, "$FAILED_PREFIX${current.message}", UiTheme.Colors.STATUS_ERROR, area)
            is ViewState.Loaded -> Unit // the tab draws loaded dyes itself
        }
    }

    /**
     * The Settings tab: the [settingsLayout] rows in one card-styled panel, clipped to it, with a
     * scrollbar when they overflow; then the Client ID field placed on its row.
     */
    private fun drawSettings(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val now = Util.getMillis()
        val layout = settingsLayout(now)
        val panel = layout.panel
        UiDraw.filledBox(
            context,
            panel.x,
            panel.y,
            panel.width,
            panel.height,
            UiTheme.Colors.CARD_BACKGROUND,
            UiTheme.Colors.CARD_BORDER,
        )
        val message = rowMessage?.takeIf { now < it.untilMs }
        if (message == null) rowMessage = null
        val inPanel = panel.contains(mouseX, mouseY)
        drawScrolled(context, panel, layout.contentHeight, settingsScroll) {
            for (row in layout.rows) {
                drawSettingsRow(context, row, message?.takeIf { it.key == row.row.key }, mouseX, mouseY, inPanel)
            }
        }
        placeClientIdField(layout)
    }

    /**
     * One Settings row: its label on the left, its detail line (or [message], which replaces it)
     * under the label in small text, both shortened to end before the pill; the pill right-aligned,
     * hover-styled unless inactive or the mouse is outside the panel ([inPanel]). The text is
     * vertically centred in the row. The Client ID row keeps its detail line's place for the field,
     * so its [message] goes after the label instead.
     */
    private fun drawSettingsRow(
        context: GuiGraphicsExtractor,
        placed: PlacedRow,
        message: RowMessage?,
        mouseX: Int,
        mouseY: Int,
        inPanel: Boolean,
    ) {
        val row = placed.row
        val rect = placed.rect
        val pill = placed.pill
        val hasField = row.key == SettingsKey.SPOTIFY_CLIENT_ID
        val textX = rect.x + MenuLayout.CARD_PADDING
        val textRight = settingsTextRight(placed)
        val detail = if (hasField) null else message?.text ?: row.detail
        val detailArgb = message?.argb ?: UiTheme.Colors.TEXT_MUTED
        val belowHeight = when {
            hasField -> MenuLayout.SETTINGS_FIELD_HEIGHT
            detail != null -> scaledLineHeight(MenuLayout.SMALL_TEXT_SCALE)
            else -> 0
        }
        val labelY = settingsLabelY(rect, belowHeight)
        val label = MenuCards.fitText(row.label, textRight - textX) { font.width(it) }
        UiDraw.drawText(context, font, label, textX, labelY, UiTheme.Colors.TEXT_PRIMARY)
        if (hasField && message != null) {
            val messageX = textX + font.width(label) + MenuLayout.TEXT_GAP
            val fitted = MenuCards.fitText(message.text, textRight - messageX) {
                scaledWidth(it, MenuLayout.SMALL_TEXT_SCALE)
            }
            UiDraw.drawText(
                context,
                font,
                fitted,
                messageX,
                labelY + font.lineHeight - scaledLineHeight(MenuLayout.SMALL_TEXT_SCALE), // level with the label's foot
                message.argb,
                scale = MenuLayout.SMALL_TEXT_SCALE,
            )
        }
        if (detail != null) {
            val fitted = MenuCards.fitText(detail, textRight - textX) { scaledWidth(it, MenuLayout.SMALL_TEXT_SCALE) }
            UiDraw.drawText(
                context,
                font,
                fitted,
                textX,
                labelY + font.lineHeight,
                detailArgb,
                scale = MenuLayout.SMALL_TEXT_SCALE,
            )
        }
        if (pill != null) {
            val hovered = inPanel && row.pillStyle != PillStyle.INACTIVE &&
                pill.rect.contains(mouseX.toDouble(), mouseY.toDouble())
            drawPill(context, pill, hovered, row.pillStyle)
        }
    }

    /** The last card click's message, centred in the bottom margin, until it expires. */
    private fun drawPinMessage(context: GuiGraphicsExtractor) {
        val message = pinMessage ?: return
        if (Util.getMillis() >= message.untilMs) {
            pinMessage = null
            return
        }
        val margin = Rect(0, height - MenuLayout.OUTER_MARGIN, width, MenuLayout.OUTER_MARGIN)
        drawMessage(context, message.text, message.argb, margin)
    }

    /**
     * The profile strip in [row], laid out by [stripLayout]: the username, the profile pill
     * (hover-styled only when it can cycle), the status text, the Refresh pill (hover-styled only
     * while it isn't [refreshBlocked], as it is ignored meanwhile), and the totals of every loaded
     * dye ([stripTotalsText]).
     */
    private fun drawStrip(context: GuiGraphicsExtractor, strip: StripState, row: Rect, mouseX: Int, mouseY: Int) {
        val textY = row.y + (row.height - font.lineHeight) / 2
        UiDraw.drawText(context, font, strip.username, row.x, textY, UiTheme.Colors.TEXT_PRIMARY)
        val layout = stripLayout(strip, row)
        val x = mouseX.toDouble()
        val y = mouseY.toDouble()
        drawPill(context, layout.profilePill, hovered = strip.canSwitch && layout.profilePill.rect.contains(x, y))
        layout.refreshPill?.let { drawPill(context, it, hovered = !refreshBlocked && it.rect.contains(x, y)) }
        for (text in listOfNotNull(layout.status, layout.totals)) {
            UiDraw.drawText(context, font, text.text, text.x, textY, text.argb)
        }
    }

    /**
     * The control pills, then [dyes] (of [profile]) in the grid laid out by [grid], or a note when
     * the filter leaves no dyes.
     */
    private fun drawDyes(
        context: GuiGraphicsExtractor,
        dyes: List<DyeProgressEntry>,
        profile: ProfileSummary,
        grid: GridGeometry,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hoveredPill = pillAt(grid.pills, mouseX.toDouble(), mouseY.toDouble())
        for (pill in grid.pills) drawPill(context, pill, hovered = pill == hoveredPill)
        if (dyes.isEmpty()) {
            drawMessage(context, EMPTY_FILTER_TEXT, UiTheme.Colors.TEXT_SUBTLE, grid.viewport)
            return
        }
        drawGrid(context, dyes, profile, grid, mouseX, mouseY)
    }

    /**
     * One pill, like the website's `Button`: white with black text when selected, else a card
     * fill and border whose border and text brighten on hover. [style] adds the Settings looks: a
     * toggle that is on takes the `success` Badge colors, a confirming action error-colored text,
     * and an inactive pill muted text; a complete Collection chip emerald text. The label is centred.
     */
    internal fun drawPill(
        context: GuiGraphicsExtractor,
        pill: MenuPill,
        hovered: Boolean,
        style: PillStyle = if (pill.selected) PillStyle.SELECTED else PillStyle.UNSELECTED,
    ) {
        val hoverBorder = if (hovered) UiTheme.Colors.PILL_HOVER_BORDER else UiTheme.Colors.CARD_BORDER
        val (fill, border, text) = when (style) {
            PillStyle.SELECTED ->
                Triple(UiTheme.Colors.PILL_SELECTED_BG, UiTheme.Colors.PILL_SELECTED_BG, UiTheme.Colors.PILL_SELECTED_TEXT)
            PillStyle.UNSELECTED -> Triple(
                UiTheme.Colors.CARD_BACKGROUND,
                hoverBorder,
                if (hovered) UiTheme.Colors.TEXT_PRIMARY else UiTheme.Colors.TEXT_SUBTLE,
            )
            PillStyle.TOGGLE_ON -> Triple(
                UiTheme.Colors.TOGGLE_ON_BG,
                if (hovered) UiTheme.Colors.PILL_HOVER_BORDER else UiTheme.Colors.TOGGLE_ON_BORDER,
                UiTheme.Colors.ACCENT_SECONDARY,
            )
            PillStyle.CONFIRM -> Triple(UiTheme.Colors.CARD_BACKGROUND, hoverBorder, UiTheme.Colors.STATUS_ERROR)
            PillStyle.INACTIVE -> Triple(UiTheme.Colors.CARD_BACKGROUND, UiTheme.Colors.CARD_BORDER, UiTheme.Colors.TEXT_MUTED)
            PillStyle.COMPLETE -> Triple(UiTheme.Colors.CARD_BACKGROUND, hoverBorder, UiTheme.Colors.OWNED_TEXT)
        }
        val rect = pill.rect
        UiDraw.filledBox(context, rect.x, rect.y, rect.width, rect.height, fill, border)
        UiDraw.drawText(
            context,
            font,
            pill.label,
            rect.x,
            rect.y + (rect.height - font.lineHeight) / 2,
            text,
            shadow = style != PillStyle.SELECTED, // a shadow under black text on white only smudges it
            availableWidth = rect.width,
            alignment = HorizontalAlignment.CENTER,
        )
    }

    /** Header bar: the title, then the [tabPills] (the active tab's selected, the other hover-styled). */
    private fun drawHeader(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        UiDraw.fillRect(context, 0, 0, width, MenuLayout.HEADER_HEIGHT, UiTheme.Colors.MENU_SURFACE)
        val textY = (MenuLayout.HEADER_HEIGHT - font.lineHeight) / 2
        UiDraw.drawText(context, font, TITLE, MenuLayout.OUTER_MARGIN, textY, UiTheme.Colors.ACCENT_PRIMARY)
        for (pill in tabPills()) {
            drawPill(context, pill, hovered = pill.rect.contains(mouseX.toDouble(), mouseY.toDouble()))
        }
    }

    /** One line of [text], centred in [area]. */
    internal fun drawMessage(context: GuiGraphicsExtractor, text: String, argb: Int, area: Rect) {
        UiDraw.drawText(
            context,
            font,
            text,
            area.x,
            area.y + (area.height - font.lineHeight) / 2,
            argb,
            availableWidth = area.width,
            alignment = HorizontalAlignment.CENTER,
        )
    }

    /**
     * The dye cards laid out by [grid] ([drawCardGrid], scrolled by [scroll]), the hovered one with
     * its [pinHint] for [profile].
     */
    private fun drawGrid(
        context: GuiGraphicsExtractor,
        dyes: List<DyeProgressEntry>,
        profile: ProfileSummary,
        grid: GridGeometry,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = MenuLayout.cardIndexAt(
            mouseX.toDouble(),
            mouseY.toDouble(),
            dyes.size,
            grid.columns,
            grid.left,
            grid.top,
            scroll,
            grid.viewport,
        )
        val hint = hovered?.let { pinHint(dyes[it], profile) }
        val obtained = obtainedCounts()
        val renderCtx = RenderContext(context, Util.getMillis(), false)
        drawCardGrid(context, dyes.size, grid.columns, grid.left, grid.viewport, grid.contentHeight, scroll) { index, x, y ->
            val isHovered = index == hovered
            drawCard(
                context,
                renderCtx,
                dyes[index],
                x,
                y,
                hovered = isHovered,
                hint = if (isHovered) hint else null,
                obtainedCount = obtained[dyes[index].dyeId] ?: 0,
            )
        }
    }

    /**
     * [itemCount] cards in a [columns]-wide grid from ([left], `viewport.y`), clipped to
     * [viewport] and scrolled up by [scroll], then the scrollbar for [contentHeight]. Only cards
     * that intersect the viewport are drawn, each by [drawItem] with its index and top-left;
     * [drawBelow] then draws any scrolled content below the cards inside the same clip.
     * Nothing is drawn when the window leaves the viewport empty (26.2 rejects a zero-size scissor).
     */
    internal fun drawCardGrid(
        context: GuiGraphicsExtractor,
        itemCount: Int,
        columns: Int,
        left: Int,
        viewport: Rect,
        contentHeight: Int,
        scroll: Int,
        drawBelow: () -> Unit = {},
        drawItem: (index: Int, x: Int, y: Int) -> Unit,
    ) {
        drawScrolled(context, viewport, contentHeight, scroll) {
            for (index in 0 until itemCount) {
                val (x, y) = MenuLayout.cardOrigin(index, columns, left, viewport.y - scroll)
                if (y >= viewport.bottom) break // cards are row-ordered, so every later card is below too
                if (y + MenuLayout.CARD_HEIGHT <= viewport.y) continue
                drawItem(index, x, y)
            }
            drawBelow()
        }
    }

    /**
     * [drawContent] clipped to [viewport], then the scrollbar for [contentHeight] scrolled by
     * [scroll]. Nothing is drawn when the window leaves the viewport empty (26.2 rejects a
     * zero-size scissor).
     */
    internal fun drawScrolled(
        context: GuiGraphicsExtractor,
        viewport: Rect,
        contentHeight: Int,
        scroll: Int,
        drawContent: () -> Unit,
    ) {
        if (viewport.width <= 0 || viewport.height <= 0) return
        context.enableScissor(viewport.x, viewport.y, viewport.right, viewport.bottom)
        drawContent()
        context.disableScissor()
        drawScrollbar(context, viewport, contentHeight, scroll)
    }

    /**
     * A thin track right of the grid with a thumb sized and placed by [scroll]; only when content
     * overflows.
     */
    private fun drawScrollbar(context: GuiGraphicsExtractor, viewport: Rect, contentHeight: Int, scroll: Int) {
        val maxScroll = contentHeight - viewport.height
        if (maxScroll <= 0) return
        val trackX = viewport.right + MenuLayout.SCROLLBAR_GAP
        UiDraw.fillRect(
            context,
            trackX,
            viewport.y,
            MenuLayout.SCROLLBAR_WIDTH,
            viewport.height,
            UiTheme.Colors.PROGRESS_TRACK,
        )
        val thumbHeight = max(MenuLayout.SCROLLBAR_MIN_THUMB, viewport.height * viewport.height / contentHeight)
            .coerceAtMost(viewport.height)
        val thumbY = viewport.y + (viewport.height - thumbHeight) * scroll / maxScroll
        UiDraw.fillRect(context, trackX, thumbY, MenuLayout.SCROLLBAR_WIDTH, thumbHeight, UiTheme.Colors.ACCENT_PRIMARY)
    }

    /**
     * One card, like the website's `DyeCard`: icon box with the sprite, name and category, a
     * status badge top-right, and a label/value line over the progress bar. A dye the player owns
     * ([obtainedCount] above 0) has its [MenuCollection.obtainedBadge] right-aligned on the category
     * line, its top level with the category text's ([MenuLayout.categoryLine]), the category ending
     * before it. The border takes the dye's accent when [hovered]. A [hint] goes at the right end of
     * the label/value line, the value moving left of it, or dropped when the label, value and hint
     * don't all fit.
     */
    private fun drawCard(
        context: GuiGraphicsExtractor,
        renderCtx: RenderContext,
        entry: DyeProgressEntry,
        x: Int,
        y: Int,
        hovered: Boolean,
        hint: PinHint?,
        obtainedCount: Int,
    ) {
        val padding = MenuLayout.CARD_PADDING
        val innerWidth = MenuLayout.CARD_WIDTH - 2 * padding
        val accent = DyeProgressWidgetView.parseAccentColor(entry.color)
        UiDraw.filledBox(
            context,
            x,
            y,
            MenuLayout.CARD_WIDTH,
            MenuLayout.CARD_HEIGHT,
            UiTheme.Colors.CARD_BACKGROUND,
            if (hovered) accent else UiTheme.Colors.CARD_BORDER,
        )
        drawIconBox(context, renderCtx, entry.dyeId, x + padding, y + padding)
        val badgeLeft = drawBadge(context, MenuCards.badgeFor(entry), x + MenuLayout.CARD_WIDTH - padding, y + padding)

        val textX = x + padding + MenuLayout.ICON_BOX + MenuLayout.TEXT_GAP
        val nameY = y + padding + MenuLayout.NAME_TOP_OFFSET
        val name = MenuCards.fitText(entry.name, badgeLeft - MenuLayout.TEXT_GAP - textX) { font.width(it) }
        UiDraw.drawText(context, font, name, textX, nameY, UiTheme.Colors.TEXT_PRIMARY)
        val textRight = x + MenuLayout.CARD_WIDTH - padding
        val categoryY = nameY + font.lineHeight
        val obtained = MenuCollection.obtainedBadge(obtainedCount)
        val categoryWidth = if (obtained == null) {
            textRight - textX
        } else {
            val obtainedWidth = badgeWidth(obtained)
            val line = MenuLayout.categoryLine(textX, textRight, obtainedWidth)
            drawBadge(context, obtained, OWNED_BADGE_COLORS, line.badgeLeft + obtainedWidth, categoryY)
            line.categoryWidth
        }
        val category = MenuCards.fitText(MenuCards.formatCategory(entry.category), categoryWidth) {
            scaledWidth(it, MenuLayout.SMALL_TEXT_SCALE)
        }
        UiDraw.drawText(
            context,
            font,
            category,
            textX,
            categoryY,
            UiTheme.Colors.TEXT_MUTED,
            scale = MenuLayout.SMALL_TEXT_SCALE,
        )

        val barHeight = UiTheme.Sizing.PROGRESS_BAR_HEIGHT
        val barY = y + MenuLayout.CARD_HEIGHT - padding - barHeight
        val lineY = barY - MenuLayout.TEXT_GAP - scaledLineHeight(MenuLayout.SMALL_TEXT_SCALE)
        val label = MenuCards.statusLabel(entry)
        UiDraw.drawText(
            context,
            font,
            label,
            x + padding,
            lineY,
            UiTheme.Colors.TEXT_SUBTLE,
            scale = MenuLayout.SMALL_TEXT_SCALE,
        )
        val value = MenuCards.valueText(entry)
        var valueWidth = innerWidth // the value is right-aligned in this width from the card's left padding
        if (hint != null) {
            UiDraw.drawText(
                context,
                font,
                hint.text,
                x + padding,
                lineY,
                hint.argb,
                scale = MenuLayout.SMALL_TEXT_SCALE,
                availableWidth = innerWidth,
                alignment = HorizontalAlignment.END,
            )
            valueWidth = innerWidth - scaledWidth(hint.text, MenuLayout.SMALL_TEXT_SCALE) - MenuLayout.TEXT_GAP
        }
        val labelEnd = scaledWidth(label, MenuLayout.SMALL_TEXT_SCALE) + MenuLayout.TEXT_GAP
        if (hint == null || labelEnd + scaledWidth(value, MenuLayout.SMALL_TEXT_SCALE) <= valueWidth) {
            UiDraw.drawText(
                context,
                font,
                value,
                x + padding,
                lineY,
                UiTheme.Colors.TEXT_PRIMARY,
                scale = MenuLayout.SMALL_TEXT_SCALE,
                availableWidth = valueWidth,
                alignment = HorizontalAlignment.END,
            )
        }
        ProgressBarWidget(
            value = (entry.progress ?: 0.0).toFloat().coerceIn(ProgressBarWidget.MIN_VALUE, ProgressBarWidget.MAX_VALUE),
            width = innerWidth,
            height = barHeight,
            fillColor = accent,
        ).draw(renderCtx, x + padding, barY)
    }

    /** The black icon box at ([x], [y]) with the dye's sprite centred in it, when the sprite is loaded. */
    internal fun drawIconBox(context: GuiGraphicsExtractor, renderCtx: RenderContext, dyeId: String, x: Int, y: Int) {
        UiDraw.filledBox(
            context,
            x,
            y,
            MenuLayout.ICON_BOX,
            MenuLayout.ICON_BOX,
            UiTheme.Colors.ICON_BOX_BACKGROUND,
            UiTheme.Colors.ICON_BOX_BORDER,
        )
        if (!DyeSprites.has(dyeId)) return
        val frameId = ImageTextureManager.currentFrameIdentifier(
            DyeSprites.spriteId(dyeId),
            renderCtx.wallClockMs,
            renderCtx.paused,
        ) ?: return
        val inset = (MenuLayout.ICON_BOX - MenuLayout.ICON_SPRITE) / 2
        UiDraw.blitStretched(
            context,
            frameId,
            x + inset,
            y + inset,
            MenuLayout.ICON_SPRITE,
            MenuLayout.ICON_SPRITE,
            MenuLayout.SPRITE_SOURCE_SIZE,
            MenuLayout.SPRITE_SOURCE_SIZE,
        )
    }

    /** Draws [badge] right-aligned to [right] with its top at [top]; returns the badge's left edge. */
    private fun drawBadge(context: GuiGraphicsExtractor, badge: CardBadge, right: Int, top: Int): Int =
        drawBadge(context, badge.label, badgeColors(badge), right, top)

    /** Draws a badge reading [label] in [colors], right-aligned to [right] with its top at [top]; returns its left edge. */
    internal fun drawBadge(context: GuiGraphicsExtractor, label: String, colors: BadgeColors, right: Int, top: Int): Int {
        val pad = MenuLayout.BADGE_PADDING
        val boxWidth = badgeWidth(label)
        val boxHeight = scaledLineHeight(MenuLayout.BADGE_SCALE) + 2 * pad
        val left = right - boxWidth
        UiDraw.filledBox(context, left, top, boxWidth, boxHeight, colors.background, colors.border)
        UiDraw.drawText(
            context,
            font,
            label,
            left + pad,
            top + pad,
            colors.text,
            scale = MenuLayout.BADGE_SCALE,
        )
        return left
    }

    /** Width of a badge reading [label], its padding included. */
    internal fun badgeWidth(label: String): Int = scaledWidth(label, MenuLayout.BADGE_SCALE) + 2 * MenuLayout.BADGE_PADDING

    /** A card badge's label [text] color, its [background] fill and its [border]. */
    internal data class BadgeColors(val text: Int, val background: Int, val border: Int)

    private fun badgeColors(badge: CardBadge): BadgeColors = when (badge) {
        CardBadge.MOD -> BadgeColors(
            UiTheme.Colors.BADGE_MOD_TEXT,
            UiTheme.Colors.BADGE_MOD_BG,
            UiTheme.Colors.BADGE_MOD_BORDER,
        )
        CardBadge.ESTIMATED -> BadgeColors(
            UiTheme.Colors.BADGE_ESTIMATED_TEXT,
            UiTheme.Colors.BADGE_ESTIMATED_BG,
            UiTheme.Colors.BADGE_ESTIMATED_BORDER,
        )
        CardBadge.MOD_ONLY, CardBadge.NOT_TRACKABLE -> BadgeColors(
            UiTheme.Colors.BADGE_MUTED_TEXT,
            UiTheme.Colors.BADGE_MUTED_BG,
            UiTheme.Colors.BADGE_MUTED_BORDER,
        )
    }

    /** Width of [text] drawn at [scale], rounded the way [UiDraw.drawText] rounds it. */
    internal fun scaledWidth(text: String, scale: Float): Int = (font.width(text) * scale).roundToInt()

    /** Height of one text line drawn at [scale]. */
    internal fun scaledLineHeight(scale: Float): Int = (font.lineHeight * scale).roundToInt()

    companion object {
        private const val TITLE = "DyeTracker"
        private const val LOADING_TEXT = "Loading your dyes…"
        private const val NO_PROFILES_PREFIX = "No SkyBlock profiles found for "
        private const val FAILED_PREFIX = "Couldn't load your dyes: "
        private const val EMPTY_FILTER_TEXT = "No dyes in this category."
        private const val COLLECTION_LOADING_TEXT = "Loading your collection…"
        private const val COLLECTION_FAILED_PREFIX = "Couldn't load your collection: "
        private const val ACHIEVEMENTS_LOADING_TEXT = "Loading achievements…"
        private const val ACHIEVEMENTS_FAILED_PREFIX = "Couldn't load achievements: "
        private const val HISTORY_LOADING_TEXT = "Loading history…"
        private const val HISTORY_FAILED_PREFIX = "Couldn't load history: "
        private const val HISTORY_CHART_LOADING_TEXT = "Loading…"
        private const val HISTORY_CHART_FAILED_TEXT = "Couldn't load the chart"
        private const val SORT_LABEL_PREFIX = "Sort: "
        private const val PROFILE_LABEL_PREFIX = "Profile: "

        /** Ends the profile pill's label when it cycles (2+ profiles). */
        private const val PROFILE_CYCLE_SUFFIX = " ▸"

        /** [MenuPill.key] of the "All Dyes" pill; every other category pill's key is its category id. */
        private const val ALL_PILL_KEY = "all"

        /** Starts the [MenuPill.key] of a Collection owned-filter pill, then the [OwnedFilter] name. */
        private const val OWNED_FILTER_KEY_PREFIX = "owned:"

        /** [MenuPill.key] of the sort pill. */
        private const val SORT_PILL_KEY = "sort"

        /** [MenuPill.key] of the strip's profile pill. */
        private const val PROFILE_PILL_KEY = "profile"

        /** [MenuPill.key] of the strip's Refresh pill. */
        private const val REFRESH_PILL_KEY = "refresh"

        private const val REFRESH_LABEL = "Refresh"

        /** The strip's status text while a background fetch runs. */
        private const val REFRESHING_TEXT = "refreshing…"

        /** The strip's status text after a background fetch failed, until the next success. */
        private const val REFRESH_FAILED_TEXT = "refresh failed"

        /** Before the dye's name in the message after a card click pinned it. */
        private const val PINNED_PREFIX = "Pinned "

        /** After the dye's name in the message after a card click pinned it. */
        private const val PINNED_SUFFIX = " to your HUD. Move it with /dyetracker dye edit."

        /** After the dye's name in the message when its card was already pinned. */
        private const val ALREADY_PINNED_SUFFIX = " is already on your HUD."

        /** After the dye's name in the message when its card can't be pinned. */
        private const val NOT_PINNABLE_SUFFIX = " can't be pinned yet."

        /** The hovered card's hint when a click would pin it. */
        private const val PIN_HINT_TEXT = "+ HUD"

        /** The hovered card's hint when it is already pinned. */
        private const val ON_HUD_HINT_TEXT = "On HUD"

        private const val ACCOUNT_LABEL = "Account"
        private const val SYNC_LABEL = "Sync"
        private const val ROTATION_LABEL = "Rotation widget"
        private const val DYE_WIDGETS_LABEL = "Dye widgets"
        private const val GIFS_LABEL = "GIF overlays"
        private const val BOUNCE_LABEL = "Bounce"
        private const val DISCORD_LABEL = "Discord presence"
        private const val DISCORD_HUNT_LABEL = "Discord hunt"

        /** The Discord hunt row's detail while no hunt dye is picked. */
        private const val DISCORD_HUNT_NONE = "None"

        /** The Discord hunt row's pill, opening the picker. */
        private const val DISCORD_HUNT_CHANGE = "Change ▸"

        /** The Edit layout row's label, and its pill's. */
        private const val EDIT_LAYOUT_LABEL = "Edit layout"

        /** Before the linked account's name in the Account row. */
        private const val LINKED_AS_PREFIX = "Linked as "

        /** The Account row's detail while no account is linked. */
        private const val NOT_LINKED_TEXT = "Not linked: get a code on the website, then /dyetracker link <code>"

        private const val UNLINK_LABEL = "Unlink"

        /** The Unlink pill's text while it waits for the confirming click. */
        private const val CONFIRM_UNLINK_LABEL = "Confirm unlink"

        /** The Account row's message after the confirming click unlinked the account. */
        private const val UNLINKED_TEXT = "Unlinked."

        /** After the Sync row's last-sync text while a sync is pending. */
        private const val SYNC_PENDING_SUFFIX = " · pending"

        private const val SYNC_NOW_LABEL = "Sync now"

        /** The Sync row's message when there is no mod data to sync. */
        private const val NOTHING_TO_SYNC_TEXT = "Nothing to sync yet."

        /** The Sync row's message after a successful sync. */
        private const val SYNCED_TEXT = "Synced."

        private const val SPOTIFY_LABEL = "Spotify"
        private const val SPOTIFY_CLIENT_ID_LABEL = "Spotify Client ID"
        private const val SPOTIFY_WIDGET_LABEL = "Spotify widget"
        private const val SPOTIFY_TINT_LABEL = "Tint all widgets"

        /** The Spotify Client ID row's pill. */
        private const val SAVE_LABEL = "Save"

        /** The Client ID field's hint while it is empty. */
        private const val CLIENT_ID_HINT = "Paste your Client ID"

        /** What the Spotify status line ([statusText]) starts with; the Spotify row's label says it already. */
        private const val SPOTIFY_STATUS_PREFIX = "Spotify: "

        /** The Spotify Client ID row's message after Save saved the ID. */
        private const val CLIENT_ID_SAVED_TEXT = "Saved. Now press Log in."

        /** The Spotify Client ID row's message after Save found no Client ID in the field. */
        private const val CLIENT_ID_INVALID_TEXT = "Not a Client ID (32 hex characters)."

        /** The Dye widgets row's detail when no dye widget is configured. */
        private const val NO_DYE_WIDGETS_TEXT = "Pin dyes from the Dyes tab"

        /** An owned dye's badge colors: the Collection tab's `✓` / `xN` and the Dyes tab's OBTAINED. */
        internal val OWNED_BADGE_COLORS = BadgeColors(
            UiTheme.Colors.OWNED_TEXT,
            UiTheme.Colors.OWNED_BG,
            UiTheme.Colors.OWNED_BORDER,
        )

        /** The GIF overlays row's detail when no overlay is configured. */
        private const val NO_GIFS_TEXT = "Add one with /dyetracker gif add <url>"
    }
}
