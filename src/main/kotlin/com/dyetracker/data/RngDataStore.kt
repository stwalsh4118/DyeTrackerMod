package com.dyetracker.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe singleton object for storing captured RNG data in memory.
 * This store is updated by various event handlers and read by the show command.
 */
object RngDataStore {

    private val slayerMeters = ConcurrentHashMap<SlayerType, SlayerRngMeter>()
    private val dungeonMeters = ConcurrentHashMap<DungeonFloor, DungeonRngMeter>()

    @Volatile
    private var nucleusMeter: NucleusRngMeter? = null

    @Volatile
    private var experimentationMeter: ExperimentationRngMeter? = null

    @Volatile
    private var mineshaftPity: MineshaftPity? = null

    /**
     * Update the stored XP for a slayer type.
     * Preserves any existing selection data.
     */
    fun updateSlayerXp(slayerType: SlayerType, xp: Long) {
        slayerMeters.compute(slayerType) { _, existing ->
            if (existing != null) {
                existing.copy(storedXp = xp)
            } else {
                SlayerRngMeter(slayerType = slayerType, storedXp = xp)
            }
        }
    }

    /**
     * Update the selected item for a slayer type.
     * Preserves any existing XP data.
     */
    fun updateSlayerSelection(slayerType: SlayerType, item: String, goalXp: Long) {
        slayerMeters.compute(slayerType) { _, existing ->
            if (existing != null) {
                existing.copy(selectedItem = item, goalXp = goalXp)
            } else {
                SlayerRngMeter(slayerType = slayerType, storedXp = 0, selectedItem = item, goalXp = goalXp)
            }
        }
    }

    /**
     * Update the dungeon RNG meter for a floor.
     */
    fun updateDungeonMeter(floor: DungeonFloor, xp: Long, item: String?, goalXp: Long?) {
        dungeonMeters[floor] = DungeonRngMeter(
            floor = floor,
            storedXp = xp,
            selectedItem = item,
            goalXp = goalXp
        )
    }

    /**
     * Update the nucleus RNG meter.
     */
    fun updateNucleusMeter(xp: Long, item: String?, goalXp: Long?) {
        nucleusMeter = NucleusRngMeter(
            storedXp = xp,
            selectedItem = item,
            goalXp = goalXp
        )
    }

    /**
     * Update the experimentation table RNG meter.
     */
    fun updateExperimentationMeter(xp: Long, item: String?, goalXp: Long?) {
        experimentationMeter = ExperimentationRngMeter(
            storedXp = xp,
            selectedItem = item,
            goalXp = goalXp
        )
    }

    /**
     * Update the mineshaft pity value.
     */
    fun updateMineshaftPity(pity: Int) {
        mineshaftPity = MineshaftPity(pityValue = pity)
    }

    /**
     * Get an immutable snapshot of all RNG data.
     */
    fun getData(): PlayerRngData {
        return PlayerRngData(
            slayerMeters = slayerMeters.toMap(),
            dungeonMeters = dungeonMeters.toMap(),
            nucleusMeter = nucleusMeter,
            experimentationMeter = experimentationMeter,
            mineshaftPity = mineshaftPity
        )
    }

    /**
     * Clear all stored RNG data.
     */
    fun clear() {
        slayerMeters.clear()
        dungeonMeters.clear()
        nucleusMeter = null
        experimentationMeter = null
        mineshaftPity = null
    }
}
