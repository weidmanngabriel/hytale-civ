# Architecture

## Goal

Keep the simulation testable without starting Hytale. Hytale is an integration boundary, not the domain model.

## Layers

```text
Core Simulation
      ↓
Hytale Adapter
      ↓
Hytale Plugin / API
```

### core

Pure Java simulation and domain rules. It must not import `com.hypixel.hytale.*`. Future people, jobs, needs, inventories, goods, production, building state, commands, economy and simulation ticks belong here.

### hytale

Adapters translating between Hytale concepts and core concepts. Entities, NPCs, world access, navigation, camera, input, UI and rendering belong here.

The current RTS validation spike contains two deliberately small Hytale-facing components:

- `RtsCameraController` applies and clears a custom angled cursor camera.
- `RtsInteractionController` owns temporary per-player RTS test state, consumes cursor mouse-button events, toggles entity selection and records world-space movement targets.

This state is a validation harness, not the future authoritative unit-selection or movement model. It intentionally does not move entities or spawn NPCs yet.

### plugin

Hytale bootstrap and lifecycle. It wires adapters/services and registers Hytale-facing commands and systems. It should contain as little game logic as possible.

`CivilizationsPlugin` currently wires the RTS interaction controller to `PlayerMouseButtonEvent` and exposes it through `/civrtstest`.

## Dependency rule

Dependencies point toward the core. `core` is Hytale-independent. `hytale` may depend on `core` and the Hytale API. `plugin` may depend on both and on the Hytale API.

This keeps most behavior executable in ordinary JUnit tests. Hytale is required only where engine behavior itself is under test.

## Current milestone

The bootstrap smoke test remains available through `/civtest`.

The first engine-validation milestone adds an RTS camera/input spike. It proves or disproves these Hytale integration assumptions before simulation systems are added:

1. the player can switch into and out of an angled cursor camera;
2. cursor clicks can resolve entities and world blocks;
3. several entities can be accumulated in one logical selection;
4. a ground click can provide a world-space destination for a future movement command.

NPC role selection, NPC spawning, pathfinding, visual selection markers, drag-box selection, zoom and camera panning are not part of this first implementation.
