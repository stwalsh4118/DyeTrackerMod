package com.dyetracker.discord

/** Discord timestamps are epoch seconds; the SkyBlock session start is epoch ms. */
private const val MS_PER_SECOND = 1_000L

/**
 * The card for [stats] and a SkyBlock session that began at [sessionStartMs] (epoch ms): the
 * DyeTracker logo, the `17/42 dyes · 23 dropped` line when owned, total and dropped are all known,
 * a [PROFILE_BUTTON_LABEL] button to the player's profile page on [DYETRACKER_SITE_URL] when the
 * uuid and profile id are known, and an elapsed timer from [sessionStartMs] when given.
 */
fun buildPresenceCard(stats: PresenceStats?, sessionStartMs: Long?): PresenceCard {
    val owned = stats?.owned
    val total = stats?.total
    val dropped = stats?.dropped
    val state = if (owned != null && total != null && dropped != null) "$owned/$total dyes · $dropped dropped" else null
    val buttons = if (stats != null && stats.uuid.isNotBlank() && stats.profileId.isNotBlank()) {
        listOf(PresenceButton(PROFILE_BUTTON_LABEL, "$DYETRACKER_SITE_URL/player/${stats.uuid}/profile/${stats.profileId}"))
    } else {
        emptyList()
    }
    return PresenceCard(
        details = null,
        state = state,
        largeImage = DISCORD_LOGO_ASSET,
        largeText = DISCORD_LOGO_TEXT,
        smallImage = null,
        smallText = null,
        startEpochSeconds = sessionStartMs?.div(MS_PER_SECOND),
        buttons = buttons,
    )
}
