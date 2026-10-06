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

This keeps the implementation tied to actual loaded native blocks instead of inventing a Civ replacement. Which exact asset wins, its visual orientation and NPC stair traversal still need one focused Hytale-Local/in-game verification.

## Fluid/gap checks

The bridge adapter uses loaded-world block state and native `WorldChunk.getFluidId(...)`. It never synchronously loads chunks during ECS work.

Current bridge detection is intentionally conservative and only recognizes a missing planned floor-support run with a solid approach, a solid opposite landing and planned continuation. Fluid below the span lowers the maximum accepted V1 span.

This is not yet a general cave/liquid simulation and should not be documented as one.
