# Mine infrastructure building

## Verified native placement contract

Layer-5 mine infrastructure deliberately does not use the normal NPC `ActionPlaceBlock` as its complete placement contract.

Inspection of the pinned `HytaleServer.jar` shows that `ActionPlaceBlock.execute(...)` ultimately writes through `WorldChunk.setBlock(...)`. That is useful native NPC placement, but it does not by itself reproduce the player-like Deco/connected-block follow-up that the project previously needed for Fir mine supports.

The pinned JAR exposes the reusable native APIs used by `MineBlockPlacement`:

- `BlockOperations.setBlock(...)`
- `BlockPhysics.markDeco(...)`
- `BlockPhysics.isDeco(...)`
- `ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(...)`
- `RotationTuple` / `Rotation`
- `WorldChunk.getFluidId(...)`
- `Fluid.getAssetMap().getAssetOrDefault(...)`
- `Fluid.hasEffect(ShaderType.Lava)`

The project had already runtime-verified the following order for V0 support beams:

```text
BlockOperations.setBlock(..., flags = 256)
BlockPhysics.markDeco(...)
ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(...)
```

Layer 5 generalizes that path in `MineBlockPlacement` for dynamically built support and bridge wood. Deco is block-physics metadata, not protection; the blocks remain normally breakable.

## Why the support prefab is not used

`Civilizations/Mine/Mine_Support_01.prefab.json` was authored for the old fixed 4x4 tunnel. Layer 5 needs support height, width and left/right post depth to follow the actual variable tunnel/cave cross-section, so support geometry is resolved block-by-block from the live world.

The prefab remains in the asset pack as a legacy/reference asset but is not part of Layer-5 runtime support placement.

## Woodcutter boundary

The existing woodcutter identifies natural wood structures from Hytale block gathering metadata. That alone cannot distinguish a Fir support from a Fir tree.

`WoodcutterWorkSystem` therefore checks `BlockPhysics.isDeco(...)` while discovering a tree base and while collecting connected tree wood. Deco Fir placed as mine infrastructure is excluded from natural-tree targeting.

This is intentionally separate from the Hytale cascade-physics fix: one rule prevents the Civ woodcutter from selecting built wood, while the native Deco metadata prevents built Fir from behaving like generated tree wood.

## Rotation

The former verified support-beam implementation used `RotationTuple.get(4)` for the Fir trunk's local beam orientation and composed Y-axis rotation to align it with the tunnel cross-axis. Layer 5 preserves this native rotation basis for dynamic `Wood_Fir_Trunk` supports and bridge beams.

Vertical `Wood_Fir_Branch_Long` posts use the block's upright/default rotation.

## Asset lookup

The repository contains the pinned server JAR but not the complete Hytale `Assets.zip` block catalog. Therefore the exact current IDs for the selected Stone Brick stair, Stone Brick Pillar - Base, lantern and wall torch cannot be proven from the JAR alone.

`MineBlockPlacement.resolveAsset(...)` uses a bounded native asset-map lookup:

1. try explicit expected IDs;
2. otherwise scan loaded `BlockType` IDs for required fragments such as `stone + brick + pillar + base`, `lantern`, or `stone + stair`.

This keeps the implementation tied to actual loaded native blocks instead of inventing a Civ replacement. Layer 8 reuses the same native placement path for its verified barrel, chest, chain, lantern, ore and timber decoration assets; see `mine-atmosphere-building.md`. Runtime placement and collision behavior are verified through focused Hytale-Local diagnostics rather than inferred from names.

## Fluid/gap checks

The bridge adapter uses loaded-world block state and native `WorldChunk.getFluidId(...)`. It never synchronously loads chunks during ECS work.

Current bridge detection is intentionally conservative and only recognizes a missing planned floor-support run with a solid approach, a solid opposite landing and planned continuation. Non-lava fluid below the span lowers the maximum accepted V1 span.

