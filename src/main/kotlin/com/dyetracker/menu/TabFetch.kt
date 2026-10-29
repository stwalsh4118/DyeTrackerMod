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

/** A [TabFetch]'s last fetch, for the profile [profileId]. */
sealed class FetchState<out T> {
    abstract val profileId: String

    data class Loading(override val profileId: String) : FetchState<Nothing>()
    data class Loaded<out T>(override val profileId: String, val data: T) : FetchState<T>()
    data class Failed(override val profileId: String, val message: String) : FetchState<Nothing>()
}

/**
 * One menu tab's fetch of per-profile data (the Collection tab's collection): the blocking call
 * runs on [scope] and its result is handed to [post] (the render thread), where it is dropped
 * when this was [close]d, a newer [fetch] started meanwhile, or the profile [shownProfileId] gives
 * is no longer the one fetched; in that last case the state is cleared ([PostedResult.CLEAR]).
 * Only the latest fetch's state is kept.
 */
class TabFetch<T>(
    private val scope: CoroutineScope,
    private val post: (() -> Unit) -> Unit,
    private val shownProfileId: () -> String?,
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

    /** A fetch is running (for whichever profile). */
    val busy: Boolean get() = state is FetchState.Loading

    /**
     * Whether [profileId] needs a fetch: none is loaded or running for it (the last one was for
     * another profile, failed, or none ran).
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
     * Starts fetching [profileId]'s data with the blocking [call], cancelling a running fetch, and
     * shows Loading. Once posted, as [dispositionOf] says: the result becomes Loaded or Failed (a
     * thrown exception too) and [onApplied] runs, or the state is cleared, or it is dropped.
     */
    fun fetch(profileId: String, call: () -> ApiClient.ApiResult<T>, onApplied: () -> Unit = {}) {
        val myRequest = ++request
        job?.cancel()
        state = FetchState.Loading(profileId)
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
                    is ApiClient.ApiResult.Success -> FetchState.Loaded(profileId, result.data)
                    is ApiClient.ApiResult.Error -> FetchState.Failed(profileId, result.message)
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
