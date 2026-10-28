package com.dyetracker.menu

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A one-shot "open the menu" request, set by `/dyetracker menu` and taken on the next client tick.
 *
 * The command can't open the menu itself: vanilla `ChatScreen.keyPressed` runs the command and
 * then closes the chat with `setScreen(null)` in the same call, which closes a screen the command
 * just opened. By the next tick the chat has closed.
 */
class MenuOpenRequest {

    private val pending = AtomicBoolean(false)

    /** Ask for the menu to open on the next tick. Several requests before that tick open it once. */
    fun request() {
        pending.set(true)
    }

    /** True when a request is pending, clearing it, so each request opens the menu once. */
    fun consume(): Boolean = pending.getAndSet(false)
}
