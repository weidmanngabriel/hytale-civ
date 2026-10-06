# Miner NPC Design

Status: canonical planning specification for Plan Phase 1 miner NPC behaviour.

This document defines the intended miner behaviour from the player's point of view before implementation. It complements `docs/mine-design.md` and is the source of truth for miner state flow, task selection, task priority, reservations, room work, interruption handling and the contract with the mine system until explicitly changed.

The design goal is an autonomous miner that looks purposeful and local rather than globally scripted. Civ decides what the miner should do, why and where. Hytale handles actual movement and pathfinding wherever possible.

## 1. Scope

Plan Phase 1 supports at most three miners assigned to one mine.

This document covers:

- miner states and normal workflow;
- task types and work units;
- task priority and aging;
- distribution of multiple miners across tasks;
- task reservation and capacity;
- interruption and reprioritization rules;
- safety and blocked-work outcomes;
- room excavation and room construction;
- infrastructure tasks;
- navigation-anchor protection;
- restart/persistence behaviour for unfinished work;
- the boundary between miner logic, mine logic and Hytale execution.

This document does not define detailed tunnel generation, room geometry, exact prefab construction algorithms, support placement formulas, lighting spacing or native Hytale navigation implementation.

## 2. Responsibility boundary

The miner system and mine system work together but own different responsibilities.

### 2.1 Mine system

The mine system owns the semantic state of the mine and exposes meaningful work opportunities, for example:

- open main-tunnel fronts;
- open side-tunnel fronts;
- room excavation work;
- room construction work;
- required supports;
- due lights;
- required steps;
- required bridges;
- optional decoration work;
- blocked, abandoned or completed fronts;
- valid work areas and placement positions.

The mine system also owns the geometry constraints that make a task valid.

### 2.2 Miner system

The miner system owns:

- whether a miner is idle, moving or working;
- selecting one currently valid task;
- reserving capacity on that task;
- executing one defined work unit;
- reporting success, interruption or blockage back to the mine system;
- selecting again after each completed work unit.

The miner system must not duplicate mine-generation or mine-topology logic.

### 2.3 Hytale adapter

Hytale owns the execution mechanics:

- movement;
- native pathfinding;
- animation;
- world interaction;
- block breaking and placement;
- reporting arrival, success or failure back to Civ.

Core decides who acts, why, and toward which semantic/world target. Hytale decides how that movement or interaction is physically carried out.

## 3. Miner state model

Plan Phase 1 keeps the actual miner state machine deliberately small.

### 3.1 Persistent autonomous states

The miner has three persistent autonomous states:

- `IDLE`
  - no currently suitable work is available;
- `MOVING_TO_TASK`
  - a task has been selected and reserved and the miner is moving toward its work area;
- `WORKING`
  - the miner is executing one defined work unit of the selected task.

### 3.2 Choosing a task

`CHOOSING_TASK` is a Core decision step, not a long-lived gameplay state.

It occurs:

- when an idle miner gains available work;
- after a completed work unit;
- after a manual command ends;
- after a task is interrupted;
- after a safety/blockage result;
- after a task becomes invalid.

The expected flow is:

`IDLE -> choose task -> MOVING_TO_TASK -> WORKING -> choose task -> ...`

If no suitable task exists after selection, the miner enters `IDLE`.

### 3.3 Manual player commands

Manual player control is not a miner-specific state.

It is an overriding inhabitant activity handled by the shared NPC activity system. While manual control is active, autonomous miner work is suppressed.

When manual control ends, the miner does not rigidly return to the previous task. Normal task selection runs again using the current mine state.

### 3.4 Safety blocked

`SAFETY_BLOCKED` is not a persistent miner state.

It is a task outcome meaning the current work cannot safely continue and no already-known direct solution can be executed as part of the current work.

After this result:

1. the affected mine task/front is updated by the mine system;
2. the miner releases the reservation;
3. task selection runs again.

The miner should not remain standing indefinitely in a dedicated blocked/waiting state.

## 4. Work rhythm and reprioritization

A miner normally finishes the currently started small work unit before reprioritizing.

The key rule is:

> After every completed work unit, the miner selects again.

A miner does not reconsider after every individual block, because that would look nervous and inefficient.

A miner also does not blindly continue an entire long planning segment without reconsideration.

Normal priority changes from `1` through `9` wait until the current work unit is complete.

