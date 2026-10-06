# Mine Layer 4 - Network Growth

Status: implemented Core planning foundation; live Hytale miner execution still uses the pre-overhaul `MineSegment` runtime path.

This document records the concrete implementation state of Layer 4 from `docs/mine-design.md`. The canonical product decisions remain in `docs/mine-design.md`; miner task semantics remain in `docs/miner-npc-design.md`.

## Implemented

`MineNetworkGrowthPlanner` composes the existing Layer-2 `MinePathPlanner` and Layer-3 `MineTunnelVoxelizer` into a deterministic logical network plan.

The planner now supports:

- one persistent logical main tunnel;
- side tunnels from the main tunnel;
- side tunnels from side tunnels with no hard branch-depth limit;
- decreasing branch-creation probability as branch depth increases;
- branch lengths produced through a decreasing continuation probability rather than a fixed maximum;
- minimum spacing between branch start points;
- collision rejection using planned tunnel centerline width plus a rock safety margin;
- rare intentional crossings when an otherwise valid candidate approaches existing unrelated mine geometry;
- a technical caller-supplied tunnel planning budget so tests/tools can bound one planning run without creating a gameplay branch-depth rule;
- deterministic output for a fixed seed.

Layer 4 does not duplicate tunnel-shape generation: every accepted logical tunnel still receives its path from Layer 2 and its voxel geometry from Layer 3.

## Initial tuning values

The first implementation intentionally treats these as balance values rather than persistence contracts:

- main-tunnel branch opportunity: `15%`;
- side-tunnel branch opportunity at branch depth 1: `30%`;
- deeper side-branch chance multiplier: `0.65` per additional depth;
- minimum branch-start spacing: `18` blocks;
- unrelated-tunnel rock gap: `5` blocks in addition to the two local half-widths;
- intentional crossing chance after a detected unrelated collision: `5%`;
- branch continuation starts at `95%`;
- continuation probability drops by `8` percentage points per additional 8-block continuation unit;
- continuation probability has a `20%` floor;
- branch length starts at `16` blocks and grows in 8-block units.

A branch attaches through an exempt connection throat before ordinary collision spacing applies to its parent tunnel. This exemption exists only so the child can physically leave the parent passage; later geometry is again subject to normal spacing checks.

## Main-tunnel fairness

`MineWorkFrontGrowthSelector` is a mine-generation selector, not the miner task scheduler from `docs/miner-npc-design.md`.

When both main and branch fronts are open:

- the main front receives an initial `40%` share of ordinary growth selections;
- after three consecutive branch selections, an available main front is forced once;
- if only one category is available, that category is selected.

This prevents a large number of side fronts from starving the mine backbone indefinitely while still allowing branches to dominate many local growth decisions.

Miner task priority, aging, reservation capacity and worker assignment remain owned by the miner scheduler and are not reimplemented here.

## Collision and crossing scope

Layer 4 only decides whether planned tunnel geometry is accepted as separate rock-separated geometry or as a rare intentional connection.

It does not create a special crossing room, support structure, lighting treatment or rail junction. Those belong to later layers.

The current crossing marker is semantic metadata on the planned tunnel. Later runtime integration must translate accepted connections into the concrete excavation/work-front sequence without maintaining a second competing topology model.

## Tests

`MineNetworkGrowthPlannerTest` covers:

- deterministic plans for equal seeds;
- exactly one main tunnel;
- nested side branches over deterministic long-run seed sets;
- varied branch lengths including occasional 100+ block branches;
- decreasing branch probability with depth;
- configured branch-start spacing;
- bounded planning runs.

`MineWorkFrontGrowthSelectorTest` covers the main-tunnel starvation safeguard and empty-front behaviour.

## Explicitly deferred

This layer does not add:

- Hytale world excavation for the new geometry;
- live miner task creation from the new planned network;
- persistence wiring for the Layer-4 planning seed/state;
- rooms;
- caves, fluids or bridges;
- supports, lighting or decoration changes;
- rails;
- special visual geometry for intentional crossings;
- final balance tuning.

The existing live `MinerWorkSystem` therefore remains on the old 4x4 `MineSegment` execution path until a later integration step explicitly replaces that runtime truth with the Layer-2/3/4 model.
