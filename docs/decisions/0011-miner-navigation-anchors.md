# 0011 - Miner navigation anchors and native recovery

Status: accepted for implementation

## Context

The mine needs reliable NPC navigation through a persistent, branching underground network without introducing a Civ-owned voxel pathfinder. Earlier planning assumed that navigation anchors could be derived from planned tunnel geometry and used a 20-block re-entry threshold from the mine connector. Both assumptions were replaced during the navigation design review.

Research against the pinned `HytaleServer.jar` confirms that Hytale already exposes native NPC navigation state through the active `MotionController`, including `NavState.BLOCKED`, `NavState.AT_GOAL` and `NavState.ABORTED`, and supports requesting a native path recomputation through `setForceRecomputePath(true)`. `BlockType.EMPTY` is also available as the canonical empty block type.

## Decision

### Anchor creation

Navigation anchors are evidence of successfully traversed world space, not projections from the mine planner.

A regular anchor may be created only when:

- a miner has actually traversed the candidate block;
- the candidate block is exactly `BlockType.EMPTY`;
- the candidate is approximately 10 blocks from surrounding existing anchors.

Semantic anchors such as junction, room, bridge-start and bridge-end anchors follow the same physical validation rule and only add semantic meaning after the point has been traversed successfully.

The current moving work position is not represented by a moving `WORK_FRONT` anchor. The existing dynamic miner work target remains responsible for the final local approach to an excavation front.

### Routing responsibility

Civ owns the small semantic graph of known-safe anchors and chooses the shortest currently valid semantic route. Hytale remains responsible for the physical path between the selected targets. This graph routing is not a voxel pathfinder.

Connections are normally bidirectional when the physical passage is safe. A bridge connection does not become active until the finished bridge has actually been traversed safely.

### Long-distance travel

The long-distance threshold is 50 blocks measured as Euclidean air-line distance, not semantic-route distance.

A miner returning from above ground must always walk normally through `workplace_access` to `mine_tunnel_connector`. Long-distance teleport is not allowed before the connector has been reached.

Once underground, if the relevant safe reference point is more than 50 blocks air-line from the next work target, Civ may teleport the miner to an already known safe anchor on the valid target route. The preferred teleport destination is the reachable safe anchor on that route closest to the target. The miner is never teleported directly to the work position; Hytale handles the remaining local movement.

The same rule may be used in reverse for long underground travel toward the surface, while the actual mine exit remains routed through the tunnel connector.

### Native navigation failure

Native Hytale navigation state is the primary failure signal. Civ must not introduce a generic stall timeout as the first-line detector.

For an active movement target:

1. normal native navigation runs;
2. on `BLOCKED` or `ABORTED`, request one native path recomputation;
3. if navigation still reports a terminal failure after that retry, apply Civ recovery.

`DEFER` is not treated as terminal failure without runtime evidence that it means failure in this role configuration.

Recovery differs by semantic area:

- main corridor and work directly in it: return to the last known-safe anchor and retry;
- side tunnels, rooms and bridge areas: after recovery fails, release the work and select another task.

The second case depends on the later multi-task miner scheduler and must not be simulated by inventing a second task system in the navigation adapter.

### Surface recovery

The existing `MinerSurfaceRecoverySystem` remains a separate emergency watchdog for accidental surface escape. It is not the implementation of normal 50-block travel or normal native navigation recovery.

## Consequences

- Planned tunnel geometry alone cannot create trusted anchors.
- Anchor persistence is small semantic state; physical paths remain Hytale-owned.
- The Hytale adapter needs to observe actual miner positions, exact `BlockType.EMPTY`, active `MotionController` state and native teleport/repath facilities.
- Core policy remains Hytale-independent and can unit-test spacing, air-line thresholds and semantic graph selection.
- Runtime testing is still required for the exact timing of native `NavState` transitions before relying on fine-grained retry timing beyond the verified API contract.
