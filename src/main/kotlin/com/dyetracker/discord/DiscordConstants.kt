package com.dyetracker.discord

/** The DyeTracker application in the Discord Developer Portal. Public, not a secret. Blank = presence never connects. */
const val DISCORD_APPLICATION_ID = ""
/** Rich Presence art asset key of the DyeTracker logo, uploaded in the Discord app. */
const val DISCORD_LOGO_ASSET = "dyetracker"
const val DISCORD_LOGO_TEXT = "DyeTracker"
const val IPC_VERSION = 1
const val IPC_PIPE_COUNT = 10
/** Frames larger than this are treated as a broken connection. */
const val IPC_MAX_FRAME_BYTES = 64 * 1024
/** How often the presence worker reconciles the wanted card with what Discord has. */
const val PRESENCE_TICK_MS = 2_000L
/** First wait after a failed connect; doubles per failure up to [RECONNECT_MAX_MS]. */
const val RECONNECT_INITIAL_MS = 5_000L
const val RECONNECT_MAX_MS = 60_000L
/** A request Discord doesn't answer within this is treated as a dead connection. */
const val IPC_RESPONSE_TIMEOUT_MS = 5_000L
/** How long game shutdown waits for the card to be cleared. */
const val SHUTDOWN_CLEAR_TIMEOUT_MS = 1_000L
/** How often a Windows pipe read checks for waiting bytes, so it never blocks in the read itself. */
const val IPC_PIPE_POLL_MS = 10L
/** While connected and idle this long, the worker pings Discord to notice a quit/restart. */
const val IPC_HEALTH_CHECK_MS = 30_000L