Priority `10` is different: it represents mandatory safety/passability work and may interrupt an active normal work unit immediately. Only as many miners are redirected as the priority-10 task still has free capacity for. Prefer miners that are already idle or selecting; if working miners must be interrupted, prefer miners currently assigned to lower-priority normal work, then use shorter distance as the next discriminator.

Manual player commands also interrupt autonomous work immediately.

## 5. Task types

Plan Phase 1 uses the following semantic miner task types.

### 5.1 `EXCAVATE_FRONT`

Excavate one normal tunnel front / slice.

Main tunnel and side tunnel are not separate task types. The task references the tunnel/front metadata that identifies whether it belongs to the main tunnel or a side tunnel.

The work front uses the actual planned tunnel geometry. It is not defined as a fixed 4x4 surface: main tunnels and side tunnels may have different and gradually changing dimensions according to `docs/mine-design.md`.

### 5.2 `EXCAVATE_ROOM`

Excavate one defined room work unit.

The whole room is not treated as one indivisible task execution. Room excavation is divided into smaller slices so priorities can be reconsidered regularly.

### 5.3 `BUILD_ROOM`

Construct one defined build section of a room prefab or room furnishing.

This is separate from excavation because a room may first need to be excavated and later have its actual workshop, break-room, storage or similar prefab/components built.

### 5.4 `BUILD_SUPPORT`

Build one required mine support.

### 5.5 `PLACE_LIGHT`

Place one due light at a valid placement location.

### 5.6 `BUILD_STEP`

Create a safe passable step treatment for a local elevation change when required.

### 5.7 `BUILD_BRIDGE`

Build a bridge section required to cross an accepted bridgeable gap.

### 5.8 Optional decoration

Optional mine-decoration work may exist as lower-base-priority tasks.

Decoration is still real work and must eventually be performed rather than remaining permanently starved.

## 6. Work-unit completion

### 6.1 Tunnel fronts

The normal excavation work unit is one complete tunnel front / excavation slice using the current planned cross-section.

One or more miners may share that same work unit up to the task capacity. They work on different still-open parts/blocks of the shared slice; two miners must never claim the same block-level sub-work at the same time.

A miner that is assigned to the front normally stays with that shared work unit until the front completes, unless interrupted by a manual command, invalidation/blockage or priority `10` work.

A possible future continuation of the tunnel is not already an active task simply because the previous front completed. The next front becomes relevant only through the next task-selection decision.

There is no special concept of a "half-finished main tunnel" for task priority.

If a tunnel front is interrupted, completed block work remains completed and the unfinished shared front remains available. Another miner may continue it later.

### 6.2 Room excavation

Room excavation is divided into small geometric slices, initially targeting roughly 1-2 blocks of depth per work unit where suitable.

The exact shape and slice generation belong to the mine system.

After each completed room slice, the miners assigned to it select again.

### 6.3 Room construction

Room construction is divided into small meaningful build sections rather than placing the entire room prefab as one indivisible miner action.

The exact sectioning belongs to the room/prefab system.

After each completed build section, the miners assigned to it select again.

### 6.4 Infrastructure

A support, light, step or bridge task completes when its defined infrastructure work unit is successfully constructed and reported back to the mine system.

Layer-5 V1 implements these four task types in live miner work. A single infrastructure task has capacity one. The miner navigates to its work area and places the resolved structure block-by-block at 0.5 seconds per block. Completion is persisted at mine level; temporary worker reservations are not.

The current implementation integrates infrastructure around the existing narrow excavation-front scheduler rather than pretending that the full general task scheduler already exists. Priority-10 steps/bridges can block and interrupt normal excavation on their tunnel. Normal support/light work is selected at normal task-selection boundaries. Full cross-category aging and room/decor scheduling remain future work.

## 7. Task priority

Plan Phase 1 uses a simple 10-point base-priority system.

| Base priority | Task category |
| --- | --- |
| `10` | mandatory safety / passability work |
| `8` | room work (`EXCAVATE_ROOM`, `BUILD_ROOM`) |
| `6` | side-tunnel excavation |
| `5` | lighting |
| `4` | main-tunnel excavation |
| `2` | optional decoration |

Required supports, bridges or steps that are necessary for safe continuation belong to priority `10`.

Recurring supports that are not required for immediate passability are ordinary infrastructure in Layer-5 V1; lights remain priority `5`. A later safety analysis may promote a specific support instance to priority `10` without changing the `BUILD_SUPPORT` task type.

Priority `10` is the only acute priority class in V1. Normal jobs can never age to `10`.

