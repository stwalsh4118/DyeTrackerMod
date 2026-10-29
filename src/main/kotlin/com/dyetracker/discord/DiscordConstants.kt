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
