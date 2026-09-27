# Hytale Civ

Java plugin foundation for a future Hytale civilization/RTS simulation. The repository and artifact name is `hytale-civ`; game logic does not depend on that name.

## Current milestone

The plugin currently only registers `/civtest`. A successful invocation replies:

```text
Civilizations smoke test OK.
```

No economy, NPC control, RTS camera, buildings or production systems are implemented yet.

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

The bootstrap was checked against the stable Hytale Server API documentation for Release 0.6.8. Hytale API usage must be re-checked against current official docs when changed.

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
4. Run `/civtest`.
5. Confirm `Civilizations smoke test OK.`.

## Architecture

```text
Core Simulation
      ↓
Hytale Adapter
      ↓
Hytale Plugin / API
```

See `docs/architecture.md`, `docs/testing.md`, and `docs/development.md`.

## CI

GitHub Actions runs on pushes and pull requests with Java 25, executes tests and a full build, uploads the plugin JAR as an Actions artifact, and uploads test reports when tests fail.