The distinction between a side tunnel and the main tunnel is therefore explicit: ordinary side-tunnel work is preferred over ordinary main-tunnel work when a new normal task must be opened.

Rooms remain higher priority than both.

## 8. Aging

Tasks that remain available but are repeatedly skipped gain priority over time so lower-base-priority work does not remain forever.

Rule:

- every time normal task selection opens/chooses among waiting work, an available, executable waiting task that is not chosen may increase its current priority by `+1`;
- only tasks that are actually available and executable age;
- a task does not age while at least one miner is actively assigned to it;
- blocked, invalid or not-yet-unlocked work does not gain aging merely because time passes;
- normal task priority is capped at `9`;
- priority `10` remains reserved for mandatory safety/passability work.

Aging is per concrete task, not global per task type.

This means, for example, a specific decoration or lighting task can eventually overtake normal excavation if it has repeatedly been skipped, but never becomes acute priority `10`.

## 9. Task selection order

Task selection is local to the miner's assigned mine. Miners assigned to another mine, including mines owned by another faction, are outside this selection pool.

Use the following order:

1. If executable priority-`10` work exists, fill that work first up to its capacity.
2. Otherwise, if one or more already-active normal tasks still have free capacity, prefer joining those active tasks before opening another normal task.
3. Among active tasks with free capacity, choose the highest current priority.
4. If those priorities are equal, prefer the nearer suitable task.
5. If they are still effectively equal, use a stable or random tie-breaker; implementation should prefer a deterministic tie-breaker where that materially improves tests without creating visibly rigid behaviour.
6. Only when no active normal task has free capacity does the miner open/select from waiting normal tasks, starting with the highest current priority, then distance, then the final tie-breaker.

This rule intentionally makes active-work completion stronger than normal priority differences from `1` through `9`: for example, a miner may join an already-active priority-`2` task with free capacity instead of opening a new priority-`9` task. Priority `10` is the explicit exception.

Normal priority therefore primarily controls which new work is opened once already-active work is full.

## 10. Multiple miners, task capacity and reservations

Plan Phase 1 supports at most three miners per mine.

Coordination is per mine, not global. V1 has no persistent `Team` object and no cross-mine scheduling. Miners temporarily form a working group only by selecting the same task.

A task may allow more than one miner to work on it. Therefore tasks use a capacity rather than a simple exclusive reservation flag.

A miner reserves one capacity position when it selects the task.

### 10.1 Initial V1 capacities

- normal tunnel front: maximum `2` miners;
- room excavation: up to `3` miners;
- room construction: typically up to `2` miners;
- individual infrastructure tasks: normally `1` miner;
- individual decoration tasks: normally `1` miner.

Room/task-specific design may lower a capacity when the available geometry makes fewer simultaneous workers sensible.

The mine building phase remains the outer worker limit for the mine: Phase 1 = `1`, Phase 2 = `2`, Phase 3 = `3` simultaneously assigned/working miners once capacity enforcement is implemented.

### 10.2 Natural distribution

The system does not hard-code squad patterns such as `2+1` or `1+1+1`.

Those distributions emerge naturally from:

- priority-10 requirements;
- active-task fill preference;
- task priorities;
- available task capacities;
- reservations;
- distance tie-breaking;
- which tasks currently exist.

For example, one miner can open a tunnel front, a second miner can automatically join it because one capacity position is still free, and a third miner can open the highest-priority waiting work after that front reaches capacity.

### 10.3 Shared front work

When multiple miners share an excavation front, they must contribute real parallel work rather than merely being logically attached to the same task while only one miner advances progress.

Block-level sub-work inside that shared front is claimed dynamically so that miners work on different still-open blocks. These claims are short-lived execution coordination, not permanent left/right work slots.

If one miner leaves or is interrupted, the other miner continues the same front. The released capacity position becomes available to another miner the next time that miner selects work.

### 10.4 No fixed per-miner standing slots in V1

A shared task defines a work area, not a permanent individual standing position for each miner.

The task capacity limits how many miners may work there, while Hytale/native execution may find suitable local positions within the work area.

Dedicated per-miner standing slots are deferred unless tests show that miners consistently collide, stack or block each other.

## 11. Reservation lifecycle

A reservation is created only after task selection has actually chosen the task.

The reservation remains while the miner is:

- `MOVING_TO_TASK`;
- `WORKING` on that task.

The reservation is released when:

