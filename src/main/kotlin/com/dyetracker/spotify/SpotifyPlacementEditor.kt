package com.dyetracker.spotify

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.core.PlacementEditor

/**
 * Write side of the Spotify now-playing widget's placement for the generalized edit screen.
 * Delegates to [ConfigManager.spotifyPlacements]. The Spotify feature seeds a placement on
 * startup, so the element always exists for transient/visibility updates.
 *
 * The widget is a singleton the user can't delete: [remove] hides it instead, so "Delete" in the
 * edit screen reads as "hide".
 */
object SpotifyPlacementEditor : PlacementEditor {

    override fun setPlacementTransient(id: String, x: Float, y: Float, scale: Float) {
        ConfigManager.spotifyPlacements.updateTransient(id) { it.copy(x = x, y = y, scale = scale) }
    }

    override fun flush() {
        ConfigManager.spotifyPlacements.flush()
    }

    override fun setVisible(id: String, visible: Boolean) {
        ConfigManager.spotifyPlacements.update(id) { it.copy(visible = visible) }
    }

    override fun remove(id: String) {
        // Singleton widget is not user-deletable; hide it instead of removing the placement.
        setVisible(id, false)
    }
}
