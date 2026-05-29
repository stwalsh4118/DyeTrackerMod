package com.dyetracker.commands

import com.dyetracker.DyeTrackerMod
import com.dyetracker.auth.AccountVerification
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.DungeonFloor
import com.dyetracker.data.RngDataStore
import com.dyetracker.dyeprogress.DyeProgressAdder
import com.dyetracker.dyeprogress.DyeProgressPlacementEditor
import com.dyetracker.overlay.OverlayAddPipeline
import com.dyetracker.rotation.DyeSprites
import com.dyetracker.rotation.RotationPlacementEditor
import com.dyetracker.rotation.RotationWidgetConfig
import com.dyetracker.sync.SyncManager
import com.dyetracker.ui.bounce.BounceController
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.texture.ImageTextureManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandRegistryAccess
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
            ClientCommandManager.literal("dyetracker")
                .then(
                    ClientCommandManager.literal("link")
                        .then(
                            ClientCommandManager.argument("code", StringArgumentType.word())
                                .executes { context ->
                                    val code = StringArgumentType.getString(context, "code")
                                    handleLinkCommand(context.source, code)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Component.literal("Usage: /dyetracker link <code>")
                                    .formatted(ChatFormatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("status")
                        .executes { context ->
                            handleStatusCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("unlink")
                        .executes { context ->
                            handleUnlinkCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("show")
                        .executes { context ->
                            handleShowCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("sync")
                        .executes { context ->
                            handleSyncCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("reload")
                        .executes { context ->
                            handleReloadCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("gif")
                        .then(
                            ClientCommandManager.literal("add")
                                .then(
                                    ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                        .executes { context ->
                                            val url = StringArgumentType.getString(context, "url")
                                            handleGifAddCommand(context.source, url)
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: /dyetracker gif add <url>")
                                            .formatted(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("list")
                                .executes { context ->
                                    handleGifListCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("remove")
                                .then(
                                    ClientCommandManager.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            val id = StringArgumentType.getString(context, "id")
                                            handleGifRemoveCommand(context.source, id)
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: /dyetracker gif remove <id>")
                                            .formatted(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("edit")
                                .executes { context ->
                                    handleGifEditCommand(context.source)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Component.literal("Usage: /dyetracker gif <add|list|remove|edit>")
                                    .formatted(ChatFormatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("rotation")
                        .then(
                            ClientCommandManager.literal("toggle")
                                .executes { context ->
                                    handleRotationToggleCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("edit")
                                .executes { context ->
                                    handleRotationEditCommand(context.source)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Component.literal("Usage: /dyetracker rotation <toggle|edit>")
                                    .formatted(ChatFormatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("dye")
                        .then(
                            ClientCommandManager.literal("list")
                                .executes { context ->
                                    handleDyeListCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("remove")
                                .then(
                                    ClientCommandManager.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            handleDyeRemoveCommand(context.source, StringArgumentType.getString(context, "id"))
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: /dyetracker dye remove <id>")
                                            .formatted(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("toggle")
                                .then(
                                    ClientCommandManager.argument("id", StringArgumentType.word())
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
                            ClientCommandManager.literal("edit")
                                .executes { context ->
                                    handleDyeEditCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("add")
                                .then(
                                    ClientCommandManager.argument("dyeId", StringArgumentType.word())
                                        .then(
                                            ClientCommandManager.argument("profile", StringArgumentType.greedyString())
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
                                                    .formatted(ChatFormatting.YELLOW)
                                            )
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Component.literal("Usage: /dyetracker dye add <dyeId> <profile>")
                                            .formatted(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Component.literal("Usage: /dyetracker dye <list|remove|edit|toggle|add>")
                                    .formatted(ChatFormatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("bounce")
                        .then(
                            ClientCommandManager.literal("on")
                                .executes { context ->
                                    handleBounceCommand(context.source, true)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("off")
                                .executes { context ->
                                    handleBounceCommand(context.source, false)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("toggle")
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
                    .formatted(ChatFormatting.RED)
            )
            return
        }

        source.sendFeedback(
            Component.literal("Downloading…")
                .formatted(ChatFormatting.YELLOW)
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
                        ).formatted(ChatFormatting.GREEN)
                    )
                    is OverlayAddPipeline.Outcome.Failure -> source.sendFeedback(
                        Component.literal(outcome.message).formatted(ChatFormatting.RED)
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
                    .formatted(ChatFormatting.YELLOW)
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
                ).formatted(ChatFormatting.GRAY)
            )
        }
    }

    private fun handleGifRemoveCommand(source: FabricClientCommandSource, id: String) {
        if (!ConfigManager.gifPlacements.remove(id)) {
            source.sendFeedback(
                Component.literal("No overlay with id '$id'.")
                    .formatted(ChatFormatting.RED)
            )
            return
        }
        ImageTextureManager.release(id)
        source.sendFeedback(
            Component.literal("Removed overlay '$id'.")
                .formatted(ChatFormatting.GREEN)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleGifEditCommand(source: FabricClientCommandSource) {
        // Brigadier client commands already execute on the render thread, so no marshal
        // needed. The screen IS the feedback; no chat output here.
        Minecraft.getInstance().setScreen(WidgetEditScreen())
    }

    private fun handleRotationToggleCommand(source: FabricClientCommandSource) {
        val current = ConfigManager.rotationPlacements.all().firstOrNull()?.visible
            ?: RotationWidgetConfig().visible
        val newVisible = !current
        RotationPlacementEditor.setVisible(RotationWidgetConfig.WIDGET_ID, newVisible)
        source.sendFeedback(
            Component.literal("Dye rotation widget ${if (newVisible) "shown" else "hidden"}.")
                .formatted(if (newVisible) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleRotationEditCommand(source: FabricClientCommandSource) {
        // Opens the same shared edit screen as `gif edit`; the rotation widget appears there
        // because it registered a PlacementEditor. The screen is the feedback.
        Minecraft.getInstance().setScreen(WidgetEditScreen())
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
                .formatted(if (newState) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        )
    }

    private fun handleDyeListCommand(source: FabricClientCommandSource) {
        val widgets = ConfigManager.dyeProgressPlacements.all()
        if (widgets.isEmpty()) {
            source.sendFeedback(
                Component.literal("No dye widgets configured. Add one in /dyetracker dye edit (+ Add dye) or /dyetracker dye add <dyeId> <profile>")
                    .formatted(ChatFormatting.YELLOW)
            )
            return
        }
        for (widget in widgets) {
            val visibility = if (widget.visible) "visible" else "hidden"
            source.sendFeedback(
                Component.literal(
                    "${widget.id}: ${widget.dyeId} ${widget.profileName} " +
                        "@(${widget.x}, ${widget.y}) scale ${widget.scale} [$visibility]"
                ).formatted(ChatFormatting.GRAY)
            )
        }
    }

    private fun handleDyeRemoveCommand(source: FabricClientCommandSource, id: String) {
        if (!ConfigManager.dyeProgressPlacements.remove(id)) {
            source.sendFeedback(
                Component.literal("No dye widget with id '$id'.")
                    .formatted(ChatFormatting.RED)
            )
            return
        }
        source.sendFeedback(
            Component.literal("Removed dye widget '$id'.")
                .formatted(ChatFormatting.GREEN)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleDyeEditCommand(source: FabricClientCommandSource) {
        // Opens the same shared edit screen as `gif edit`; dye widgets appear there because they
        // registered a PlacementEditor, and the "+ Add dye" panel is available. Screen = feedback.
        Minecraft.getInstance().setScreen(WidgetEditScreen())
    }

    /** Toggle one widget's visibility, or all of them when [id] is null. */
    private fun handleDyeToggleCommand(source: FabricClientCommandSource, id: String?) {
        val widgets = ConfigManager.dyeProgressPlacements.all()
        if (widgets.isEmpty()) {
            source.sendFeedback(
                Component.literal("No dye widgets configured.")
                    .formatted(ChatFormatting.YELLOW)
            )
            return
        }
        if (id == null) {
            for (widget in widgets) {
                DyeProgressPlacementEditor.setVisible(widget.id, !widget.visible)
            }
            source.sendFeedback(
                Component.literal("Toggled ${widgets.size} dye widget(s).")
                    .formatted(ChatFormatting.GREEN)
            )
            return
        }
        val widget = widgets.firstOrNull { it.id == id }
        if (widget == null) {
            source.sendFeedback(
                Component.literal("No dye widget with id '$id'.")
                    .formatted(ChatFormatting.RED)
            )
            return
        }
        val newVisible = !widget.visible
        DyeProgressPlacementEditor.setVisible(id, newVisible)
        source.sendFeedback(
            Component.literal("Dye widget '$id' ${if (newVisible) "shown" else "hidden"}.")
                .formatted(if (newVisible) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        )
    }

    private fun handleDyeAddCommand(source: FabricClientCommandSource, dyeId: String, profile: String) {
        if (!ConfigManager.config.isLinked()) {
            source.sendFeedback(
                Component.literal("Account not linked. Use /dyetracker link <code> first.")
                    .formatted(ChatFormatting.RED)
            )
            return
        }
        if (!DyeSprites.has(dyeId)) {
            source.sendFeedback(
                Component.literal("Unknown dye '$dyeId'. Use /dyetracker dye edit to pick from the list.")
                    .formatted(ChatFormatting.RED)
            )
            return
        }
        source.sendFeedback(
            Component.literal("Resolving profile…")
                .formatted(ChatFormatting.YELLOW)
        )
        val username = ConfigManager.config.linkedUsername
        dyeCommandScope.launch {
            // resolveAndAdd performs the blocking profiles fetch + add + immediate poll off-thread.
            val result = DyeProgressAdder.resolveAndAdd(username, dyeId, profile)
            postOnClient {
                when (result) {
                    is DyeProgressAdder.Result.Added -> source.sendFeedback(
                        Component.literal("Added dye widget '${result.id}' ($dyeId @ ${profile.trim()}).")
                            .formatted(ChatFormatting.GREEN)
                    )
                    DyeProgressAdder.Result.NotLinked -> source.sendFeedback(
                        Component.literal("Account not linked.").formatted(ChatFormatting.RED)
                    )
                    DyeProgressAdder.Result.InvalidDye -> source.sendFeedback(
                        Component.literal("Unknown dye '$dyeId'.").formatted(ChatFormatting.RED)
                    )
                    DyeProgressAdder.Result.EmptyProfile -> source.sendFeedback(
                        Component.literal("Enter a profile name.").formatted(ChatFormatting.RED)
                    )
                    is DyeProgressAdder.Result.UnknownProfile -> source.sendFeedback(
                        Component.literal("No profile '${result.typed}'. Available: ${result.available.joinToString()}")
                            .formatted(ChatFormatting.RED)
                    )
                    is DyeProgressAdder.Result.NetworkError -> source.sendFeedback(
                        Component.literal("Couldn't load profiles: ${result.message}").formatted(ChatFormatting.RED)
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
                    .formatted(ChatFormatting.RED)
            )
            return
        }

        // Check if already linked
        if (AccountVerification.isLinked()) {
            source.sendFeedback(
                Component.literal("Account already linked as ")
                    .formatted(ChatFormatting.YELLOW)
                    .append(
                        Component.literal(ConfigManager.config.linkedUsername)
                            .formatted(ChatFormatting.AQUA)
                    )
                    .append(
                        Component.literal(". Use /dyetracker unlink first.")
                            .formatted(ChatFormatting.YELLOW)
                    )
            )
            return
        }

        source.sendFeedback(
            Component.literal("Verifying account...")
                .formatted(ChatFormatting.YELLOW)
        )

        // Run verification async
        AccountVerification.verifyAccount(code)
            .thenAccept { result ->
                // Must run on main thread to send feedback
                Minecraft.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Component.literal("\u2714 ")
                                .formatted(ChatFormatting.GREEN)
                                .append(
                                    Component.literal(result.message)
                                        .formatted(ChatFormatting.GREEN)
                                )
                        )
                        source.sendFeedback(
                            Component.literal("Linked as: ")
                                .formatted(ChatFormatting.GRAY)
                                .append(
                                    Component.literal(result.username ?: "")
                                        .formatted(ChatFormatting.AQUA, ChatFormatting.BOLD)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Component.literal("\u2718 ")
                                .formatted(ChatFormatting.RED)
                                .append(
                                    Component.literal(result.message)
                                        .formatted(ChatFormatting.RED)
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
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        Component.literal("Linked")
                            .formatted(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    )
            )
            source.sendFeedback(
                Component.literal("Username: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        Component.literal(ConfigManager.config.linkedUsername)
                            .formatted(ChatFormatting.AQUA)
                    )
            )
            source.sendFeedback(
                Component.literal("UUID: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        Component.literal(ConfigManager.config.linkedUuid)
                            .formatted(ChatFormatting.DARK_GRAY)
                    )
            )
            // Show token status
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Component.literal("Auth Token: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        if (hasToken) {
                            Component.literal("Valid")
                                .formatted(ChatFormatting.GREEN)
                        } else {
                            Component.literal("Missing")
                                .formatted(ChatFormatting.RED)
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
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        Component.literal(lastSyncText)
                            .formatted(if (lastSyncSuccess) ChatFormatting.GREEN else ChatFormatting.RED)
                    )
                    .append(
                        if (syncPending) {
                            Component.literal(" (sync pending)")
                                .formatted(ChatFormatting.YELLOW)
                        } else {
                            Component.literal("")
                        }
                    )
            )
        } else {
            source.sendFeedback(
                Component.literal("Account Status: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        Component.literal("Not Linked")
                            .formatted(ChatFormatting.RED)
                    )
            )
            source.sendFeedback(
                Component.literal("Use /dyetracker link <code> to link your account.")
                    .formatted(ChatFormatting.YELLOW)
            )
        }
    }

    private fun handleSyncCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Component.literal("Account not linked. Use /dyetracker link <code> first.")
                    .formatted(ChatFormatting.RED)
            )
            return
        }

        val data = RngDataStore.getData()
        if (!data.hasData()) {
            source.sendFeedback(
                Component.literal("No RNG data to sync.")
                    .formatted(ChatFormatting.YELLOW)
            )
            return
        }

        source.sendFeedback(
            Component.literal("Syncing RNG data...")
                .formatted(ChatFormatting.YELLOW)
        )

        SyncManager.syncNow(data)
            .thenAccept { result ->
                Minecraft.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Component.literal("\u2714 ")
                                .formatted(ChatFormatting.GREEN)
                                .append(
                                    Component.literal("RNG data synced successfully!")
                                        .formatted(ChatFormatting.GREEN)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Component.literal("\u2718 ")
                                .formatted(ChatFormatting.RED)
                                .append(
                                    Component.literal("Sync failed: ${result.message}")
                                        .formatted(ChatFormatting.RED)
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
                    .formatted(ChatFormatting.GREEN)
            )
            source.sendFeedback(
                Component.literal("API URL: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        Component.literal(ConfigManager.config.apiUrl)
                            .formatted(ChatFormatting.AQUA)
                    )
            )
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Component.literal("Auth Token: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        if (hasToken) {
                            Component.literal("Present")
                                .formatted(ChatFormatting.GREEN)
                        } else {
                            Component.literal("Not set")
                                .formatted(ChatFormatting.YELLOW)
                        }
                    )
            )
            source.sendFeedback(
                Component.literal("Bounce mode: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        if (ConfigManager.isBounceEnabled()) {
                            Component.literal("ON").formatted(ChatFormatting.GREEN)
                        } else {
                            Component.literal("OFF").formatted(ChatFormatting.YELLOW)
                        }
                    )
            )
        } catch (e: Exception) {
            source.sendFeedback(
                Component.literal("\u2718 Failed to reload config: ${e.message}")
                    .formatted(ChatFormatting.RED)
            )
            DyeTrackerMod.LOGGER.error("Failed to reload config", e)
        }
    }

    private fun handleUnlinkCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Component.literal("No account is currently linked.")
                    .formatted(ChatFormatting.YELLOW)
            )
            return
        }

        val username = ConfigManager.config.linkedUsername
        AccountVerification.unlink()

        source.sendFeedback(
            Component.literal("Account ")
                .formatted(ChatFormatting.GRAY)
                .append(
                    Component.literal(username)
                        .formatted(ChatFormatting.AQUA)
                )
                .append(
                    Component.literal(" has been unlinked.")
                        .formatted(ChatFormatting.GRAY)
                )
        )
    }

    private fun handleShowCommand(source: FabricClientCommandSource) {
        val data = RngDataStore.getData()

        if (!data.hasData()) {
            source.sendFeedback(
                Component.literal("No RNG data captured yet.")
                    .formatted(ChatFormatting.YELLOW)
            )
            source.sendFeedback(
                Component.literal("Open RNG meter menus in-game to capture data.")
                    .formatted(ChatFormatting.GRAY)
            )
            return
        }

        source.sendFeedback(
            Component.literal("=== RNG Data ===")
                .formatted(ChatFormatting.GOLD, ChatFormatting.BOLD)
        )

        // Show dungeon meters
        if (data.dungeonMeters.isNotEmpty()) {
            source.sendFeedback(
                Component.literal("Dungeon RNG Meters:")
                    .formatted(ChatFormatting.LIGHT_PURPLE)
            )
            for ((floor, meter) in data.dungeonMeters) {
                val itemInfo = if (meter.selectedItem != null) {
                    " [${meter.selectedItem}: ${formatXp(meter.goalXp ?: 0)} goal]"
                } else ""
                source.sendFeedback(
                    Component.literal("  ${floor.name}: ")
                        .formatted(ChatFormatting.GRAY)
                        .append(
                            Component.literal("${formatXp(meter.storedXp)} XP")
                                .formatted(ChatFormatting.WHITE)
                        )
                        .append(
                            Component.literal(itemInfo)
                                .formatted(ChatFormatting.DARK_GRAY)
                        )
                )
            }
        }

        // Show nucleus meter
        data.nucleusMeter?.let { meter ->
            val itemInfo = if (meter.selectedItem != null) {
                " [${meter.selectedItem}: ${formatXp(meter.goalXp ?: 0)} goal]"
            } else ""
            source.sendFeedback(
                Component.literal("Nucleus RNG: ")
                    .formatted(ChatFormatting.GREEN)
                    .append(
                        Component.literal("${formatXp(meter.storedXp)} XP")
                            .formatted(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(itemInfo)
                            .formatted(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show experimentation meter
        data.experimentationMeter?.let { meter ->
            val itemInfo = if (meter.selectedItem != null) {
                " [${meter.selectedItem}: ${formatXp(meter.goalXp ?: 0)} goal]"
            } else ""
            source.sendFeedback(
                Component.literal("Experimentation RNG: ")
                    .formatted(ChatFormatting.BLUE)
                    .append(
                        Component.literal("${formatXp(meter.storedXp)} XP")
                            .formatted(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(itemInfo)
                            .formatted(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show mineshaft pity
        data.mineshaftPity?.let { pity ->
            source.sendFeedback(
                Component.literal("Mineshaft Pity: ")
                    .formatted(ChatFormatting.DARK_AQUA)
                    .append(
                        Component.literal("${pity.pityValue}/2,000")
                            .formatted(ChatFormatting.WHITE)
                    )
            )
        }

        // Show archfiend dye rolls
        data.archfiendDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Archfiend Dye:")
                    .formatted(ChatFormatting.DARK_RED)
            )
            source.sendFeedback(
                Component.literal("  High Class Dice: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        Component.literal("${dye.highClassDiceRolls} rolls")
                            .formatted(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (1/666)")
                            .formatted(ChatFormatting.DARK_GRAY)
                    )
            )
            source.sendFeedback(
                Component.literal("  Archfiend Dice: ")
                    .formatted(ChatFormatting.GRAY)
                    .append(
                        Component.literal("${dye.archfiendDiceRolls} rolls")
                            .formatted(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (1/6,600)")
                            .formatted(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show nyanza dye commissions
        data.nyanzaDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Nyanza Dye: ")
                    .formatted(ChatFormatting.GREEN)
                    .append(
                        Component.literal("${formatXp(dye.commissionsCompleted.toLong())} commissions")
                            .formatted(ChatFormatting.WHITE)
                    )
                    .append(
                        Component.literal(" (1/250,000)")
                            .formatted(ChatFormatting.DARK_GRAY)
                    )
            )
        }

        // Show copper dye visitors seen (per-tier snapshot from the Visitor's Logbook)
        data.copperDye?.let { dye ->
            source.sendFeedback(
                Component.literal("Copper Dye:")
                    .formatted(ChatFormatting.GOLD)
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
                        .formatted(ChatFormatting.GRAY)
                        .append(
                            Component.literal("$count seen")
                                .formatted(ChatFormatting.WHITE)
                        )
                        .append(
                            Component.literal(" ($dropRate)")
                                .formatted(ChatFormatting.DARK_GRAY)
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
                .formatted(ChatFormatting.GOLD, ChatFormatting.BOLD)
        )
        source.sendFeedback(
            Component.literal("  /dyetracker link <code>")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Link your account")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker status")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Show link status")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker unlink")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Unlink your account")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker show")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Show captured RNG data")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker sync")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Force sync RNG data to backend")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker reload")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Reload config (debug)")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker gif <add|list|remove|edit>")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Manage HUD image/GIF overlays")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker rotation <toggle|edit>")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Toggle/position the dye rotation widget")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker dye <list|remove|edit|toggle|add>")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - Manage single-dye progress widgets")
                        .formatted(ChatFormatting.GRAY)
                )
        )
        source.sendFeedback(
            Component.literal("  /dyetracker bounce [on|off|toggle]")
                .formatted(ChatFormatting.YELLOW)
                .append(
                    Component.literal(" - DVD-bounce your HUD widgets around the screen")
                        .formatted(ChatFormatting.GRAY)
                )
        )
    }
}
