# Architecture

## Goal

Keep the simulation testable without starting Hytale. Hytale is an integration boundary, not the domain model.

Before version 1, backward compatibility is not a goal when it would require migrations, parallel legacy paths, compatibility defaults or feature-specific exceptions. The current documented architecture and data model are authoritative. This policy must be revisited before persistent player worlds or public stable releases make compatibility a product requirement.

## Layers

```text
Core Simulation
      ↓
Hytale Adapter
      ↓
Hytale Plugin / API
```

### core

Pure Java simulation and domain rules. It must not import `com.hypixel.hytale.*`. People, jobs, needs, inventories, goods, production, building state, commands, economy and simulation ticks belong here. Implemented job state now includes the Hytale-independent `FarmBuilding`, `WoodcutterJob`, `BlockPosition` and `Profession` types.

### hytale

Adapters translating between Hytale concepts and core concepts. Entities, NPCs, world access, navigation, camera, input, UI and rendering belong here.

The current RTS validation spike plus Farm and Woodcutter slices contains these deliberately small Hytale-facing components:

- `RtsCameraController` applies and clears the fixed angled cursor camera. RTS mode does not switch the player to Spectator.
- `RtsInteractionController` owns temporary per-player RTS input state. Selection is deliberately single-select; build-menu and placement state are also isolated per player.
- `RtsToolbarHud` renders the persistent left-side RTS menu and `BuildingMenuPage` provides the modal building catalog.
- Hytale's standard `Use` action (default F) opens `PersonActionsPage` for the selected Civ NPC.
- `CivUnitRegistry` is a runtime-only registry that marks explicitly claimed NPCs as Civ test units and stores profession and movement target state.
- `CivNpcMovementSystem` drives claimed NPCs toward movement targets through their existing Hytale `MotionController`.
- `WoodcutterWorkSystem` finds nearby natural-looking Hytale trunk blocks, drives a WOODCUTTER to an adjacent work position, and uses Hytale's native `BlockHarvestUtils.performBlockDamage` path to fell the base block so normal drops, break events and block physics remain engine-owned.
- `FarmPrefabService` loads the Farm prefab from the standalone Asset Pack through Hytale's `PrefabStore`, validates terrain, renders the per-player placement preview, sinks the prefab floor one block into the terrain, maps `Civ_BuildingEntrance` markers to empty blocks, and records the world blocks replaced by the embedded floor.
- `FarmBuildingRegistry` binds placed farm instances and assigned NPC refs to the core `FarmBuilding` state, tracks placement footprints for overlap checks, and retains each instance's replaced-floor snapshot for future demolition restoration.
- `FarmNpcWorkSystem` translates the core farm states into entrance/exit movement targets and advances production while the Farmer is inside.

`CivUnitRegistry` identifies a runtime entity by its `Store` plus entity index while retaining and validating the original `Ref`. This avoids relying on Java object identity for repeated `Ref` instances and prevents stale entity slots from being treated as valid Civ units.

Claims, profession state and work targets are intentionally runtime-only. A future inhabitant lifecycle should replace this debug ownership mechanism.

The movement system uses the NPC role's active `MotionController` and Hytale steering. This validates real locomotion and collision handling rather than teleporting entities. It is not an A* route planner and does not promise to route around arbitrary obstacles.

The fixed RTS camera is a Hytale Custom camera, not Spectator mode. No verified native API for hiding only the local player's own model in this camera mode has been established yet, so the player entity is not despawned or hidden through an unverified workaround.

### plugin

Hytale bootstrap and lifecycle. It wires adapters/services and registers Hytale-facing commands and systems. It should contain as little game logic as possible.

`CivilizationsPlugin` currently:

- registers the Farm, Woodcutter and shared Civ NPC movement ticking systems;
- exposes `/civtest`, `/civrtstest`, `/civclaim` and `/civfarm`;
- wires mouse-button, mouse-motion, Hytale Use/F interaction and disconnect events to the RTS interaction controller.

## Dependency rule

Dependencies point toward the core. `core` is Hytale-independent. `hytale` may depend on `core` and the Hytale API. `plugin` may depend on both and on the Hytale API.

This keeps most behavior executable in ordinary JUnit tests. Hytale is required only where engine behavior itself is under test.

## Multiplayer interaction and world authority

