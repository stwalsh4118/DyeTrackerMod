# Minecraft Mod - Claude Context

## Project Structure

This mod is part of a private monorepo that includes a web frontend and backend. The mod directory is synced to a **public repository** for open-source distribution using `git subtree`.

## Entry Points

- `com.dyetracker.DyeTrackerMod` (`ModInitializer`, `main`): config + commands + sync handlers (common init).
- `com.dyetracker.client.DyeTrackerModClient` (`ClientModInitializer`, `client`): starts the UI-toolkit HUD host, registers the GIF overlay feature, the `G` edit-mode keybind, and startup re-hydration of persisted GIF overlays.

## UI Toolkit (`com.dyetracker.ui`)

A reusable in-game UI component toolkit (PBI 30) for building HUD widgets and edit screens from shared primitives instead of hand-rolled `DrawContext` calls. The GIF/image overlay (PBI 28) is built on it and is the reference consumer.

Packages:

- `ui.core` — `Widget` contract (`measure(): Size` + `draw(ctx, x, y)`; `RenderContext` carries the `DrawContext` + a pause-aware wall clock); geometry (`Size`, `Insets`, `Alignment`, `Rect`); `UiDraw` primitives (`fillRect`, `strokeRect`/`filledBox`, alignment/scale-aware `drawText`, non-tiling `blitStretched`); `WidgetPlacement` (read: id + fractional center x/y + scale + visible) and `PlacementEditor` (write).
- `ui.theme` — `UiTheme` style tokens (`Colors`, `Spacing`, `Sizing`). No raw ARGB/px literals in widgets.
- `ui.texture` — `ImageTextureManager` (feature-agnostic GPU texture + frame-animation backend; `upload`/`release`/`currentFrameIdentifier`/`widthOf`/`heightOf`) consuming `ImageFrames`.
- `ui.components` — `TextWidget`, `PanelWidget`, `SpriteWidget`.
- `ui.layout` — `Row`, `Column`, `Stack` containers (spacing, padding, alignment; nest recursively).
- `ui.hud` — `HudWidgetHost` (one Fabric HUD element before vanilla chat; F1-hide, pause-freeze, shared wall-clock anim; positions each entry from fractional center + measured size, scaling the whole tree via the GUI matrix) and `HudWidgetRegistry` (`HudWidgetEntry`, `HudWidgetProvider`).
- `ui.persist` — `PlacementStore<T : WidgetPlacement>`: generic id-keyed add/remove/update/`updateTransient`/`flush` over a config-list slice (transient-then-flush avoids a disk write per drag/scale frame).
- `ui.edit` — `WidgetEditScreen` (widget-agnostic drag-to-move / scroll-to-scale / `E` visibility / `Delete` remove / focus border + label / ESC-save), `EditScreenAction` + `EditScreenActionRegistry` (pluggable per-feature panels), `EditModeKeybind` (`G`).

### How to add a HUD widget

1. Define a placement config implementing `WidgetPlacement` (`@Serializable`, fractional center coords) and add a `List<It>` slice to `ModConfig`.
2. Create a `PlacementStore` over that slice in `ConfigManager` (mirror `gifPlacements`).
3. Build the widget from toolkit components (`Panel`/`Row`/`Column`/`Text`/`Sprite`). For images, `ImageTextureManager.upload(id, ImageFrames)` then draw with `SpriteWidget(id)`.
4. Register a `HudWidgetProvider` with `HudWidgetRegistry` mapping your configs → `HudWidgetEntry(placement, widget, editor)`; supply a `PlacementEditor` for edit support.
5. (Optional) Contribute an `EditScreenAction` to `EditScreenActionRegistry` for add/config affordances in the edit screen.

Reference implementation (GIF overlay): `overlay/GifHudFeature`, `GifOverlayConfig`, `GifPlacementEditor`, `GifAddAction`, plus the download/decode pipeline (`OverlayDownloader`, `OverlayDecoder`, `OverlayAddPipeline`).

## Repository Setup

