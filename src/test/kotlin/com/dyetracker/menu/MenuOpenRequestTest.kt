package com.dyetracker.menu

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unit coverage for [MenuOpenRequest]: the one-shot flag `/dyetracker menu` sets for the next tick. */
class MenuOpenRequestTest {

    @Test
    fun `nothing to consume before a request`() {
        val request = MenuOpenRequest()

        assertFalse(request.consume())
    }

    @Test
    fun `a request is consumed once`() {
        val request = MenuOpenRequest()

        request.request()

        assertTrue(request.consume())
        assertFalse(request.consume())
    }

    @Test
    fun `two requests before a tick open the menu once`() {
        val request = MenuOpenRequest()

        request.request()
        request.request()

        assertTrue(request.consume())
        assertFalse(request.consume())
    }

    @Test
    fun `a new request after a consume is consumed again`() {
        val request = MenuOpenRequest()

        request.request()
        request.consume()
        request.request()

        assertTrue(request.consume())
    }
}
