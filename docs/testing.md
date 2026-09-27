# Testing strategy

The goal is to keep most game behavior testable without launching Hytale.

```text
Unit Tests
    ↓
Simulation / Scenario Tests
    ↓
Hytale Adapter Tests
    ↓
Hytale Server Integration Tests
    ↓
Manual Client / UX Tests
```

## General regression rule

Behavioral changes need coverage at the lowest layer that can prove the behavior without depending on Hytale unnecessarily.

A successful compile or build is not sufficient coverage for a new domain rule. When a feature introduces a meaningful multi-step flow, add a regression test for the complete relevant path rather than testing only individual helper methods.

Domain rules and invariants should normally be expressed through deterministic core tests. Hytale adapter tests should prove translation and engine-boundary behavior, not duplicate core rules with mocks.

## Unit tests

Fast JUnit 5 tests for pure Java domain rules and utilities.

## Simulation / scenario tests

Deterministic multi-step tests are the preferred coverage for inhabitants, needs, jobs, inventories, production, logistics, economy and other coupled simulation behavior.

As these systems are introduced, use small golden scenarios with explicit initial state, commands and expected resulting state. Important invariants should be checked directly, for example that inputs are consumed exactly once, inventories never become negative and the same command sequence produces the same result.

Scenario tests should remain Hytale-independent unless the behavior being tested is genuinely an engine contract.

## Hytale adapter tests

Tests around translation and adapter behavior where possible without a running server.

The current RTS spike mainly exercises client camera, cursor targeting and Hytale NPC motion behavior and therefore does not pretend to cover those engine contracts with mocked unit tests.

## Hytale server integration tests

Future controlled-server tests for lifecycle, registration and engine interaction. Not implemented yet.

## Manual client / UX tests

The controllable-NPC spike has this acceptance sequence:

1. run `/civrtstest` and confirm the view changes to an angled cursor camera;
2. left-click an unclaimed NPC and confirm it is not added to the Civ selection;
3. run `/civclaim`, left-click that NPC and confirm it is claimed;
4. left-click the claimed NPC and confirm the selection count changes;
5. claim and select several NPCs;
6. right-click open, reasonably flat ground and confirm selected NPCs move toward nearby, slightly offset targets rather than teleporting;
7. right-click behind an obstacle and observe collision behavior; this milestone does not require the NPCs to find a route around the obstacle;
8. run `/civclaim` and click a claimed NPC again to release it; confirm it can no longer be selected or commanded;
9. run `/civrtstest` again and confirm normal camera control returns;
10. confirm claims are runtime-only and do not survive a plugin/server restart.

A compatible NPC role with an active motion controller is required for the movement test. Native NPC behavior may compete with the debug steering and is part of what this spike is intended to reveal.

## Current automated tests

- `CoreSmokeTest` proves JUnit works.
- `CoreIndependenceTest` guards against direct Hytale imports in `core`.
- `ManifestValidationTest` validates packaged plugin metadata without starting Hytale.


## Farm vertical-slice coverage

Automated coverage now includes:

- FarmBuildingTest, which proves one Farmer slot, five seconds of active work per wheat, mandatory exit after every production step and a hard stop at 10 wheat;
- FarmPrefabValidationTest, which validates the committed Asset Pack prefab metadata, unique block coordinates, visible entrance, door, roof and crop-bed materials.

Manual acceptance sequence:

1. install/deploy both hytale-civ.jar and hytale-civ-assets;
2. run /civrtstest;
3. run /civfarm, then right click reasonably flat ground and confirm the visible Farm prefab appears with its doorway at the clicked anchor;
4. claim an NPC with /civclaim and select exactly that NPC;
5. right click the Farm doorway and confirm the assignment message identifies the NPC as the Farm's Farmer;
6. confirm the NPC walks to the doorway, waits inside for about five seconds, then walks two blocks outside;
7. confirm it re-enters and repeats this leave/re-enter cycle for each production step;
8. after the tenth wheat, confirm the NPC remains outside and stops cycling;
9. release the NPC with /civclaim during a cycle and confirm the Farm assignment is cleared.

The current prefab has a fixed south-facing entrance and assumes reasonably flat placement terrain. Terrain validation and rotation are separate future building-placement work.
