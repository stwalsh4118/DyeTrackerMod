# DyeTracker Mod

A Fabric mod for Minecraft that integrates with the [DyeTracker](https://dyetracker.xyz) website to track your Hypixel SkyBlock dye collection progress.

## Features

- Link your Minecraft account to DyeTracker securely via Mojang session verification
- Automatically sync your player stats with the website
- No API keys or passwords required - uses Minecraft's built-in authentication

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

New features ship for Minecraft **26.1.2** and **26.2**. The **1.21.10** and **1.21.11** builds get bug fixes only.

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
| `/dyetracker menu` | Open the in-game DyeTracker menu (Minecraft 26.1.2 and 26.2 only) |

### In-game menu

The menu is in the Minecraft **26.1.2** and **26.2** builds only.

Open the menu with `/dyetracker menu`, or bind a key to **Open DyeTracker Menu** in **Controls → DyeTracker** (it has no key until you set one).

**Dyes tab**

- Shows your dyes and your progress on each, looked up by your in-game name. You don't need to link your account to use it.
- If your account is linked, the counts the mod tracks for you (see [Dyes the mod counts for you](#dyes-the-mod-counts-for-you)) show their exact synced values.
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

**Settings tab**

- **Account**: see which account is linked, or unlink it (click Unlink, then Confirm unlink).
- **Sync**: send your tracked counts to DyeTracker now.
- **Rotation widget**, **Dye widgets**, **GIF overlays** and **Bounce**: turn each on or off.
  - The rotation widget shows the dyes on Vincent's Dyes screen and their boosts. When your account is linked, opening that screen also sends the rotation to DyeTracker (whether or not the widget is on), so it knows which dyes are boosted that SkyBlock year (Minecraft 26.1.2 and 26.2).
- **Edit layout**: move and resize your HUD widgets.

### Dye widgets

Click a dye in the menu's Dyes tab to pin it to your HUD. The widget shows the dye's name, your progress and a progress bar.

**Rotation line** (Minecraft 26.1.2 and 26.2, linked accounts only)

Under the bar, the widget shows what you did toward the dye this rotation (the current SkyBlock year, about 5 real days) and the chance that work alone gave you, at this year's boost if the dye is boosted:

- `1,234 skeletons · 0.04% this rotation` when DyeTracker has your stats from the start of the rotation.
- `1,000 skeletons · 0.03% since 3h ago` when it started counting partway through, e.g. on a newly linked account. The first time, the line reads `0 skeletons · 0.00% since just now`.
- Dyes with no drop chance (Livid, Necron, Jade and Dark Purple) show the count only, e.g. `12 completions this rotation`.
- [Dyes the mod counts for you](#dyes-the-mod-counts-for-you) show the line once you've done some of that work this rotation.
- The line follows your stats as DyeTracker refreshes them, which can take a few minutes after you play.

The line is on by default. To hide it on one widget, open **Edit layout**, click the widget, press **C** and untick **Rotation line**.

## Building from Source

> **Minecraft 26.x is in development on the `mc-26x` branch.** This page describes the shipping **1.21.x** line (Yarn mappings, JDK 21). The 26.x line targets Minecraft **26.1.2** and **26.2** (unobfuscated) and requires **JDK 25** with the no-remap `net.fabricmc.fabric-loom` plugin; its build tasks target `:26.1.2` and `:26.2`. Full 26.x instructions will land with the first 26.x release.

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

When your account is linked, each time your dye progress is looked up (by the mod, the menu or the website), DyeTracker also saves the stat counters behind it on its server, at most once every 5 minutes and only when they changed. It uses them to show what you did this rotation (see [Dye widgets](#dye-widgets)) and keeps them.

Your Minecraft session token is **never** sent to DyeTracker servers - only to Mojang's official authentication servers.

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
- Unlinking deletes all stored credentials

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Links

- [DyeTracker Website](https://dyetracker.xyz)
- [Issue Tracker](https://github.com/stwalsh4118/DyeTrackerMod/issues)
