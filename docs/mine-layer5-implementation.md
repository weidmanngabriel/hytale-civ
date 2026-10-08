# Mine Layer 5 - Miner-built infrastructure

Status: implemented V1 for supports, stairs, bridges and lighting. This document describes the Layer-5 checkpoint; rooms were added in Layer 6 and optional decoration plus general aging in Layer 8. Rails remain later work.

This document records the concrete implementation of the infrastructure decisions from `docs/mine-design.md` and `docs/miner-npc-design.md`. Product rules remain canonical in those documents.

## Scope

Layer 5 adds four semantic miner tasks:

- `BUILD_SUPPORT`
- `BUILD_STEP`
- `BUILD_BRIDGE`
- `PLACE_LIGHT`

`MineInfrastructurePlanner` is Hytale-independent. It decides recurring support/light opportunities and maps Layer-3 height transitions to mandatory passability work. World-sensitive shape resolution stays in the Hytale adapter because actual stone, open cave space, fluids and block assets are world facts.

One infrastructure task has capacity one. A miner reserves it, walks to its work area and places the resolved construction one block at a time. Each block takes 0.5 seconds. Infrastructure completion IDs are persisted in `MineNetwork`; temporary worker reservations and partial in-memory placement cursors are not.

At the Layer-5 checkpoint, `MineFrontTaskScheduler` was still the live excavation scheduler and infrastructure was integrated around it. Layer 8 supersedes that runtime arrangement: `MineNormalTaskSelector` now owns the shared normal candidate pool and persistent aging, while priority-10 passability remains the separate acute path. The Layer-5 task/placement mechanisms themselves are still reused.

## Supports

Supports are not prefabs. `Mine_Support_01.prefab.json` is a legacy asset and is not used by Layer-5 support construction.

Initial planning:

- target spacing: 6-10 tunnel slices;
- support position may move up to 3 slices before/after the target to find a better local cross-section;
- straighter local geometry is preferred over a sharp curve;
- minimum free opening inside the frame: 4 blocks wide and 3 blocks high;
- a support that cannot satisfy the minimum opening is skipped rather than blocking the corridor.

Concrete world resolution:

- side posts use `Wood_Fir_Branch_Long`;
- top beam uses `Wood_Fir_Trunk`;
- each side post independently extends downward to the first solid block below that side and starts one block above it; the solid floor/stone is never replaced;
- the top beam is placed as high as the locally open cross-section allows;
- the frame may therefore be asymmetric on uneven ground;
- posts are vertical and the trunk beam is rotated along the local tunnel cross-axis;
- build order is left post bottom-to-top, right post bottom-to-top, then the beam from its outside ends toward the centre.

Support spacing is ordinary infrastructure in the current V1. A future explicitly safety-critical support may use priority 10 without changing the shape/build mechanism.

## Stairs

Every Layer-3 one-block height transition creates a `BUILD_STEP` work unit at priority 10. Consecutive transitions therefore become a connected staircase as the miner advances, while each transition stays a small independently completable passability unit.

The adapter resolves a native stone stair/step asset from the loaded Hytale block assets, rotates it toward the rise direction and attempts to cover the three-block guaranteed corridor. Raw one-block ledges must not be crossed as finished mine infrastructure.

Exact asset IDs are resolved at runtime because this repository pins `HytaleServer.jar` but not the full `Assets.zip` catalog. Runtime resolution prefers known Stone Brick stair IDs and otherwise matches loaded stone stair/step IDs. Real NPC traversal of the selected asset still requires an in-game runtime check.

## Bridges

Bridge tasks are discovered from the actual world rather than pre-generated from tunnel geometry.

The current conservative V1 requires:

- the planned floor support at the current front to be empty;
- the previous side to have solid floor support;
- a later slice to provide a solid opposite landing;
- at least one planned continuation slice beyond that landing;
- span at most 16 slices;
- when fluid is detected below the gap, span at most 10 slices.

A bridge is priority 10 and blocks excavation beyond that point until completed.

The V1 bridge is a simple recognizable mine bridge rather than a flat plate:

- three-block-wide deck;
- Fir longitudinal beams along both sides;
- Fir cross-girders under the deck every few slices;
- no mandatory pillars to the cave floor;
- no railing in V1 because navigation safety is more important than visual complexity.

This is intentionally not yet a full natural-cave classifier. It does not distinguish every cave shape or lava-specific semantic case; the fluid span reduction is the current safety guard.

## Lighting

Lighting has priority 5.

Main tunnel:

- target spacing: 8-14 slices;
- one upright `Stone Brick Pillar - Base`;
- one lantern placed on top;
- the pillar is preferably one block inward from the wall rather than touching it;
- it is rejected if that would occupy the central guaranteed navigation corridor.

Side tunnels:

- target spacing: 7-12 slices;
- wall-mounted torch;
- placement requires an empty target cell and a solid wall to mount against.

V1 does not measure ambient light. It uses spacing and geometry so lighting remains deterministic and cheap.

## Native placement and tree physics

Fir infrastructure uses the player-like placement sequence previously verified for the V0 miner:

1. `BlockOperations.setBlock(..., flags = 256)`
2. `BlockPhysics.markDeco(...)` when the block supports Deco
3. `ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(...)`

This prevents built Fir from inheriting generated-tree cascade behaviour while keeping blocks normally breakable. `WoodcutterWorkSystem` independently rejects Deco wood from natural-tree discovery, so Hytale tree physics and Civ tree selection are protected separately.

See `docs/hytale/mine-infrastructure-building.md` for the Hytale integration evidence.

## Persistence

Mine persistence format is now `N3`. It adds completed infrastructure task IDs while continuing to read the immediately previous `N2` network format with an empty completion set.

Actual placed blocks remain Hytale-world state. Completion IDs prevent deterministic recurring tasks from being selected again after restart. Consistent with the mine product rule, player-destroyed completed infrastructure is not automatically reconstructed.

## Deferred / limitations

Deferred from the Layer-5 checkpoint were rails, rooms, decoration, material consumption, unified aging, richer cave handling and richer bridge art. Since then rooms are implemented in Layer 6, cave/hazard integration in Layer 7, and optional decoration plus unified normal-task aging in Layer 8. Still open here are rails/native minecarts, material consumption, richer bridge railings/pillars and further visual/asset tuning.
