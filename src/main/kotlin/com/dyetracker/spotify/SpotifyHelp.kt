package com.dyetracker.spotify

/*
 * The `/dyetracker spotify help` setup guide (PBI 76, task 76-11): one list of segments per chat
 * line, each with a tone and, for the links, what a click does. Pure, so it is unit-tested without
 * a client; the command maps tones to chat colors and clicks to click events.
 */

/** How a [HelpSegment] is colored in chat. */
enum class HelpTone { HEADER, BODY, HINT, LINK }

/** What clicking a [HelpTone.LINK] segment does. */
sealed interface HelpClick {
    /** Open [url] in the browser. */
    data class OpenUrl(val url: String) : HelpClick

    /** Copy [text] to the clipboard. */
    data class Copy(val text: String) : HelpClick

    /** Put [command] in the chat box, unsent. */
    data class Suggest(val command: String) : HelpClick
}

/** One run of text in a help line, with its [tone] and an optional [click]. */
data class HelpSegment(val text: String, val tone: HelpTone, val click: HelpClick? = null)

/** The `clientid` command with its argument left for the player to paste. */
private const val CLIENT_ID_SUGGEST = "/dyetracker spotify clientid "
private const val LOGIN_COMMAND = "/dyetracker spotify login"

private fun body(text: String) = HelpSegment(text, HelpTone.BODY)
private fun hint(text: String) = HelpSegment(text, HelpTone.HINT)
private fun link(text: String, click: HelpClick) = HelpSegment("[$text]", HelpTone.LINK, click)

/** The setup steps, one inner list per chat line. */
val SETUP_HELP: List<List<HelpSegment>> = listOf(
    listOf(HelpSegment("Spotify widget setup (needs Spotify Premium):", HelpTone.HEADER)),
    listOf(
        body("1. Create an app at "),
        link("developer.spotify.com/dashboard", HelpClick.OpenUrl(SpotifyConstants.DASHBOARD_URL)),
    ),
    listOf(
        body("2. Redirect URI: "),
        link(SpotifyConstants.REGISTERED_REDIRECT_URI, HelpClick.Copy(SpotifyConstants.REGISTERED_REDIRECT_URI)),
        hint(" (click to copy)"),
    ),
    listOf(body("3. Tick Web API, save, open Settings and copy the Client ID.")),
    listOf(body("4. "), link("${CLIENT_ID_SUGGEST}<id>", HelpClick.Suggest(CLIENT_ID_SUGGEST))),
    listOf(body("5. "), link(LOGIN_COMMAND, HelpClick.Suggest(LOGIN_COMMAND))),
    listOf(hint("\"Spotify refused\"? Add your Spotify email under User Management in the app.")),
    listOf(
        hint(
            "Using a friend's Client ID? They add your Spotify email under User Management " +
                "(max ${SpotifyConstants.FRIEND_USER_LIMIT} people).",
        ),
    ),
)
