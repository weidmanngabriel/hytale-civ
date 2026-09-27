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

As these systems are introduced, use small golden scenarios with explicit initial state, commands and expected resulting state.

## Hytale adapter tests

Tests around translation and adapter behavior where possible without a running server.

The current RTS, camera, input, native tree harvesting and NPC steering contracts require manual Hytale validation rather than mocked engine behavior.

## Hytale server integration tests

Future controlled-server tests for lifecycle, registration and engine interaction. Not implemented yet.

## Current automated tests

- `CoreSmokeTest` proves JUnit works.
- `CoreIndependenceTest` guards against direct Hytale imports in `core`.
- `WoodcutterJobTest` proves the target → arrive → chopping → ready-to-fell state cycle without Hytale.
- `FarmBuildingTest` proves one Farmer slot, five seconds of active work per wheat, mandatory exit after every production step and a hard stop at 10 wheat.
- `FarmPrefabValidationTest` validates the committed Farm prefab structure.
- `ManifestValidationTest` validates packaged plugin metadata without starting Hytale.

## RTS + Woodcutter manual acceptance

1. install/deploy both `hytale-civ.jar` and `hytale-civ-assets`;
2. run `/civrtstest` and confirm the fixed angled cursor camera appears without entering Spectator mode;
3. run `/civclaim`, left-click an NPC, then left-click it normally to select it;
4. select a second claimed NPC and confirm it replaces the first selection rather than creating a multi-selection;
5. press the standard Hytale Use key (default F) and confirm the Personenaktionen page opens;
6. click `Holzfäller` and confirm the page closes and the NPC starts autonomous work;
7. place the NPC near a normal Hytale tree and confirm it walks beside the trunk rather than into the trunk;
8. after the chopping phase, confirm the base trunk is broken through normal Hytale harvesting;
9. verify the tree's normal drops appear and its remaining structure reacts according to the tree asset's native support/falling-block configuration;
10. confirm the Woodcutter then searches for another nearby tree;
11. confirm right-click still moves the currently selected NPC;
12. run `/civrtstest` again and confirm the normal camera returns.

The current prototype recognizes Hytale wood-gathering trunk blocks and intentionally relies on the tree asset's own support/physics configuration. If a specific tree asset does not fall after its base breaks, that is an engine/asset behavior to inspect rather than a signal to immediately add a custom Civ tree-collapse simulation.

## Farm vertical-slice coverage

Manual acceptance sequence:

1. run `/civrtstest`;
2. run `/civfarm`, then right click reasonably flat ground and confirm the Farm prefab appears;
3. claim and select one NPC;
4. right click the Farm doorway and confirm Farmer assignment;
5. confirm the NPC completes its entrance/work/exit cycle and stops at 10 wheat.
