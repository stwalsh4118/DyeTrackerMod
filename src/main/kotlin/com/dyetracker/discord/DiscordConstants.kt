package com.dyetracker.discord

/** The DyeTracker application in the Discord Developer Portal. Public, not a secret. Blank = presence never connects. */
const val DISCORD_APPLICATION_ID = "1565497379032141898"
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
/** Hypixel's server domain; any address ending in it counts (mc.hypixel.net, hypixel.net:25565). */
const val HYPIXEL_DOMAIN = "hypixel.net"
/** Hypixel's SkyBlock sidebar title contains this once formatting is stripped. */
const val SKYBLOCK_SIDEBAR_MARKER = "SKYBLOCK"
/** How often (client ticks) the SkyBlock check runs. */
const val SKYBLOCK_POLL_TICKS = 20
/** Off SkyBlock for less than this keeps the card (lobby hops, server swaps). */
const val SKYBLOCK_LEAVE_DEBOUNCE_MS = 10_000L
/** The public website; the card's button links to the player's page on it. */
const val DYETRACKER_SITE_URL = "https://dyetracker.xyz"
const val PROFILE_BUTTON_LABEL = "View on DyeTracker"
/** How often the card's stats are re-fetched while it is shown. */
const val STATS_REFRESH_MS = 30_000L
/** Discord rate-limits SET_ACTIVITY (commonly cited as one update per 15 s); never send faster. */
const val MIN_UPDATE_INTERVAL_MS = 15_000L
/** Where the website serves dye art; Discord fetches it through its media proxy. */
const val DYE_IMAGE_PATH = "/dyes/"
const val DYE_IMAGE_EXTENSION = ".webp"
const val HUNT_PREFIX = "Hunting "
/** Odds at or above this read as complete. */
const val HUNT_COMPLETE_PERCENT = 100.0
