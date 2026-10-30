package com.dyetracker.commands

import com.dyetracker.DyeTrackerMod
import com.dyetracker.auth.AccountVerification
import com.dyetracker.client.showScreen
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.PotionTier
import com.dyetracker.data.RngDataStore
import com.dyetracker.discord.DiscordPresence
import com.dyetracker.discord.disableDiscordPresence
import com.dyetracker.discord.enableDiscordPresence
import com.dyetracker.dyeprogress.DyeCatalog
import com.dyetracker.dyeprogress.DyeProgressAdder
import com.dyetracker.dyeprogress.DyeProgressPlacementEditor
import com.dyetracker.menu.MenuKeybind
import com.dyetracker.overlay.OverlayAddPipeline
import com.dyetracker.rotation.DyeSprites
import com.dyetracker.rotation.RotationPlacementEditor
import com.dyetracker.rotation.RotationWidgetConfig
import com.dyetracker.spotify.SpotifyActions
import com.dyetracker.spotify.SpotifyConstants
import com.dyetracker.spotify.SpotifyHelpRenderer
import com.dyetracker.spotify.SpotifyHudFeature
import com.dyetracker.spotify.tintAllText
import com.dyetracker.sync.SyncManager
import com.dyetracker.ui.bounce.BounceController
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.texture.ImageTextureManager
import com.dyetracker.ui.theme.DyeThemeColors
import com.dyetracker.ui.theme.ThemePresets
import com.dyetracker.ui.theme.selectedThemeKey
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Registers client-side commands for the DyeTracker mod.
 */
object DyeTrackerCommands {

    private const val GIF_URL_DISPLAY_MAX = 60
    private const val GIF_URL_TRUNCATE_AT = 57
    /** The `/dyetracker discord hunt` usage, shown in its status and in the help. */
    private const val DISCORD_HUNT_USAGE = "/dyetracker discord hunt <dye|clear>"
    /** The `/dyetracker theme` usage, shown in the help. */
    private const val THEME_USAGE = "/dyetracker theme <name>"
    /** The `/dyetracker theme dye` usage, shown when the dye is missing. */
    private const val THEME_DYE_USAGE = "/dyetracker theme dye <dye>"
    /** Label before the theme name in the theme status and confirmation. */
    private const val THEME_LABEL = "Theme: "
    private const val SPOTIFY_USAGE =
        "/dyetracker spotify help | clientid <id> | login | logout | now | toggle | edit | tint on|off|toggle"

    /** The subcommands registered under `/dyetracker spotify`, in [SPOTIFY_USAGE] order. */
    internal val SPOTIFY_SUBCOMMANDS = listOf("help", "clientid", "login", "logout", "now", "toggle", "edit", "tint")

