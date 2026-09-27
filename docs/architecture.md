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

Pure Java simulation and domain rules. It must not import `com.hypixel.hytale.*`. People, jobs, needs, inventories, goods, production, building state, commands, economy and simulation ticks belong here. The first implemented building slice is represented by the Hytale-independent FarmBuilding, BlockPosition and Profession domain types.

### hytale

Adapters translating between Hytale concepts and core concepts. Entities, NPCs, world access, navigation, camera, input, UI and rendering belong here.

The current RTS validation spike plus Farm slice contains these deliberately small Hytale-facing components:

- `RtsCameraController` applies and clears a custom angled cursor camera.
- `RtsInteractionController` owns temporary per-player RTS input state and translates clicks into claim, selection and move commands.
- `CivUnitRegistry` is a runtime-only registry that marks explicitly claimed NPCs as Civ test units and stores their temporary movement targets.
- `CivNpcMovementSystem` ticks claimed NPCs with active targets and drives their existing Hytale `MotionController` using pursuit steering.
- `FarmPrefabService` loads the Farm prefab from the standalone Asset Pack through Hytale's `PrefabStore`, reads `Civ_BuildingEntrance` creator markers, maps those markers to empty blocks during placement and returns their world positions.
- `FarmBuildingRegistry` binds placed farm instances and assigned NPC refs to the core `FarmBuilding` state.
- `FarmNpcWorkSystem` translates the core farm states into entrance/exit movement targets and advances production while the Farmer is inside.

`CivUnitRegistry` identifies a runtime entity by its `Store` plus entity index while retaining and validating the original `Ref`. This avoids relying on Java object identity for repeated `Ref` instances and prevents stale entity slots from being treated as valid Civ units.

The registry is intentionally not a core-domain ownership model. Claims and move targets disappear when the plugin/server restarts. A future inhabitant lifecycle should replace this debug ownership mechanism.

The movement system uses the NPC role's active `MotionController` and Hytale steering. This validates real locomotion and collision handling rather than teleporting entities. It is not an A* route planner and does not promise to route around arbitrary obstacles.

### plugin

Hytale bootstrap and lifecycle. It wires adapters/services and registers Hytale-facing commands and systems. It should contain as little game logic as possible.

`CivilizationsPlugin` currently:

- registers the Civ NPC movement ticking system;
- exposes `/civtest`, `/civrtstest`, `/civclaim` and `/civfarm`;
- wires mouse-button and disconnect events to the RTS interaction controller.

## Dependency rule

Dependencies point toward the core. `core` is Hytale-independent. `hytale` may depend on `core` and the Hytale API. `plugin` may depend on both and on the Hytale API.

This keeps most behavior executable in ordinary JUnit tests. Hytale is required only where engine behavior itself is under test.

## Current milestone

The bootstrap smoke test remains available through `/civtest`.

The current engine-validation milestone tests the first controllable Civ NPC loop:

1. switch into and out of an angled cursor camera;
2. explicitly claim an existing `NPCEntity` as a temporary Civ test unit;
3. select only claimed Civ units;
4. select several Civ units;
5. right-click a world block to assign slightly offset movement targets;
6. let claimed NPCs locomote toward those targets through their existing Hytale motion controller.

Unclaimed animals, monsters or other NPCs are not controllable merely because they are `NPCEntity` instances. The debug claim command can deliberately claim any compatible NPC for testing.

NPC spawning, persistent Civ ownership, obstacle route planning, visual selection markers, drag-box selection, zoom and camera panning are not part of this milestone. The Farm vertical slice is the first concrete building feature layered on top of the validation spike.


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
FarmPrefabService places it in Hytale
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

The Farm prefab is creator-editable at `asset-pack/Server/Prefabs/Civilizations/Farm/Farm_01.prefab.json`. The prefab anchor is placement metadata only and no longer defines the entrance. The current exterior exit target remains two blocks south of the selected entrance because Farm rotation is still fixed. Rotation-aware entrance direction metadata is deferred until rotated building placement is introduced.
