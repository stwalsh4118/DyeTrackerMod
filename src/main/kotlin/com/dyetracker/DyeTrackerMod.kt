package com.dyetracker

import com.dyetracker.config.ConfigManager
import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DyeTrackerMod : ModInitializer {
    companion object {
        const val MOD_ID = "dyetracker"
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

        /**
         * Log a debug message.
         */
        fun debug(message: String) {
            LOGGER.debug(message)
        }

        /**
         * Log a debug message with formatting.
         */
        fun debug(format: String, vararg args: Any?) {
            LOGGER.debug(format, *args)
        }

        /**
         * Log an info message.
         */
        fun info(message: String) {
            LOGGER.info(message)
        }

        /**
         * Log an info message with formatting.
         */
        fun info(format: String, vararg args: Any?) {
            LOGGER.info(format, *args)
        }

        /**
         * Log a warning message.
         */
        fun warn(message: String) {
            LOGGER.warn(message)
        }

        /**
         * Log a warning message with formatting.
         */
        fun warn(format: String, vararg args: Any?) {
            LOGGER.warn(format, *args)
        }

        /**
         * Log an error message.
         */
        fun error(message: String) {
            LOGGER.error(message)
        }

        /**
         * Log an error message with formatting.
         */
        fun error(format: String, vararg args: Any?) {
            LOGGER.error(format, *args)
        }

        /**
         * Log an error message with an exception.
         */
        fun error(message: String, throwable: Throwable) {
            LOGGER.error(message, throwable)
        }
    }

    override fun onInitialize() {
        info("DyeTracker initializing...")

        // Load configuration
        ConfigManager.load()
        info("Configuration loaded from {}", ConfigManager.configPath)

        info("DyeTracker initialized successfully")
    }
}