    private val gifCommandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** IO scope for the async dye-widget add command (profile resolution). */
    private val dyeCommandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Register all commands via the ClientCommandRegistrationCallback.
     */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            registerCommands(dispatcher)
        }
    }

    private fun registerCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommands.literal("dyetracker")
                .then(
                    ClientCommands.literal("link")
                        .then(
                            ClientCommands.argument("code", StringArgumentType.word())
                                .executes { context ->
                                    val code = StringArgumentType.getString(context, "code")
                                    handleLinkCommand(context.source, code)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Component.literal("Usage: /dyetracker link <code>")
                                    .withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommands.literal("status")
                        .executes { context ->
                            handleStatusCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("unlink")
                        .executes { context ->
                            handleUnlinkCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("show")
                        .executes { context ->
                            handleShowCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("sync")
                        .executes { context ->
                            handleSyncCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("reload")
                        .executes { context ->
                            handleReloadCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("gif")
                        .then(
                            ClientCommands.literal("add")
                                .then(
                                    ClientCommands.argument("url", StringArgumentType.greedyString())
                                        .executes { context ->
                                            val url = StringArgumentType.getString(context, "url")
                                            handleGifAddCommand(context.source, url)
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: /dyetracker gif add <url>")
                                            .withStyle(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("list")
                                .executes { context ->
                                    handleGifListCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("remove")
                                .then(
                                    ClientCommands.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            val id = StringArgumentType.getString(context, "id")
                                            handleGifRemoveCommand(context.source, id)
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: /dyetracker gif remove <id>")
                                            .withStyle(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("edit")
                                .executes { context ->
                                    handleGifEditCommand(context.source)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Component.literal("Usage: /dyetracker gif <add|list|remove|edit>")
                                    .withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommands.literal("rotation")
                        .then(
                            ClientCommands.literal("toggle")
                                .executes { context ->
                                    handleRotationToggleCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("edit")
                                .executes { context ->
                                    handleRotationEditCommand(context.source)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Component.literal("Usage: /dyetracker rotation <toggle|edit>")
                                    .withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommands.literal("dye")
                        .then(
                            ClientCommands.literal("list")
                                .executes { context ->
                                    handleDyeListCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("remove")
                                .then(
                                    ClientCommands.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            handleDyeRemoveCommand(context.source, StringArgumentType.getString(context, "id"))
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: /dyetracker dye remove <id>")
                                            .withStyle(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("toggle")
                                .then(
                                    ClientCommands.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            handleDyeToggleCommand(context.source, StringArgumentType.getString(context, "id"))
                                            1
                                        }
                                )
                                .executes { context ->
                                    // No id → toggle every dye widget at once.
                                    handleDyeToggleCommand(context.source, null)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("edit")
                                .executes { context ->
                                    handleDyeEditCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("add")
                                .then(
                                    ClientCommands.argument("dyeId", StringArgumentType.word())
                                        .then(
                                            ClientCommands.argument("profile", StringArgumentType.greedyString())
                                                .executes { context ->
                                                    handleDyeAddCommand(
                                                        context.source,
                                                        StringArgumentType.getString(context, "dyeId"),
                                                        StringArgumentType.getString(context, "profile"),
                                                    )
                                                    1
                                                }
                                        )
                                        .executes { context ->
                                            context.source.sendFeedback(
                                                Component.literal("Usage: /dyetracker dye add <dyeId> <profile>")
                                                    .withStyle(ChatFormatting.YELLOW)
                                            )
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: /dyetracker dye add <dyeId> <profile>")
                                            .withStyle(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Component.literal("Usage: /dyetracker dye <list|remove|edit|toggle|add>")
                                    .withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommands.literal("bounce")
                        .then(
                            ClientCommands.literal("on")
                                .executes { context ->
                                    handleBounceCommand(context.source, true)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("off")
                                .executes { context ->
                                    handleBounceCommand(context.source, false)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("toggle")
                                .executes { context ->
                                    handleBounceCommand(context.source, null)
                                    1
                                }
                        )
                        // Bare `/dyetracker bounce` toggles.
                        .executes { context ->
                            handleBounceCommand(context.source, null)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("discord")
                        .then(
                            ClientCommands.literal("on")
                                .executes { context ->
                                    handleDiscordCommand(context.source, true)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("off")
                                .executes { context ->
                                    handleDiscordCommand(context.source, false)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("hunt")
                                // Before the argument, so `clear` is never read as a dye id.
                                .then(
                                    ClientCommands.literal("clear")
                                        .executes { context ->
                                            handleDiscordHuntClear(context.source)
                                            1
                                        }
                                )
                                .then(
                                    ClientCommands.argument("dye", StringArgumentType.word())
                                        .suggests { _, builder ->
                                            SharedSuggestionProvider.suggest(DyeCatalog.all.map { it.dyeId }, builder)
                                        }
                                        .executes { context ->
                                            handleDiscordHuntSet(context.source, StringArgumentType.getString(context, "dye"))
                                            1
                                        }
                                )
                                // Bare `/dyetracker discord hunt` prints the pick and the usage.
                                .executes { context ->
                                    handleDiscordHuntStatus(context.source)
                                    1
                                }
                        )
                        // Bare `/dyetracker discord` prints the status.
                        .executes { context ->
                            handleDiscordCommand(context.source, null)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("theme")
                        // Literals before the argument: Brigadier tries literals first whatever the order,
                        // so this only keeps the tree readable.
                        .then(
                            ClientCommands.literal("dye")
                                .then(
                                    ClientCommands.argument("dye", StringArgumentType.word())
                                        .suggests { _, builder ->
                                            SharedSuggestionProvider.suggest(DyeThemeColors.COLORS.keys.sorted(), builder)
                                        }
                                        .executes { context ->
                                            handleThemeDyeSet(context.source, StringArgumentType.getString(context, "dye"))
                                            1
                                        }
                                )
                                // Bare `/dyetracker theme dye` prints the usage.
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: $THEME_DYE_USAGE").withStyle(ChatFormatting.RED)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal(ThemePresets.CUSTOM)
                                .executes { context ->
                                    handleThemeCustom(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.argument("preset", StringArgumentType.word())
                                .suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(ThemePresets.PRESET_KEYS, builder)
                                }
                                .executes { context ->
                                    handleThemeSet(context.source, StringArgumentType.getString(context, "preset"))
                                    1
                                }
                        )
                        // Bare `/dyetracker theme` prints the active theme.
                        .executes { context ->
                            handleThemeStatus(context.source)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("menu")
                        .executes { context ->
                            handleMenuCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommands.literal("spotify")
                        .then(
                            ClientCommands.literal("help")
                                .executes { context ->
                                    handleSpotifyHelpCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("clientid")
                                .then(
                                    ClientCommands.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            handleSpotifyClientIdCommand(
                                                context.source,
                                                StringArgumentType.getString(context, "id"),
                                            )
                                            1
                                        }
                                )
                                .executes { context ->
                                    sendSpotifyUsage(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("login")
                                .executes { context ->
                                    handleSpotifyLoginCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("logout")
                                .executes { context ->
                                    handleSpotifyLogoutCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("now")
                                .executes { context ->
                                    handleSpotifyNowCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("toggle")
                                .executes { context ->
                                    handleSpotifyToggleCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("edit")
                                .executes { context ->
                                    handleSpotifyEditCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommands.literal("tint")
                                .then(
                                    ClientCommands.literal("on")
                                        .executes { context ->
                                            handleSpotifyTintCommand(context.source, true)
                                            1
                                        }
                                )
                                .then(
                                    ClientCommands.literal("off")
                                        .executes { context ->
                                            handleSpotifyTintCommand(context.source, false)
                                            1
                                        }
                                )
                                .then(
                                    ClientCommands.literal("toggle")
                                        .executes { context ->
                                            handleSpotifyTintCommand(context.source, null)
                                            1
                                        }
                                )
                                // Bare `/dyetracker spotify tint` toggles.
                                .executes { context ->
                                    handleSpotifyTintCommand(context.source, null)
                                    1
                                }
                        )
                        .executes { context ->
                            handleSpotifyStatusCommand(context.source)
                            1
                        }
                )
                .executes { context ->
                    showHelp(context.source)
                    1
                }
        )
    }

    private fun handleGifAddCommand(source: FabricClientCommandSource, rawUrl: String) {
        if (rawUrl.trim().isEmpty()) {
            source.sendFeedback(
                Component.literal("Invalid URL.")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }

        source.sendFeedback(
            Component.literal("Downloading…")
                .withStyle(ChatFormatting.YELLOW)
        )

        gifCommandScope.launch {
            // The pipeline handles download → decode → upload → persist (gifPlacements.add)
            // and surfaces a single human-readable failure string. We translate it to chat.
            val outcome = OverlayAddPipeline.addFromUrl(rawUrl)
            postOnClient {
                when (outcome) {
                    is OverlayAddPipeline.Outcome.Success -> source.sendFeedback(
                        Component.literal(
                            "Added overlay '${outcome.id}' (${outcome.decoded.frames.size} frames, " +
                                "${outcome.decoded.totalDurationMs}ms loop)"
                        ).withStyle(ChatFormatting.GREEN)
                    )
                    is OverlayAddPipeline.Outcome.Failure -> source.sendFeedback(
                        Component.literal(outcome.message).withStyle(ChatFormatting.RED)
                    )
                }
            }
        }
    }

    private fun handleGifListCommand(source: FabricClientCommandSource) {
        val gifs = ConfigManager.gifPlacements.all()
        if (gifs.isEmpty()) {
            source.sendFeedback(
                Component.literal("No overlays configured. Add one with /dyetracker gif add <url>")
                    .withStyle(ChatFormatting.YELLOW)
            )
            return
        }
        for (gif in gifs) {
            val truncatedUrl = if (gif.url.length > GIF_URL_DISPLAY_MAX) {
                gif.url.take(GIF_URL_TRUNCATE_AT) + "..."
            } else {
                gif.url
            }
            val visibility = if (gif.visible) "visible" else "hidden"
            source.sendFeedback(
                Component.literal(
                    "${gif.id}: $truncatedUrl @(${gif.x}, ${gif.y}) scale ${gif.scale} [$visibility]"
                ).withStyle(ChatFormatting.GRAY)
            )
        }
    }

    private fun handleGifRemoveCommand(source: FabricClientCommandSource, id: String) {
        if (!ConfigManager.gifPlacements.remove(id)) {
            source.sendFeedback(
                Component.literal("No overlay with id '$id'.")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }
        ImageTextureManager.release(id)
        source.sendFeedback(
            Component.literal("Removed overlay '$id'.")
                .withStyle(ChatFormatting.GREEN)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleGifEditCommand(source: FabricClientCommandSource) {
        // Brigadier client commands already execute on the render thread, so no marshal
        // needed. The screen IS the feedback; no chat output here.
        Minecraft.getInstance().showScreen(WidgetEditScreen())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleMenuCommand(source: FabricClientCommandSource) {
        // Opening it here would be undone at once: the chat screen closes itself after the
        // command runs. MenuKeybind opens it on the next tick instead. The screen is the feedback.
        MenuKeybind.requestOpen()
    }

    private fun sendSpotifyUsage(source: FabricClientCommandSource) {
        source.sendFeedback(
            Component.literal("Usage: $SPOTIFY_USAGE")
                .withStyle(ChatFormatting.YELLOW)
        )
    }

    /** Bare `/dyetracker spotify`: the status line, then the usage line. */
    private fun handleSpotifyStatusCommand(source: FabricClientCommandSource) {
        source.sendFeedback(Component.literal(SpotifyActions.status()).withStyle(ChatFormatting.WHITE))
        sendSpotifyUsage(source)
    }

    /** `/dyetracker spotify help`: the setup steps, one chat line each, with clickable links. */
    private fun handleSpotifyHelpCommand(source: FabricClientCommandSource) {
        SpotifyHelpRenderer.lines().forEach { source.sendFeedback(it) }
    }

    private fun handleSpotifyClientIdCommand(source: FabricClientCommandSource, id: String) {
        when (SpotifyActions.setClientId(id)) {
            SpotifyActions.SetClientIdResult.Saved -> source.sendFeedback(
                Component.literal("Spotify Client ID saved. Now run /dyetracker spotify login")
                    .withStyle(ChatFormatting.GREEN)
            )
            SpotifyActions.SetClientIdResult.Invalid -> source.sendFeedback(
                Component.literal(
                    "That isn't a Spotify Client ID (32 hex characters). See ${SpotifyConstants.HELP_COMMAND}",
                )
                    .withStyle(ChatFormatting.RED)
            )
        }
    }

    private fun handleSpotifyLoginCommand(source: FabricClientCommandSource) {
        SpotifyActions.login { source.sendFeedback(it) }
    }

    private fun handleSpotifyLogoutCommand(source: FabricClientCommandSource) {
        SpotifyActions.logout { source.sendFeedback(it) }
    }

    private fun handleSpotifyNowCommand(source: FabricClientCommandSource) {
        SpotifyActions.now { source.sendFeedback(it) }
    }

    private fun handleSpotifyToggleCommand(source: FabricClientCommandSource) {
        val visible = SpotifyActions.toggleWidget()
        source.sendFeedback(
            Component.literal("Spotify widget ${if (visible) "shown" else "hidden"}.")
                .withStyle(if (visible) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        )
    }

    /**
     * Turn "Tint all widgets" on or off (PBI 76). [target] is the state for `on`/`off`, or null
     * for the bare/`toggle` form, which flips the saved flag.
     */
    private fun handleSpotifyTintCommand(source: FabricClientCommandSource, target: Boolean?) {
        val enabled = if (target == null) {
            SpotifyActions.toggleTintAll()
        } else {
            SpotifyActions.setTintAll(target)
            target
        }
        source.sendFeedback(
            Component.literal(tintAllText(enabled))
                .withStyle(if (enabled) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleSpotifyEditCommand(source: FabricClientCommandSource) {
        // The same shared edit screen as `rotation edit`, opened on the next tick (the chat screen
        // closes itself after the command). The screen is the feedback.
        SpotifyHudFeature.requestEditScreen()
    }

    private fun handleRotationToggleCommand(source: FabricClientCommandSource) {
        val current = ConfigManager.rotationPlacements.all().firstOrNull()?.visible
            ?: RotationWidgetConfig().visible
        val newVisible = !current
        RotationPlacementEditor.setVisible(RotationWidgetConfig.WIDGET_ID, newVisible)
        source.sendFeedback(
            Component.literal("Dye rotation widget ${if (newVisible) "shown" else "hidden"}.")
                .withStyle(if (newVisible) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleRotationEditCommand(source: FabricClientCommandSource) {
        // Opens the same shared edit screen as `gif edit`; the rotation widget appears there
        // because it registered a PlacementEditor. The screen is the feedback.
        Minecraft.getInstance().showScreen(WidgetEditScreen())
    }

    /**
     * Enable/disable/toggle DVD-bounce mode (PBI 38). [target] is the explicit state for `on`/`off`,
     * or null for the bare/`toggle` form which flips the current live state. Persists the new flag and
     * applies it to the live [BounceController]; drift positions are never persisted.
     */
    private fun handleBounceCommand(source: FabricClientCommandSource, target: Boolean?) {
        val newState = target ?: !BounceController.isEnabled()
        BounceController.setEnabled(newState)
        ConfigManager.setBounceEnabled(newState)
        source.sendFeedback(
            Component.literal("Bounce mode: ${if (newState) "ON" else "OFF"}")
                .withStyle(if (newState) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        )
    }

    /**
     * Turn Discord Rich Presence on/off (PBI 77), or print its status when [enabled] is null. On
     * and off go through [enableDiscordPresence] / [disableDiscordPresence], which only flip the
     * persisted flag and wake the presence worker (all Discord IPC runs on the worker, never on
     * this client thread); the first time on also prints the one-time setup hint.
     */
    private fun handleDiscordCommand(source: FabricClientCommandSource, enabled: Boolean?) {
        when (enabled) {
            true -> {
                source.sendFeedback(
                    Component.literal("Discord presence on.").withStyle(ChatFormatting.GREEN)
                )
                enableDiscordPresence { source.sendFeedback(it) }
            }
            false -> {
                disableDiscordPresence()
                source.sendFeedback(
                    Component.literal("Discord presence off.").withStyle(ChatFormatting.YELLOW)
                )
            }
            null -> {
                val on = ConfigManager.isDiscordEnabled()
                source.sendFeedback(
                    Component.literal("Discord presence: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(
                            Component.literal(if (on) "ON" else "OFF")
                                .withStyle(if (on) ChatFormatting.GREEN else ChatFormatting.YELLOW)
                        )
                )
            }
        }
    }

    /**
     * Pick [dyeId] as the Discord card's hunt (PBI 77) when it is exactly a [DyeCatalog] id, and
     * wake the presence worker; an unknown id leaves the config as it was.
     */
    private fun handleDiscordHuntSet(source: FabricClientCommandSource, dyeId: String) {
        val dye = DyeCatalog.byId(dyeId)
        if (dye == null) {
            source.sendFeedback(
                Component.literal("Unknown dye '$dyeId'. Press Tab for the list.").withStyle(ChatFormatting.RED)
            )
            return
        }
        ConfigManager.setDiscordHuntDyeId(dye.dyeId)
        DiscordPresence.wake()
        source.sendFeedback(
            Component.literal("Discord hunt set to ${dye.displayName}.").withStyle(ChatFormatting.GREEN)
        )
    }

    /** Clear the Discord card's hunt (PBI 77) and wake the presence worker. */
    private fun handleDiscordHuntClear(source: FabricClientCommandSource) {
        ConfigManager.setDiscordHuntDyeId("")
        DiscordPresence.wake()
        source.sendFeedback(
            Component.literal("Discord hunt cleared.").withStyle(ChatFormatting.YELLOW)
        )
    }

    /** Print the active theme's name (PBI 81), as the Theme screen shows it ([selectedThemeKey]). */
    private fun handleThemeStatus(source: FabricClientCommandSource) {
        source.sendFeedback(
            Component.literal(THEME_LABEL)
                .withStyle(ChatFormatting.GRAY)
                .append(
                    Component.literal("${ThemePresets.displayName(selectedThemeKey(ConfigManager.config))}.")
                        .withStyle(ChatFormatting.WHITE)
                )
        )
    }

    /**
     * Switch to the preset [key] (PBI 81) when it is exactly one of [ThemePresets.PRESET_KEYS]; the
     * save makes it the active theme on the next frame. An unknown key leaves the config as it was.
     */
    private fun handleThemeSet(source: FabricClientCommandSource, key: String) {
        if (key !in ThemePresets.PRESET_KEYS) {
            source.sendFeedback(
                Component.literal("Unknown theme '$key'. Press Tab for the list.").withStyle(ChatFormatting.RED)
            )
            return
        }
        ConfigManager.setThemeKey(key)
        source.sendFeedback(
            Component.literal("$THEME_LABEL${ThemePresets.displayName(key)}.").withStyle(ChatFormatting.GREEN)
        )
    }

    /**
     * Theme the mod after [dyeId] (PBI 81) when it is exactly a [DyeThemeColors.COLORS] id; the save
     * makes it the active theme on the next frame. An unknown id leaves the config as it was.
     */
    private fun handleThemeDyeSet(source: FabricClientCommandSource, dyeId: String) {
        if (dyeId !in DyeThemeColors.COLORS) {
            source.sendFeedback(
                Component.literal("Unknown dye '$dyeId'. Press Tab for the list.").withStyle(ChatFormatting.RED)
            )
            return
        }
        val key = ThemePresets.dyeKey(dyeId)
        ConfigManager.setThemeKey(key)
        source.sendFeedback(
            Component.literal("$THEME_LABEL${ThemePresets.displayName(key)}.").withStyle(ChatFormatting.GREEN)
        )
    }

    /**
     * Switch to the saved Custom slot (PBI 81) when there is one; the save makes it the active theme
     * on the next frame. Without a slot the config is left as it was.
     */
    private fun handleThemeCustom(source: FabricClientCommandSource) {
        if (!ConfigManager.hasCustomTheme()) {
            source.sendFeedback(
                Component.literal("No custom theme yet. Edit a color in Settings > Theme.").withStyle(ChatFormatting.RED)
            )
            return
        }
        ConfigManager.setThemeKey(ThemePresets.CUSTOM)
        source.sendFeedback(
            Component.literal("$THEME_LABEL${ThemePresets.displayName(ThemePresets.CUSTOM)}.")
                .withStyle(ChatFormatting.GREEN)
        )
    }

    /** Print the Discord card's hunt (PBI 77), or `none`, and the hunt usage. */
    private fun handleDiscordHuntStatus(source: FabricClientCommandSource) {
        val huntDyeId = ConfigManager.discordHuntDyeId()
        val name = if (huntDyeId.isBlank()) {
            "none"
        } else {
            DyeCatalog.displayNameOr(huntDyeId)
        }
        source.sendFeedback(
            Component.literal("Discord hunt: $name").withStyle(ChatFormatting.GRAY)
        )
        source.sendFeedback(
            Component.literal("Usage: $DISCORD_HUNT_USAGE").withStyle(ChatFormatting.GRAY)
        )
    }

    private fun handleDyeListCommand(source: FabricClientCommandSource) {
        val widgets = ConfigManager.dyeProgressPlacements.all()
        if (widgets.isEmpty()) {
            source.sendFeedback(
                Component.literal("No dye widgets configured. Add one in /dyetracker dye edit (+ Add dye) or /dyetracker dye add <dyeId> <profile>")
                    .withStyle(ChatFormatting.YELLOW)
            )
            return
        }
        for (widget in widgets) {
            val visibility = if (widget.visible) "visible" else "hidden"
            source.sendFeedback(
                Component.literal(
                    "${widget.id}: ${widget.dyeId} ${widget.profileName} " +
                        "@(${widget.x}, ${widget.y}) scale ${widget.scale} [$visibility]"
                ).withStyle(ChatFormatting.GRAY)
            )
        }
    }

    private fun handleDyeRemoveCommand(source: FabricClientCommandSource, id: String) {
        if (!ConfigManager.dyeProgressPlacements.remove(id)) {
            source.sendFeedback(
                Component.literal("No dye widget with id '$id'.")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }
        source.sendFeedback(
            Component.literal("Removed dye widget '$id'.")
                .withStyle(ChatFormatting.GREEN)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleDyeEditCommand(source: FabricClientCommandSource) {
        // Opens the same shared edit screen as `gif edit`; dye widgets appear there because they
        // registered a PlacementEditor, and the "+ Add dye" panel is available. Screen = feedback.
        Minecraft.getInstance().showScreen(WidgetEditScreen())
    }

    /** Toggle one widget's visibility, or all of them when [id] is null. */
    private fun handleDyeToggleCommand(source: FabricClientCommandSource, id: String?) {
        val widgets = ConfigManager.dyeProgressPlacements.all()
        if (widgets.isEmpty()) {
            source.sendFeedback(
                Component.literal("No dye widgets configured.")
                    .withStyle(ChatFormatting.YELLOW)
            )
            return
        }
        if (id == null) {
            for (widget in widgets) {
                DyeProgressPlacementEditor.setVisible(widget.id, !widget.visible)
            }
            source.sendFeedback(
                Component.literal("Toggled ${widgets.size} dye widget(s).")
                    .withStyle(ChatFormatting.GREEN)
            )
            return
        }
        val widget = widgets.firstOrNull { it.id == id }
        if (widget == null) {
            source.sendFeedback(
                Component.literal("No dye widget with id '$id'.")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }
        val newVisible = !widget.visible
        DyeProgressPlacementEditor.setVisible(id, newVisible)
        source.sendFeedback(
            Component.literal("Dye widget '$id' ${if (newVisible) "shown" else "hidden"}.")
                .withStyle(if (newVisible) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        )
    }

    private fun handleDyeAddCommand(source: FabricClientCommandSource, dyeId: String, profile: String) {
        if (!ConfigManager.config.isLinked()) {
            source.sendFeedback(
                Component.literal("Account not linked. Use /dyetracker link <code> first.")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }
        if (!DyeSprites.has(dyeId)) {
            source.sendFeedback(
                Component.literal("Unknown dye '$dyeId'. Use /dyetracker dye edit to pick from the list.")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }
        source.sendFeedback(
            Component.literal("Resolving profile…")
                .withStyle(ChatFormatting.YELLOW)
        )
        val username = ConfigManager.config.linkedUsername
        dyeCommandScope.launch {
            // resolveAndAdd performs the blocking profiles fetch + add + immediate poll off-thread.
            val result = DyeProgressAdder.resolveAndAdd(username, dyeId, profile)
            postOnClient {
                when (result) {
                    is DyeProgressAdder.Result.Added -> source.sendFeedback(
                        Component.literal("Added dye widget '${result.id}' ($dyeId @ ${profile.trim()}).")
                            .withStyle(ChatFormatting.GREEN)
                    )
                    DyeProgressAdder.Result.NotLinked -> source.sendFeedback(
                        Component.literal("Account not linked.").withStyle(ChatFormatting.RED)
                    )
                    DyeProgressAdder.Result.InvalidDye -> source.sendFeedback(
                        Component.literal("Unknown dye '$dyeId'.").withStyle(ChatFormatting.RED)
                    )
                    DyeProgressAdder.Result.EmptyProfile -> source.sendFeedback(
                        Component.literal("Enter a profile name.").withStyle(ChatFormatting.RED)
                    )
                    is DyeProgressAdder.Result.UnknownProfile -> source.sendFeedback(
                        Component.literal("No profile '${result.typed}'. Available: ${result.available.joinToString()}")
                            .withStyle(ChatFormatting.RED)
                    )
                    is DyeProgressAdder.Result.NetworkError -> source.sendFeedback(
                        Component.literal("Couldn't load profiles: ${result.message}").withStyle(ChatFormatting.RED)
                    )
                }
            }
        }
    }

    /** Marshal a callback onto the client thread so we never touch client APIs from IO. */
    private fun postOnClient(block: () -> Unit) {
        Minecraft.getInstance().execute(block)
    }

    private fun handleLinkCommand(source: FabricClientCommandSource, code: String) {
        // Validate code format
        if (code.length != 8 || !code.all { it.isLetterOrDigit() }) {
            source.sendFeedback(
                Component.literal("Invalid code format. Please enter the 8-character code from the website.")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }

        // Check if already linked
        if (AccountVerification.isLinked()) {
            source.sendFeedback(
                Component.literal("Account already linked as ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(
                        Component.literal(ConfigManager.config.linkedUsername)
                            .withStyle(ChatFormatting.AQUA)
                    )
                    .append(
                        Component.literal(". Use /dyetracker unlink first.")
                            .withStyle(ChatFormatting.YELLOW)
                    )
            )
            return
        }

        source.sendFeedback(
            Component.literal("Verifying account...")
                .withStyle(ChatFormatting.YELLOW)
        )

        // Run verification async
        AccountVerification.verifyAccount(code)
            .thenAccept { result ->
                // Must run on main thread to send feedback
                Minecraft.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Component.literal("\u2714 ")
                                .withStyle(ChatFormatting.GREEN)
                                .append(
                                    Component.literal(result.message)
                                        .withStyle(ChatFormatting.GREEN)
                                )
                        )
                        source.sendFeedback(
                            Component.literal("Linked as: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(
                                    Component.literal(result.username ?: "")
                                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Component.literal("\u2718 ")
                                .withStyle(ChatFormatting.RED)
                                .append(
                                    Component.literal(result.message)
                                        .withStyle(ChatFormatting.RED)
                                )
                        )
                    }
                }
            }
    }

    private fun handleStatusCommand(source: FabricClientCommandSource) {
        if (AccountVerification.isLinked()) {
            source.sendFeedback(
                Component.literal("Account Status: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        Component.literal("Linked")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    )
            )
            source.sendFeedback(
                Component.literal("Username: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        Component.literal(ConfigManager.config.linkedUsername)
                            .withStyle(ChatFormatting.AQUA)
                    )
            )
            source.sendFeedback(
                Component.literal("UUID: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        Component.literal(ConfigManager.config.linkedUuid)
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
            )
            // Show token status
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Component.literal("Auth Token: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        if (hasToken) {
                            Component.literal("Valid")
                                .withStyle(ChatFormatting.GREEN)
                        } else {
                            Component.literal("Missing")
                                .withStyle(ChatFormatting.RED)
                        }
                    )
            )

            // Show sync status
            val lastSync = SyncManager.getLastSyncTime()
            val lastSyncText = if (lastSync > 0) {
                SimpleDateFormat("HH:mm:ss").format(Date(lastSync))
            } else {
                "Never"
            }
            val syncPending = SyncManager.isSyncPending()
            val lastSyncSuccess = SyncManager.wasLastSyncSuccessful()

            source.sendFeedback(
                Component.literal("Last Sync: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        Component.literal(lastSyncText)
                            .withStyle(if (lastSyncSuccess) ChatFormatting.GREEN else ChatFormatting.RED)
                    )
                    .append(
                        if (syncPending) {
                            Component.literal(" (sync pending)")
                                .withStyle(ChatFormatting.YELLOW)
                        } else {
                            Component.literal("")
                        }
                    )
            )
        } else {
            source.sendFeedback(
                Component.literal("Account Status: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        Component.literal("Not Linked")
                            .withStyle(ChatFormatting.RED)
                    )
            )
            source.sendFeedback(
                Component.literal("Use /dyetracker link <code> to link your account.")
                    .withStyle(ChatFormatting.YELLOW)
            )
        }
    }

    private fun handleSyncCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Component.literal("Account not linked. Use /dyetracker link <code> first.")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }

        val data = RngDataStore.getData()
        if (!data.hasData()) {
            source.sendFeedback(
                Component.literal("No RNG data to sync.")
                    .withStyle(ChatFormatting.YELLOW)
            )
            return
        }

        source.sendFeedback(
            Component.literal("Syncing RNG data...")
                .withStyle(ChatFormatting.YELLOW)
        )

        SyncManager.syncNow(data)
            .thenAccept { result ->
                Minecraft.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Component.literal("\u2714 ")
                                .withStyle(ChatFormatting.GREEN)
                                .append(
                                    Component.literal("RNG data synced successfully!")
                                        .withStyle(ChatFormatting.GREEN)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Component.literal("\u2718 ")
                                .withStyle(ChatFormatting.RED)
                                .append(
                                    Component.literal("Sync failed: ${result.message}")
                                        .withStyle(ChatFormatting.RED)
                                )
                        )
                    }
                }
            }
    }

    private fun handleReloadCommand(source: FabricClientCommandSource) {
        try {
            ConfigManager.load()
            // Re-apply the persisted bounce flag to the live controller (PBI 38).
            BounceController.setEnabled(ConfigManager.isBounceEnabled())
            source.sendFeedback(
                Component.literal("\u2714 Configuration reloaded")
                    .withStyle(ChatFormatting.GREEN)
            )
            source.sendFeedback(
                Component.literal("API URL: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        Component.literal(ConfigManager.config.apiUrl)
                            .withStyle(ChatFormatting.AQUA)
                    )
            )
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Component.literal("Auth Token: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        if (hasToken) {
                            Component.literal("Present")
                                .withStyle(ChatFormatting.GREEN)
                        } else {
                            Component.literal("Not set")
                                .withStyle(ChatFormatting.YELLOW)
                        }
                    )
            )
            source.sendFeedback(
                Component.literal("Bounce mode: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        if (ConfigManager.isBounceEnabled()) {
                            Component.literal("ON").withStyle(ChatFormatting.GREEN)
                        } else {
                            Component.literal("OFF").withStyle(ChatFormatting.YELLOW)
                        }
                    )
            )
        } catch (e: Exception) {
            source.sendFeedback(
                Component.literal("\u2718 Failed to reload config: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
            DyeTrackerMod.LOGGER.error("Failed to reload config", e)
        }
    }

    private fun handleUnlinkCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Component.literal("No account is currently linked.")
                    .withStyle(ChatFormatting.YELLOW)
            )
            return
        }

        val username = ConfigManager.config.linkedUsername
        AccountVerification.unlink()

        source.sendFeedback(
            Component.literal("Account ")
                .withStyle(ChatFormatting.GRAY)
                .append(
                    Component.literal(username)
                        .withStyle(ChatFormatting.AQUA)
                )
                .append(
                    Component.literal(" has been unlinked.")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
    }

    private fun handleShowCommand(source: FabricClientCommandSource) {
        val data = RngDataStore.getData()

        if (!data.hasData()) {
            source.sendFeedback(
                Component.literal("No RNG data captured yet.")
                    .withStyle(ChatFormatting.YELLOW)
            )
            source.sendFeedback(
                Component.literal("Open the tracked menus in-game to capture data.")
                    .withStyle(ChatFormatting.GRAY)
            )
            return
        }

        source.sendFeedback(
            Component.literal("=== RNG Data ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        )

        // Show mineshaft pity
        data.mineshaftPity?.let { pity ->
            source.sendFeedback(
                Component.literal("Mineshaft Pity: ")
                    .withStyle(ChatFormatting.DARK_AQUA)
                    .append(
                        Component.literal("${pity.pityValue}/2,000")
                            .withStyle(ChatFormatting.WHITE)
                    )
            )
        }

        // Show archfiend dye rolls
        data.archfiendDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Archfiend Dye:")
                    .withStyle(ChatFormatting.DARK_RED)
            )
            source.sendFeedback(
                Component.literal("  High Class Dice: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        Component.literal("${dye.highClassDiceRolls} rolls")
                            .withStyle(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (1/666)")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
            )
            source.sendFeedback(
                Component.literal("  Archfiend Dice: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                        Component.literal("${dye.archfiendDiceRolls} rolls")
                            .withStyle(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (1/6,600)")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show nyanza dye commissions
        data.nyanzaDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Nyanza Dye: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(
                        Component.literal("${formatXp(dye.commissionsCompleted.toLong())} commissions")
                            .withStyle(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (1/250,000)")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show copper dye visitors seen (per-tier snapshot from the Visitor's Logbook)
        data.copperDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Copper Dye:")
                    .withStyle(ChatFormatting.GOLD)
            )
            // Display drop rates for each rarity tier
            val dropRates = mapOf(
                "UNCOMMON" to "1/100,000",
                "RARE" to "1/20,000",
                "LEGENDARY" to "1/4,000",
                "MYTHIC" to "1/800",
                "SPECIAL" to "1/500"
            )
            for ((rarity, count) in dye.visitorsSeen) {
                val dropRate = dropRates[rarity.name] ?: "???"
                source.sendFeedback(
                    Component.literal("  ${rarity.name}: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(
                            Component.literal("$count seen")
                                .withStyle(ChatFormatting.WHITE)
                        )
                        .append(
                            Component.literal(" ($dropRate)")
                                .withStyle(ChatFormatting.DARK_GRAY)
                        )
                )
            }
        }

        // Show fossil dye excavations (PBI 66)
        data.fossilDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Fossil Dye: ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(
                        Component.literal("${formatXp(dye.excavations.toLong())} excavations")
                            .withStyle(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (1/500,000)")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show dark purple dye auctions attended (PBI 66)
        data.darkPurpleDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Dark Purple Dye: ")
                    .withStyle(ChatFormatting.DARK_PURPLE)
                    .append(
                        Component.literal("${formatXp(dye.auctionsAttended.toLong())} Dark Auctions")
                            .withStyle(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (1/400 per round, 3 rounds)")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show holly dye gift count (PBI 66); the red share is estimated by the backend
        data.hollyDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Holly Dye: ")
                    .withStyle(ChatFormatting.RED)
                    .append(
                        Component.literal("${formatXp(dye.giftRewards.values.sumOf { it.toLong() })} gifts")
                            .withStyle(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (red share estimated online)")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show mocha dye potions brewed, total then per tier (PBI 66)
        data.mochaDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Mocha Dye: ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(
                        Component.literal("${formatXp(dye.potionsByTier.values.sumOf { it.toLong() })} potions brewed")
                            .withStyle(ChatFormatting.WHITE)
                    )
            )
            for (tier in PotionTier.entries) {
                val count = dye.potionsByTier[tier] ?: 0
                if (count == 0) continue
                source.sendFeedback(
                    Component.literal("  Tier ${tier.name}: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(
                            Component.literal(formatXp(count.toLong()))
                                .withStyle(ChatFormatting.WHITE)
                        )
                )
            }
        }
    }

    /**
     * Format an XP value with commas for readability.
     */
    private fun formatXp(xp: Long): String {
        return "%,d".format(xp)
    }

    private fun showHelp(source: FabricClientCommandSource) {
        source.sendFeedback(
            Component.literal("DyeTracker Commands:")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        )
        source.sendFeedback(
            Component.literal("  /dyetracker link <code>")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Link your account")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker status")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Show link status")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker unlink")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Unlink your account")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker show")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Show captured RNG data")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker sync")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Force sync RNG data to backend")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker reload")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Reload config (debug)")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker gif <add|list|remove|edit>")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Manage HUD image/GIF overlays")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker rotation <toggle|edit>")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Toggle/position the dye rotation widget")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker dye <list|remove|edit|toggle|add>")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Manage single-dye progress widgets")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker bounce [on|off|toggle]")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - DVD-bounce your HUD widgets around the screen")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker discord <on|off>")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Show your dye stats on Discord")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  $DISCORD_HUNT_USAGE")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Pick the dye your Discord card shows")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  $THEME_USAGE | dye <dye> | ${ThemePresets.CUSTOM}")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(
                        " - Pick a theme (${ThemePresets.PRESET_KEYS.joinToString()}), theme the mod after a dye," +
                            " or switch back to your custom theme"
                    )
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker menu")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Open the DyeTracker menu")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  $SPOTIFY_USAGE")
                .withStyle(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Show your Spotify now playing")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
    }
}
