package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.DroppedDye
import com.dyetracker.data.RngDataStore
import com.dyetracker.data.VisitorEntry
import com.dyetracker.rotation.RotationPusher
import com.dyetracker.sync.SyncManager
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

/**
 * Handles inventory screen events for the screens the mod captures (Vincent, rotation, commissions,
 * Visitor's Logbook, Brewing Stand).
 */
object InventoryHandler {

    // Delay in ticks before scanning inventory (allows GUI to fully load)
    private const val SCAN_DELAY_TICKS = 5

    // How often to re-scan a paginated GUI (Dye Compendium, Visitor's Logbook) for page changes (in ticks)
    private const val COMPENDIUM_SCAN_INTERVAL_TICKS = 10

    // A container screen's handler lists the player's main inventory and hotbar after the
    // container's own slots (PBI 66: only the Brewing Stand's own slots are watched)
    private const val PLAYER_INVENTORY_SLOT_COUNT = 36

    // Accumulated dyes across all pages while the Dye Compendium is open
    private val accumulatedDyes = mutableMapOf<String, DroppedDye>()

    // Dyes shown at 0 owned while the Dye Compendium is open
    private val zeroOwnedDyes = mutableSetOf<String>()

    // Accumulated visitors (de-duped by name) across all pages while the Visitor's Logbook is open (PBI 42)
    private val accumulatedVisitors = mutableMapOf<String, VisitorEntry>()

    /**
     * Register the screen event listener.
     */
    fun register() {
        ScreenEvents.AFTER_INIT.register { client, screen, scaledWidth, scaledHeight ->
            onScreenInit(client, screen)
        }
        DyeTrackerMod.info("InventoryHandler registered")
    }

    private fun onScreenInit(client: Minecraft, screen: Screen) {
        if (screen !is AbstractContainerScreen<*>) {
            return
        }

        val title = screen.title.string
        val inventoryType = InventoryUtils.detectInventoryType(title) ?: return

        DyeTrackerMod.debug("Detected inventory: {} ({})", title, inventoryType)

        when (inventoryType) {
            is InventoryType.VincentDyeCollection ->
                // Dye Compendium is paginated — scan continuously and accumulate
                scheduleCompendiumScan(client, screen)
            is InventoryType.VisitorLogbook ->
                // Visitor's Logbook is paginated — scan continuously and accumulate (PBI 42)
                scheduleLogbookScan(client, screen)
            is InventoryType.BrewingStand ->
                // Brewing Stand: watch the stand's slots every tick for potions leaving it (PBI 66)
                scheduleBrewingWatch(screen)
            else ->
                // Other inventory types: one-shot scan after delay
                scheduleScan(client, screen, inventoryType)
        }
    }

    private fun scheduleScan(client: Minecraft, screen: AbstractContainerScreen<*>, inventoryType: InventoryType) {
        var ticksRemaining = SCAN_DELAY_TICKS
        var processed = false

        ScreenEvents.afterTick(screen).register { _ ->
            if (processed) return@register
            ticksRemaining--
            if (ticksRemaining <= 0) {
                processed = true
                processInventory(screen, inventoryType)
            }
        }
    }

    private fun processInventory(screen: AbstractContainerScreen<*>, inventoryType: InventoryType) {
        when (inventoryType) {
            is InventoryType.CommissionMilestones -> processCommissionMilestones(screen)
            is InventoryType.VincentDyeRotation -> processDyeRotation(screen)
            is InventoryType.VincentDyeCollection -> {} // Handled by scheduleCompendiumScan
            is InventoryType.VisitorLogbook -> {} // PBI 42: paginated capture wired in 42-5 (scheduleLogbookScan)
            is InventoryType.BrewingStand -> {} // PBI 66: watched every tick by scheduleBrewingWatch
        }
    }

    /**
     * Process the Vincent "Dyes" current-rotation screen: parse the container's dye slots into a
     * [com.dyetracker.data.DyeRotation], persist it (last-write-wins) and notify the player. Skips
     * persistence when no dyes were parsed so a half-loaded or mis-identified screen never clobbers
     * the last-known rotation.
     */
    private fun processDyeRotation(screen: AbstractContainerScreen<*>) {
        val player = Minecraft.getInstance().player ?: return
        val rotation = InventoryUtils.extractDyeRotation(screen.menu.slots, player.inventory)

        if (rotation.dyeIds.isEmpty()) {
            DyeTrackerMod.debug("Dyes screen detected but no rotation dyes parsed; not persisting")
            return
        }

        ConfigManager.updateDyeRotation(rotation)
        RotationPusher.push(rotation)
        DyeTrackerMod.info("Captured dye rotation ({} dyes): {}", rotation.dyeIds.size, rotation.dyeIds)

        player.sendSystemMessage(
            Component.literal("Captured current dye rotation (${rotation.dyeIds.size} dyes)")
                .withStyle(ChatFormatting.GREEN)
        )
    }

