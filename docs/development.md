# Development workflow

## Prerequisites

- JDK 25
- Git
- No global Gradle installation; use the wrapper.

## Local loop

```text
Change code/assets
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

## Asset Pack

Creator-editable Hytale assets live in:

```text
asset-pack/
```

The directory is a standalone Hytale Asset Pack and therefore contains its own `manifest.json`. Keep Java/plugin resources in `src/main/resources`; do not move the plugin manifest out of the JAR.

`./gradlew build` creates a distribution ZIP under:

```text
build/distributions/hytale-civ-<version>-bundle.zip
```

The ZIP contains:

```text
hytale-civ.jar
hytale-civ-assets/
```

The outer ZIP is only a release/download container. The asset directory stays separate from the JAR so installed assets can be edited or replaced without recompiling Java.

## In-game prefab authoring

Hytale's prefab editor can load prefabs from Asset Packs and save them back into a selected Asset Pack. During development, the repository's `asset-pack/` directory should therefore be the authoritative editable copy rather than a separately deployed copy.

Recommended local setup:

```text
Git repository
└── asset-pack/                    ← authoritative editable files

Hytale Mods directory
└── hytale-civ-assets              ← directory link/junction to repo asset-pack/
```

With that setup the authoring loop is:

```text
open Hytale prefab editor
    ↓
load Civilizations/Farm/Farm_01
    ↓
edit blocks and creator markers
    ↓
save to HytaleCivAssets
    ↓
asset-pack/ in the Git working tree changes directly
    ↓
review diff, test, commit
```

Do not use `deployToHytale` as the authoring source when editing prefabs in-game. That task intentionally copies the Asset Pack into the Mods directory, so subsequent in-game edits would modify the copied installation instead of the repository. Use deployment copies for runtime testing; use a directory link/junction for round-trip prefab authoring.

Example development links (paths are placeholders and must not be committed):

Windows Command Prompt, using a directory junction:

```bat
mklink /J "%APPDATA%\\Hytale\\UserData\\Mods\\hytale-civ-assets" "C:\\path\\to\\hytale-civ\\asset-pack"
```

macOS/Linux:

```bash
ln -s /path/to/hytale-civ/asset-pack /path/to/Hytale/UserData/Mods/hytale-civ-assets
```

If a normal deployed `hytale-civ-assets` directory already exists, remove or rename that deployed copy before creating the link. Never commit machine-specific Hytale paths or links into the repository.

## Local deployment

Set `HYTALE_MODS_DIR` and run:

```bash
./gradlew deployToHytale
```

Or pass:

```bash
./gradlew deployToHytale -PhytaleModsDir=/path/to/mods
```

The task copies both the plugin JAR and `hytale-civ-assets/` into the configured Mods directory. Normal tests and builds do not require this setting.

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

Pull requests run tests and a full Java-25 Gradle build for feature branches. Ordinary pushes to non-main branches do not start a second duplicate workflow. Pushes to `main` and `v*` tags still run CI because they drive development and stable releases. When a newer commit updates the same pull request or branch/ref, GitHub Actions cancels the older in-progress workflow so only the newest revision continues. The release bundle ZIP is uploaded as an Actions artifact. Failed test reports are uploaded for inspection.

Every successful push to `main` additionally creates a GitHub pre-release with a deterministic tag based on the commit SHA:

```text
build-<short-sha>
```

The release asset is:

```text
hytale-civ-build-<short-sha>.zip
```

It contains the tested plugin JAR plus the editable Asset Pack. This keeps every successfully built main revision permanently downloadable without treating it as a stable version.

The GitHub Release description is the subject of the commit being released. Because completed project changes are squash-merged, this gives each main release a one-line summary of that adjustment.

For stable versions, push a SemVer-style tag such as:

```bash
git tag v0.1.0
git push origin v0.1.0
```

A successful tagged build creates a normal GitHub Release with the same ZIP bundle attached. Tags beginning with `v` are treated as stable releases; ordinary main builds remain pre-releases.

Re-running a release job is idempotent: if the release already exists, the workflow updates its short release note and replaces its ZIP asset instead of creating a duplicate release.
