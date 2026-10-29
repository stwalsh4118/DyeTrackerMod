package com.dyetracker.spotify

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The one thread every blocking Spotify Web API call runs on (PBI 76): `/dyetracker spotify now`
 * and the HUD poller. One thread means calls never overlap, so they never race on a token
 * refresh, and never touch the render thread. Logins have their own thread (they block on the
 * browser for minutes).
 */
object SpotifyThreads {

    private const val API_THREAD_NAME = "DyeTracker-SpotifyApi"

    /** The single daemon Spotify API thread. */
    val api: ExecutorService = daemonWorker(API_THREAD_NAME)

    /** [api] as a coroutine dispatcher, for the poller. */
    val apiDispatcher: CoroutineDispatcher = api.asCoroutineDispatcher()

    /** A single-thread executor on a daemon thread named [name], so it never keeps the game alive. */
    fun daemonWorker(name: String): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }
}
