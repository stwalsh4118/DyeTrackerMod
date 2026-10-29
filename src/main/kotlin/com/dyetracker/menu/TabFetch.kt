package com.dyetracker.menu

import com.dyetracker.api.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Before a thrown exception's text in a menu load's or fetch's failure message. */
private const val UNEXPECTED_ERROR_PREFIX = "Unexpected error: "

/** A failure message for [e] thrown by a menu load or fetch: its message, else its class name. */
internal fun unexpectedErrorMessage(e: Throwable): String =
    "$UNEXPECTED_ERROR_PREFIX${e.message ?: e.javaClass.simpleName}"

/** What [TabFetch] does with a posted result ([TabFetch.dispositionOf]). */
enum class PostedResult {
    /** The newest fetch, for the profile shown, while open: it becomes Loaded or Failed. */
    APPLY,

    /**
     * The newest fetch, but the strip moved to another profile meanwhile: dropped, and the state
     * cleared so that profile is fetched again when next needed instead of staying Loading.
     */
    CLEAR,

    /** Closed, or a newer fetch started: dropped, the state left to the newer fetch. */
    DROP,
}

/** Whether a [FetchState.Loaded] is being fetched again in the background, and how that went. */
enum class RefreshStatus {
    /** Not being fetched again; the data is the last fetch's. */
    IDLE,

    /** A background fetch runs while the cached data shows ("refreshing…"). */
    REFRESHING,

    /** The last background fetch failed; the cached data still shows ("refresh failed"). */
    FAILED,
}

/** A [TabFetch]'s last fetch, for the profile [profileId]. */
sealed class FetchState<out T> {
    abstract val profileId: String

    /** A fetch runs with nothing cached to show. */
    data class Loading(override val profileId: String) : FetchState<Nothing>()

    /** [data] fetched at [fetchedAtMs] (epoch ms), and whether it is being fetched again ([refresh]). */
    data class Loaded<out T>(
        override val profileId: String,
        val data: T,
        val fetchedAtMs: Long,
        val refresh: RefreshStatus = RefreshStatus.IDLE,
    ) : FetchState<T>()

    /** The fetch failed with nothing cached to show. */
    data class Failed(override val profileId: String, val message: String) : FetchState<Nothing>()
}

/**
 * One menu tab's fetch of per-profile data (the Collection tab's collection): the blocking call
 * runs on [scope] and its result is handed to [post] (the render thread), where it is dropped
 * when this was [close]d, a newer [fetch] started meanwhile, or the profile [shownProfileId] gives
 * is no longer the one fetched; in that last case the state is cleared ([PostedResult.CLEAR]).
 * Only the latest fetch's state is kept.
 *
 * A fetch whose player and profile are in [cache] shows the cached data at once and fetches in the
 * background; an applied success is written to [cache], stamped with [now] (epoch ms).
 */
class TabFetch<T>(
    private val scope: CoroutineScope,
    private val post: (() -> Unit) -> Unit,
    private val shownProfileId: () -> String?,
    private val cache: SessionCache<T>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The last fetch's state; `null` until the first [fetch]. */
    var state: FetchState<T>? = null
        private set

    /** Bumped per [fetch]; a posted result whose request is stale is dropped. */
    private var request = 0

    /** The running fetch; cancelled when another starts. */
    private var job: Job? = null

    /** Set by [close]; a result posted afterwards is dropped. */
    private var closed = false

    /** A fetch is running (for whichever profile), behind Loading or in the background. */
    val busy: Boolean
        get() = when (val current = state) {
            is FetchState.Loading -> true
            is FetchState.Loaded -> current.refresh == RefreshStatus.REFRESHING
            null, is FetchState.Failed -> false
        }

    /**
     * Whether [profileId] needs a fetch: none is loaded or running for it (the last one was for
     * another profile, failed with nothing cached, or none ran). Cached data whose background fetch
     * failed counts as loaded.
     */
    fun needsFetch(profileId: String): Boolean = when (stateFor(profileId)) {
        null, is FetchState.Failed -> true
        is FetchState.Loading, is FetchState.Loaded -> false
    }

    /** The last fetch's state when it was for [profileId], else `null`. */
    fun stateFor(profileId: String): FetchState<T>? = state?.takeIf { it.profileId == profileId }

    /** The data loaded for [profileId], else `null` (loading, failed, or another profile's). */
    fun loadedFor(profileId: String): T? = (stateFor(profileId) as? FetchState.Loaded)?.data

    /**
     * Starts fetching [username]'s profile [profileId] with the blocking [call], cancelling a
     * running fetch. Its [cache] entry shows at once, refreshing in the background; without one it
     * shows Loading. Once posted, as [dispositionOf] says: the result is applied and [onApplied]
     * runs, or the state is cleared, or it is dropped. Applied, a success becomes Loaded and goes
     * into [cache]; an error (a thrown exception too) keeps the cached data with the refresh
     * [failed][RefreshStatus.FAILED], or without one becomes Failed.
     */
    fun fetch(username: String, profileId: String, call: () -> ApiClient.ApiResult<T>, onApplied: () -> Unit = {}) {
        val myRequest = ++request
        job?.cancel()
        val cached = cache.get(username, profileId)
        state = cached?.let { FetchState.Loaded(profileId, it.value, it.fetchedAtMs, RefreshStatus.REFRESHING) }
            ?: FetchState.Loading(profileId)
        job = scope.launch {
            val result = try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ApiClient.ApiResult.Error(unexpectedErrorMessage(e))
            }
            post {
                when (dispositionOf(myRequest, profileId)) {
                    PostedResult.DROP -> return@post
                    PostedResult.CLEAR -> {
                        state = null
                        return@post
                    }
                    PostedResult.APPLY -> Unit
                }
                state = when (result) {
                    is ApiClient.ApiResult.Success -> {
                        val fetchedAtMs = now()
                        cache.put(username, profileId, result.data, fetchedAtMs)
                        FetchState.Loaded(profileId, result.data, fetchedAtMs)
                    }
                    is ApiClient.ApiResult.Error ->
                        cached?.let { FetchState.Loaded(profileId, it.value, it.fetchedAtMs, RefreshStatus.FAILED) }
                            ?: FetchState.Failed(profileId, result.message)
                }
                onApplied()
            }
        }
    }

    /**
     * What happens to a posted result of [request] for [profileId]: dropped when [close]d or a
     * newer fetch started; else cleared when the profile shown is no longer [profileId]; else applied.
     */
    internal fun dispositionOf(request: Int, profileId: String): PostedResult = when {
        closed || request != this.request -> PostedResult.DROP
        shownProfileId() != profileId -> PostedResult.CLEAR
        else -> PostedResult.APPLY
    }

    /** Drops every result posted from now on and cancels the running fetch (the screen closed). */
    fun close() {
        closed = true
        job?.cancel()
    }
}
