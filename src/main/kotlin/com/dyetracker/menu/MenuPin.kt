package com.dyetracker.menu

import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.ProfileSummary
import com.dyetracker.dyeprogress.DyeProgressWidgetConfig

/** What a click on a menu dye card does: pin the dye to the HUD, or say why it doesn't. */
sealed class PinDecision {
    /** A HUD widget for this dye and profile already exists. */
    data object AlreadyPinned : PinDecision()

    /** The dye has no bundled sprite, so a HUD widget can't show it. */
    data object NotPinnable : PinDecision()

    /** Add a HUD widget for [dyeId] on the profile [profileId] (shown as [profileName]). */
    data class Add(val dyeId: String, val profileId: String, val profileName: String) : PinDecision()
}

/**
 * Whether clicking [entry]'s card, for [profile], pins it: [PinDecision.NotPinnable] when
 * [hasSprite] is false for the dye, [PinDecision.AlreadyPinned] when [existing] has a widget for
 * the same dye and profile, else [PinDecision.Add] with the profile's id and cute name.
 */
fun pinDecision(
    existing: List<DyeProgressWidgetConfig>,
    entry: DyeProgressEntry,
    profile: ProfileSummary,
    hasSprite: (String) -> Boolean,
): PinDecision = when {
    !hasSprite(entry.dyeId) -> PinDecision.NotPinnable
    existing.any { it.dyeId == entry.dyeId && it.profileId == profile.id } -> PinDecision.AlreadyPinned
    else -> PinDecision.Add(entry.dyeId, profile.id, profile.cuteName)
}
