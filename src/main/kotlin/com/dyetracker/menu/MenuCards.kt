package com.dyetracker.menu

import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.dyeprogress.DyeProgressWidgetView

/** The one status badge in a menu card's top-right corner; labels match the website's `DyeCard`. */
enum class CardBadge(val label: String) {
    MOD("MOD"),
    MOD_ONLY("MOD ONLY"),
    ESTIMATED("ESTIMATED"),
    NOT_TRACKABLE("NOT TRACKABLE"),
}

/**
 * Pure text logic for a menu dye card, mirroring the website's `DyeCard`
 * (`packages/web/src/components/DyeCard.tsx`): which badge to show, the bottom-row label and
 * value, the category label, and fitting text into a pixel width.
 */
object MenuCards {
    private const val PROGRESS_LABEL = "Progress"
    private const val STATUS_LABEL = "Status"
    private const val MOD_ONLY_VALUE = "Sync via mod"
    private const val NOT_TRACKABLE_VALUE = "Not trackable"
    private const val CATEGORY_SEPARATOR = '_'
    private const val CATEGORY_JOINER = " "
    private const val ELLIPSIS = "…"

    /** The card's badge, in the website's order: verified, then mod-only, then estimated. */
    fun badgeFor(entry: DyeProgressEntry): CardBadge = when {
        entry.isVerified -> CardBadge.MOD
        entry.trackable && entry.progress == null -> CardBadge.MOD_ONLY
        entry.trackable -> CardBadge.ESTIMATED
        else -> CardBadge.NOT_TRACKABLE
    }

    /** `Progress` when the card shows a percent, else `Status`. */
    fun statusLabel(entry: DyeProgressEntry): String =
        if (entry.trackable && entry.progress != null) PROGRESS_LABEL else STATUS_LABEL

    /** The bottom-row value: a hint for mod-only and untrackable dyes, else the percent. */
    fun valueText(entry: DyeProgressEntry): String = when (badgeFor(entry)) {
        CardBadge.MOD_ONLY -> MOD_ONLY_VALUE
        CardBadge.NOT_TRACKABLE -> NOT_TRACKABLE_VALUE
        else -> DyeProgressWidgetView.formatProgress(entry.progress)
    }

    /** `mob_drop` → `Mob Drop`: split on `_` and capitalise each word, like the website's card. */
    fun formatCategory(category: String): String =
        category.split(CATEGORY_SEPARATOR).joinToString(CATEGORY_JOINER) { word -> word.replaceFirstChar { it.uppercase() } }

    /**
     * [text] if it fits in [maxWidth] (as measured by [measure]); else the longest prefix plus `…`
     * that fits; `""` when not even `…` fits. Never cuts a surrogate pair (one code point) in half.
     */
    fun fitText(text: String, maxWidth: Int, measure: (String) -> Int): String {
        if (measure(text) <= maxWidth) return text
        for (length in text.length - 1 downTo 0) {
            if (length > 0 && Character.isHighSurrogate(text[length - 1])) continue
            val candidate = text.substring(0, length) + ELLIPSIS
            if (measure(candidate) <= maxWidth) return candidate
        }
        return ""
    }
}
