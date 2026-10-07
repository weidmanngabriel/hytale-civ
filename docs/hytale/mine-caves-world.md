# Mine caves and loaded-world observation

This page records the Hytale-specific evidence used by Civ's natural-cave integration.

## What Hytale exposes

The pinned `HytaleServer.jar` contains world-generation cave classes under `com.hypixel.hytale.server.worldgen.cave`, including `Cave`, `CaveGenerator`, cave nodes and cave shapes.

Those classes are evidence that Hytale has a native cave-generation subsystem. They are not evidence of a runtime API that maps an arbitrary generated world position or `WorldChunk` back to a semantic cave object.

For runtime world inspection, the pinned JAR exposes the data Civ actually needs on `WorldChunk`:

- `getBlockType(int, int, int)`;
- `getFluidId(int, int, int)`;
- `getFluidLevel(int, int, int)`;
- `getHeight(...)`.

The established Civ integration obtains chunks with `World.getChunkIfLoaded(...)` so ECS/world reads do not synchronously load new chunks during active systems.

## Civ integration rule

Natural-cave detection is therefore a loaded-world observation problem, not a worldgen-object lookup and not a navigation problem.

`MineCaveScanner`:

- reads only already-loaded chunks;
- classifies connected empty world space adjacent to the planned tunnel envelope;
- excludes known planned mine excavation from the cave volume;
- reads native fluid state;
- never supplies movement paths.

Hytale remains authoritative for NPC navigation through any accepted cave or completed bridge.

## Fluids

Civ reads native fluid IDs from `WorldChunk.getFluidId(...)`.

Lava classification continues to use the loaded native `Fluid` asset and `Fluid.hasEffect(ShaderType.Lava)`; no numeric lava ID is hard-coded.

## Runtime-dependent limits

JAR signatures establish available APIs, not generated-world semantics. The exact shapes, block materials and chunk-loading pattern of naturally generated caves remain runtime-dependent.

A focused Hytale-Local scenario may be used when a deterministic cave fixture is available, but cave recognition must not depend on random worldgen for automated correctness.
