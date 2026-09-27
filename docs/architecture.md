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

Adapters translating between Hytale concepts and core concepts. Future entities, NPCs, world access, navigation, camera, input, UI and rendering belong here.

### plugin

Hytale bootstrap and lifecycle. It wires adapters/services and registers Hytale-facing commands and systems. It should contain as little game logic as possible.

## Dependency rule

Dependencies point toward the core. `core` is Hytale-independent. `hytale` may depend on `core` and the Hytale API. `plugin` may depend on both and on the Hytale API.

This keeps most behavior executable in ordinary JUnit tests. Hytale is required only where engine behavior itself is under test.

## Current milestone

Only the smoke-test plugin and `/civtest` exist. No speculative abstractions are introduced for RTS controls, NPC simulation, buildings, logistics or economy.
