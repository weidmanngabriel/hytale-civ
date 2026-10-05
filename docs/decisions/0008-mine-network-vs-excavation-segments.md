# ADR 0008: Separate mine topology from excavation segments

## Status

Accepted

## Context

The pre-overhaul miner uses `MineSegment` both as the unit of excavation progress and, through `parentId`, as an implicit tunnel tree. The new mine design requires one persistent main tunnel, nested side tunnels, rooms, multiple work fronts and navigation anchors. Later layers also need one logical tunnel to contain many geometry/form phases.

Treating every new excavation segment as a new branch would make geometry decisions define domain topology and would prevent a logical tunnel from evolving through many segments without becoming a false branch tree.

## Decision

Introduce a Hytale-independent `MineNetwork` aggregate in Core.

- `MineTunnel` owns logical `MAIN` / `BRANCH` identity and branch parentage.
- A logical tunnel can reference multiple concrete `MineSegment` IDs.
- `MineRoom`, `MineWorkFront` and `MineNavigationAnchor` reference logical tunnel IDs.
- `MineSegment` remains the current concrete excavation/progress representation until later geometry layers replace or generalize it.
- Segment parentage may remain temporarily for the existing worker sequence, but it is not the authoritative branch topology.
- Hytale world blocks remain authoritative for actual excavated geometry; the network persists only Civ-owned semantic state.

The current Layer-1 integration assigns segments produced by the existing miner to the logical main tunnel unless a caller explicitly selects another logical tunnel. Real branch creation and work-front selection remain Layer 4 responsibilities.

## Consequences

The domain can represent nested branches before visual generation exists, while later form/geometry work can change segment shapes without redefining what a branch is. Persistence stores a small semantic network in addition to the current excavation progress instead of duplicating the block world.

Existing development saves containing only segment data are normalized on load into a single deterministic main-tunnel network, so runtime code does not maintain parallel legacy and new topology models.
