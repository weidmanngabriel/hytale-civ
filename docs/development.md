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

## Change integration

Implement each adjustment on a temporary branch. Intermediate commits are allowed while the change is in progress.

Before integration:

1. complete the intended code, tests and affected documentation on the temporary branch;
2. run `./gradlew test` and `./gradlew build` whenever the local environment permits it;
3. open a pull request against `main` for final GitHub Actions validation;
4. if validation requires fixes, push them to the same temporary branch and revalidate;
5. squash-merge only the final validated branch so exactly one meaningful commit remains on `main` for the adjustment;
6. verify the resulting `main` workflow and generated pre-release.

Do not add follow-up commits to a branch after its final validated state and then merge the unvalidated head. A post-merge fix starts from a new temporary branch and becomes a separate squash commit.

## GitHub workflow

Pushes and pull requests run tests and a full Java-25 Gradle build. The plugin JAR is uploaded as an Actions artifact. Failed test reports are uploaded for inspection.

Every successful push to `main` additionally creates a GitHub pre-release with a deterministic tag based on the commit SHA:

```text
build-<short-sha>
```

The release asset is renamed to:

```text
hytale-civ-build-<short-sha>.jar
```

This keeps every successfully built main revision permanently downloadable without treating it as a stable version.

For stable versions, push a SemVer-style tag such as:

```bash
git tag v0.1.0
git push origin v0.1.0
```

A successful tagged build creates a normal GitHub Release with the tested JAR attached. Tags beginning with `v` are treated as stable releases; ordinary main builds remain pre-releases.

Re-running a release job is idempotent: if the release already exists, the workflow replaces its JAR asset instead of creating a duplicate release.
