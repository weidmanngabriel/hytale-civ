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
