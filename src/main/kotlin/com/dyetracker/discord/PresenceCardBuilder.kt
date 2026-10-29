package com.dyetracker.discord

import com.dyetracker.dyeprogress.DyeCatalog
import com.dyetracker.dyeprogress.rotationPercentText

/** Discord timestamps are epoch seconds; the SkyBlock session start is epoch ms. */
private const val MS_PER_SECOND = 1_000L

/** The hunt odds from [HUNT_COMPLETE_PERCENT] up. */
private const val HUNT_COMPLETE_TEXT = "100%"

/** What [rotationPercentText] prints for odds just under [HUNT_COMPLETE_PERCENT] (99.95 up). */
private const val ROUNDED_COMPLETE_TEXT = "100.0%"

/**
 * The hunt line's odds for a dye's [progress] (percent): `null` without odds, [HUNT_COMPLETE_TEXT]
 * from [HUNT_COMPLETE_PERCENT] up and for odds that round up to it, else the rotation line's
 * [rotationPercentText] (`42.0%`, `0.03%`, `0.0027%`).
 */
fun huntOddsText(progress: Double?): String? {
    if (progress == null) return null
    if (progress >= HUNT_COMPLETE_PERCENT) return HUNT_COMPLETE_TEXT
    val text = rotationPercentText(progress)
    return if (text == ROUNDED_COMPLETE_TEXT) HUNT_COMPLETE_TEXT else text
}

/** The website's art for [dyeId], e.g. `https://dyetracker.xyz/dyes/nadeshiko.webp`. */
fun dyeImageUrl(dyeId: String): String = "$DYETRACKER_SITE_URL$DYE_IMAGE_PATH$dyeId$DYE_IMAGE_EXTENSION"

/**
 * The card for [stats] and a SkyBlock session that began at [sessionStartMs] (epoch ms): the
 * DyeTracker logo, the `17/42 dyes · 23 dropped` line when owned, total and dropped are all known,
 * a [PROFILE_BUTTON_LABEL] button to the player's profile page on [DYETRACKER_SITE_URL] when the
 * uuid and profile id are known, and an elapsed timer from [sessionStartMs] when given.
 *
 * A non-blank [huntDyeId] adds the `Hunting Nadeshiko · 0.03%` line (no odds part when the dye
 * has none). When the dye is in [PresenceStats.dyes] (the API's catalog, which the website has art
 * for) its art from [dyeImageUrl] becomes the large image, with the logo and the profile name as
 * the small one; otherwise (stats not loaded yet, or a dye the website has no art for) the logo
 * stays the large image. The name comes from the stats, then [DyeCatalog], then the id itself.
 */
fun buildPresenceCard(stats: PresenceStats?, sessionStartMs: Long?, huntDyeId: String = ""): PresenceCard {
    val owned = stats?.owned
    val total = stats?.total
    val dropped = stats?.dropped
    val state = if (owned != null && total != null && dropped != null) "$owned/$total dyes · $dropped dropped" else null
    val buttons = if (stats != null && stats.uuid.isNotBlank() && stats.profileId.isNotBlank()) {
        listOf(PresenceButton(PROFILE_BUTTON_LABEL, "$DYETRACKER_SITE_URL/player/${stats.uuid}/profile/${stats.profileId}"))
    } else {
        emptyList()
    }
    val card = PresenceCard(
        details = null,
        state = state,
        largeImage = DISCORD_LOGO_ASSET,
        largeText = DISCORD_LOGO_TEXT,
        smallImage = null,
        smallText = null,
        startEpochSeconds = sessionStartMs?.div(MS_PER_SECOND),
        buttons = buttons,
    )
    if (huntDyeId.isBlank()) return card
    val entry = stats?.dyes?.firstOrNull { it.dyeId == huntDyeId }
    val name = entry?.name ?: DyeCatalog.all.firstOrNull { it.dyeId == huntDyeId }?.displayName ?: huntDyeId
    val odds = huntOddsText(entry?.progress)
    val details = if (odds != null) "$HUNT_PREFIX$name · $odds" else "$HUNT_PREFIX$name"
    if (entry == null) return card.copy(details = details)
    return card.copy(
        details = details,
        largeImage = dyeImageUrl(huntDyeId),
        largeText = name,
        smallImage = DISCORD_LOGO_ASSET,
        smallText = stats.profileName,
    )
}
