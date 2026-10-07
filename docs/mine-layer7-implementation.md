# Mine Layer 7 - natural caves and hazards

Status: implemented V1 for natural cave observation, large natural chamber integration and safer bridge landing checks.

Product rules remain canonical in `docs/mine-design.md` and `docs/miner-npc-design.md`.

## Existing foundation reused

Layer 7 deliberately reuses already implemented obstacle behavior:

- Hytale remains the physical pathfinder;
- water in the walkable corridor abandons a front;
- lava on/below the required route abandons a front;
- a bridgeable missing-floor run creates mandatory `BUILD_BRIDGE` work;
- dry bridge span is at most 16 planned slices;
- non-lava fluid below a crossing reduces the accepted span to 10;
- an unusable front becomes `ABANDONED` and miners select other work.

No second bridge, fluid or navigation system was added.

## Natural cave observation

`MineCaveScanner` inspects only already-loaded Hytale chunks through `World.getChunkIfLoaded(...)`. It never synchronously loads chunks and never provides routes.

The scanner begins from extra empty world space adjacent to the planned tunnel excavation envelope. Planned mine excavation blocks are excluded from the flood-fill so a long already-excavated Civ tunnel cannot classify itself as a natural cave.

The scan is intentionally local:

- horizontal radius: 12 blocks from the encounter slice;
- vertical radius: 8 blocks;
- maximum observed connected empty blocks: 2048.

The scanner records:

- observed empty volume;
- horizontal and vertical spans;
- empty positions with solid usable floor below;
- fluid presence;
- lava presence;
- whether the bounded observation was complete.

If the scan reaches unloaded world data or its configured bounds before a useful large cave is proven, the result is `INCOMPLETE` rather than prematurely classifying the opening as small.

These values are initial V1 tuning values, not permanent world-generation rules.

## Classification

`MineCavePolicy` is Hytale-independent.

Current large-useful-cave threshold:

- at least 160 observed empty blocks;
- at least 24 usable floor positions;
- at least 8 blocks horizontal span;
- at least 4 blocks vertical span.

Outcomes:

- `NONE`: no additional natural empty space detected;
- `SMALL`: complete local opening below large-chamber thresholds;
- `INCOMPLETE`: loaded-world observation was insufficient for a small/large conclusion;
- `LARGE`: enough useful natural space is already proven.

Small safe openings require no separate gameplay object. Existing already-empty tunnel slices continue naturally toward the next solid face.

## Large natural chambers

A proven large useful cave is persisted as a `MineRoom` with:

- type `LARGE_NATURAL_CHAMBER`;
- state `NATURAL_INTEGRATED`;
- attachment to the tunnel/slice where it was encountered;
- observed chamber center as semantic position.

`NATURAL_INTEGRATED` is terminal. It produces neither `EXCAVATE_ROOM` nor `BUILD_ROOM` work and uses no prefab.

Nearby observations within 18 blocks are treated as the same natural chamber so consecutive tunnel slices do not create duplicate semantic rooms.

Natural chambers are world-derived. The older synthetic 5-10% room-planner probability is removed.

## Opposite landing and bridges

The opposite side is still searched only along the already planned tunnel route. Civ does not calculate an arbitrary path through the cave.

The existing missing-floor scan finds the first planned slice after the gap. Layer 7 strengthens the landing contract: the landing must provide several walk-level navigation columns with:

- empty walk space;
- solid floor;
- no fluid in the walk position.

A landing still also needs planned tunnel continuation beyond it.

## Fluids and hazards

Water somewhere inside a large observed cave does not automatically invalidate the chamber. Only fluid that affects the required route/crossing changes passability.

V1 remains:

- non-lava water below a short accepted crossing may be bridged;
- fluid occupying the actual navigation corridor is not swum through;
- lava on or below the required route is unsafe;
- no pumping, filling, redirection, swimming or lava bridge is implemented.

## Native Hytale boundary

The pinned `HytaleServer.jar` confirms:

- `WorldChunk.getBlockType(...)`;
- `WorldChunk.getFluidId(...)`;
- `World.getChunkIfLoaded(...)`;
- native fluid assets including `Fluid.hasEffect(ShaderType.Lava)`;
- Hytale world-generation cave classes such as `server.worldgen.cave.Cave`.

The world-generation cave classes expose generation-time cave structures but no verified runtime lookup from a generated `WorldChunk` to a semantic cave object. Civ therefore classifies local generated empty space from loaded block/fluid state rather than inventing a cave lookup API.

## Limitations / deferred

- cave classification is a bounded local observation, not a complete global cave reconstruction;
- a scan that cannot see enough loaded world remains `INCOMPLETE`; V1 does not persist pending incomplete scans;
- player-created empty space cannot currently be reliably distinguished from naturally generated empty space once it intersects an otherwise untouched planned slice;
- one natural chamber is attached semantically to the first tunnel that integrates it; multi-tunnel chamber topology is deferred;
- no cave-specific furnishing, supports, loot, resource logic or navigation graph synthesis is added;
- explicit unblock/recovery for abandoned fronts remains deferred.
