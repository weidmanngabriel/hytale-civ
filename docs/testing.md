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

The current RTS spike mainly exercises client camera, cursor targeting, anchor UI actions, custom pages, client-side placement preview, native tree harvesting and Hytale NPC path/movement behavior and therefore does not pretend to cover those engine contracts with mocked unit tests.

## Hytale server integration tests

Future controlled-server tests for lifecycle, registration and engine interaction. Not implemented yet.

## Manual client / UX tests

The controllable-NPC spike has this acceptance sequence:

1. run `/civrtstest` and confirm the view changes to a fixed angled cursor camera without entering Spectator mode;
2. left-click an unclaimed NPC and confirm it is not added to the Civ selection;
3. run `/civclaim`, left-click that NPC and confirm it is claimed;
4. left-click the claimed NPC and confirm it becomes the selected Civ unit;
5. claim another NPC and select it; confirm it replaces the previous selection rather than creating a multi-selection;
6. right-click the selected NPC and confirm the Personenaktionen page opens;
7. spawn/use a `Civ_Inhabitant`, right-click open, reasonably flat ground and confirm it travels toward the target using its native Hytale `Seek`/Walk behavior rather than Civ steering;
8. right-click behind an obstacle and confirm Hytale's native path/movement stack, rather than Civ code, determines the route behavior;
9. run `/civclaim` and click a claimed NPC again to release it; confirm it can no longer be selected or commanded;
10. run `/civrtstest` again and confirm normal camera control returns;
11. confirm claims are runtime-only and do not survive a plugin/server restart;
12. assign a profession to a claimed NPC, restart the server/plugin, reclaim the same persisted NPC entity and confirm its profession data is still present. Farm assignment itself is not expected to survive yet.

The direct movement acceptance test uses the committed `Civ_Inhabitant` role. Its single `CivMoveTarget` position slot is the explicit Java/asset contract; `CivInhabitantRoleValidationTest` guards that slot assumption. Civ does not apply its own per-tick steering force.

## Current automated tests

- `CoreSmokeTest` proves JUnit works.
- `CoreIndependenceTest` guards against direct Hytale imports in `core`.
- `WoodcutterJobTest` proves the target → arrival → chopping → ready-to-fell work cycle.
- `ManifestValidationTest` validates packaged plugin metadata without starting Hytale.\n- `CivInhabitantRoleValidationTest` validates the committed Civ NPC role and guards the single position-slot contract used by Java movement.

## Woodcutter vertical-slice coverage

Manual acceptance sequence:

1. install/deploy both `hytale-civ.jar` and `hytale-civ-assets`;
2. run `/civrtstest`;
3. claim an NPC with `/civclaim`, then select it with a normal left click;
4. right-click the selected NPC and confirm the Personenaktionen menu opens;
5. click `Holzfäller` and confirm the page closes and the NPC starts autonomous work;
6. keep the NPC near a normal Hytale tree and confirm it walks beside the trunk rather than trying to stand inside it;
7. after the chopping phase, confirm the base trunk is broken through Hytale's normal harvesting path;
8. verify normal drops appear and the remaining tree reacts according to its native support/falling-block configuration;
9. confirm the Woodcutter then searches for another nearby tree.

The prototype intentionally relies on Hytale's native tree asset behavior after the trunk is broken. If a specific tree asset does not collapse, inspect that asset's support/physics configuration before adding custom Civ collapse logic.

## Farm vertical-slice coverage

Automated coverage now includes:

- FarmBuildingTest, which proves one Farmer slot, five seconds of active work per wheat, mandatory exit after every production step and a hard stop at 10 wheat;
- FarmPrefabValidationTest, which validates the committed Asset Pack prefab metadata, unique block coordinates, visible entrance opening, roof and crop-bed materials.

Manual acceptance sequence:

1. install/deploy both `hytale-civ.jar` and `hytale-civ-assets`;
2. run `/civrtstest` and confirm an interactive **Bauen** button appears on the left;
3. click **Bauen** and confirm the click is handled by Hytale's anchor UI event system and a modal **Gebäude** catalog opens to its right, blocking normal RTS world interaction;
4. confirm the current catalog contains **Farm** and can be closed without starting placement;
5. open it again, choose **Farm**, then move the cursor across terrain and confirm a Farm ghost follows the pointed block;
6. right click and confirm placement is cancelled without changing the world;
7. choose **Farm** again and left click valid, flat, supported ground; confirm the Farm is placed with its floor embedded into the terrain rather than sitting one block above it;
8. try again over a hole, liquid, blocked building volume, blocked entrance and an existing Farm footprint; confirm placement is refused with a reason and remains in placement mode;
9. confirm `/civfarm` enters the same Farm placement flow as the menu;
10. claim an NPC with `/civclaim`, select it, right click the Farm doorway and confirm Farmer assignment still works;
11. confirm the NPC walks to the doorway, waits inside for about five seconds, leaves, re-enters for each production step, and remains outside after the tenth wheat;
12. release the NPC with `/civclaim` during a cycle and confirm the Farm assignment is cleared.

Multiplayer acceptance:

1. connect two players and enter RTS mode with both;
2. start Farm placement independently and confirm moving or cancelling one preview does not affect the other player's preview/state;
3. make both previews target overlapping valid footprints;
4. let player A place first, then let player B confirm without moving the cursor;
5. confirm player B is rejected by the server-side revalidation instead of overlapping player A's Farm.

The original floor-block snapshot is internal runtime state until demolition UI exists. When demolition is implemented, its acceptance test must verify exact restoration of those recorded blocks. Rotation remains separate future work.