- the work unit completes;
- a manual player command interrupts the miner;
- a priority-`10` reassignment interrupts normal work;
- a safety/blockage result ends the current work;
- the task becomes invalid;
- the NPC disappears;
- the NPC changes profession or otherwise stops being eligible for the task.

Plan Phase 1 does not add an arbitrary time-based reservation timeout.

A reliable explicit native navigation failure result releases the reservation and triggers fresh task selection once the multi-task scheduler owns that task lifecycle. The current navigation adapter uses Hytale `NavState` as the primary failure signal instead of an arbitrary stall timer.

## 12. Safety and blocked work

A miner must never blindly continue excavation into unsafe or unclassified space.

When a problem is detected, distinguish between two cases.

### 12.1 Known solvable problem

If mine logic already knows a direct safe solution, the problem becomes a mandatory task, for example:

- required bridge;
- required step;
- required support;
- another explicitly defined passability fix.

The current work stops at the safe boundary and task selection can select the mandatory solution at priority `10`.

### 12.2 Not safely solvable

V1 distinguishes two persistent front outcomes:

- `BLOCKED` means the front is currently unusable but may become usable again through an explicit future recovery/unblock mechanism. V1 does not periodically retry blocked fronts.
- `ABANDONED` means the mine has concluded that this route has no safe V1 continuation. Autonomous miners no longer select it.

A terminal native navigation failure after the single Hytale recompute attempt produces `BLOCKED`, because the world may later be changed by the player or another system.

A known unsafe/unusable geometry result produces `ABANDONED`, including:

- a non-bridgeable gap;
- a gap without a safe opposite landing or planned continuation;
- lava on the required route or below a required crossing;
- water/fluid occupying the walkable corridor where V1 would require swimming;
- mandatory bridge/step work that cannot be resolved safely;
- another explicitly classified unsafe geometry result.

Natural open cave space with safe floor is not an error: already-empty tunnel slices may be crossed and excavation continues at the next solid face. Large-cave integration as a dedicated room/node is deferred.

The miner releases the task and selects again rather than waiting indefinitely.

## 13. Rooms

At most two rooms may be active at the same time in the current mine design.

Room work as a category has base priority `8`, regardless of whether the current room phase is excavation or construction.

There is no separate priority boost for `BUILD_ROOM` over `EXCAVATE_ROOM`; the room lifecycle determines which phase is currently available.

A simple semantic room lifecycle may expose states such as:

`PLANNED -> EXCAVATING -> READY_TO_BUILD -> BUILT`

The exact internal room model remains owned by the mine/room system.

## 14. Infrastructure

### 14.1 Supports

A support that is required for safe continuation is mandatory priority `10` work.

The exact support shape, orientation and placement formula belong to the mine system.

### 14.2 Steps

A step task is mandatory when a local elevation difference would otherwise make the intended route unsafe or unusable.

Do not automatically build elaborate stairs for every vertical change. The mine system determines the appropriate geometry.

### 14.3 Bridges

A bridge is mandatory priority `10` when safe continuation requires crossing an accepted bridgeable gap.

Ordinary excavation must not continue through the unsafe gap before the bridge work is complete.

### 14.4 Lighting

Lighting has base priority `5`.

It is intentionally important but does not normally interrupt a currently active work unit.

Because lighting participates in aging while waiting, a repeatedly skipped light task will rise in priority and eventually be performed.

### 14.5 Decoration

Optional decoration begins at base priority `2` but also participates in aging while waiting.

Decoration may therefore eventually overtake ordinary excavation, but it can never age to mandatory safety priority `10`.

### 14.6 Placement failure and nearby fallback

Normal infrastructure such as recurring supports and lighting first attempts its planned slice. If that exact location cannot be resolved safely, V1 checks nearby slices in nearest-first order up to three slices before/after the planned point. Navigation clearance and existing world geometry still win over placement. If no valid nearby position exists, the normal task is skipped.

Mandatory passability work does not use this fallback. A bridge or step is tied to the obstacle it solves. If mandatory infrastructure cannot be resolved safely, the associated front becomes `ABANDONED`.

If block placement fails after construction has already started, already placed world blocks remain. The task is re-resolved against current world state once work continues. Normal infrastructure may then use the same bounded nearby fallback; mandatory infrastructure that remains unresolvable abandons the associated front. V1 performs no automatic rollback of already placed blocks.

## 15. Navigation anchors and placement protection

Navigation anchors are shared safe semantic points for the mine. They support movement and navigation but do not replace Hytale pathfinding.