The pinned 0.6.8 JAR exposes the loaded fluid asset map and `Fluid.hasEffect(ShaderType)`; the protocol enum contains `ShaderType.Lava`. Layer 6 therefore classifies lava from the actual loaded Hytale fluid asset instead of hard-coding a numeric fluid ID. A lava crossing is rejected, as is fluid occupying the miner's navigation corridor.

This is not a general cave/liquid simulation: V1 does not pump, fill, redirect or swim through liquids.

## Nearby fallback for normal infrastructure

Recurring supports, lighting and optional decoration are allowed to re-resolve against nearby tunnel slices when their preferred slice is unsuitable. The ordered fallback is preferred slice, then nearest slices before/after it up to ±3. Mandatory passability work does not use this fallback because its location is tied to the obstacle being solved.

A failed block placement keeps already placed world blocks. The next work tick re-resolves from current loaded-world state. If normal infrastructure or decoration has no valid preferred or fallback placement it is skipped; optional atmosphere never blocks a front. A mandatory step becomes executable only after its lower adjacent excavation slice is finished; attempting to place a stair into as-yet-unexcavated natural stone must not abandon a front. If mandatory bridge/step work remains truly unresolvable AFTER its prerequisite work is completed, the associated front is abandoned.


## Native section reference for placement (2026-10-08)

The pinned HytaleServer.jar `BlockOperations.setBlock(ChunkStore, Ref<ChunkStore>, int, int, int, ...)` resolves `ChunkSection`, `BlockSection` and `BlockComponentSection` on its reference argument. This is a **section** reference; `WorldChunk.getReference()` is the **column** reference and causes `setBlock` to return `false` when the required `ChunkSection` is absent. `MineBlockPlacement` must use `ChunkStore.getChunkSectionReferenceAtBlock(x,y,z)` for placement, connected-block notifications and `BlockPhysics.markDeco/isDeco`, while `WorldChunk` remains useful for loaded-world inspection. This contract is bytecode-verified against the pinned JAR; gameplay success should still be confirmed in a live three-miner session.


For regular main-tunnel lantern pillars, the resolver now explicitly prefers the verified native asset ID `Deco_Lantern`, rather than selecting an arbitrary block containing `lantern` (such as a temple fixture). `BUILD_STEP` is only offered after the lower adjacent slice has been fully excavated, so valid natural stone in that not-yet-dug slice cannot be mistaken for terminal stair placement failure. This change is to Civ scheduling; Hytale still places stairs through the existing native BlockOperations path.


Optional support/light/decor placement uses a headless `MinePlacementExcavationGuard` with native read-only `loadedBlockType` world inspection to detect still-solid authored excavation within one additional block on all three axes. The check occurs after resolution and just before each native block operation. On conflict, `MinerWorkSystem` releases the worker assignment, retains the optional task and delays reselection until the particular conflicting front advances; it never changes Hytale block placement semantics. Mandatory step and bridge work is excluded from this optional-work delay mechanism.


Temporary mine stair switch: `MineInfrastructurePlanner.ENABLE_MINE_STEPS=false`. No stair placement jobs or debug anchors are produced. The resolver implementation is retained, and vertical geometry is unchanged; raw height changes may remain untraversable in native NPC navigation.

## Branch support timber selection

Both main and branch tunnel support crossbeams use `Wood_Fir_Trunk`. The vertical side posts continue to use `Wood_Fir_Branch_Long`. The previous implementation selected `Wood_Fir_Branch_Long` as the branch crossbeam as well; this was a Civ resolver choice, not a Hytale placement limitation. The engine block-model footprint and clearance near rotated curves still require runtime verification.

## Conservative corridor clearance for supports

