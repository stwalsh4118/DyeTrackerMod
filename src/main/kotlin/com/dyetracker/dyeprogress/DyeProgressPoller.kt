package com.dyetracker.dyeprogress

import com.dyetracker.DyeTrackerMod
import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressResponse
import com.dyetracker.auth.PlayerIdentity
import com.dyetracker.config.ConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background scheduler that keeps [DyeProgressStore] current. Every [POLL_INTERVAL_MS] it
 * fetches dye progress for the player's account, one request per distinct `profileId` across
 * all configured widgets (so N widgets sharing a profile cost a single fetch).
 *
 * The widget always represents the player's own account ([PlayerIdentity.hudUsername]: the
 * linked account, else the in-game name, so unlinked players' widgets fill in too); only the
 * profile varies. Polling no-ops when there is no username or when there are zero configured
 * widgets. Failures are logged and never propagate into gameplay.
 */
object DyeProgressPoller {

    /** Poll cadence in ms. Matches the website overlay's `OVERLAY_POLL_INTERVAL_MS` (30s). */
    const val POLL_INTERVAL_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var job: Job? = null

    /** Start the periodic poll loop. Idempotent — a second call while running is a no-op. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                safeTick("scheduled")
                delay(POLL_INTERVAL_MS)
            }
        }
        DyeTrackerMod.info("Dye progress poller started ({}ms interval)", POLL_INTERVAL_MS)
    }

    /** Cancel the poll loop (e.g. on client stop). */
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Fetch immediately, off the next tick — used by the add panel (34-6) / commands (34-7) so
     * progress appears right after a widget is added instead of waiting up to [POLL_INTERVAL_MS].
     */
    fun pollOnce() {
        scope.launch { safeTick("manual") }
    }

    private suspend fun safeTick(reason: String) {
        try {
            tick()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            DyeTrackerMod.warn("Dye progress {} poll failed: {}", reason, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun tick() {
        pollProfiles(
            username = PlayerIdentity.hudUsername(),
            profileIds = ConfigManager.dyeProgressPlacements.all().map { it.profileId },
            fetch = ApiClient::fetchDyeProgress,
            update = DyeProgressStore::update,
        )
    }

    /**
     * Pure, testable poll core: fetch each DISTINCT non-blank profile once and write the result.
     * No-ops (returns an empty set) when [username] is blank or when there are no non-blank
     * profiles. Returns the set of profiles actually fetched (for tests / diagnostics).
     */
    internal fun pollProfiles(
        username: String,
        profileIds: List<String>,
        fetch: (String, String) -> ApiClient.ApiResult<DyeProgressResponse>,
        update: (String, ApiClient.ApiResult<DyeProgressResponse>) -> Unit,
    ): Set<String> {
        if (username.isBlank()) return emptySet()
        val distinct = profileIds.filterTo(LinkedHashSet()) { it.isNotBlank() }
        if (distinct.isEmpty()) return emptySet()
        for (profileId in distinct) {
            update(profileId, fetch(username, profileId))
        }
        return distinct
    }
}
