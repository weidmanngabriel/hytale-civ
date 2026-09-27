# Development workflow

## Prerequisites

- JDK 25
- Git
- No global Gradle installation; use the wrapper.

## Local loop

```text
Change code
    ↓
./gradlew test
    ↓
./gradlew build
    ↓
optional: ./gradlew deployToHytale
    ↓
test in Hytale
```

On Windows use `gradlew.bat`.

## Hytale dependency

Release repository: `https://maven.hytale.com/release`

Dependency: `com.hypixel.hytale:Server`

`hytaleServerVersion` is centralized in `gradle.properties`. Before changing Hytale API usage or the dependency selector, verify current official documentation.

## Local deployment

Set `HYTALE_MODS_DIR` and run:

```bash
./gradlew deployToHytale
```

Or pass:

```bash
./gradlew deployToHytale -PhytaleModsDir=/path/to/mods
```

Normal tests and builds do not require this setting.

## GitHub workflow

Pushes and pull requests run tests and a full Java-25 Gradle build. The plugin JAR is uploaded as an Actions artifact. Failed test reports are uploaded for inspection.

GitHub Releases are intentionally not created for every commit. A later release workflow can attach a tested JAR to explicit version tags.
