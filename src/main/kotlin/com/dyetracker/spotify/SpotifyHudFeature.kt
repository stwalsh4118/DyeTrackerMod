package com.dyetracker.spotify

import com.dyetracker.client.currentScreen
import com.dyetracker.client.showScreen
import com.dyetracker.config.ConfigManager
import com.dyetracker.menu.MenuOpenRequest
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.hud.HudWidgetEntry
import com.dyetracker.ui.hud.HudWidgetRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * Wires the Spotify now-playing widget into the UI toolkit (PBI 76). Registers a
 * [HudWidgetRegistry] provider that, each frame, picks the [widgetState] from the session status
 * and [SpotifyNowPlayingStore]'s record (filled by [SpotifyPoller]) and builds the widget with
 * [SpotifyWidgetView]. A hidden state draws nothing, except in the HUD edit screen, where it is a
 * placeholder so the widget can still be placed. The provider only reads volatile state: it runs
 * on the render thread every frame.
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
            val placement = ConfigManager.spotifyPlacements.all().firstOrNull() ?: SpotifyWidgetConfig()
            val nowMs = System.currentTimeMillis()
            val editing = Minecraft.getInstance().currentScreen() is WidgetEditScreen
            // A hidden placement isn't polled, so its record is stale: it shows only as the edit
            // placeholder (dimmed and struck through by the edit screen), so E can show it again.
            val state = if (placement.visible) {
                widgetState(SpotifySessions.default.status(), SpotifyNowPlayingStore.record, nowMs)
            } else {
                SpotifyWidgetState.Hidden
            }
            val widget = SpotifyWidgetView.build(state, nowMs, editing)
            if (widget == null) emptyList() else listOf(HudWidgetEntry(placement, widget, SpotifyPlacementEditor))
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (editRequest.consume()) client.showScreen(WidgetEditScreen())
        }
    }

    /**
     * Open the HUD edit screen on the next client tick. `/dyetracker spotify edit` can't open it
     * itself: the chat screen that ran the command closes itself right after, closing the edit
     * screen with it (the same reason `/dyetracker` opens on the next tick).
     */
    fun requestEditScreen() {
        editRequest.request()
    }
}
