package com.dyetracker.discord

import com.dyetracker.config.ModConfig

/** The one-time chat hint printed the first time Discord presence is turned on, after the `[DyeTracker]` prefix. */
const val DISCORD_HINT_TEXT = " Discord presence is on. It shows while you're on SkyBlock, in the Discord desktop app with " +
    "\"Share your detected activities\" turned on. On Lunar Client, turn off Lunar's own Discord status first: " +
    "launcher gear icon > General > Discord Rich Presence."

/** Whether the one-time Discord setup hint still has to be printed for [config]. */
fun shouldShowDiscordHint(config: ModConfig): Boolean = !config.discordHintShown
