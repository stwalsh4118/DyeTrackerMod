package com.dyetracker.commands

import com.dyetracker.DyeTrackerMod
import com.dyetracker.auth.AccountVerification
import com.dyetracker.config.ConfigManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Registers client-side commands for the DyeTracker mod.
 */
object DyeTrackerCommands {

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
                                Text.literal("Usage: /dyetracker link <code>")
                                    .formatted(Formatting.YELLOW)
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
                .executes { context ->
                    showHelp(context.source)
                    1
                }
        )
    }

    private fun handleLinkCommand(source: FabricClientCommandSource, code: String) {
        // Validate code format
        if (code.length != 8 || !code.all { it.isLetterOrDigit() }) {
            source.sendFeedback(
                Text.literal("Invalid code format. Please enter the 8-character code from the website.")
                    .formatted(Formatting.RED)
            )
            return
        }

        // Check if already linked
        if (AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("Account already linked as ")
                    .formatted(Formatting.YELLOW)
                    .append(
                        Text.literal(ConfigManager.config.linkedUsername)
                            .formatted(Formatting.AQUA)
                    )
                    .append(
                        Text.literal(". Use /dyetracker unlink first.")
                            .formatted(Formatting.YELLOW)
                    )
            )
            return
        }

        source.sendFeedback(
            Text.literal("Verifying account...")
                .formatted(Formatting.YELLOW)
        )

        // Run verification async
        AccountVerification.verifyAccount(code)
            .thenAccept { result ->
                // Must run on main thread to send feedback
                MinecraftClient.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Text.literal("\u2714 ")
                                .formatted(Formatting.GREEN)
                                .append(
                                    Text.literal(result.message)
                                        .formatted(Formatting.GREEN)
                                )
                        )
                        source.sendFeedback(
                            Text.literal("Linked as: ")
                                .formatted(Formatting.GRAY)
                                .append(
                                    Text.literal(result.username ?: "")
                                        .formatted(Formatting.AQUA, Formatting.BOLD)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Text.literal("\u2718 ")
                                .formatted(Formatting.RED)
                                .append(
                                    Text.literal(result.message)
                                        .formatted(Formatting.RED)
                                )
                        )
                    }
                }
            }
    }

    private fun handleStatusCommand(source: FabricClientCommandSource) {
        if (AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("Account Status: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal("Linked")
                            .formatted(Formatting.GREEN, Formatting.BOLD)
                    )
            )
            source.sendFeedback(
                Text.literal("Username: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal(ConfigManager.config.linkedUsername)
                            .formatted(Formatting.AQUA)
                    )
            )
            source.sendFeedback(
                Text.literal("UUID: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal(ConfigManager.config.linkedUuid)
                            .formatted(Formatting.DARK_GRAY)
                    )
            )
            // Show token status
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Text.literal("Auth Token: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        if (hasToken) {
                            Text.literal("Valid")
                                .formatted(Formatting.GREEN)
                        } else {
                            Text.literal("Missing")
                                .formatted(Formatting.RED)
                        }
                    )
            )
        } else {
            source.sendFeedback(
                Text.literal("Account Status: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal("Not Linked")
                            .formatted(Formatting.RED)
                    )
            )
            source.sendFeedback(
                Text.literal("Use /dyetracker link <code> to link your account.")
                    .formatted(Formatting.YELLOW)
            )
        }
    }

    private fun handleUnlinkCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("No account is currently linked.")
                    .formatted(Formatting.YELLOW)
            )
            return
        }

        val username = ConfigManager.config.linkedUsername
        AccountVerification.unlink()

        source.sendFeedback(
            Text.literal("Account ")
                .formatted(Formatting.GRAY)
                .append(
                    Text.literal(username)
                        .formatted(Formatting.AQUA)
                )
                .append(
                    Text.literal(" has been unlinked.")
                        .formatted(Formatting.GRAY)
                )
        )
    }

    private fun showHelp(source: FabricClientCommandSource) {
        source.sendFeedback(
            Text.literal("DyeTracker Commands:")
                .formatted(Formatting.GOLD, Formatting.BOLD)
        )
        source.sendFeedback(
            Text.literal("  /dyetracker link <code>")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Link your account")
                        .formatted(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker status")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Show link status")
                        .formatted(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker unlink")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Unlink your account")
                        .formatted(Formatting.GRAY)
                )
        )
    }
}
