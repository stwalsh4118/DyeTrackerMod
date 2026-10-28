package com.dyetracker.menu

import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.ProfileSummary
import com.dyetracker.client.showScreen
import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.PlayerRngData
import com.dyetracker.data.RngDataStore
import com.dyetracker.dyeprogress.DyeProgressPoller
import com.dyetracker.dyeprogress.DyeProgressWidgetConfig
import com.dyetracker.dyeprogress.DyeProgressWidgetView
import com.dyetracker.rotation.DyeSprites
import com.dyetracker.sync.SyncManager
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Rect
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.texture.ImageTextureManager
import com.dyetracker.ui.theme.UiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
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
 */
class DyeTrackerMenuScreen : Screen(Component.literal(TITLE)) {

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

    /** A load is running, in the background or behind Loading; Refresh is ignored meanwhile. */
    private val fetchRunning: Boolean get() = refreshing || state is ViewState.Loading

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init() {
        super.init()
        clampScrollToContent() // a resize changes the viewport and column count
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

    /** Shows [cached]'s dyes and profile, and its fetch time for the age text. */
    private fun showCached(cached: CachedMenu) {
        val result = cached.result
        strip = StripState(result.username, result.profiles, result.profile)
        state = ViewState.Loaded(result)
        fetchedAtMs = cached.fetchedAtMs
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
     * dropped even when its blocking call was already in flight.
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
                MenuLoadResult.Failed("$UNEXPECTED_ERROR_PREFIX${e.message ?: e.javaClass.simpleName}")
            }
            Minecraft.getInstance().execute {
                if (myEpoch != epoch) return@execute // stale — screen closed or another load started
                applyResult(result, background)
            }
        }
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
     */
    private fun applyResult(result: MenuLoadResult, background: Boolean) {
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
            }
            background -> refreshFailed = true
            else -> state = result.toViewState()
        }
    }

    private fun MenuLoadResult.toViewState(): ViewState = when (this) {
        is MenuLoadResult.Loaded -> ViewState.Loaded(this)
        is MenuLoadResult.NoProfiles -> ViewState.NoProfiles(username)
        is MenuLoadResult.Failed -> ViewState.Failed(message)
    }

    override fun removed() {
        epoch++
        scope.cancel()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (super.mouseClicked(click, doubled)) return true
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val dyes = visibleDyes()
        val grid = gridGeometry(dyes.size)
        val currentStrip = strip
        if (currentStrip != null) {
            val layout = stripLayout(currentStrip, grid.stripRow)
            // The profile pill also works while a switch loads or after it failed, so the player can switch again.
            if (currentStrip.canSwitch && layout.profilePill.rect.contains(click.x(), click.y())) {
                switchProfile(currentStrip)
                return true
            }
            if (layout.refreshPill?.rect?.contains(click.x(), click.y()) == true) {
                if (!fetchRunning) fetchProfile(currentStrip)
                return true
            }
        }
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
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
    }

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
     * pill always shows unless not even `Profile:  ▸` fits beside it; the status and totals show
     * only over loaded dyes, and when space is short the totals go first, then the status text.
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
        val status = loaded?.let { statusText() }?.takeIf { (text, _) -> font.width(text) + gap <= spare }
        if (status != null) spare -= font.width(status.first) + gap
        val totals = loaded?.let { MenuData.totalsText(MenuData.totals(it.result.dyes)) }
            ?.takeIf { font.width(it) + gap <= spare }

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

    /** The strip's status text and its color: "refreshing…", else "refresh failed", else the data's age. */
    private fun statusText(): Pair<String, Int> = when {
        refreshing -> REFRESHING_TEXT to UiTheme.Colors.TEXT_MUTED
        refreshFailed -> REFRESH_FAILED_TEXT to UiTheme.Colors.STATUS_ERROR
        else -> formatAge(System.currentTimeMillis(), fetchedAtMs) to UiTheme.Colors.TEXT_MUTED
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

    override fun onClose() {
        Minecraft.getInstance().showScreen(null)
    }

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(context, mouseX, mouseY, delta)
        UiDraw.fillRect(context, 0, 0, width, height, UiTheme.Colors.MENU_BACKGROUND)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(context, mouseX, mouseY, delta)
        drawHeader(context)
        val dyes = visibleDyes()
        val grid = gridGeometry(dyes.size)
        val currentStrip = strip
        // Messages go below the strip when it shows (a profile switch loading or failing), else below the header.
        val messageArea = if (currentStrip == null) {
            Rect(0, MenuLayout.HEADER_HEIGHT, width, height - MenuLayout.HEADER_HEIGHT)
        } else {
            drawStrip(context, currentStrip, grid.stripRow, mouseX, mouseY)
            Rect(0, grid.stripRow.bottom, width, height - grid.stripRow.bottom)
        }
        when (val current = state) {
            ViewState.Loading -> drawMessage(context, LOADING_TEXT, UiTheme.Colors.TEXT_SUBTLE, messageArea)
            is ViewState.NoProfiles ->
                drawMessage(context, "$NO_PROFILES_PREFIX${current.username}.", UiTheme.Colors.TEXT_SUBTLE, messageArea)
            is ViewState.Failed ->
                drawMessage(context, "$FAILED_PREFIX${current.message}", UiTheme.Colors.STATUS_ERROR, messageArea)
            is ViewState.Loaded -> drawDyes(context, dyes, current.result.profile, grid, mouseX, mouseY)
        }
        drawPinMessage(context)
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
     * while no load runs, as it is ignored meanwhile), and the totals of every loaded dye.
     */
    private fun drawStrip(context: GuiGraphicsExtractor, strip: StripState, row: Rect, mouseX: Int, mouseY: Int) {
        val textY = row.y + (row.height - font.lineHeight) / 2
        UiDraw.drawText(context, font, strip.username, row.x, textY, UiTheme.Colors.TEXT_PRIMARY)
        val layout = stripLayout(strip, row)
        val x = mouseX.toDouble()
        val y = mouseY.toDouble()
        drawPill(context, layout.profilePill, hovered = strip.canSwitch && layout.profilePill.rect.contains(x, y))
        layout.refreshPill?.let { drawPill(context, it, hovered = !fetchRunning && it.rect.contains(x, y)) }
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
     * fill and border whose border and text brighten on hover. The label is centred.
     */
    private fun drawPill(context: GuiGraphicsExtractor, pill: MenuPill, hovered: Boolean) {
        val (fill, border, text) = when {
            pill.selected ->
                Triple(UiTheme.Colors.PILL_SELECTED_BG, UiTheme.Colors.PILL_SELECTED_BG, UiTheme.Colors.PILL_SELECTED_TEXT)
            hovered -> Triple(UiTheme.Colors.CARD_BACKGROUND, UiTheme.Colors.PILL_HOVER_BORDER, UiTheme.Colors.TEXT_PRIMARY)
            else -> Triple(UiTheme.Colors.CARD_BACKGROUND, UiTheme.Colors.CARD_BORDER, UiTheme.Colors.TEXT_SUBTLE)
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
            shadow = !pill.selected, // a shadow under black text on white only smudges it
            availableWidth = rect.width,
            alignment = HorizontalAlignment.CENTER,
        )
    }

    /** Header bar: the title, then the tab bar (one active tab, underlined, for now). */
    private fun drawHeader(context: GuiGraphicsExtractor) {
        UiDraw.fillRect(context, 0, 0, width, MenuLayout.HEADER_HEIGHT, UiTheme.Colors.MENU_SURFACE)
        val textY = (MenuLayout.HEADER_HEIGHT - font.lineHeight) / 2
        UiDraw.drawText(context, font, TITLE, MenuLayout.OUTER_MARGIN, textY, UiTheme.Colors.ACCENT_PRIMARY)

        val tabX = MenuLayout.OUTER_MARGIN + font.width(TITLE) + MenuLayout.TAB_GAP
        UiDraw.drawText(context, font, DYES_TAB_LABEL, tabX, textY, UiTheme.Colors.TEXT_PRIMARY)
        UiDraw.fillRect(
            context,
            tabX,
            textY + font.lineHeight,
            font.width(DYES_TAB_LABEL),
            MenuLayout.TAB_UNDERLINE_HEIGHT,
            UiTheme.Colors.ACCENT_PRIMARY,
        )
    }

    /** One line of [text], centred in [area]. */
    private fun drawMessage(context: GuiGraphicsExtractor, text: String, argb: Int, area: Rect) {
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
     * The dye cards laid out by [grid], clipped to its viewport and scrolled by [scroll]; only
     * cards that intersect the viewport are drawn, the hovered one with its [pinHint] for
     * [profile]. Nothing is drawn when the window leaves the viewport empty (26.2 rejects a
     * zero-size scissor).
     */
    private fun drawGrid(
        context: GuiGraphicsExtractor,
        dyes: List<DyeProgressEntry>,
        profile: ProfileSummary,
        grid: GridGeometry,
        mouseX: Int,
        mouseY: Int,
    ) {
        val viewport = grid.viewport
        if (viewport.width <= 0 || viewport.height <= 0) return
        val hovered = MenuLayout.cardIndexAt(
            mouseX.toDouble(),
            mouseY.toDouble(),
            dyes.size,
            grid.columns,
            grid.left,
            grid.top,
            scroll,
            viewport,
        )
        val hint = hovered?.let { pinHint(dyes[it], profile) }
        val renderCtx = RenderContext(context, Util.getMillis(), false)
        context.enableScissor(viewport.x, viewport.y, viewport.right, viewport.bottom)
        for ((index, entry) in dyes.withIndex()) {
            val (x, y) = MenuLayout.cardOrigin(index, grid.columns, grid.left, grid.top - scroll)
            if (y >= viewport.bottom) break // cards are row-ordered, so every later card is below too
            if (y + MenuLayout.CARD_HEIGHT <= viewport.y) continue
            val isHovered = index == hovered
            drawCard(context, renderCtx, entry, x, y, hovered = isHovered, hint = if (isHovered) hint else null)
        }
        context.disableScissor()
        drawScrollbar(context, viewport, grid.contentHeight)
    }

    /** A thin track right of the grid with a thumb sized and placed by the scroll; only when content overflows. */
    private fun drawScrollbar(context: GuiGraphicsExtractor, viewport: Rect, contentHeight: Int) {
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
     * status badge top-right, and a label/value line over the progress bar. The border takes the
     * dye's accent when [hovered]. A [hint] goes at the right end of the label/value line, the
     * value moving left of it, or dropped when the label, value and hint don't all fit.
     */
    private fun drawCard(
        context: GuiGraphicsExtractor,
        renderCtx: RenderContext,
        entry: DyeProgressEntry,
        x: Int,
        y: Int,
        hovered: Boolean,
        hint: PinHint?,
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
        val category = MenuCards.fitText(MenuCards.formatCategory(entry.category), textRight - textX) {
            scaledWidth(it, MenuLayout.SMALL_TEXT_SCALE)
        }
        UiDraw.drawText(
            context,
            font,
            category,
            textX,
            nameY + font.lineHeight,
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
    private fun drawIconBox(context: GuiGraphicsExtractor, renderCtx: RenderContext, dyeId: String, x: Int, y: Int) {
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
    private fun drawBadge(context: GuiGraphicsExtractor, badge: CardBadge, right: Int, top: Int): Int {
        val pad = MenuLayout.BADGE_PADDING
        val boxWidth = scaledWidth(badge.label, MenuLayout.BADGE_SCALE) + 2 * pad
        val boxHeight = scaledLineHeight(MenuLayout.BADGE_SCALE) + 2 * pad
        val left = right - boxWidth
        val colors = badgeColors(badge)
        UiDraw.filledBox(context, left, top, boxWidth, boxHeight, colors.background, colors.border)
        UiDraw.drawText(
            context,
            font,
            badge.label,
            left + pad,
            top + pad,
            colors.text,
            scale = MenuLayout.BADGE_SCALE,
        )
        return left
    }

    private data class BadgeColors(val text: Int, val background: Int, val border: Int)

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
    private fun scaledWidth(text: String, scale: Float): Int = (font.width(text) * scale).roundToInt()

    /** Height of one text line drawn at [scale]. */
    private fun scaledLineHeight(scale: Float): Int = (font.lineHeight * scale).roundToInt()

    companion object {
        private const val TITLE = "DyeTracker"
        private const val DYES_TAB_LABEL = "Dyes"
        private const val LOADING_TEXT = "Loading your dyes…"
        private const val NO_PROFILES_PREFIX = "No SkyBlock profiles found for "
        private const val FAILED_PREFIX = "Couldn't load your dyes: "
        private const val UNEXPECTED_ERROR_PREFIX = "Unexpected error: "
        private const val EMPTY_FILTER_TEXT = "No dyes in this category."
        private const val SORT_LABEL_PREFIX = "Sort: "
        private const val PROFILE_LABEL_PREFIX = "Profile: "

        /** Ends the profile pill's label when it cycles (2+ profiles). */
        private const val PROFILE_CYCLE_SUFFIX = " ▸"

        /** [MenuPill.key] of the "All Dyes" pill; every other category pill's key is its category id. */
        private const val ALL_PILL_KEY = "all"

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
    }
}