Supports never occupy a navigation-core voxel. They additionally reject corner-only diagonal adjacency to the core, while allowing edge-adjacent side posts needed for width-five branch tunnels. The true Hytale block model collision footprint remains a separate runtime verification requirement. Ground scanning for post foundations is bounded to three checked levels (nominal floor-minus-one through floor-minus-three); a missing foundation causes the optional support placement to skip instead of creating deep wood rods across open floor gaps. This is a conservative voxel-space proxy, not proof of actual Hytale rotated block hitbox clearance. Native block-model footprint and navigation should be validated in-game before narrowing this margin.

## Overlapping bridge tasks (2026-10-08)

User-provided server logs show distinct `BUILD_BRIDGE` task IDs for overlapping slice ranges of the same tunnel (for example slices 5–7 and slice 5). Different NPCs then attempted to place `Wood_Fir_Trunk` where `Wood_Fir_Branch_Long` had already been placed (`TARGET_OCCUPIED`), followed by `INFRASTRUCTURE_RESOLVE_FAILED` and `FRONT_ABANDONED`.

The guard in `MinerWorkSystem.refreshBridgeTasks` postpones further bridge assessments within a tunnel until its existing unfinished bridge task completes, because adjacent spans can share crossbeam positions. An unresolved bridge whose entire center walking deck is already solid is recognized as completed rather than abandoned; it does not prove side supports are complete. Bridge creation records the floor-center and actual world block type for further investigation. This avoids assessing a bridge while its deck is only partly present, and avoids selecting overlapping work at that slice. It does not prove that the original gap detection was correct, and does not yet solve adjacent-slice or cross-tunnel overlapping placements. Those require world-state diagnostics and behavioral tests before treating the bridge lifecycle as fixed.

## Nearby follow-up work

The Core normal-work selector retains active-work preference and the original effective-priority/aging policy. Only when the active candidate is farther than 24 blocks and a same-effective-priority waiting task lies within 8 blocks does it select that local task. This is a distance heuristic, not an independent pathfinder; Hytale remains responsible for reachability.

## Closed bridge deck and bounded outer posts (2026-10-08)

`resolveBridge` keeps the authored slice height (`floorCenter.y - 1`), forward/cross axes, Fir trunk side rails and the original three-slice crossbeam cadence. The three-block center floor uses `Wood_Softwood_Planks`; if that asset is unavailable, a mandatory bridge is not silently substituted with an open Fir-branch lattice. At each existing crossbeam pair of outer positions, short upright `Wood_Fir_Branch_Long` posts extend up to three empty voxels down from the crossbeam. A solid/occupied voxel stops a post; terrain is not excavated to lengthen it. Existing native placement checks reject occupied targets. The exact visual footprint and walking height of connected blocks still require Hytale runtime confirmation.

No destructive collision cleanup has been introduced: the current runtime does not reliably persist per-voxel Civ provenance for old supports, chain, lanterns and decorations; using block IDs alone would risk deleting natural wooden solids or containers. The general access/bridge-recovery algorithm remains a separate change.

## Extended bridge landing overlap and Deco collisions (2026-10-08)

Mandatory bridge spans still detect the actual missing floor at the current excavation slice, but the construction footprint now extends three semantic slices into each landing (clipped to the authored tunnel), retaining the existing floorCenter-based heights, Fir beams and spacing. The core availability gate accepts a bridge task while the excavation front lies anywhere within its task span. Already assigned miners yield to pending mandatory bridge work; completion rechecks the walking floor rather than relying on exhausted placement steps alone.

The bridge resolver considers empty voxels and existing `BlockPhysics.isDeco` voxels eligible for targeted bridge placements. At execution, only a collision that is actually needed by this bridge is removed through the pinned native `WorldChunk.breakBlock` path, and only after `isDeco` is rechecked. This explicitly includes player-like blocks (not only Civ-placed blocks); no block-ID allowlist has been introduced. No natural/non-Deco solid is removed. Runtime caveats: Deco is not ownership metadata, container contents and native connected-block effects are not proven safe by the tag alone; these remain a concern for in-game tests. There is no general pathfinding replacement.