An anchor becomes trusted only after a miner has actually traversed its block and that block is exactly Hytale `BlockType.EMPTY`. Planned tunnel geometry alone cannot create a trusted anchor. Regular candidates are accepted at approximately 10-block spacing; a candidate closer than 10 blocks to an existing anchor is skipped.

Junction, room-access, bridge-start and bridge-end anchors use the same traversal and `EMPTY` validation and only add semantic meaning. A moving excavation face is not represented by a moving `WORK_FRONT` anchor in V1; the existing dynamic work target handles the final local approach.

Placed mine objects must not obstruct anchor navigation.

V1 placement rule:

- never place a new blocking object directly on an anchor point;
- keep the immediate horizontal area around an anchor clear where possible;
- treat roughly one block of horizontal clearance around the anchor as the initial protection target;
- protect the usable walking corridor rather than blindly reserving a full `3x3x3` cube;
- if a planned placement conflicts with the protected navigation area, the mine system should choose another valid placement position;
- if no valid alternative exists, preserving the anchor/navigation corridor wins over placing the optional object.

This applies to placed infrastructure, decoration and room/prefab construction where relevant.

The miner should normally receive an already-valid placement target from the mine system rather than independently solving anchor-clearance geometry.

### 15.1 Autonomous travel and re-entry

After a miner has been brought out of the mine and autonomous work resumes, the staged route remains:

`workplace_access -> mine_tunnel_connector -> work position`

The miner must physically walk from above ground through `workplace_access` to `mine_tunnel_connector`. No long-distance teleport is allowed before the connector has been reached.

Long-distance travel uses a threshold of **more than 50 blocks measured as Euclidean air-line distance**. At the connector, the distance reference is the connector. For a miner already underground, use the current relevant known-safe anchor/reference point.

When the next work target is farther than that threshold, Civ may teleport the miner to an already known, reachable safe anchor on the valid target route. Prefer the reachable anchor on that route that is closest to the work target. Never teleport directly to the work position. From the selected anchor, Hytale-native navigation handles the remaining local route.

The same rule may be used in reverse for long underground travel toward the surface, while the actual exit remains routed through `mine_tunnel_connector`.

### 15.2 Navigation failure

Hytale native navigation state is the primary failure signal. `BLOCKED` or `ABORTED` first requests exactly one native path recomputation. If the same target still reports terminal failure after that retry, the navigation adapter reports the failure back to miner work ownership rather than inventing a second scheduler.

V1 handling is:

- excavation front: persist the affected front as `BLOCKED`, release transient reservations/claims and select other work;
- mandatory infrastructure: persist the associated front as `BLOCKED`, release the infrastructure reservation and select other work;
- normal/optional infrastructure: skip that infrastructure task rather than blocking the whole tunnel;
- there is no periodic automatic retry of a `BLOCKED` front in V1.

If the miner is in the main corridor and a known-safe anchor exists, the navigation adapter may still use that native teleport recovery as an escape/safety action, but it does not turn the failed work target back into an automatically retried task.

`DEFER` is not considered terminal failure without runtime evidence, and an arbitrary time-based stall detector is not the primary mechanism.

The separate surface-recovery watchdog remains an emergency protection against a miner accidentally escaping to the surface. It is not normal anchor routing or the 50-block travel rule.

## 16. Manual interruption behaviour

A manual movement command immediately suppresses autonomous miner work.

If a miner is currently moving to or working on a task:

1. the task reservation is released;
2. the task remains in the mine's normal semantic state unless the mine system has another reason to change it;
3. the player command executes;
4. after manual control ends, task selection runs again.

There is no automatic resume bonus for the previously interrupted task and no forced return to the exact old task after any interruption, including a completed priority-`10` task.

If another miner is still working on the interrupted task and capacity is free, the normal active-work preference may naturally lead the returning miner back there. That outcome is a consequence of normal selection, not a remembered return obligation.

## 17. Persistence and restart behaviour

Persist the mine/task state required to continue unfinished work, not transient team composition.

Persist, where applicable:

- open tasks/fronts;
- their current semantic state;
- unfinished work progress;
- current priority including relevant aging state;
- mine/topology data needed to reconstruct valid work.

Do not require persistence of:

- which miner occupied which task capacity position;
- temporary working-group composition;
- short-lived block-level claims inside a shared front;
- a remembered previous task for automatic return.

After a restart, miners assigned to the mine select again from the restored open work using the normal selection rules.

