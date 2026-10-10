# ADR 0012: Miner task control belongs in shared Core

## Status

Implemented for Phase 5B; integration evidence is maintained in
[the sandbox roadmap](../simulation-sandbox-roadmap.md).

## Context

The production miner and live simulation previously had separate work loops.
Shared planners and selectors alone did not establish behavioral parity.

## Decision

`MinerWorkController<W>` is the authoritative, Hytale-independent lifecycle for
both `MinerWorkSystem.NativeMinerEngine` and `SimulationRuntime.MineLab.LabEngine`.
It owns entry, selection, task continuity, reservations, navigation-before-work,
work budgets, block/section claims, interruption, resumption, bounded retry and
return through the connector and access. It reuses `MineNormalTaskSelector`,
`MineFrontWorkDecision`, `MineFrontCoordinator`, `MineRoomCoordinator` and
`MineWorkerRouteDecision`. `MineRoom` owns semantic room progress.

The engine port supplies eligible domain tasks and world observations (`Work`),
executes navigation and `WorkIntent`, and reports synchronous success/failure.
A semantic revision invalidates stale work progress and claims. Engine results
are consumed in the same serialized world/simulation tick; this contract does
not allow delayed asynchronous acknowledgements. No Hytale objects enter Core.

The Core outcome policy distinguishes advance, defer, blocked front, abandoned
front, unavailable room and skipped optional infrastructure. A failed operation
allows at most eight retries after its first attempt. Native build probes are
chosen only from engine-validated standing positions. Terminal entry/return
navigation failures await explicit recovery; blocked work is never silently
reopened. Manual interruption frees every transient reservation, keeps persisted
progress and requires access/connector staging on resume.

## Engine boundaries

Hytale remains authoritative for loaded-world reads, native Seek and repath,
animations, block breaking, block/prefab placement, fluids and safe-anchor
teleport recovery. The adapter applies Core outcomes to the existing MineNetwork
persistence and native world; it does not run another work state machine.

The live lab uses voxel A*, actual voxel excavation and explicit synthetic
placement acknowledgements. Its room fixture uses three synthetic build
sections, not native prefab section counts. Shared control does not imply
identical geometry availability or native physics. The legacy offline recording
exporter remains a scripted visual fixture, not a behavioral parity oracle.

## Acceptance

Core tests assert ordered entry/work/return, active-task continuity, capacity,
exclusive claims, stale work, manual interruption/resumption, terminal failures,
bounded retries, all work kinds and outcome policies. Golden histories compare
injected production-coordinator wiring with standalone headless wiring under
identical observations/results. A separate history verifies equal work budgets
at 500-ms native and 50-ms simulator ticks. These are engine-contract tests;
they do not run the Hytale engine or prove native navigation/placement semantics.

`AutonomousMineWorldTest` exercises the actual live runtime against voxel
terrain with multiple miners, manual interruption, water/lava failure and
explicit retry. Source-boundary tests protect native API use and delegation.
Normal tests/build and PR/main CI are the acceptance gates. Hytale Local remains
optional and subject to AGENTS.md opt-in rules.
