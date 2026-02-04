package com.dyetracker

import com.dyetracker.commands.DyeTrackerCommands
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.RngDataStore
import com.dyetracker.events.ChatEventHandler
import com.dyetracker.events.InventoryHandler
import com.dyetracker.events.TablistHandler
import com.dyetracker.sync.SyncManager
import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DyeTrackerMod : ModInitializer {
    companion object {
        const val MOD_ID = "dyetracker"
        private const val LOG_PREFIX = "[DyeTracker] "
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

        /**
         * Log a debug message.
         */
        fun debug(message: String) {
            LOGGER.debug("$LOG_PREFIX$message")
        }

        /**
         * Log a debug message with formatting.
         */
        fun debug(format: String, vararg args: Any?) {
            LOGGER.debug("$LOG_PREFIX$format", *args)
        }

        /**
         * Log an info message.
         */
        fun info(message: String) {
            LOGGER.info("$LOG_PREFIX$message")
        }

        /**
         * Log an info message with formatting.
         */
        fun info(format: String, vararg args: Any?) {
            LOGGER.info("$LOG_PREFIX$format", *args)
        }

        /**
         * Log a warning message.
         */
        fun warn(message: String) {
            LOGGER.warn("$LOG_PREFIX$message")
        }

        /**
         * Log a warning message with formatting.
         */
        fun warn(format: String, vararg args: Any?) {
            LOGGER.warn("$LOG_PREFIX$format", *args)
        }

        /**
         * Log an error message.
         */
        fun error(message: String) {
            LOGGER.error("$LOG_PREFIX$message")
        }

        /**
         * Log an error message with formatting.
         */
        fun error(format: String, vararg args: Any?) {
            LOGGER.error("$LOG_PREFIX$format", *args)
        }

        /**
         * Log an error message with an exception.
         */
        fun error(message: String, throwable: Throwable) {
            LOGGER.error("$LOG_PREFIX$message", throwable)
        }
    }

    override fun onInitialize() {
        info("DyeTracker initializing...")

        // Load configuration
        ConfigManager.load()
        info("Configuration loaded from {}", ConfigManager.configPath)

        // Initialize RNG data store (loads persisted data)
        RngDataStore.init()
        info("RNG data store initialized")

        // Register sync manager as listener (after persistence listener is registered in init)
        RngDataStore.addListener(SyncManager)
        info("Sync manager registered")

        // Register commands
        DyeTrackerCommands.register()
        info("Commands registered")

        // Register RNG data capture handlers
        ChatEventHandler.register()
        InventoryHandler.register()
        TablistHandler.register()
        info("RNG data handlers registered")

        info("DyeTracker initialized successfully")
    }
}