Completed tasks should be removed from the active/persisted task system once their durable result is represented by the mine metadata and/or Hytale world state. The system should not retain completed task records merely as history, because they consume space and create a risk of finished work being selected again. Historical/statistical work tracking is explicitly deferred unless a later feature requires it.

## 18. Core information requirements

The Core model needs enough information to make gameplay decisions without owning Hytale pathfinding details.

### 18.1 Per miner

At minimum:

- current miner state (`IDLE`, `MOVING_TO_TASK`, `WORKING`);
- currently selected task reference, if any;
- current work-unit reference/progress where needed;
- whether the miner currently holds task capacity/reservation.

### 18.2 Per task

At minimum:

- stable task identity;
- task type;
- mine element reference (front, room, support point, light point, bridge, etc.);
- base priority;
- current priority including aging;
- task status;
- capacity;
- current reservation count/owners as required;
- semantic work area or valid target information;
- whether the task is currently executable.

Core does not need Hytale path nodes, detailed native path state or engine-specific movement internals.

## 19. Normal lifecycle summary

Normal success path:

`IDLE`

`-> choose available task`

`-> reserve task capacity`

`-> MOVING_TO_TASK`

`-> arrive`

`-> WORKING`

`-> complete one work unit`

`-> report result to mine system`

`-> release reservation`

`-> choose again`

Priority-10 interruption path:

`MOVING_TO_TASK / WORKING on normal task`

`-> priority-10 capacity requires this miner`

`-> release normal reservation`

`-> select/reserve priority-10 task`

`-> complete mandatory work`

`-> choose again normally`

Manual/blockage interruption path:

`MOVING_TO_TASK / WORKING`

`-> manual command or blockage/invalidity result`

`-> release reservation`

`-> update mine/task state where required`

`-> after override/problem handling, choose again`

## 20. Deferred features

The following are intentionally outside Plan Phase 1 or remain implementation/tuning work:

- dedicated per-miner standing slots inside shared tasks;
- arbitrary reservation timeouts;
- sleep / accommodation behaviour;
- real storage logistics;
- workshop usage;
- material consumption for supports, bridges, rooms or decoration;
- hunger, sleep and other needs;
- formal work shifts;
- specialist miner professions;
- more than three miners per mine;
- persistent/formal team objects;
- cross-mine task scheduling;
- complex material transport;
- advanced rail economics;
- final support-spacing formula;
- final lighting-spacing formula;
- final bridge algorithm;
- final room-slice dimensions and build-section sizes;
- final anchor-clearance tuning;
- final decoration rules;
- historical completed-task/statistics storage.

## 21. Implementation boundary

This document defines intended behaviour only.

Implementation must preserve the repository architecture rules:

- Civ/Core owns gameplay states, priorities, semantic tasks, aging, reservations and decisions;
- the mine model owns mine topology, valid work opportunities and placement constraints;
- Hytale owns native movement and pathfinding wherever suitable;
- anchor points are semantic support targets, not a reason to recreate Hytale navigation;
- long-distance anchor teleport is a bounded underground travel optimization, never an above-ground shortcut and never a replacement for local Hytale pathfinding;
- new gameplay behaviour should be testable at the Core boundary;
- no speculative systems should be added beyond behaviour required by this specification.

## Current implementation checkpoint - NPC layer 4

The current live miner implementation now covers the execution half of `EXCAVATE_FRONT` for the continuing main tunnel:

- one persistent semantic `MineWorkFront` points at the current Layer-3 excavation slice;
- the slice uses the actual planned variable tunnel cross-section rather than a fixed 4x4 face;
- a normal tunnel front accepts at most two miners;
- miners share the front through short-lived block claims, so they cannot work the same block concurrently and no permanent left/right standing slots are introduced;
- the Hytale world is reconciled as the truth for blocks already excavated, while the front position/state is persisted in `MineNetwork`;
- Hytale-native movement, the existing looping pickaxe animation and `BlockHarvestUtils.performBlockBreak` execute the physical work;
- manual interruption, profession changes and disappearing workers release runtime front/claim ownership;
- support placement is no longer an automatic side effect of excavation. `BUILD_SUPPORT` remains a separate infrastructure task as specified above.

This checkpoint intentionally does **not** implement the general multi-task scheduler. Only the current main-tunnel continuation is exposed to live workers, so branch-front selection, priority/aging, room work, infrastructure task selection and distribution of a third miner to another task remain later NPC layers. After one main-tunnel slice completes, the current one-task integration deterministically exposes the next main slice; this is the temporary single-available-task form of the later selection step, not a new priority rule.