Player-facing transient state is isolated by player UUID. Selection, modal/build interaction and active placement previews must never be stored as one global RTS state shared by all players.

The preview is advisory client UX only. Any action that mutates shared world state must be validated again on the server at commit time against the current world and building registry. This prevents two players from successfully committing overlapping placements after both previously saw a valid preview.

Placed building instances retain the original world block IDs replaced by their embedded floor. That snapshot is runtime-only while buildings themselves are runtime-only. When placed buildings become persistent, the terrain snapshot must be persisted with the same building instance so future demolition can restore the previous ground.

## Current milestone

The bootstrap smoke test remains available through `/civtest`.

The current engine-validation milestone tests the first controllable Civ NPC loop:

1. switch into and out of a fixed angled cursor camera;
2. explicitly claim an existing `NPCEntity` as a temporary Civ test unit;
3. left-click one claimed Civ unit to make it the single selection;
4. press Hytale's standard Use key (default F) to open that person's action menu;
5. assign the Woodcutter profession from the menu;
6. let the NPC find a nearby tree, walk beside its base and fell it through Hytale's native block-harvest/physics path;
7. right-click still provides the existing direct move command for the selected unit.

Unclaimed animals, monsters or other NPCs are not controllable merely because they are `NPCEntity` instances. The debug claim command can deliberately claim any compatible NPC for testing.

NPC spawning, persistent Civ ownership, obstacle route planning, visual selection markers, drag-box selection, zoom and camera panning are not part of this milestone.

## Distribution boundary

Runtime Java code and creator-editable Hytale assets are distributed separately inside one convenience archive.

Repository layout:

```text
src/main/...           Java plugin code and plugin manifest
asset-pack/            standalone editable Hytale Asset Pack
```

Release layout:

```text
hytale-civ-<release>.zip
├── hytale-civ.jar
└── hytale-civ-assets/
    └── manifest.json
```

The outer ZIP is only the downloadable release bundle. Hytale still receives the Java plugin as a JAR and the assets as a standalone Asset Pack folder. This keeps asset changes independent from Java compilation: after installation, files inside `hytale-civ-assets/` can be changed without rebuilding the plugin JAR.

Gameplay data should only move into the Asset Pack when a concrete Hytale asset type is required. Core simulation rules and domain state remain in the existing Java architecture unless a later feature establishes a different boundary.

## Farm production vertical slice

The first real production feature intentionally stays concrete rather than introducing a speculative generic building framework.

~~~text
Farm prefab in Asset Pack
        ↓
RTS menu → modal catalog → per-player ghost placement
        ↓
FarmPrefabService validates terrain, sinks the floor by one block and places it in Hytale
        ↓
FarmBuildingRegistry creates a core FarmBuilding
        ↓
one claimed NPC is assigned Profession.FARMER
        ↓
FarmNpcWorkSystem drives:
entrance → 5 s inside → +1 local wheat → exit → repeat
        ↓
stop outside when local wheat reaches 10
~~~

Building entrances are authored inside the prefab with the technical `Civ_BuildingEntrance` marker block. The marker uses Hytale's editor-oriented GizmoCube rendering, has no physical collision, and is converted to an empty block when the runtime prefab is placed. Its cell sits one block above the physical threshold; the simulation derives the threshold block directly below it.

A prefab must contain at least one entrance marker. Multiple markers are supported. For the current one-Farmer Farm slice, the registry selects the marker nearest to the assigned NPC by straight-line world distance and then lets the NPC's normal Hytale movement controller travel to that target. This is not yet path-cost-aware entrance selection.

The NPC remains a normal Hytale entity; "inside" is currently a simulation state reached when its position reaches the selected entrance target. The prototype does not hide, despawn or teleport the NPC while working.

The Farm prefab is creator-editable at `asset-pack/Server/Prefabs/Civilizations/Farm/Farm_01.prefab.json`. The prefab anchor is placement metadata only and no longer defines the entrance. During RTS placement the clicked terrain surface is treated as the finished floor height, so the prefab anchor is shifted down by one block and the prefab's floor replaces that terrain layer. The replaced block IDs are retained on the placed Farm instance for future demolition restoration. The current exterior exit target remains two blocks south of the selected entrance because Farm rotation is still fixed. Rotation-aware entrance direction metadata is deferred until rotated building placement is introduced.