- **Private monorepo**: Contains mod + frontend + backend (where you're working now)
- **Public repo**: Contains only the mod code, synced on releases

## Release Workflow

When releasing a new version of the mod:

1. Ensure all mod changes are committed to the monorepo
2. Update the mod version number in the appropriate config files
3. From the **monorepo root** (not this directory), run:

```bash
git subtree push --prefix=path/to/mod origin-public main
```

> During the 26.x transition, run this **from the branch whose line you are publishing** (`main` for 1.21.x, `mc-26x` for 26.x). See "Two-line maintenance" above.

Or if using tags for releases:

```bash
git tag v1.x.x
git push origin v1.x.x  # This triggers the GitHub Action to sync
```

### One tag, all jars

One release tag carries the jars of **both** lines (1.21.x and 26.x). The `release.yml` workflow attaches its jars to the release for that tag, creating the release if it does not exist yet:

1. The **first** line to release pushes tag `vX.Y.Z`; its jars attach to the new release.
2. The **other** line attaches its jars to that same release afterwards by running the workflow on its own branch:

   ```bash
   gh workflow run release.yml --repo stwalsh4118/DyeTrackerMod --ref <branch> -f version=vX.Y.Z
   ```

> The 26.x line must be on the public repo as branch `mc-26x` for `--ref mc-26x` to work.

> **Pre-releases for testers:** a tag with a suffix (e.g. `v1.11.0-beta.1`) is published as a GitHub pre-release, so it never shows as the latest release. Jars are named `dyetracker-1.11.0-beta.1+<mc>.jar`.

> **Always push tag `vX.Y.Z` to the public repo before any workflow run.** Never `workflow_dispatch` a version whose tag does not exist yet: the action would then create the tag at the head of the default branch (`main`, the 1.21.x code) while it uploads the other line's jars. Release metadata shows `target_commitish: main`, the same as every past release.

## Important Guidelines

### Commit Hygiene
- Keep mod-related commits focused on mod changes only when possible
- Write clear commit messages—they will appear in the public repo's history
- Avoid referencing private backend/frontend details in commit messages for mod changes

### Sensitive Information
- **Never** include API keys, server URLs, or credentials in this directory
- Any configuration that references the private backend should use environment variables or config files that are gitignored
- The public repo should be fully functional as a standalone mod

### File Organization
- Keep all mod source files self-contained within this directory
- Shared types/utilities used by both mod and backend should be duplicated or extracted to a separate shared package, not referenced via relative imports outside this directory

### Documentation
- README.md in this directory should be written for public consumers
- Include build instructions that work standalone (not dependent on monorepo setup)
- Document any configuration options users need to set

## What NOT to Sync

These patterns should be in .gitignore or excluded from the public repo:
- Build artifacts
- IDE configurations
- Local development configs with private URLs
- Any test fixtures containing real data

## Two-line maintenance (1.21.x `main` + 26.x `mc-26x`)

During the Minecraft 26.x transition the mod is developed on **two parallel git lines**, both actively maintained for a transition window. Know which branch you are on before building or publishing.

| Line (branch) | Minecraft | Mappings | Loom plugin | Java | Status |
|---|---|---|---|---|---|
| `main` | 1.21.10 + 1.21.11 | Yarn (obfuscated) | `fabric-loom` (remap) | 21 | ships today |
| `mc-26x` | 26.1.2 + 26.2 | none — 26.x is **unobfuscated** | `net.fabricmc.fabric-loom` (no-remap) | 25 | incubating |

**Where feature work lands.** A feature that should exist on both lines must be applied on **`main` (Yarn)** *and* separately **re-applied / translated on `mc-26x`** (unobfuscated Mojang names). A straight `git cherry-pick` will **not** apply cleanly across the lines, because the Minecraft symbol names differ (e.g. `MinecraftClient` → `Minecraft`, `Identifier` package move, `Text` → `Component`, `KeyBinding` → `KeyMapping`). Treat cross-line changes as a *port*, not a merge.

**Per-branch public-repo subtree sync.** The public mod repo is fed via `git subtree push` from the monorepo root, and must be run **from the branch whose line you are publishing** — the `versions/`, `stonecutter.gradle.kts`, and build files all sync naturally per branch (same mechanism as PBI-29):

```bash
# from the monorepo root, on the branch you are publishing:
git subtree push --prefix=path/to/mod origin-public main      # 1.21.x line
git subtree push --prefix=path/to/mod origin-public mc-26x    # 26.x line
```

Publish `main` for the shipping 1.21.x line and `mc-26x` for the 26.x line — don't cross them.

**Build-command divergence.** On `mc-26x` the build/run/test tasks target `:26.1.2` and `:26.2` and require **JDK 25**; the 1.21.x `:1.21.10`/`:1.21.11` tasks do not exist on this branch (and vice-versa). See the `mc-26x` subsection under "Building the Mod".

## Building the Mod

> **Branch-specific.** The commands below describe the **`main`** (1.21.x) line. On the **`mc-26x`** branch the version targets and JDK differ — see "On the `mc-26x` branch (Minecraft 26.x)" at the end of this section.

The build is multi-version-managed via [Stonecutter](https://stonecutter.kikugie.dev/). A single shared `src/` tree is compiled against each Minecraft version registered in `settings.gradle.kts` (on `main`: 1.21.10 and 1.21.11; on `mc-26x`: 26.1.2 and 26.2). Per-version dependency coordinates live under `versions/<mc-version>/gradle.properties`.

```bash
# Build all registered versions (produces one jar per version)
./gradlew chiseledBuild

# Build a single version
./gradlew :1.21.10:build
./gradlew :1.21.11:build

# Run a per-version dev client
./gradlew :1.21.10:runClient
./gradlew :1.21.11:runClient
```

Per-version jars land at `versions/<mc-version>/build/libs/dyetracker-<modver>+<mc-version>.jar` (e.g. `versions/1.21.11/build/libs/dyetracker-1.1.0+1.21.11.jar`).

### On the `mc-26x` branch (Minecraft 26.x)

The `mc-26x` branch targets Minecraft **26.1.2** and **26.2** (post-obfuscation) and **requires JDK 25** as the Gradle JVM (1.21.x targets Java 21). The build uses the no-remap `net.fabricmc.fabric-loom` plugin with **no mappings** (26.1 is unobfuscated). Provision JDK 25 with [SDKMAN](https://sdkman.io/) (`sdk install java 25.0.3-ms`) — the committed Gradle Java toolchain (`languageVersion 25`) then auto-detects it, so the build JVM is pinned to 25 regardless of your shell's default `java`.

```bash
# all of these run with JDK 25 (toolchain auto-selects it)
./gradlew chiseledBuild          # builds the 26.1.2 and 26.2 jars
./gradlew :26.1.2:build
./gradlew :26.2:build
./gradlew :26.1.2:test
./gradlew :26.2:test
```

Jars land at `versions/<mc-version>/build/libs/dyetracker-<modver>+<mc-version>.jar` (`+26.1.2`, `+26.2`). Per-version coordinates (Loader / Fabric API / FLK) live in `versions/<mc-version>/gradle.properties` — note there is **no** `yarn_mappings` key. Each node's `minecraft_dependency` there (`~26.1.2`, `~26.2`) is expanded into `fabric.mod.json`, so every jar claims only its own Minecraft version. The pinned toolchains are documented in `docs/delivery/59/research/59-1-26.1.2-toolchain-guide.md` and `docs/delivery/53/research/53-1-26.2-toolchain-guide.md`.

**26.x version differences.** The checked-in source is the 26.1.2 state (`vcsVersion`). Where 26.2 differs, the code switches with Stonecutter version comments (`//? if >=26.2 {` … `//?} else {` … `//?}`); keep those in `src/main/kotlin/com/dyetracker/client/GuiCompat.kt` (today: screen + F1 HUD state, which 26.2 moved onto `Minecraft.gui`) and call its extensions instead of the vanilla members.

**In-game checks run on Windows** (Prism Launcher or Lunar Client) with the built jar. Don't launch `runClient` under WSLg.

### Running unit tests

Pure-JVM unit tests (JUnit 5 + kotlin-test) live in the shared `src/test/kotlin/` tree. Run them
against a registered version subproject (there is no root `test` task):

```bash
./gradlew :1.21.10:test
./gradlew :1.21.11:test
```

See `docs/delivery/33/33-2-junit-guide.md` for the wiring rationale.

Switch the active version (controls which preprocessor branches Stonecutter writes to disk in `src/`):

```bash
./gradlew "Set active project to 1.21.11"
./gradlew "Reset active project"   # restore VCS version before committing
```

Always run `"Reset active project"` before committing to avoid Stonecutter rewriting `src/` files in the diff.

### Adding a new Minecraft version

See the public-facing instructions in `README.md` (section: "Adding a new Minecraft version"). Summary: create `versions/<new-ver>/gradle.properties`, add `"<new-ver>"` to `settings.gradle.kts`, run `:<new-ver>:build`, fix any source-level diff with `//? if >=<new-ver> { ... //?}` blocks or named swaps in `stonecutter.gradle.kts`.