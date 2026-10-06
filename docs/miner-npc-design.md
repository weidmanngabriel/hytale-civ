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

Only two things may interrupt an active work unit before its normal completion in Plan Phase 1:

1. a manual player command;
2. an immediate safety/passability problem.

Normal priority changes wait until the current work unit is complete.

## 5. Task types

Plan Phase 1 uses the following semantic miner task types.

### 5.1 `EXCAVATE_FRONT`

Excavate one normal tunnel front / slice.

Main tunnel and side tunnel are not separate task types. The task references the tunnel/front metadata that identifies whether it belongs to the main tunnel or a side tunnel.

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

The normal excavation work unit is one complete tunnel front / excavation slice.

A miner that starts the front normally finishes that front before selecting again.

A possible future continuation of the tunnel is not already an active task simply because the previous front completed. The next front becomes relevant only through the next task-selection decision.

There is no special concept of a "half-finished main tunnel" for task priority.

If a main-tunnel front is interrupted, it remains a normal open main-tunnel task and competes by the normal priority rules when miners select again.

### 6.2 Room excavation

Room excavation is divided into small geometric slices, initially targeting roughly 1-2 blocks of depth per work unit where suitable.

The exact shape and slice generation belong to the mine system.

After each completed room slice, the miner selects again.

### 6.3 Room construction

Room construction is divided into small meaningful build sections rather than placing the entire room prefab as one indivisible miner action.

The exact sectioning belongs to the room/prefab system.

After each completed build section, the miner selects again.

### 6.4 Infrastructure

A support, light, step or bridge task completes when its defined infrastructure work unit is successfully constructed and reported back to the mine system.

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

The distinction between a side tunnel and the main tunnel is therefore explicit: ordinary side-tunnel work is preferred over ordinary main-tunnel work.

Rooms remain higher priority than both.

## 8. Aging

Tasks that remain available but are repeatedly skipped gain priority over time so lower-base-priority work does not remain forever.

Rule:

- every time task selection runs and an available, executable task is not chosen, its current priority increases by `+1`;
- only tasks that were actually available and executable age;
- blocked, invalid or not-yet-unlocked work does not gain aging merely because time passes;
- normal task priority is capped at `9`;
- priority `10` remains reserved for mandatory safety/passability work.

Aging is per concrete task, not global per task type.

This means, for example, a specific decoration or lighting task can eventually overtake normal excavation if it has repeatedly been skipped.

## 9. Tie-breaking

When multiple tasks have the same current priority, use this order:

1. higher current priority;
2. higher original base priority;
3. nearer suitable task;
4. random choice when the previous criteria are effectively equal.

This keeps behaviour understandable while avoiding completely rigid repeated patterns.

## 10. Multiple miners, task capacity and reservations

Plan Phase 1 supports at most three miners per mine.

A task may allow more than one miner to work on it. Therefore tasks use a capacity rather than a simple exclusive reservation flag.

A miner reserves one capacity position when it selects the task.

### 10.1 Initial V1 capacities

- normal tunnel front: maximum `2` miners;
- room excavation: up to `3` miners;
- room construction: typically up to `2` miners;
- individual infrastructure tasks: normally `1` miner;
- individual decoration tasks: normally `1` miner.

Room/task-specific design may lower a capacity when the available geometry makes fewer simultaneous workers sensible.

### 10.2 Natural distribution

The system does not hard-code squad patterns such as `2+1` or `1+1+1`.

Those distributions emerge naturally from:

- task priorities;
- available task capacities;
- reservations;
- distance tie-breaking;
- which tasks currently exist.

For example, a tunnel front with capacity `2` plus one valid light task can naturally produce a `2+1` distribution.

### 10.3 No fixed per-miner work slots in V1

A shared task defines a work area, not a permanent individual standing position for each miner.

The task capacity limits how many miners may work there, while Hytale/native execution may find suitable local positions within the work area.

Dedicated per-miner work slots are deferred unless tests show that miners consistently collide, stack or block each other.

## 11. Reservation lifecycle

A reservation is created only after task selection has actually chosen the task.

The reservation remains while the miner is:

- `MOVING_TO_TASK`;
- `WORKING` on that task.

The reservation is released when:

- the work unit completes;
- a manual player command interrupts the miner;
- a safety/blockage result ends the current work;
- the task becomes invalid;
- the NPC disappears;
- the NPC changes profession or otherwise stops being eligible for the task.

Plan Phase 1 does not add an arbitrary time-based reservation timeout.

If navigation later exposes a reliable explicit failure result, that result may release the reservation and trigger fresh task selection.

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

If no defined safe solution exists, the current task/front is reported as blocked.

Examples may include:

- a dangerous or unsuitable cave;
- a non-bridgeable gap;
- unsafe water/lava without an implemented solution;
- an invalid or unreachable work area.

The mine system then decides whether the front remains blocked, is redirected or is abandoned.

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

Because lighting participates in aging, a repeatedly skipped light task will rise in priority and eventually be performed.

### 14.5 Decoration

Optional decoration begins at base priority `2` but also participates in aging.

Decoration may therefore eventually overtake ordinary excavation, but it can never age to mandatory safety priority `10`.

## 15. Navigation anchors and placement protection

Navigation anchors are shared safe semantic points for the mine. They support movement and navigation but do not replace Hytale pathfinding.

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

## 16. Manual interruption behaviour

A manual movement command immediately suppresses autonomous miner work.

If a miner is currently moving to or working on a task:

1. the task reservation is released;
2. the task remains in the mine's normal semantic state unless the mine system has another reason to change it;
3. the player command executes;
4. after manual control ends, task selection runs again.

There is no automatic resume bonus for the previously interrupted task.

In particular, an interrupted main-tunnel front remains an ordinary main-tunnel task at its normal priority. If side-tunnel work is more important, miners may legitimately select that instead.

## 17. Core information requirements

The Core model needs enough information to make gameplay decisions without owning Hytale pathfinding details.

### 17.1 Per miner

At minimum:

- current miner state (`IDLE`, `MOVING_TO_TASK`, `WORKING`);
- currently selected task reference, if any;
- current work-unit reference/progress where needed;
- whether the miner currently holds task capacity/reservation.

### 17.2 Per task

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

## 18. Normal lifecycle summary

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

Interruption path:

`MOVING_TO_TASK / WORKING`

`-> manual command or safety result`

`-> release reservation`

`-> update mine/task state where required`

`-> after override/problem handling, choose again`

## 19. Deferred features

The following are intentionally outside Plan Phase 1 or remain implementation/tuning work:

- dedicated per-miner work slots inside shared tasks;
- arbitrary reservation timeouts;
- sleep / accommodation behaviour;
- real storage logistics;
- workshop usage;
- material consumption for supports, bridges, rooms or decoration;
- hunger, sleep and other needs;
- formal work shifts;
- specialist miner professions;
- more than three miners per mine;
- complex material transport;
- advanced rail economics;
- final support-spacing formula;
- final lighting-spacing formula;
- final bridge algorithm;
- final room-slice dimensions and build-section sizes;
- final anchor-clearance tuning;
- final decoration rules.

## 20. Implementation boundary

This document defines intended behaviour only.

Implementation must preserve the repository architecture rules:

- Civ/Core owns gameplay states, priorities, semantic tasks, aging, reservations and decisions;
- the mine model owns mine topology, valid work opportunities and placement constraints;
- Hytale owns native movement and pathfinding wherever suitable;
- anchor points are semantic support targets, not a reason to recreate Hytale navigation;
- new gameplay behaviour should be testable at the Core boundary;
- no speculative systems should be added beyond behaviour required by this specification.