    /**
     * Process a Commission Milestones inventory to capture the total commission count for Nyanza
     * Dye. Scans for the "Milestone I Rewards" item and parses the absolute completed count from
     * its lore (e.g. "208/5"). Milestone I is used because its threshold never changes, so the
     * count is shown stably even for maxed players — unlike the dynamic "Commission Milestones"
     * item in the parent Commissions GUI, which drops the count once every tier is complete.
     */
    private fun processCommissionMilestones(screen: AbstractContainerScreen<*>) {
        val handler = screen.menu

        for (slot in handler.slots) {
            val stack = slot.item
            if (stack.isEmpty) continue

            val count = InventoryUtils.parseCommissionMilestoneCount(stack)
            if (count != null) {
                RngDataStore.updateCommissionsCompleted(count)
                DyeTrackerMod.debug("Updated commission milestone count: {}", count)
                return
            }
        }

        DyeTrackerMod.debug("Milestone I Rewards item not found in Commission Milestones GUI")
    }

    /**
     * Schedule continuous scanning of the Dye Compendium.
     * Scans every COMPENDIUM_SCAN_INTERVAL_TICKS ticks to pick up page changes,
     * accumulates dyes across pages, and finalizes when the screen closes.
     */
    private fun scheduleCompendiumScan(client: Minecraft, screen: AbstractContainerScreen<*>) {
        accumulatedDyes.clear()
        zeroOwnedDyes.clear()
        var ticksSinceLastScan = SCAN_DELAY_TICKS // Start with initial delay worth of ticks

        // Periodic scan while screen is open
        ScreenEvents.afterTick(screen).register { _ ->
            ticksSinceLastScan++
            if (ticksSinceLastScan >= COMPENDIUM_SCAN_INTERVAL_TICKS) {
                ticksSinceLastScan = 0
                scanCompendiumPage(screen)
            }
        }

        // Finalize when screen closes
        ScreenEvents.remove(screen).register { _ ->
            finalizeCompendium()
        }
    }

    /**
     * Scan the current page of the Dye Compendium and accumulate obtained dyes, plus the dyes it
     * shows at 0 owned.
     */
    private fun scanCompendiumPage(screen: AbstractContainerScreen<*>) {
        val handler = screen.menu
        val pageDyes = InventoryUtils.extractDyeCollection(handler.slots)

        for (dye in pageDyes) {
            if (!accumulatedDyes.containsKey(dye.dyeId)) {
                accumulatedDyes[dye.dyeId] = dye
                DyeTrackerMod.debug("Found obtained dye: {}", dye.dyeId)
            }
        }
        zeroOwnedDyes.addAll(InventoryUtils.extractZeroOwnedDyeIds(handler.slots))
    }

    /**
     * Called when the Dye Compendium screen closes.
     * Applies what the screen showed to the existing collection and pushes to the data store. Dyes
     * shown at 0 are queued for the next sync, so their rows are deleted on the server too.
     */
    private fun finalizeCompendium() {
        if (accumulatedDyes.isEmpty() && zeroOwnedDyes.isEmpty()) {
            DyeTrackerMod.debug("No dyes found in Dye Compendium")
            return
        }

        val existing = RngDataStore.getDyeCollection()?.dyes.orEmpty()
        val updated = InventoryUtils.applyDyeGuideScan(existing, accumulatedDyes, zeroOwnedDyes)

        // Queued before the update, which is what schedules the sync
        SyncManager.queueDyeRemovals(zeroOwnedDyes - accumulatedDyes.keys)
        RngDataStore.updateDyeCollection(updated)
        DyeTrackerMod.info(
            "Dye Compendium: {} new dyes this session, {} shown at 0, {} total in collection",
            accumulatedDyes.size, zeroOwnedDyes.size, updated.size
        )
        accumulatedDyes.clear()
        zeroOwnedDyes.clear()
    }

