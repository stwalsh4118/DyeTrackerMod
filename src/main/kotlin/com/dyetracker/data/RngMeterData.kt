package com.dyetracker.data

/**
 * Types of slayer bosses in SkyBlock.
 */
enum class SlayerType {
    REVENANT,
    TARANTULA,
    SVEN,
    VOIDGLOOM,
    INFERNO,
    RIFTSTALKER
}

/**
 * Dungeon floors that have RNG meters.
 */
enum class DungeonFloor {
    M5,
    M7
}

/**
 * RNG meter data for a slayer boss.
 */
data class SlayerRngMeter(
    val slayerType: SlayerType,
    val storedXp: Long,
    val selectedItem: String? = null,
    val goalXp: Long? = null
)

/**
 * RNG meter data for a dungeon floor.
 */
data class DungeonRngMeter(
    val floor: DungeonFloor,
    val storedXp: Long,
    val selectedItem: String? = null,
    val goalXp: Long? = null
)

/**
 * RNG meter data for Crystal Hollows Nucleus.
 */
data class NucleusRngMeter(
    val storedXp: Long,
    val selectedItem: String? = null,
    val goalXp: Long? = null
)

/**
 * RNG meter data for Experimentation Table.
 */
data class ExperimentationRngMeter(
    val storedXp: Long,
    val selectedItem: String? = null,
    val goalXp: Long? = null
)

/**
 * Mineshaft pity counter (0-2000).
 */
data class MineshaftPity(
    val pityValue: Int
)

/**
 * Composite data class holding all RNG data for a player.
 */
data class PlayerRngData(
    val slayerMeters: Map<SlayerType, SlayerRngMeter> = emptyMap(),
    val dungeonMeters: Map<DungeonFloor, DungeonRngMeter> = emptyMap(),
    val nucleusMeter: NucleusRngMeter? = null,
    val experimentationMeter: ExperimentationRngMeter? = null,
    val mineshaftPity: MineshaftPity? = null
) {
    /**
     * Returns true if any RNG data has been captured.
     */
    fun hasData(): Boolean {
        return slayerMeters.isNotEmpty() ||
            dungeonMeters.isNotEmpty() ||
            nucleusMeter != null ||
            experimentationMeter != null ||
            mineshaftPity != null
    }
}
