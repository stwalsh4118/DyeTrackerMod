package com.dyetracker.api

import com.dyetracker.data.ArchfiendDyeData
import com.dyetracker.data.CopperDyeData
import com.dyetracker.data.DarkPurpleDyeData
import com.dyetracker.data.DroppedDye
import com.dyetracker.data.FossilDyeData
import com.dyetracker.data.HollyDyeData
import com.dyetracker.data.MochaDyeData
import com.dyetracker.data.NyanzaDyeData
import com.dyetracker.data.TreasureDyeData
import com.dyetracker.data.MineshaftPity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for POST /api/v1/rng-data
 */
@Serializable
data class SyncRngDataRequest(
    // PBI 47 / PBI 63: slayerMeters and the dungeon / nucleus / experimentation meter fields removed —
    // the mod no longer captures or sends RNG-meter data.
    val mineshaftPity: MineshaftPity? = null,
    val archfiendDye: ArchfiendDyeData? = null,
    val copperDye: CopperDyeData? = null,
    val nyanzaDye: NyanzaDyeData? = null,
    val treasureDye: TreasureDyeData? = null,
    val fossilDye: FossilDyeData? = null,
    val darkPurpleDye: DarkPurpleDyeData? = null,
    val hollyDye: HollyDyeData? = null,
    val mochaDye: MochaDyeData? = null,
    val modTimestamp: Long
)

/**
 * Response from POST /api/v1/auth/start-verify
 */
@Serializable
data class StartVerifyResponse(
    val serverId: String
)

/**
 * Response from POST /api/v1/auth/complete-verify
 */
@Serializable
data class CompleteVerifyResponse(
    val success: Boolean,
    val uuid: String,
    val username: String,
    val token: String
)

/**
 * Error response from the API
 */
@Serializable
data class ApiErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)

/**
 * Response from GET /api/v1/auth/me
 */
@Serializable
data class AuthMeResponse(
    val authenticated: Boolean,
    val uuid: String,
    val username: String
)

/**
 * Response from POST /api/v1/rng-data
 */
@Serializable
data class SyncRngDataResponse(
    val success: Boolean,
    val uuid: String,
    val updatedAt: Long
)

/**
 * Request body for POST /api/v1/dye-collection
 */
@Serializable
data class SyncDyeCollectionRequest(
    val profileId: String,
    val dyes: List<DroppedDye>,
    val modTimestamp: Long
)

/**
 * Response from POST /api/v1/dye-collection
 */
@Serializable
data class SyncDyeCollectionResponse(
    val success: Boolean,
    val uuid: String,
    val syncedCount: Int
)

/**
 * Request body for POST /api/v1/rotation: the Vincent rotation captured from the "Dyes" screen.
 * [boosts] holds only the dyes whose lore carries a boost line (dyeId → multiplier).
 */
@Serializable
data class RotationPushRequest(
    val dyeIds: List<String>,
    val boosts: Map<String, Int>,
    val skyblockYear: Int,
    val capturedAt: Long
)

/**
 * Response from POST /api/v1/rotation: whether the server [stored] the rotation, and the
 * [skyblockYear] it is filed under.
 */
@Serializable
data class RotationPushResponse(
    val stored: Boolean = false,
    val skyblockYear: Int = 0
)

/**
 * A profile id + its human-readable name (`cute_name` in the backend JSON, e.g. "Mango").
 * Used both standalone in [PlayerProfilesResponse] and as the `profile` field of
 * [DyeProgressResponse].
 */
@Serializable
data class ProfileSummary(
    val id: String,
    @SerialName("cute_name") val cuteName: String,
    /** True for the profile currently selected in-game (Hypixel's `selected`). */
    val selected: Boolean = false
)

/**
 * One dye's progress entry from `GET /api/v1/player/:username/profile/:profileId/dyes`.
 * Decodes only the fields the in-game single-dye widget needs; the client's
 * `ignoreUnknownKeys` Json tolerates the rest. `progress` is null for untrackable dyes.
 * Optional/absent fields default so a missing key never fails decoding.
 */
@Serializable
data class DyeProgressEntry(
    val dyeId: String,
    val name: String,
    val color: String,
    val category: String,
    val progress: Double? = null,
    val trackable: Boolean = false,
    val formula: String? = null,
    val source: String? = null,
    val isVerified: Boolean = false
)

/**
 * Response from `GET /api/v1/player/:username/profile/:profileId/dyes` (public endpoint).
 * `modSyncTimestamp` is null when the player has no mod-synced data for the profile.
 */
@Serializable
data class DyeProgressResponse(
    val username: String,
    val profile: ProfileSummary,
    val dyes: List<DyeProgressEntry>,
    val modSyncTimestamp: Long? = null
)

/**
 * Response from `GET /api/v1/player/:username` (public endpoint): the player's SkyBlock
 * profiles, used to resolve a typed profile `cute_name` to the `profileId` the dyes endpoint
 * requires.
 */
@Serializable
data class PlayerProfilesResponse(
    val uuid: String,
    val username: String,
    val profiles: List<ProfileSummary>
)

/**
 * One owned dye from `GET /api/v1/player/:username/collection`: [count] of [dyeId] on
 * [profileId], first obtained at [obtainedAt] (epoch ms) when known. The JSON's `syncedAt` and
 * `metadata` are dropped by the client's `ignoreUnknownKeys` Json.
 */
@Serializable
data class CollectionEntry(
    val dyeId: String,
    val profileId: String = "",
    val count: Int = 1,
    val obtainedAt: Long? = null
)

/**
 * Response from `GET /api/v1/player/:username/collection` (public endpoint): the dyes the
 * player owns. Only linked players who synced from Vincent's Dye Guide have any; everyone else
 * gets an empty [dyes].
 */
@Serializable
data class CollectionResponse(
    val uuid: String = "",
    val username: String = "",
    val dyes: List<CollectionEntry> = emptyList()
)

/** How far a locked achievement is: [current] of [total]. */
@Serializable
data class AchievementProgress(val current: Int = 0, val total: Int = 0)

/**
 * One achievement from `GET /api/v1/player/:username/achievements`: its id, whether and when
 * ([unlockedAt], epoch ms) the player unlocked it, and its [progress] when it counts towards one.
 * [name], [description], [category] and [tier] come from the catalog and are null on an API that
 * doesn't send them yet; the menu then falls back ([com.dyetracker.menu.MenuAchievements]).
 */
@Serializable
data class PlayerAchievementEntry(
    val achievementId: String,
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val tier: String? = null,
    val secret: Boolean = false,
    val unlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val progress: AchievementProgress? = null
)

/**
 * Response from `GET /api/v1/player/:username/achievements?profileId=` (public endpoint): every
 * achievement with the player's state on that profile, and how many of [totalCount] are unlocked.
 * The JSON's `profile` is dropped by the client's `ignoreUnknownKeys` Json.
 */
@Serializable
data class AchievementsResponse(
    val uuid: String = "",
    val username: String = "",
    val achievements: List<PlayerAchievementEntry> = emptyList(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0
)
