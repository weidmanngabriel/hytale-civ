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

The plugin JAR is written to:

```text
build/libs/hytale-civ-<version>.jar
```

The artifact name is configured centrally via `artifactBaseName` in `gradle.properties`.

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

If no path is configured, normal tests and builds still work; only `deployToHytale` fails.

## Manual Hytale smoke test

1. Deploy or copy the built JAR into the Hytale Mods folder.
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

GitHub Actions runs on pushes and pull requests with Java 25, executes tests and a full build, uploads the plugin JAR as an Actions artifact, and uploads test reports when tests fail.

Every successful push to `main` also creates a GitHub pre-release tagged `build-<short-sha>`. Its JAR is named `hytale-civ-build-<short-sha>.jar`, so every successfully built main revision remains directly downloadable.

Stable versions use explicit `v*` tags such as `v0.1.0`. A successful tagged build creates a normal GitHub Release with the tested JAR attached.
