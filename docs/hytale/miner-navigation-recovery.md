# Miner navigation anchors and recovery

This note records the Hytale-specific contracts verified for the mine navigation layer. Product rules remain in `docs/mine-design.md` / `docs/miner-npc-design.md`; this file documents the adapter-facing engine facts.

## Verified against the pinned HytaleServer.jar

The pinned server exposes native navigation state through `com.hypixel.hytale.server.npc.movement.NavState`:

- `INIT`
- `PROGRESSING`
- `BLOCKED`
- `DEFER`
- `AT_GOAL`
- `ABORTED`

`com.hypixel.hytale.server.npc.movement.controllers.MotionController` exposes:

- `getNavState()`
- `getThrottleDuration()`
- `getTargetDeltaSquared()`
- `setForceRecomputePath(boolean)`
- `isForceRecomputePath()`

`com.hypixel.hytale.server.npc.role.Role` exposes `getActiveMotionController()`, so the Civ Hytale adapter can inspect native navigation status without implementing a parallel movement detector.

The exact runtime timing of transitions between these states is not proven by signatures alone. In particular, `DEFER` is not treated as a terminal navigation failure until a focused runtime observation establishes that meaning for the Civ inhabitant role.

## Recovery contract

For a Civ miner with an active move target:

1. Hytale pathfinding remains authoritative.
2. `BLOCKED` or `ABORTED` causes one native `setForceRecomputePath(true)` request.
3. A later terminal failure after that retry is passed to Civ recovery policy.
4. No generic elapsed-time stall detector is the primary failure signal.

A small watchdog may only be added later if runtime evidence shows a native stuck case that never reaches a useful `NavState`.

## Anchor world validation

`BlockType.EMPTY` exists as the canonical empty block type in the pinned API. Navigation anchors intentionally require exact `BlockType.EMPTY`; empty-material blocks, half slabs, barrels, decoration or other passable-looking blocks are not accepted merely because native navigation can cross them.

Anchor creation therefore combines two facts:

- the miner actually traversed the block;
- the world block at that traversed position is exactly `BlockType.EMPTY`.

The mine planner does not manufacture trusted anchors from planned geometry.

## Teleport execution

The existing miner surface recovery already uses Hytale's ECS `Teleport` component. Long-distance anchor travel should use the same native teleport mechanism while preserving the current movement target, so native Hytale navigation resumes for the local remainder.

The surface-recovery watchdog remains separate from normal long-distance mine travel and from native navigation-failure recovery.

## Related native mine capabilities

Research for this layer also confirmed reusable native systems for later mine work:

- `BlockHarvestUtils` for block break/harvest semantics (already used by `MinerWorkSystem`);
- `ResourceView` / NPC block reservation primitives for short-lived block claims;
- native NPC block placement helpers/actions for supports, lights, steps and bridges;
- native fluid data, prefab placement, animations, rails/minecarts and trigger volumes.

These should be preferred over Civ-owned engine replacements when their runtime semantics fit the required feature.
