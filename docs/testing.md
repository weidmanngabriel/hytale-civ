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

## Hytale server integration tests

Future controlled-server tests for lifecycle, registration and engine interaction. Not implemented yet.

## Manual client / UX tests

Reserved for behavior that genuinely requires the client, rendering or input.

## Current tests

- `CoreSmokeTest` proves JUnit works.
- `CoreIndependenceTest` guards against direct Hytale imports in `core`.
- `ManifestValidationTest` validates packaged plugin metadata without starting Hytale.
