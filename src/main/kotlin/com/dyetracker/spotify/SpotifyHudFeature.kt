package com.dyetracker.spotify

import com.dyetracker.client.showScreen
import com.dyetracker.config.ConfigManager
import com.dyetracker.menu.MenuOpenRequest
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.hud.HudWidgetEntry
import com.dyetracker.ui.hud.HudWidgetRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Wires the Spotify now-playing widget into the UI toolkit (PBI 76). Registers a
 * [HudWidgetRegistry] provider that, each frame, reads [SpotifyNowPlayingStore] (filled by
 * [SpotifyPoller]) and builds the widget with [SpotifyWidgetView]. While there is no snapshot
 * the widget isn't drawn at all.
 *
 * The single [SpotifyWidgetConfig] placement is supplied with [SpotifyPlacementEditor], so the
 * widget gets drag-to-move, scroll-to-scale, visibility toggle, F1-hide, bounce and persisted
 * placement from the toolkit.
 */
object SpotifyHudFeature {

    private val editRequest = MenuOpenRequest()

    /**
     * Seed the placement (so edit mode has a target and the poller reads a visibility) and
     * register the HUD provider and the tick listener that opens a requested edit screen. Call
     * once at client init.
     */
    fun register() {
        if (ConfigManager.spotifyPlacements.all().isEmpty()) {
            ConfigManager.spotifyPlacements.add(SpotifyWidgetConfig())
        }
        HudWidgetRegistry.register {
            val snapshot = SpotifyNowPlayingStore.snapshot
            if (snapshot == null) {
                emptyList()
            } else {
                val placement = ConfigManager.spotifyPlacements.all().firstOrNull() ?: SpotifyWidgetConfig()
                val widget = SpotifyWidgetView.build(snapshot, System.currentTimeMillis())
                listOf(HudWidgetEntry(placement, widget, SpotifyPlacementEditor))
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (editRequest.consume()) client.showScreen(WidgetEditScreen())
        }
    }

    /**
     * Open the HUD edit screen on the next client tick. `/dyetracker spotify edit` can't open it
     * itself: the chat screen that ran the command closes itself right after, closing the edit
     * screen with it (the same reason `/dyetracker menu` opens on the next tick).
     */
    fun requestEditScreen() {
        editRequest.request()
    }
}
