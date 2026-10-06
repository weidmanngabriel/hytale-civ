# Mine Layer 3 - Excavation Geometry

Status: implemented Core geometry foundation; Hytale runtime excavation integration remains open.

This document records the concrete implementation state of Layer 3 from `docs/mine-design.md`. The canonical product decisions remain in `docs/mine-design.md` and the miner task semantics remain in `docs/miner-npc-design.md`.

## Implemented

Layer 2 already produces a deterministic `MineTunnelPath` containing sampled centerline points and form phases. Layer 3 now converts that path into concrete voxel excavation geometry in the Hytale-independent Core.

`MineTunnelVoxelizer` produces a `MineTunnelGeometry` with:

- one ordered excavation slice per sampled path point;
- the concrete excavation blocks of every slice;
- the union of all excavation blocks for the planned path;
- a guaranteed navigation-core volume;
- explicit one-block floor-height transitions as `StepTransition` records.

Main-tunnel slice dimensions remain in the Layer-2 planned 6-8 block range. Branch dimensions remain in the 3-5 block range. Width and height are rounded from the continuous Layer-2 values only when they become voxel geometry.

The normal structural cross-section follows the current tunnel tangent, so curves and diagonal headings produce oriented excavation rather than reverting to the old cardinal 4x4 tunnel model.

## Guaranteed navigation core

The guaranteed Core corridor is currently a conservative 3x3 horizontal prism with three blocks of clear height around each rounded centerline sample.

This core is deliberately axis-aligned even when the visible tunnel cross-section is rotated. A first implementation rotated the three-block-wide core with the tangent. Golden/property tests showed that integer voxel rounding can then make two visually adjacent slices touch only diagonally, which is insufficient for a guaranteed six-neighbour-connected voxel volume.

Consecutive rounded center positions are therefore joined one world axis at a time and the 3x3x3 core is stamped at each intermediate position. This makes the guaranteed corridor six-neighbour connected by construction across curves, lateral drift and one-block height changes.

This Core navigation guarantee is not a replacement for Hytale pathfinding and does not create trusted navigation anchors. `docs/mine-design.md` and `docs/hytale/npc-navigation.md` remain authoritative: an anchor becomes trusted only after a miner has actually traversed an exact `BlockType.EMPTY` position.

## Organic excavation

Naturalization is deterministic from the tunnel seed and only expands the structural volume. It never removes navigation-core blocks.

The current Layer-3 algorithm creates coherent clusters spanning multiple neighbouring slices rather than independent random holes. A cluster currently chooses one of three forms:

- left-wall recess;
- right-wall recess;
- ceiling pocket.

Clusters span several consecutive slices, use one or two blocks of extra depth/height and are separated by a small seeded gap before another cluster can start. Exact frequencies and sizes remain tuning parameters for later visual balancing.

The floor is intentionally not randomized by this pass.

## Height transitions

Layer 2 changes elevation gradually by at most one block per form phase. Layer 3 rounds the continuous floor height to voxels. Whenever two consecutive rounded slice centers differ by exactly one Y block, `MineTunnelGeometry` records a `StepTransition`.

`StepTransition` is semantic geometry information for later infrastructure/execution. Layer 3 does not yet place a Hytale stair block or complete a miner `BUILD_STEP` task. The product rule from `docs/mine-design.md` remains unchanged: a usable one-block floor transition must receive a safe stair treatment before it is relied on as normal passable infrastructure.

## Tests

`MineTunnelVoxelizerTest` covers the main invariants:

- deterministic output for an identical Layer-2 path;
- main-tunnel dimensions remain 6-8 blocks;
- branch dimensions remain 3-5 blocks;
- navigation core is always part of the excavated volume;
- the complete navigation core remains six-neighbour connected across 100 deterministic main-tunnel seeds;
- curves and height changes do not disconnect consecutive slices;
- organic naturalization creates extra wall/ceiling volume without reducing the core;
- rounded height changes become explicit one-block step transitions.

`MineTunnelVoxelizerGoldenTest` provides a small explicit coordinate oracle for a straight three-slice tunnel. It intentionally does not call production geometry helpers to build its expected voxel box.

The first CI version of the Layer-3 implementation exposed a diagonal-only navigation-core connection. The implementation was corrected rather than weakening the test; the resulting CI build is green.

## Current limitation / next integration boundary

The current in-game `MinerWorkSystem` still excavates the pre-overhaul `MineSegment` representation with its fixed 4x4 horizontal geometry and fixed 4x4x4 junction. Layer 3 does **not** yet make that old runtime path consume `MineTunnelGeometry`.

That distinction is intentional and must not be lost in later chats. Replacing the runtime path safely also affects continuation, old segment/junction assumptions, support placement, persistence/progress and work-front execution. Doing that as an isolated local patch inside `MinerWorkSystem` would create two competing topology/geometry models and would cross into later mine integration responsibilities.

The next integration work must therefore use `MineTunnelGeometry.Slice` as the concrete shape behind an `EXCAVATE_FRONT` work unit and retire the old fixed-4x4 geometry as the runtime excavation truth rather than maintaining both indefinitely.

Until that migration is complete:

- Layer 2 and Layer 3 are deterministic Core planning/geometry foundations;
- the live miner continues to show the old 4x4 tunnel slice described in `docs/concept.md`;
- no new Hytale API is required or assumed by Layer 3;
- Hytale remains responsible for actual block interaction and physical NPC navigation once the Core slices are wired into execution.

## Explicitly deferred from Layer 3

This implementation does not add:

- branch creation or work-front scheduling;
- rooms;
- natural-cave classification;
- fluids or bridges;
- support placement changes;
- light placement;
- decoration;
- rails;
- multi-miner front reservations or block claims;
- Hytale stair placement;
- persistence of a second copy of excavated world blocks.

Those remain in their existing later layers/specifications.
