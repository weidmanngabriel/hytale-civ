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

## Unit tests

Fast JUnit 5 tests for pure Java domain rules and utilities.

## Simulation / scenario tests

Future deterministic multi-step tests for people, needs, jobs, inventories, production, logistics and economy.

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
