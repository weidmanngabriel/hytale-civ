# ADR 0012: Miner task control belongs in shared Core

## Context

The production Hytale `MinerWorkSystem` currently combines Civ work choices,
short-lived front reservations, navigation results, and Hytale-specific block
operations in one adapter. The headless `SimulationRuntime.MineLab` also has
its own simplified excavation loop. A passing simulator scenario cannot claim
parity with a distinct production state machine.

## Decision

Incrementally move *gameplay decisions* into a Hytale-independent miner
controller within `dev.civilizations.core`. Both the real Hytale adapter and
the headless simulator must invoke that same controller.

The shared input shall contain only domain observations: eligible work,
reservations, navigation arrived/blocked, work result, interruption and world
changes. The shared output shall be small intents (choose/claim task, request
navigation to a semantic destination, request block work, release reservation,
retry/return) and an explicit state snapshot.

Hytale remains authoritative for loaded-world reads, pathfinding, native
Seek/role motion, block breaks, animation and placement. The simulator maps
intents to simple voxel A* and synthetic success/failure acknowledgements.
No native Hytale types enter Core.

Existing shared `MineNormalTaskSelector`, `MineFrontCoordinator` and tunnel
planning are reused rather than creating a new selection engine. The initial
slice applies the same Core task selection and capacity policy to the headless
lab. That is **not yet** full behavioral parity.

## Rollout and acceptance

1. Extract the worker-task lifecycle and explicit intents/results under Core
   with regression tests for interruptions, stale claims, retry and return.
2. Adapt Hytale `MinerWorkSystem` to feed observations/results and execute
   intents without duplicating state transitions.
3. Adapt `SimulationRuntime` to feed the same messages, using simplified
   engine outcomes, not its own task state rules.
4. Run shared golden histories through both adapters and validate decisions,
   state transitions and reservations; retain native navigation as separate
   integration evidence.
5. Merge only after green CI. The standalone live lab does not establish
   equivalence before steps 1–4.

The full rollout remains an open Phase 5B item in
`docs/simulation-sandbox-roadmap.md`.
