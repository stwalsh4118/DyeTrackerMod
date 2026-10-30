# DyeTracker Mod

A Fabric mod for Minecraft that integrates with the [DyeTracker](https://dyetracker.xyz) website to track your Hypixel SkyBlock dye collection progress.

## Features

- Link your Minecraft account to DyeTracker securely via Mojang session verification
- Automatically sync your player stats with the website
- No API keys or passwords required - uses Minecraft's built-in authentication
- Discord Rich Presence (26.x, opt-in): show your collection, total dyes dropped and the dye you're hunting on your Discord profile while you play SkyBlock.
- Spotify now-playing widget (Minecraft 26.x): your current song on the HUD, colored by the album art

### Dyes the mod counts for you

Some dyes depend on things the Hypixel API doesn't record. For these, the mod counts the attempts while you play and syncs the totals to your DyeTracker profile:

| Dye | What the mod counts |
|-----|---------------------|
| Fossil | Suspicious Scraps you excavate (one per `EXCAVATION COMPLETE` message) |
| Dark Purple | Dark Auctions you attend (one per `Transferring to Dark Auction...` message) |
| Holly | Gifts you give or open (one per gift reward message, by rarity and reward) |
| Mocha | Potions you take out of a Brewing Stand after they finish brewing, by tier (I to VIII) |

- **Counting starts when you install the mod.** Excavations, auctions, gifts and potions from before that can't be counted.
- Gift messages look the same for Red, Green and White Gifts, so the website estimates how many of your gifts were red from the rewards you got.
- Run `/dyetracker show` to see your current counts in game.

## Requirements

- Minecraft **1.21.10** or **1.21.11** (a separate mod jar is published for each — see [Installation](#installation))
- [Fabric Loader](https://fabricmc.net/) 0.16.0+
- [Fabric API](https://modrinth.com/mod/fabric-api) (the build matching your Minecraft version)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.12.3+

### Minecraft versions

New features ship for Minecraft **26.1.2**, **26.2** and **26.3**. The **1.21.10** and **1.21.11** builds get bug fixes only.

## Installation

1. Install Fabric Loader for your Minecraft version (1.21.10 or 1.21.11).
2. Download the required dependencies (Fabric API + Fabric Language Kotlin) for your version.
3. Download the DyeTracker mod jar that matches your Minecraft version from [Releases](https://github.com/stwalsh4118/DyeTrackerMod/releases):
   - For MC 1.21.10 → `dyetracker-<modver>+1.21.10.jar`
   - For MC 1.21.11 → `dyetracker-<modver>+1.21.11.jar`
   The version suffix after the `+` indicates which Minecraft version the jar targets. Pick the one that matches your game.
4. Place all `.jar` files in your `.minecraft/mods` folder.
5. Launch Minecraft.

## Usage

### Linking Your Account

1. Go to [DyeTracker](https://dyetracker.xyz) and click "Link Account"
2. You'll receive an 8-character code
3. In Minecraft, run: `/dyetracker link <code>`
4. Your account will be verified and linked automatically

### Commands

| Command | Description |
|---------|-------------|
| `/dyetracker link <code>` | Link your account using a code from the website |
| `/dyetracker status` | Check your current link status |
| `/dyetracker unlink` | Unlink your account |
| `/dyetracker menu` | Open the in-game DyeTracker menu (Minecraft 26.1.2, 26.2 and 26.3 only) |
| `/dyetracker discord <on\|off>` | Turn [Discord Rich Presence](#discord-rich-presence) on or off; `/dyetracker discord` on its own shows whether it's on (Minecraft 26.1.2, 26.2 and 26.3 only) |
| `/dyetracker discord hunt <dye\|clear>` | Pick the dye your Discord card says you're hunting (Tab completes it), or clear it; `/dyetracker discord hunt` on its own shows the current pick (Minecraft 26.1.2, 26.2 and 26.3 only) |
| `/dyetracker spotify …` | Set up and control the Spotify widget (see [Spotify widget](#spotify-widget); Minecraft 26.1.2, 26.2 and 26.3 only) |
| `/dyetracker theme` | Show your current [theme](#themes) (Minecraft 26.1.2, 26.2 and 26.3 only) |
| `/dyetracker theme <name>` | Switch to a preset theme: `default`, `neutral`, `midnight`, `forest`, `sunset` or `ice` (Tab completes it; Minecraft 26.1.2, 26.2 and 26.3 only) |
| `/dyetracker theme dye <dye>` | Theme the mod after a dye (Tab completes it; Minecraft 26.1.2, 26.2 and 26.3 only) |
| `/dyetracker theme custom` | Switch back to your Custom theme, once you have one (Minecraft 26.1.2, 26.2 and 26.3 only) |

### In-game menu

The menu is in the Minecraft **26.1.2**, **26.2** and **26.3** builds only.

Open the menu with `/dyetracker menu`, or bind a key to **Open DyeTracker Menu** in **Controls → DyeTracker** (it has no key until you set one).

**Dyes tab**

- Shows your dyes and your progress on each, looked up by your in-game name. You don't need to link your account to use it.
- If your account is linked, the counts the mod tracks for you (see [Dyes the mod counts for you](#dyes-the-mod-counts-for-you)) show their exact synced values.
- Your percentage counts work done in a boosted year at that year's boost.
- Switch between your SkyBlock profiles with the profile switcher.
- Filter by category and change the sort order (progress or name).
- Click a dye to pin it to your HUD as a progress widget.
- Dyes you've obtained show an **OBTAINED** badge, and the top bar shows how many of the dyes you've obtained (see the Collection tab).

**Collection tab**

- Shows every dye: the ones you own are highlighted with a check mark (or a count when you have more than one), and the ones you're missing are greyed out. A summary at the top says how many you own and the percentage.
- Your collection comes from syncing Vincent's Dye Guide while your account is linked. Until then every dye is greyed and the tab tells you how to record it.
- Filter by category (each chip shows how many of that category you own) and by **All / Owned / Unowned**.
- The **Drop Timeline** below the grid lists the dyes you own with a drop date, newest first, with the date and how long ago it was.

**Achievements tab**

- Shows every achievement in sections (Dye Drops, Milestones, Sweeps, Progress and Secrets), with a count per section and your overall total at the top.
- Each achievement shows its tier (Bronze, Silver, Gold, Platinum or Legendary), in that tier's color once unlocked, its description, and when you unlocked it or how far along you are.
- Secret achievements show as **???** until you unlock them.
- Filter by **All / Unlocked / Locked**.

**History tab**

- Shows your gains over the last **1h / 24h / 7d / 30d** (pick one with the pills at the top): for each dye, the work you did and the chance that work gave you, at each year's boost. The biggest gains come first.
- Click a dye to see bars of its work per time slice (5 minutes, an hour, 6 hours or a day, depending on the range). Hover a bar for its time and work.
- History is only recorded for linked players, so link your account with the mod to start it. Until then the tab tells you so.
- The website's history page has the full charts and the **Update me now** button.

**Settings tab**

- **Account**: see which account is linked, or unlink it (click Unlink, then Confirm unlink).
- **Sync**: send your tracked counts to DyeTracker now.
- **Rotation widget**, **Dye widgets**, **GIF overlays** and **Bounce**: turn each on or off.
  - The rotation widget shows the dyes on Vincent's Dyes screen and their boosts. When your account is linked, opening that screen also sends the rotation to DyeTracker (whether or not the widget is on), so it knows which dyes are boosted that SkyBlock year (Minecraft 26.1.2, 26.2 and 26.3).
- **Discord presence** and **Discord hunt**: see [Discord Rich Presence](#discord-rich-presence).
- **Edit layout**: move and resize your HUD widgets.
- **Spotify**: log in or out, your Client ID, the Spotify widget and **Tint all widgets** (see [Spotify widget](#spotify-widget)). Scroll down the tab to reach it.
- **Theme**: shows your current theme; **Customize ▸** opens the Theme screen (see [Themes](#themes)).

### Themes

Themes are in the Minecraft **26.1.2**, **26.2** and **26.3** builds only.

A theme sets the colors of the menu, its screens and your HUD widgets. Pick one on the Theme screen (Settings tab → **Theme** → **Customize ▸**) or with `/dyetracker theme`.

The Theme screen has the preset buttons, **Dye ▸**, **Custom** (once you have one), six color boxes, a **HUD opacity** slider, a live preview, **Reset** and **Done**.

**Presets**

| Preset | Look |
|--------|------|
| `default` | The original look: magenta and cyan on near-black. You start on this one. |
| `neutral` | Grays in place of the magenta and cyan |
| `midnight` | Periwinkle and lavender on deep navy |
| `forest` | Green and gold on dark green |
| `sunset` | Coral and amber on dark plum |
| `ice` | Aqua and periwinkle on dark slate |

**Dye themes**

- Theme the mod after any of the 42 dyes: **Dye ▸** on the Theme screen (search, then click a dye), or `/dyetracker theme dye <dye>` (Tab completes the dye).
- They work without linking your account.
- The dye's color becomes the accent. Dark dyes are lightened so text stays readable, and panels stay dark.

**Custom**

- The six color boxes are **Accent**, **Accent 2**, **Panel**, **Border**, **Text** and **Text 2**. Type a color as `RRGGBB` or `#RRGGBB`.
- Editing a color while you're on another theme starts a new Custom theme based on that theme. There's one Custom slot, so this replaces the Custom theme you had.
- An invalid color turns the box red and isn't saved.
- **Reset** clears your colors and goes back to the theme your Custom theme was based on.
- The **Custom** button shows once you have a Custom theme. Click it, or run `/dyetracker theme custom`, to switch back to it.

**HUD opacity**

- The **HUD opacity** slider sets how see-through the backgrounds of the rotation, dye progress and Spotify widgets are. It starts at 78%, the original look.
- It works on every theme, and moving it doesn't switch you to Custom. Menus and screens stay solid.

**What never changes**

Themes leave these colors alone:

- Boost 2x and 3x colors
- Achievement tier colors
- The owned greens and the estimated badge
- Error red
- Each dye widget's own dye color
- Chat colors
- Minecraft's own buttons, text boxes and sliders

### Discord Rich Presence

Discord Rich Presence is in the Minecraft **26.1.2**, **26.2** and **26.3** builds only. It's off until you turn it on.

While you play Hypixel SkyBlock, your Discord profile shows a DyeTracker card:

- `Hunting Nadeshiko · 0.03%`: the dye you're hunting and your progress on it, when you've picked one. Dyes with no drop chance show the name only.
- `17/42 dyes · 23 dropped`: how many dyes you own and how many have dropped for you in total. This line shows once your collection is recorded (open Vincent's Dye Guide while your account is linked).
- The hunted dye's art as the big picture, with the DyeTracker logo small. With no hunt picked, the DyeTracker logo.
- How long you've been on SkyBlock.
- A **View on DyeTracker** button to your profile page on the website. Other people see it; you don't.

**Turn it on**

- Settings tab → **Discord presence**, or `/dyetracker discord on`.
- Pick a hunt: Settings tab → **Discord hunt** → **Change ▸** (search, click a dye, or **Clear**), or `/dyetracker discord hunt <dye>` (Tab completes the dye).

**When it shows**

- Only on Hypixel SkyBlock. It clears about 10 seconds after you leave SkyBlock (for a lobby or another server), and right away when you disconnect or quit.
- The card updates at most every 15 seconds.

**What you need**

- The Discord **desktop** app running. The browser version of Discord can't show it. On Linux, the Flatpak and Snap Discord work too.
- In Discord settings → **Activity Privacy**, **Share your detected activities with others** turned on.

**Lunar Client:** Lunar's launcher sets its own Discord status, and that one wins. Turn it off: launcher gear icon → **General** → **Discord Rich Presence**. The same goes for other launchers with their own Discord status. The first time you turn presence on, the mod prints these steps in chat.

### Dye widgets

Click a dye in the menu's Dyes tab to pin it to your HUD. The widget shows the dye's name, your progress and a progress bar.

Your progress counts work done in a boosted year at that year's boost.

**Rotation line** (Minecraft 26.1.2, 26.2 and 26.3, accounts that have been linked)

Under the bar, the widget shows what you did toward the dye this rotation (the current SkyBlock year, about 5 real days) and the chance that work alone gave you, at this year's boost if the dye is boosted:

- `1,234 skeletons · 0.04% this rotation`, or `16 skeletons · 0.0027% this rotation (2x)` when the dye is boosted this SkyBlock year (`(3x)` at a 3x boost).
- If DyeTracker started counting partway through the rotation (e.g. on a newly linked account), the line still reads "this rotation" but only counts from then on.
- Dyes with no drop chance (Livid, Necron, Jade and Dark Purple) show the count only, e.g. `12 completions this rotation`.
- [Dyes the mod counts for you](#dyes-the-mod-counts-for-you) show the line once you've done some of that work this rotation.
- The line follows your stats as DyeTracker refreshes them, which can take a few minutes after you play.

The line is on by default. To hide it on one widget, open **Edit layout**, click the widget, press **C** and untick **Rotation line**.

### Spotify widget

The Spotify widget is in the Minecraft **26.1.2**, **26.2** and **26.3** builds only.

It shows the song you're playing on Spotify on your HUD, in the colors of its album art. It uses a Spotify app you create yourself, so it talks to Spotify as you.

**Needs Spotify Premium** (Spotify requires it for apps in Development Mode) and about two minutes of one-time setup.

**Setup**

In game, `/dyetracker spotify help` prints these steps with clickable links.

1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard), log in, and click **Create app** (any name and description).
2. Set the Redirect URI to exactly `http://127.0.0.1:43821/callback` (not `localhost`).
3. Tick **Web API** and save.
4. Open the app's **Settings** and copy the **Client ID**.
5. In game, run `/dyetracker spotify clientid <id>` (or paste it in the menu's Settings tab and click **Save**), then `/dyetracker spotify login` and approve in the browser.
6. If the widget says "Spotify refused", add your Spotify account's email under the app's **User Management**.

If login says port 43821 is in use, close whatever is using it and run `/dyetracker spotify login` again.

**What the widget shows**

- The album art (with rounded corners), the song's title, its artists, and a progress bar with the time. A podcast shows the episode and its show; an ad shows "Advertisement".
- Its colors come from the album art, and fade to the new cover's colors when the song changes. A new song makes the border pulse, and the front of the progress bar is brighter.
- A paused song dims the widget and its time reads "Paused".
- With nothing playing for 30 seconds, the widget hides until something plays again.
- Press `G` (or run `/dyetracker spotify edit`) for edit mode: move or scale the widget, or click it and press **E** to hide it (press **E** again to bring it back).
- **Tint all widgets** colors the rotation and dye progress widgets with the album colors too (GIF overlays keep their own look). It works with the Spotify widget hidden. While it's on, the album colors replace your [theme](#themes)'s panel background and border on those widgets, whether the song is playing or paused.

No beat sync: Spotify no longer offers tempo or beat data to new apps.

**Commands**

| Command | Description |
|---------|-------------|
| `/dyetracker spotify` | Show your Spotify status |
| `/dyetracker spotify help` | Print these setup steps in chat |
| `/dyetracker spotify clientid <id>` | Save your Spotify app's Client ID |
| `/dyetracker spotify login` | Log in to Spotify in your browser |
| `/dyetracker spotify logout` | Log out of Spotify (your Client ID stays saved) |
| `/dyetracker spotify now` | Print the song you're playing, with a LISTEN ON SPOTIFY link |
| `/dyetracker spotify toggle` | Show or hide the widget |
| `/dyetracker spotify edit` | Open edit mode to move or scale the widget |
| `/dyetracker spotify tint on\|off\|toggle` | Turn **Tint all widgets** on or off |

The menu's Settings tab does the same from its **Spotify** section: log in or out, paste your Client ID and click **Save**, and switch the **Spotify widget** and **Tint all widgets** on or off.

## Building from Source

> **Minecraft 26.x is in development on the `mc-26x` branch.** This page describes the shipping **1.21.x** line (Yarn mappings, JDK 21). The 26.x line targets Minecraft **26.1.2**, **26.2** and **26.3** (unobfuscated) and requires **JDK 25** with the no-remap `net.fabricmc.fabric-loom` plugin; its build tasks target `:26.1.2`, `:26.2` and `:26.3`. Full 26.x instructions will land with the first 26.x release.

The build is multi-version: a single `src/` tree compiles against every Minecraft version registered in `settings.gradle.kts`, producing one jar per version. This is wired with the [Stonecutter](https://stonecutter.kikugie.dev/) Gradle plugin.

### Prerequisites

- JDK 21 or higher
- Git

### Build Steps

```bash
# Clone the repository
git clone https://github.com/stwalsh4118/DyeTrackerMod.git
cd DyeTrackerMod

# Build every registered Minecraft version (produces both jars)
./gradlew chiseledBuild

# Or build only a single version:
./gradlew :1.21.10:build
./gradlew :1.21.11:build
```

Per-version jars land at `versions/<mc-version>/build/libs/dyetracker-<modver>+<mc-version>.jar`. For example:
- `versions/1.21.10/build/libs/dyetracker-1.1.0+1.21.10.jar`
- `versions/1.21.11/build/libs/dyetracker-1.1.0+1.21.11.jar`

### Development

```bash
# Run a Minecraft dev client for a specific version with the mod loaded
./gradlew :1.21.10:runClient
./gradlew :1.21.11:runClient

# Switch the "active" version (controls which preprocessor branches are written to src/)
./gradlew "Set active project to 1.21.11"
./gradlew "Reset active project"   # restores the VCS version before committing
```

The active-version setting is rewritten by Stonecutter on switch — always run `"Reset active project"` before committing to keep `git status` clean.

For IntelliJ IDEA users, install the [Stonecutter plugin](https://plugins.jetbrains.com/) (search for "Stonecutter") to get syntax highlighting for `//?` preprocessor comments and a one-click version switcher in the run-configurations dropdown.

### Adding a new Minecraft version

When a new Minecraft patch (e.g. 1.21.12) ships:

1. **Create `versions/<new-ver>/gradle.properties`** with the new coordinates. Copy any existing `versions/<ver>/gradle.properties` as a starting point and update:
   - `yarn_mappings=<new-ver>+build.<latest>` — pull the latest non-pre/non-rc build from `https://maven.fabricmc.net/net/fabricmc/yarn/`
   - `fabric_version=<latest>+<new-ver>` — pull from `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/`
   - `loader_version` and `fabric_kotlin_version` rarely change between Minecraft patches; leave them at the values from any existing `versions/<ver>/gradle.properties` unless a bump is needed.
2. **Register the version in `settings.gradle.kts`**: add `"<new-ver>"` to the `versions(...)` call inside `stonecutter.create(...)`.
3. **(Optional) Create `versions/<new-ver>/build.gradle.kts`** — only if the new version needs version-specific Gradle logic that can't be expressed via `if (sc.current.parsed >= ...)` inside the root `build.gradle.kts`. For adjacent patch versions this is virtually never needed; the root build script is evaluated once per registered version and inherits any per-version `gradle.properties` automatically.
4. **Build and verify**:
   ```bash
   ./gradlew :<new-ver>:build
   ```
   If the build fails due to source-level API differences between Minecraft versions, fix the affected call sites using Stonecutter preprocessor comments — `//? if >=<new-ver> { ... //?}` blocks for multi-line code, or named swaps registered in `stonecutter.gradle.kts` for one-liners. See the [Stonecutter docs](https://stonecutter.kikugie.dev/wiki/start/comments) for the full syntax.

That's it — no source duplication, no branch fork, no manual `gradle.properties` swaps. In practice steps 1 and 2 are all that's needed (a two-file change); step 3 is a documented escape hatch for the rare case a future version needs its own Gradle logic.

## Configuration

The mod stores its configuration in `.minecraft/config/dyetracker.json`. This includes your linked account credentials (stored locally and securely).

The [theme](#themes) settings (Minecraft 26.1.2, 26.2 and 26.3):

| Key | Default | What it does |
|-----|---------|--------------|
| `themeKey` | `"default"` | The active theme: a preset (`default`, `neutral`, `midnight`, `forest`, `sunset` or `ice`), `dye:<id>` for a dye theme (e.g. `dye:nadeshiko`), or `custom` for your Custom theme. An unknown value shows Default. |
| `themeCustomBase` | `"default"` | The theme your Custom theme is based on. |
| `themeCustomOverrides` | `{}` | Your Custom theme's colors, as `RRGGBB`, under the keys `accentPrimary`, `accentSecondary`, `panelBackground`, `panelBorder`, `textPrimary` and `textSecondary` (Accent, Accent 2, Panel, Border, Text and Text 2 on the Theme screen). Invalid entries are ignored. |
| `hudPanelAlpha` | `200` | How solid the HUD widget backgrounds are, from `0` (clear) to `255` (solid). `200` is the HUD opacity slider's 78%. |

## Privacy & Security

### What this mod accesses

| Data | Used For | Sent To |
|------|----------|---------|
| Username | Account linking | DyeTracker API |
| UUID | Account identification | DyeTracker API |
| Session Token | Prove account ownership | Mojang only (never DyeTracker) |
| Dye counts (excavations, Dark Auctions, gift rewards, potions brewed) | Dye progress on the website | DyeTracker API |
| Dye collection from Vincent's Dye Guide (the dyes you own, how many, and when you got them) | Collection tab and the website | DyeTracker API |
| Vincent's rotation (the dye ids on his Dyes screen, their boosts, and the SkyBlock year), linked accounts only | Knowing which dyes are boosted each SkyBlock year | DyeTracker API |
| Your DyeTracker stats (read from the DyeTracker API the mod already uses), hunt dye, SkyBlock profile name, and a link to your DyeTracker profile page (which has your Minecraft UUID in it), only while [Discord Rich Presence](#discord-rich-presence) is on (off by default) | Shown on your Discord profile while enabled | The local Discord app (Discord shows it to people who can see your profile) |
| **Spotify (your own app)**: your currently playing track (`user-read-currently-playing` only) | The [Spotify widget](#spotify-widget) | Spotify only: straight from Spotify to your game, nothing goes to DyeTracker's servers |

Once you've linked your account, each time your dye progress is looked up (by the mod, the menu or the website), DyeTracker also saves the stat counters behind it on its server, at most once every 5 minutes and only when they changed. It uses them to show what you did this rotation (see [Dye widgets](#dye-widgets)), and keeps them to show your history in the in-game History tab and on your public history page on the website. This keeps happening after you unlink in the mod (see [Data storage](#data-storage)).

Your Minecraft session token is **never** sent to DyeTracker servers - only to Mojang's official authentication servers.

Your Spotify tokens are stored on your computer in `config/dyetracker/spotify.json`. Treat `spotify.json` like a password; don't share it.

### How verification works

The mod uses Minecraft's standard server authentication flow - the same method every Minecraft server uses when you join:

1. You enter a link code from the website
2. The mod calls Mojang's official `sessionserver.mojang.com` to prove you own your account
3. DyeTracker's server verifies with Mojang that the authentication succeeded
4. A session token is saved locally for future API calls

**Your Minecraft session token is only ever sent to Mojang's official servers, never to DyeTracker.** This is the same secure flow used by every Minecraft server you've ever joined.

### Data storage

- Credentials are stored locally in `.minecraft/config/dyetracker.json`
- You can unlink your account at any time with `/dyetracker unlink`
- Unlinking deletes the credentials stored on your computer only. It doesn't unlink your account on DyeTracker's side or delete the stat counters DyeTracker has saved.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Links

- [DyeTracker Website](https://dyetracker.xyz)
- [Issue Tracker](https://github.com/stwalsh4118/DyeTrackerMod/issues)
