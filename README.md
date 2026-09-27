# Hytale Civ

Java plugin foundation for a future Hytale civilization/RTS simulation. The repository and artifact name is `hytale-civ`; game logic does not depend on that name.

## Current milestone

The plugin contains three validation commands:

```text
/civtest
/civrtstest
/civclaim
```

`/civtest` is the plugin-load smoke test.

`/civrtstest` toggles the RTS interaction mode with an angled cursor camera.

`/civclaim` arms the next left click so an existing Hytale `NPCEntity` can be explicitly claimed or released as a temporary Civ test unit. Only claimed Civ units can be selected and commanded.

In RTS mode, left-click claimed units to build a multi-selection and right-click ground to move the selection. Multiple units receive slightly offset destinations. Movement uses each NPC's existing Hytale motion controller and steering, not teleportation.

This milestone validates direct collision-aware NPC locomotion. It does not yet provide full route planning around arbitrary obstacles, persistent Civ ownership, custom Civ NPC spawning, economy, buildings or production systems.

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

Hytale API usage must be re-checked against current official docs when changed.

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
7. Right-click open ground and confirm it walks toward the target rather than teleporting.
8. Repeat with several claimed NPCs and confirm their destinations are slightly separated.
9. Verify an unclaimed NPC cannot be selected or commanded.
10. Run `/civclaim` and click a claimed NPC to release it.
11. Run `/civrtstest` again to restore the normal camera.

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
/civfarm
→ right click flat ground to place the Farm

/civclaim
→ left click an NPC to claim it
→ left click the claimed NPC to select it
→ right click the Farm doorway
~~~

The assigned Farmer walks into the Farm, produces one local wheat after five seconds of work, walks outside after every unit, re-enters, and stops once the Farm reaches 10 wheat. The visible building is a creator-editable Hytale prefab in the standalone hytale-civ-assets Asset Pack.
