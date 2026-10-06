# Mine NPC Layer 6 - obstacles and failure cases

Status: implemented V1 for unreachable work targets, unsafe cave/gap/liquid encounters, infrastructure resolution failure and safe task release.

Product rules remain canonical in `docs/mine-design.md` and `docs/miner-npc-design.md`.

## Implemented behavior

- Hytale remains the physical pathfinder.
- `BLOCKED` / `ABORTED` first requests one native path recomputation.
- A second terminal result for the same work target is handed from `MinerNavigationSystem` to `MinerWorkSystem` through a transient `MinerNavigationFailureRegistry`.
- An unreachable excavation front or mandatory infrastructure marks its associated persistent `MineWorkFront` as `BLOCKED`.
- Normal support/light work that is unreachable is skipped without blocking the tunnel.
- `BLOCKED` is not automatically retried in V1.
- Unsafe/unusable geometry and unresolved mandatory bridge/step work produce `ABANDONED`.
- `BLOCKED` and `ABANDONED` are restored into the runtime plan after restart and are excluded from automatic selection.

## Caves and gaps

Already-empty planned slices remain valid when the navigation corridor has usable floor and no fluid occupying it. This lets a planned tunnel pass through a small natural cave without inventing a separate cave subsystem.

A missing-floor run becomes a bridge candidate only when:

- the previous side provides a solid approach;
- the gap stays within the accepted span;
- a solid opposite landing exists;
- at least one planned continuation slice exists beyond that landing;
- the crossing is not lava.

V1 maximum span is 16 slices without fluid and 10 slices with non-lava fluid below. Larger/invalid gaps abandon the work front.

Large natural cave integration as a semantic chamber/node is deliberately deferred.

## Water and lava

World fluid state is read only from loaded chunks through Hytale `WorldChunk.getFluidId(...)`.

Lava is classified from the loaded native `Fluid` asset with `Fluid.hasEffect(ShaderType.Lava)`; no numeric lava ID is hard-coded.

V1 rules:

- water/non-lava fluid below an otherwise valid short gap may be bridged;
- fluid inside the actual navigation corridor is not swum through and abandons the front;
- lava on/below the required route abandons the front;
- no pumping, filling, redirection or lava bridge is implemented.

## Infrastructure failure

Mandatory bridge/step work remains tied to its obstacle. If its concrete placement cannot be resolved safely, the associated front becomes `ABANDONED`.

Normal supports and lighting use the Core `MineObstaclePolicy.fallbackSliceOrder(...)`: preferred slice first, then nearest alternatives up to ±3 slices. If no valid placement exists, the task is persisted as skipped/completed so it does not loop forever.

If placement fails after some blocks were already placed, those world blocks remain. The placement is re-resolved from current Hytale world state. V1 performs no rollback transaction.

## Architecture

`MineObstaclePolicy` owns the Hytale-independent mapping from failure kind to persistent front state and the bounded fallback ordering.

`MinerNavigationFailureRegistry` is transient adapter coordination only. It contains no persistent gameplay truth and no routing logic.

`MinerWorkSystem` owns the task/front state transition and reservation release. `MineInfrastructurePlacementResolver` owns world-sensitive concrete placement. Hytale owns movement, loaded-world block/fluid data and physical block placement.

## Tests

Coverage includes:

- Core state mapping for navigation versus unsafe geometry;
- ±3 nearest-first fallback ordering;
- adapter contract for native retry -> terminal failure handoff;
- adapter contract for lava/gap abandonment;
- mandatory infrastructure resolution failure;
- optional infrastructure fallback versus fixed mandatory placement.

The normal completion gates remain `./gradlew test` and `./gradlew build`.
