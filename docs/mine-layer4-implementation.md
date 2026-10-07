# Mine Layer 4 - Network Growth

Status: Core planning and the tunnel-excavation runtime are implemented for main and branch fronts. Layer-5 supports/lights/steps/bridges are now implemented separately; rooms, decoration and the full general task scheduler remain deferred.

This document records the concrete implementation state of Layer 4 from `docs/mine-design.md`. The canonical product decisions remain in `docs/mine-design.md`; miner task semantics remain in `docs/miner-npc-design.md`.

## Implemented

`MineNetworkGrowthPlanner` composes the existing Layer-2 `MinePathPlanner` and Layer-3 `MineTunnelVoxelizer` into a deterministic logical network plan.

The planner supports:

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

Layer 4 does not duplicate tunnel-shape generation: every accepted logical tunnel receives its path from Layer 2 and its voxel geometry from Layer 3.

## Live tunnel-front runtime

`MinerWorkSystem` regenerates the deterministic Layer-4 plan for the assigned mine and creates or resumes one persistent `MineWorkFront` per planned main/branch tunnel. The Hytale world remains authoritative for blocks already removed; the work-front position persists the current slice of each tunnel.

The Layer-3 geometry itself is regenerated deterministically at runtime and cached by `MineTunnelRegistry` for the current world/mine. Navigation and surface recovery use this geometry for semantic tunnel membership. Runtime geometry is intentionally not a second persisted copy of excavation state.

A branch is not executable merely because it exists in the plan. Its first front becomes eligible after the connection point has physically opened in the world. Once excavation on a tunnel has started, later slices continue from the persisted work-front position.

After every completed slice the transient worker/block claims for that front are released and miners run task selection again. This implements the canonical small-work-unit rhythm without forcing one miner to finish an entire planned tunnel.

## Current miner front scheduling

`MineFrontTaskScheduler` remains the narrow Core scheduler for `EXCAVATE_FRONT` work only. Layer-5 infrastructure exists alongside it through `MineInfrastructurePlanner` and `MinerWorkSystem`; the front scheduler is not widened into a mixed world-aware scheduler.

For currently executable tunnel fronts:

- an already active normal front with free capacity is filled before a new normal front is opened;
- a normal tunnel front has capacity `2` through `MineFrontCoordinator`;
- among active fronts with free capacity, higher task priority wins, then worker distance and a stable id tie-break;
- only when no active normal front has free capacity is a waiting front opened; branch fronts use base priority `6` and the main-tunnel front base priority `4`;
- after a slice completes, workers select again.

This is miner task scheduling. It is separate from `MineWorkFrontGrowthSelector`, which remains the mine-generation fairness rule that prevents the logical main tunnel from being starved by branch growth.

## Initial tuning values

The first implementation intentionally treats these as balance values rather than persistence contracts:

- main-tunnel branch opportunity: `15%`;
- side-tunnel branch opportunity at branch depth 1: `30%`;
- deeper side-branch chance multiplier: `0.65` per additional depth;
- minimum branch-start spacing: `18` blocks;
- unrelated-tunnel rock gap: `5` blocks in addition to the two local half-widths;
- intentional crossing chance after a detected unrelated collision: `5%`;
- branch continuation starts at `95%`;
- continuation probability drops by `4` percentage points per additional 8-block continuation unit;
- continuation probability has a `20%` floor;
- branch length starts at `16` blocks and grows in 8-block units.

A branch attaches through an exempt connection throat before ordinary collision spacing applies to its parent tunnel. This exemption exists only so the child can physically leave the parent passage; later geometry is again subject to normal spacing checks.

## Main-tunnel fairness

`MineWorkFrontGrowthSelector` is a mine-generation selector, not the miner task scheduler from `docs/miner-npc-design.md`.

When both main and branch growth fronts are open:

- the main front receives an initial `40%` share of ordinary growth selections;
- after three consecutive branch selections, an available main front is forced once;
- if only one category is available, that category is selected.

This prevents a large number of side fronts from starving the mine backbone indefinitely while still allowing branches to dominate many local growth decisions.

## Collision and crossing scope

Layer 4 only decides whether planned tunnel geometry is accepted as separate rock-separated geometry or as a rare intentional connection.

It does not create a special crossing room, support structure, lighting treatment or rail junction. Those belong to later layers.

The current crossing marker is semantic metadata on the planned tunnel. Later room/infrastructure integration must translate accepted connections into concrete tasks without maintaining a second competing topology model.

## Persistence and restart

The persisted `MineNetwork` is the semantic restart state for topology, work-front positions/states and navigation anchors. Tunnel voxel geometry is regenerated from the stable mine Building-ID, while actual excavated blocks remain Hytale-world state.

Mine persistence now uses `N3`: the Layer-4 network state plus completed deterministic Layer-5 infrastructure task IDs. The immediately previous `N2` network format is read with an empty infrastructure-completion set; older incompatible pre-network records remain unsupported.

## Tests

`MineNetworkGrowthPlannerTest` covers deterministic plans, exactly one main tunnel, nested side branches, varied branch lengths, decreasing branch probability, branch-start spacing and bounded planning runs.

`MineWorkFrontGrowthSelectorTest` covers the mine-generation main-tunnel starvation safeguard.

`MineFrontTaskSchedulerTest` covers active-front fill, branch-vs-main priority, capacity spillover and unavailable fronts.

Hytale contract tests cover multi-front runtime wiring, restart progress and geometry-based navigation/surface recovery. Browser simulation recordings also derive mine geometry from the current Layer-4 planner rather than from a separate excavation model.

## Later layers / still deferred

Layer 5 now supplies supports, lighting, steps and bridges, including priority-10 passability handling. See `docs/mine-layer5-implementation.md`.

Still deferred:

- room excavation/construction task execution;
- optional decoration tasks;
- full normal-task aging and one unified general task scheduler;
- special visual geometry for intentional crossings;
- rails;
- final balance tuning;
- a terrain-aware replacement for the temporary surface-recovery Y heuristic.
