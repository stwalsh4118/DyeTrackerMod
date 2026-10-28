package com.dyetracker.menu

import com.dyetracker.config.ModConfig

/** Whether the one-time chat hint about the menu still has to be printed for [config]. */
fun shouldShowMenuHint(config: ModConfig): Boolean = !config.menuHintShown
