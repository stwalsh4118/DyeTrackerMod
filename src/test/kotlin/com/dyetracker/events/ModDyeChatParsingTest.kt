package com.dyetracker.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the pure Fossil-excavation and Dark-Auction chat parsing in [ChatEventHandler]
 * (PBI 66): [ChatEventHandler.chatLines], [ChatEventHandler.isExcavationComplete] and
 * [ChatEventHandler.isDarkAuctionTransfer].
 *
 * Text is passed already-stripped of Minecraft formatting codes (§X), as `onChatMessage` supplies
 * it. Mirrors the pure-helper structure of [TreasureCatchParsingTest].
 */
class ModDyeChatParsingTest {

    @Test
    fun `bare excavation complete line is detected`() {
        assertTrue(ChatEventHandler.isExcavationComplete("EXCAVATION COMPLETE"))
    }

    @Test
    fun `excavation complete line with surrounding whitespace is detected`() {
        assertTrue(ChatEventHandler.isExcavationComplete("  EXCAVATION COMPLETE  "))
    }

    @Test
    fun `multi-line excavation reward block is detected`() {
        val block = "\nEXCAVATION COMPLETE\n\nREWARDS\n  Enchanted Tungsten x8\n  Fossil Essence x10\n"
        assertTrue(ChatEventHandler.isExcavationComplete(block))
    }

    @Test
    fun `stacked excavation complete line is detected (chat-mod repeat suffix ignored)`() {
        assertTrue(ChatEventHandler.isExcavationComplete("EXCAVATION COMPLETE (2)"))
    }

    @Test
    fun `player chat echoing excavation complete is rejected`() {
        assertFalse(ChatEventHandler.isExcavationComplete("Steve: EXCAVATION COMPLETE"))
    }

    @Test
    fun `channel-prefixed player chat echoing excavation complete is rejected`() {
        assertFalse(ChatEventHandler.isExcavationComplete("Party > [MVP+] Steve: EXCAVATION COMPLETE"))
    }

    @Test
    fun `rewards line alone is not an excavation`() {
        assertFalse(ChatEventHandler.isExcavationComplete("REWARDS"))
    }

    @Test
    fun `dark auction transfer with three dots is detected`() {
        assertTrue(ChatEventHandler.isDarkAuctionTransfer("Transferring to Dark Auction..."))
    }

    @Test
    fun `dark auction transfer with an ellipsis character is detected`() {
        assertTrue(ChatEventHandler.isDarkAuctionTransfer("Transferring to Dark Auction…"))
    }

    @Test
    fun `stacked dark auction transfer is detected (chat-mod repeat suffix ignored)`() {
        assertTrue(ChatEventHandler.isDarkAuctionTransfer("Transferring to Dark Auction... (3)"))
    }

    @Test
    fun `dark auction sign-up line is not a transfer`() {
        assertFalse(ChatEventHandler.isDarkAuctionTransfer("Signing up to the Dark Auction..."))
    }

    @Test
    fun `Sirius sign-up confirmation is not a transfer`() {
        assertFalse(ChatEventHandler.isDarkAuctionTransfer("[NPC] Sirius: You signed up for the Auction!"))
    }

    @Test
    fun `server-send line is not a transfer`() {
        assertFalse(ChatEventHandler.isDarkAuctionTransfer("Sending to server mini6T..."))
    }

    @Test
    fun `player chat echoing the transfer line is rejected`() {
        assertFalse(ChatEventHandler.isDarkAuctionTransfer("Steve: Transferring to Dark Auction..."))
    }

    @Test
    fun `chatLines strips the stack suffix`() {
        assertEquals(listOf("COMMON! x"), ChatEventHandler.chatLines("COMMON! x (12)"))
    }
}
