# Hytale Civ

Java plugin foundation for a future Hytale civilization/RTS simulation.

## Current milestone

Prototype commands:

```text
/civtest
/civrtstest
/civclaim
/civfarm
```

`/civrtstest` enables the fixed angled RTS cursor camera. It is a Custom camera, not Spectator mode.

`/civclaim` arms the next left click so an existing Hytale `NPCEntity` can be claimed or released as a temporary Civ unit.

In RTS mode:

- left click a claimed NPC to make it the single selected person;
- press Hytale's standard Use key, normally **F**, to open that person's action menu;
- choose **Holzfäller** to assign the Woodcutter profession;
- right click ground for the existing direct move command.

A Woodcutter searches for a nearby tree, walks beside its trunk, works briefly and then fells the base through Hytale's native block-harvest path. Hytale therefore remains responsible for normal drops and the tree asset's support/falling-block behavior.

The Farm vertical slice remains available through `/civfarm`.

## Requirements

- Java 25
- Git
- Hytale for manual engine/UX validation
- Gradle Wrapper included

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

The plugin JAR is written to `build/libs/`. The distributable bundle is written to `build/distributions/` and contains the plugin JAR plus the editable `hytale-civ-assets` Asset Pack.

## Hytale API

The build uses the official Hytale release Maven repository and `com.hypixel.hytale:Server` as a `compileOnly` dependency.

Before implementing Hytale-facing behavior, the project first checks for an existing native Hytale API, asset, interaction, game mode, UI primitive or engine system and prefers that over recreating equivalent behavior.

## Local deployment

Set `HYTALE_MODS_DIR` or pass `-PhytaleModsDir=/path/to/mods`, then run:

```bash
./gradlew deployToHytale
```

The deployment task copies both the plugin JAR and editable Asset Pack into the Mods folder.

## Manual Hytale smoke test

1. Deploy both artifacts.
2. Start a compatible Hytale server/world.
3. Run `/civrtstest`.
4. Run `/civclaim` and claim an NPC.
5. Left-click the claimed NPC to select it.
6. Press **F** and select **Holzfäller**.
7. Confirm it finds a nearby tree, walks beside it and fells it.
8. Confirm native drops/physics occur.
9. Right-click ground and confirm direct movement still works.
10. Run `/civrtstest` to restore the normal camera.

See `docs/architecture.md`, `docs/concept.md`, `docs/domain.md` and `docs/testing.md` for the maintained details.
