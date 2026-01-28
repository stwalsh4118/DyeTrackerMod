# DyeTracker Mod

A Fabric mod for Minecraft that integrates with the [DyeTracker](https://dye-tracker.pages.dev) website to track your Hypixel SkyBlock dye collection progress.

## Features

- Link your Minecraft account to DyeTracker securely via Mojang session verification
- Automatically sync your player stats with the website
- No API keys or passwords required - uses Minecraft's built-in authentication

## Requirements

- Minecraft 1.21+
- [Fabric Loader](https://fabricmc.net/) 0.16.0+
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.115.0+
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.12.3+

## Installation

1. Install Fabric Loader for Minecraft 1.21+
2. Download the required dependencies (Fabric API, Fabric Language Kotlin)
3. Download the latest DyeTracker mod from [Releases](https://github.com/stwalsh4118/DyeTrackerMod/releases)
4. Place all `.jar` files in your `.minecraft/mods` folder
5. Launch Minecraft

## Usage

### Linking Your Account

1. Go to [DyeTracker](https://dye-tracker.pages.dev) and click "Link Account"
2. You'll receive an 8-character code
3. In Minecraft, run: `/dyetracker link <code>`
4. Your account will be verified and linked automatically

### Commands

| Command | Description |
|---------|-------------|
| `/dyetracker link <code>` | Link your account using a code from the website |
| `/dyetracker status` | Check your current link status |
| `/dyetracker unlink` | Unlink your account |

## Building from Source

### Prerequisites

- JDK 21 or higher
- Git

### Build Steps

```bash
# Clone the repository
git clone https://github.com/stwalsh4118/DyeTrackerMod.git
cd DyeTrackerMod

# Build the mod
./gradlew build

# The built JAR will be in build/libs/
```

### Development

```bash
# Run the Minecraft client with the mod
./gradlew runClient

# Generate sources for IDE support
./gradlew genSources
```

## Configuration

The mod stores its configuration in `.minecraft/config/dyetracker.json`. This includes your linked account credentials (stored locally and securely).

## Privacy & Security

### What this mod accesses

| Data | Used For | Sent To |
|------|----------|---------|
| Username | Account linking | DyeTracker API |
| UUID | Account identification | DyeTracker API |
| Session Token | Prove account ownership | Mojang only (never DyeTracker) |

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

- [DyeTracker Website](https://dye-tracker.pages.dev)
- [Issue Tracker](https://github.com/stwalsh4118/DyeTrackerMod/issues)
