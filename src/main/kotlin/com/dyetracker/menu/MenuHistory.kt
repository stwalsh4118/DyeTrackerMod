package com.dyetracker.menu

import com.dyetracker.api.HistoryDyeGain
import com.dyetracker.api.HistoryResponse
import com.dyetracker.dyeprogress.rotationPercentText

/** A rolling period the History tab shows gains over: [apiValue] is the route's `period`, [label] the pill's text. */
enum class HistoryPeriod(val apiValue: String, val label: String) {
    HOUR("1h", "1h"),
    DAY("24h", "24h"),
    WEEK("7d", "7d"),
    MONTH("30d", "30d"),
    ;

    companion object {
        /** The period the History tab opens on. */
        val DEFAULT = DAY
    }
}

/** A row's chance when the dye has none. */
private const val NO_CHANCE_TEXT = "—"

/** Between a row's dye name and its work. */
private const val ROW_SEPARATOR = " · "

/** Between the profile id, the period and the dye in a [historyKey] or [historySeriesKey]. */
private const val KEY_SEPARATOR = "|"

private const val NOT_LINKED_TEXT = "Link the mod to start recording history"
private const val NO_HISTORY_TEXT = "No history yet. Snapshots are saved while your dyes are being viewed."
private const val NOTHING_GAINED_PREFIX = "Nothing gained in the last "
private const val NOTHING_GAINED_SUFFIX = "."

/** A row's chance: `—` without one, else [rotationPercentText] (tiny chances keep 2 significant digits). */
fun historyChanceText(chance: Double?): String = chance?.let(::rotationPercentText) ?: NO_CHANCE_TEXT

/**
 * What the History tab says instead of its list for [response] over [period]: the link hint for an
 * unlinked player, a no-history-yet hint without any snapshot, `Nothing gained in the last 24h.`
 * without a snapshot in the period or without any dye work in it; `null` when the list shows.
 */
fun historyStateMessage(response: HistoryResponse, period: HistoryPeriod): String? = when {
    !response.linked -> NOT_LINKED_TEXT
    response.to == null -> NO_HISTORY_TEXT
    response.snapshotCount == 0 || response.dyes.isEmpty() -> "$NOTHING_GAINED_PREFIX${period.label}$NOTHING_GAINED_SUFFIX"
    else -> null
}

/** A History row's left text (`Bone Dye · 1,000 skeletons`) and right-aligned chance ([historyChanceText]). */
fun historyRowText(gain: HistoryDyeGain): Pair<String, String> =
    "${gain.name}$ROW_SEPARATOR${gain.work}" to historyChanceText(gain.chance)

/** The History fetch's and [HISTORY_CACHE]'s key for [profileId] over [period], e.g. `p1|24h`. */
fun historyKey(profileId: String, period: HistoryPeriod): String = "$profileId$KEY_SEPARATOR${period.apiValue}"

/** The History chart fetch's and [HISTORY_SERIES_CACHE]'s key for [dyeId] on [profileId] over [period], e.g. `p1|24h|bone`. */
fun historySeriesKey(profileId: String, period: HistoryPeriod, dyeId: String): String =
    "${historyKey(profileId, period)}$KEY_SEPARATOR$dyeId"

/**
 * The History row whose chart shows: the one for [selectedDyeId] while it is listed in [gains],
 * else the first row; `null` without rows.
 */
fun historySelection(gains: List<HistoryDyeGain>, selectedDyeId: String?): HistoryDyeGain? =
    gains.firstOrNull { it.dyeId == selectedDyeId } ?: gains.firstOrNull()
