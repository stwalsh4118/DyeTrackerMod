package com.dyetracker.menu

/** Whether a group of HUD widgets is shown: none exist, all shown, all hidden, or some of each. */
enum class ToggleState { NONE, ON, OFF, MIXED }

/** The group state of widgets whose visibility flags are [visible]. */
fun aggregate(visible: List<Boolean>): ToggleState = when {
    visible.isEmpty() -> ToggleState.NONE
    visible.all { it } -> ToggleState.ON
    visible.none { it } -> ToggleState.OFF
    else -> ToggleState.MIXED
}

/**
 * The visibility a click on a group's toggle gives every widget in it: [ToggleState.ON] hides
 * them all, [ToggleState.OFF] and [ToggleState.MIXED] show them all; `null` for
 * [ToggleState.NONE], where there is nothing to switch.
 */
fun targetVisibility(state: ToggleState): Boolean? = when (state) {
    ToggleState.NONE -> null
    ToggleState.ON -> false
    ToggleState.OFF, ToggleState.MIXED -> true
}

/** The toggle pill's text for [state]. */
fun toggleLabel(state: ToggleState): String = when (state) {
    ToggleState.ON -> "On"
    ToggleState.OFF -> "Off"
    ToggleState.MIXED -> "Mixed"
    ToggleState.NONE -> "None"
}

/**
 * A two-click confirm (the Settings tab's Unlink): the first [click] arms it, and a second click
 * at most [windowMs] later confirms and disarms it. A click after the window arms it again.
 * Times are from one clock (e.g. `Util.getMillis`).
 */
class ConfirmClick(private val windowMs: Long = MenuLayout.UNLINK_CONFIRM_MS) {
    /** When the first click armed the confirm; `null` while disarmed. */
    private var armedAtMs: Long? = null

    /** `true` when this click confirms; `false` when it only arms (or re-arms) the confirm. */
    fun click(nowMs: Long): Boolean {
        if (isArmed(nowMs)) {
            armedAtMs = null
            return true
        }
        armedAtMs = nowMs
        return false
    }

    /** Whether a click at [nowMs] would confirm. */
    fun isArmed(nowMs: Long): Boolean {
        val armedAt = armedAtMs ?: return false
        return nowMs - armedAt in 0..windowMs
    }
}

private const val NEVER_SYNCED_TEXT = "Never synced"
private const val LAST_SYNC_PREFIX = "Last sync: "

/** What [formatAge] puts before the age. */
private const val AGE_PREFIX = "updated "

/**
 * The Settings tab's sync detail: `Never synced` when [lastSyncMs] is `0` (no sync this game
 * session), else `Last sync: ` and the age from [formatAge], e.g. `Last sync: 3m ago`.
 */
fun lastSyncText(nowMs: Long, lastSyncMs: Long): String =
    if (lastSyncMs == 0L) NEVER_SYNCED_TEXT else LAST_SYNC_PREFIX + formatAge(nowMs, lastSyncMs).removePrefix(AGE_PREFIX)