    /**
     * Watch the Brewing Stand's own slots every tick while it is open (PBI 66). A new
     * [BrewingStandTracker] per opened screen compares each tick's slot names with the last and
     * reports the potions that left the stand; each one counts toward Mocha Dye. The player's
     * inventory slots are left out, so a potion moved into the inventory leaves the stand's slot
     * empty and is counted once.
     */
    private fun scheduleBrewingWatch(screen: AbstractContainerScreen<*>) {
        val tracker = BrewingStandTracker()

        ScreenEvents.afterTick(screen).register { _ ->
            val slots = screen.menu.slots
            val standSlotCount = slots.size - PLAYER_INVENTORY_SLOT_COUNT
            val slotNames = mutableMapOf<Int, String?>()
            for (index in 0 until standSlotCount) {
                val stack = slots[index].item
                slotNames[index] = if (stack.isEmpty) null else InventoryUtils.stripFormatting(stack.hoverName.string)
            }

            for (tier in tracker.observe(slotNames)) {
                RngDataStore.incrementBrewedPotion(tier)
                DyeTrackerMod.debug("Brewed potion taken out of the Brewing Stand: tier {}", tier)
            }
        }
    }

    /**
     * Schedule continuous scanning of the Visitor's Logbook (PBI 42).
     * Mirrors [scheduleCompendiumScan]: scans every [COMPENDIUM_SCAN_INTERVAL_TICKS] ticks to pick
     * up page changes, accumulates visitors de-duped by name while this screen is open, and on close
     * merges them into the persisted visitor union (see [finalizeLogbook]).
     */
    private fun scheduleLogbookScan(client: Minecraft, screen: AbstractContainerScreen<*>) {
        accumulatedVisitors.clear()
        var ticksSinceLastScan = SCAN_DELAY_TICKS // Start with initial delay worth of ticks

        // Periodic scan while screen is open
        ScreenEvents.afterTick(screen).register { _ ->
            ticksSinceLastScan++
            if (ticksSinceLastScan >= COMPENDIUM_SCAN_INTERVAL_TICKS) {
                ticksSinceLastScan = 0
                scanLogbookPage(screen)
            }
        }

        // Finalize when screen closes
        ScreenEvents.remove(screen).register { _ ->
            finalizeLogbook()
        }
    }

    /**
     * Scan the current page of the Visitor's Logbook and accumulate visitors de-duped by name
     * (latest "Times Visited" value wins) via the pure [InventoryUtils.accumulateVisitors].
     */
    private fun scanLogbookPage(screen: AbstractContainerScreen<*>) {
        val pageEntries = InventoryUtils.extractVisitorEntriesFromSlots(screen.menu.slots)
        if (pageEntries.isEmpty()) return

        // accumulateVisitors returns a fresh merged map (not an alias of the field), so rebuilding
        // the field from it via clear()+putAll() cannot drop previously-accumulated visitors.
        val merged = InventoryUtils.accumulateVisitors(accumulatedVisitors, pageEntries)
        accumulatedVisitors.clear()
        accumulatedVisitors.putAll(merged)
        DyeTrackerMod.debug(
            "Logbook page scanned: {} visitors this page, {} accumulated",
            pageEntries.size, accumulatedVisitors.size
        )
    }

    /**
     * Called when the Visitor's Logbook screen closes. Merges the visitors accumulated while this
     * screen was open into the persisted visitor union via [RngDataStore.mergeVisitorsSeen], which
     * re-derives the per-tier Copper snapshot from the full union.
     *
     * Crucially this is a MERGE, not a full replace: Hypixel re-creates the Logbook screen on each
     * page-turn, so each page closes and finalizes separately. Merging by visitor name accumulates
     * the per-tier totals across all pages (and across re-opens/restarts) without double-counting,
     * where a full replace would keep only the last page viewed. If nothing was accumulated (e.g. a
     * mis-identified or half-loaded screen), the merge is a no-op — never clobber prior data
     * (mirrors [finalizeCompendium]'s empty guard).
     */
    private fun finalizeLogbook() {
        if (accumulatedVisitors.isEmpty()) {
            DyeTrackerMod.debug("No visitors found in Visitor's Logbook; not writing snapshot")
            return
        }

        RngDataStore.mergeVisitorsSeen(accumulatedVisitors.toMap())
        DyeTrackerMod.info(
            "Visitor's Logbook: merged {} visitors from this view into the seen union",
            accumulatedVisitors.size
        )
        accumulatedVisitors.clear()
    }
}
