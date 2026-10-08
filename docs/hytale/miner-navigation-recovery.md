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

## Planned geometry and tunnel membership

The live mine runtime regenerates the deterministic Layer-3 `MineTunnelGeometry` for every planned main/branch tunnel after the mine is loaded for work. `MineTunnelRegistry` keeps that geometry only as runtime data keyed by world, mine and tunnel.

`MinerNavigationSystem` uses those geometry volumes to identify which semantic tunnel contains the miner and which tunnel is closest to a movement target. It no longer derives tunnel membership from legacy `MineSegment` bounds or `MineTunnel.segmentIds`.

This geometry is not treated as proof that a block is safe. It only supplies semantic tunnel ownership. Hytale remains authoritative for the actual world, and trusted navigation anchors still require observed traversal of an exact empty block.

`MinerSurfaceRecoverySystem` uses the same regenerated geometry to avoid treating planned/excavated underground tunnel positions as surface escapes. Its existing Y-level heuristic remains a temporary emergency watchdog rather than normal mine navigation.

## Anchor world validation

`BlockType.EMPTY` exists as the canonical empty block type in the pinned API. Navigation anchors intentionally require exact `BlockType.EMPTY`; empty-material blocks, half slabs, barrels, decoration or other passable-looking blocks are not accepted merely because native navigation can cross them.

Anchor creation therefore combines two facts:

- the miner actually traversed the block;
- the world block at that traversed position is exactly `BlockType.EMPTY`.

The mine planner does not manufacture trusted anchors from planned geometry.

## Teleport execution

The existing miner surface recovery already uses Hytale's ECS `Teleport` component. Long-distance anchor travel uses the same native teleport mechanism while preserving the current movement target, so native Hytale navigation resumes for the local remainder.

The surface-recovery watchdog remains separate from normal long-distance mine travel and from native navigation-failure recovery.

## Related native mine capabilities

Research for this layer also confirmed reusable native systems for later mine work:

- `BlockHarvestUtils` for block break/harvest semantics (already used by `MinerWorkSystem`);
- `ResourceView` / NPC block reservation primitives for short-lived block claims;
- native NPC block placement helpers/actions for supports, lights, steps and bridges;
- native fluid data, prefab placement, animations, rails/minecarts and trigger volumes.

These should be preferred over Civ-owned engine replacements when their runtime semantics fit the required feature.

## On-demand elevation repair (2026-10-08, awaiting runtime verification)

Eager stair planning remains disabled. When Hytale reports a terminal navigation failure for an assigned excavation front, the mine worker may instead create one mandatory `BUILD_STEP` for the transition ending in the previous (already excavated) slice. Both adjacent authored slices must be fully excavated and the task must not already exist. The same native placement resolver handles the actual block placement. A failure without an eligible adjacent elevation transition retains the existing `BLOCKED` outcome, and a failed mandatory repair still follows the normal failure policy. This does not provide general-purpose path carving; any repair outside existing authored mine geometry remains unsupported.

`MineInfrastructurePlanner.recoveryStepTask` uses the original deterministic step task ID and priority 10; it does not re-enable `ENABLE_MINE_STEPS` globally. This behavior is code-verified only and requires focused gameplay validation for actual Hytale stair collision/navigation semantics.
