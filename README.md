# Hytale Civ

Java plugin foundation for a future Hytale civilization/RTS simulation. The repository and artifact name is `hytale-civ`; game logic does not depend on that name.

## Current milestone

The plugin contains two validation commands:

```text
/civtest
/civrtstest
```

`/civtest` is the original plugin-load smoke test.

`/civrtstest` toggles the first RTS interaction spike: an angled cursor camera, logical entity multi-selection by repeated left clicks, and right-click world targeting. It does not move or spawn NPCs yet; that remains a separate engine validation once a concrete NPC role and navigation path are selected.

No economy, buildings or production systems are implemented yet.

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
3. Confirm the plugin loads.
4. Run `/civtest` and confirm `Civilizations smoke test OK.`.
5. Run `/civrtstest` and confirm an angled cursor camera is activated.
6. Left-click existing entities to build a multi-selection; click one again to remove it.
7. Right-click a world block and confirm the target coordinates plus current selection count are reported.
8. Run `/civrtstest` again and confirm the normal camera returns.

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
