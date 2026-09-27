# Hytale Civ

Java plugin foundation for a future Hytale civilization/RTS simulation. The repository and artifact name is `hytale-civ`; game logic does not depend on that name.

## Current milestone

The plugin currently exposes four prototype commands:

```text
/civtest
/civrtstest
/civclaim
/civfarm
```

`/civtest` is the plugin-load smoke test.

`/civrtstest` toggles the RTS interaction mode with a fixed angled cursor camera. The mode uses a Hytale Custom camera, not Spectator mode.

`/civclaim` arms the next left click so an existing Hytale `NPCEntity` can be explicitly claimed or released as a temporary Civ test unit. Only claimed Civ units can be selected and commanded.

In RTS mode, left-click a claimed unit to make it the single selected person. Press Hytale's standard Use key (normally **F**) to open that person's action menu. Right-click ground still issues the existing direct move command. A left-side **Bauen** menu opens a modal, alphabetically ordered building catalog.

The first action-menu profession is **Holzfäller**. A Woodcutter searches for a nearby tree, walks beside the trunk, works briefly and then fells the base through Hytale's native block-harvest path. Hytale therefore remains responsible for normal drops and the tree asset's support/falling-block behavior.

The RTS spike validates direct collision-aware NPC locomotion. The Farm vertical slice adds the first concrete building and production loop; full route planning, persistent Civ ownership, custom Civ NPC spawning and a general economy are still future work.

## Requirements

- Java 25
- Git
- Hytale only for the final manual smoke test
- No global Gradle installation; use the Gradle Wrapper

## VS Code

Recommended extensions are in `.vscode/extensions.json`:
- Red Hat Java
- Gradle for Java
- Test Runner for Java

Open the repository root. The Java extension should import the Gradle project automatically.

## Test and build

macOS/Linux:

```bash
./gradlew test
./gradlew build
```

Windows:

```powershell
gradlew.bat test
gradlew.bat build
```

The Java plugin JAR is written to:

```text
build/libs/hytale-civ-<version>.jar
```

The distributable bundle is written to:

```text
build/distributions/hytale-civ-<version>-bundle.zip
```

The bundle contains:

```text
hytale-civ.jar
hytale-civ-assets/
└── manifest.json
```

The outer ZIP is only the download package. Hytale receives the Java plugin as a JAR and the assets as a separate Asset Pack. Files in `hytale-civ-assets/` can therefore be changed after installation without recompiling the Java plugin.

Editable source assets live in `asset-pack/`. The artifact name is configured centrally via `artifactBaseName` in `gradle.properties`.

## Hytale API

The build uses the official release repository `https://maven.hytale.com/release` and `com.hypixel.hytale:Server` as a `compileOnly` dependency. The selector is configured as `hytaleServerVersion`.

Hytale API usage must be re-checked against current official docs when changed. Before implementing Hytale-facing behavior, the project first checks for an existing native Hytale API, asset, interaction, game mode, UI primitive or engine system and prefers that over recreating equivalent behavior.

## Local deployment

Configure a local Mods folder without committing its path.

macOS/Linux:

```bash
export HYTALE_MODS_DIR="$HOME/path/to/Hytale/UserData/Mods"
./gradlew deployToHytale
```

Windows PowerShell:

```powershell
$env:HYTALE_MODS_DIR = "$env:APPDATA\Hytale\UserData\Mods"
.\gradlew.bat deployToHytale
```

Alternative:

```bash
./gradlew deployToHytale -PhytaleModsDir=/path/to/mods
```

The deployment task copies both `hytale-civ-<version>.jar` and the editable `hytale-civ-assets/` directory into the Mods folder.

If no path is configured, normal tests and builds still work; only `deployToHytale` fails.

## Manual Hytale smoke test

1. Deploy or extract the release bundle and copy both the JAR and `hytale-civ-assets/` into the Hytale Mods folder.
2. Start a compatible Hytale server/world.
3. Confirm the plugin loads and run `/civtest`.
4. Run `/civrtstest`.
5. Run `/civclaim`, then left-click an existing NPC to claim it.
6. Left-click the claimed NPC to select it.
7. Press **F** and confirm the Personenaktionen menu opens.
8. Choose **Holzfäller** and confirm the NPC finds a nearby tree, walks beside it and fells it through normal Hytale harvesting.
9. Confirm the tree's normal drops and native support/falling-block behavior occur.
10. Right-click open ground and confirm the selected NPC can still receive a direct move command.
11. Verify selecting a second claimed NPC replaces the first selection.
12. Verify an unclaimed NPC cannot be selected or commanded.
13. Run `/civclaim` and click a claimed NPC to release it.
14. Run `/civrtstest` again to restore the normal camera.

Obstacle avoidance beyond the NPC motion controller's direct collision handling is not an acceptance criterion yet.

## Architecture

```text
Core Simulation
      ↓
Hytale Adapter
      ↓
Hytale Plugin / API
```

See `docs/architecture.md`, `docs/testing.md`, and `docs/development.md`.

## CI and releases

GitHub Actions runs on pushes and pull requests with Java 25, executes tests and a full build, and uploads the release bundle ZIP as an Actions artifact. Test reports are uploaded when tests fail.

Every successful push to `main` also creates a GitHub pre-release tagged `build-<short-sha>`. Its downloadable asset is named:

```text
hytale-civ-build-<short-sha>.zip
```

Each Release gets a one-line description taken from the released commit subject. With the repository's squash-merge workflow, that means the Release directly summarizes the corresponding change on `main`.

Stable versions use explicit `v*` tags such as `v0.1.0`. A successful tagged build creates a normal GitHub Release with the same ZIP bundle attached.

## Farm prototype

The first building/production slice is available in RTS test mode:

~~~text
/civrtstest
→ click Bauen
→ select Farm
→ move the ghost to valid ground
→ left click to place, right click to cancel

/civfarm
→ debug shortcut into the same Farm placement mode

/civclaim
→ left click an NPC to claim it
→ left click the claimed NPC to select it
→ right click the Farm doorway
~~~

The Farm floor is sunk one block into valid terrain rather than sitting above it. Placement is revalidated server-side at confirmation, and each placed Farm retains the original blocks replaced by its floor for future demolition restoration. The assigned Farmer walks into the Farm, produces one local wheat after five seconds of work, walks outside after every unit, re-enters, and stops once the Farm reaches 10 wheat. The visible building is a creator-editable Hytale prefab in the standalone hytale-civ-assets Asset Pack.
