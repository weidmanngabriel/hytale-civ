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

The current RTS spike mainly exercises client camera and cursor behavior and therefore does not pretend to cover those engine contracts with mocked unit tests.

## Hytale server integration tests

Future controlled-server tests for lifecycle, registration and engine interaction. Not implemented yet.

## Manual client / UX tests

The RTS interaction spike has a concrete manual acceptance sequence:

1. run `/civrtstest` and confirm the view changes to an angled cursor camera;
2. click several existing entities and confirm the reported selection count increases;
3. click a selected entity again and confirm it is removed;
4. left-click empty world space and confirm selection clears;
5. select one or more entities, right-click a visible world block and confirm its coordinates and the selection count are reported;
6. run `/civrtstest` again and confirm normal camera control returns;
7. verify ordinary mouse interactions are suppressed only while the RTS test mode is active.

Actual NPC movement is not an acceptance criterion for this milestone.

## Current automated tests

- `CoreSmokeTest` proves JUnit works.
- `CoreIndependenceTest` guards against direct Hytale imports in `core`.
- `ManifestValidationTest` validates packaged plugin metadata without starting Hytale.
