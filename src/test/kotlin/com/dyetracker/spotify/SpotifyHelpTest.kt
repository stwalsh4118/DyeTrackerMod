package com.dyetracker.spotify

import com.dyetracker.commands.DyeTrackerCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit coverage for the `/dyetracker spotify help` setup guide (task 76-11): its eight lines and
 * their text, and where each clickable part leads.
 */
class SpotifyHelpTest {

    @Test
    fun `setup help has eight lines and starts with a header`() {
        assertEquals(EXPECTED_LINES.size, SETUP_HELP.size)
        assertEquals(listOf(HelpTone.HEADER), SETUP_HELP[0].map { it.tone })
    }

    @Test
    fun `each line reads exactly as the guide`() {
        assertEquals(EXPECTED_LINES, SETUP_HELP.map { line -> line.joinToString("") { it.text } })
    }

    @Test
    fun `the dashboard link opens the dashboard`() {
        assertEquals(
            listOf(HelpSegment("[developer.spotify.com/dashboard]", HelpTone.LINK, HelpClick.OpenUrl(DASHBOARD_URL))),
            links(SETUP_HELP[1]),
        )
        assertEquals("https://developer.spotify.com/dashboard", DASHBOARD_URL)
    }

    @Test
    fun `the redirect URI link copies the registered redirect URI`() {
        val link = links(SETUP_HELP[2]).single()
        assertEquals(HelpClick.Copy(SpotifyConstants.REGISTERED_REDIRECT_URI), link.click)
        assertTrue(link.text.contains(SpotifyConstants.REGISTERED_REDIRECT_URI))
        assertEquals("[http://127.0.0.1:43821/callback]", link.text)
        assertEquals(HelpTone.HINT, SETUP_HELP[2].last().tone)
    }

    @Test
    fun `the command links fill the chat box`() {
        assertEquals(HelpClick.Suggest("/dyetracker spotify clientid "), links(SETUP_HELP[4]).single().click)
        assertEquals(HelpClick.Suggest("/dyetracker spotify login"), links(SETUP_HELP[5]).single().click)
    }

    @Test
    fun `every suggested command names a registered spotify subcommand`() {
        val suggests = SETUP_HELP.flatten().mapNotNull { it.click as? HelpClick.Suggest }
        assertEquals(2, suggests.size)
        for (suggest in suggests) {
            assertTrue(suggest.command.startsWith(SPOTIFY_PREFIX), suggest.command)
            val subcommand = suggest.command.removePrefix(SPOTIFY_PREFIX).trim().substringBefore(' ')
            assertTrue(subcommand in DyeTrackerCommands.SPOTIFY_SUBCOMMANDS, subcommand)
        }
    }

    @Test
    fun `only link segments are clickable and every link has a click`() {
        for (segment in SETUP_HELP.flatten()) {
            assertEquals(segment.tone == HelpTone.LINK, segment.click != null, segment.text)
        }
    }

    @Test
    fun `the subcommand list starts with help`() {
        assertEquals("help", DyeTrackerCommands.SPOTIFY_SUBCOMMANDS.first())
    }

    private fun links(line: List<HelpSegment>): List<HelpSegment> = line.filter { it.tone == HelpTone.LINK }

    private companion object {
        const val SPOTIFY_PREFIX = "/dyetracker spotify "

        val DASHBOARD_URL = SpotifyConstants.DASHBOARD_URL

        val EXPECTED_LINES = listOf(
            "Spotify widget setup (needs Spotify Premium):",
            "1. Create an app at [developer.spotify.com/dashboard]",
            "2. Redirect URI: [${SpotifyConstants.REGISTERED_REDIRECT_URI}] (click to copy)",
            "3. Tick Web API, save, open Settings and copy the Client ID.",
            "4. [/dyetracker spotify clientid <id>]",
            "5. [/dyetracker spotify login]",
            "\"Spotify refused\"? Add your Spotify email under User Management in the app.",
            "Using a friend's Client ID? They add your Spotify email under User Management " +
                "(max ${SpotifyConstants.FRIEND_USER_LIMIT} people).",
        )
    }
}
