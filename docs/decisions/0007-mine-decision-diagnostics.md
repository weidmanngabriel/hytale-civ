# ADR 0007: Mine decision diagnostics cross the Core/Hytale boundary as events

## Status

Accepted

## Context

The planned mine generator contains probabilistic and spatial decisions that need to be explainable during development. Those decisions will increasingly live in the Hytale-independent Core, while server logging is provided by Hytale's plugin logger. Importing `HytaleLogger` into Core would violate the existing integration boundary, while a second file/logging framework would duplicate infrastructure.

## Decision

Mine code may emit immutable structured `MineDecisionEvent` values through an optional `MineDecisionSink`. The Core types contain only decision context and no Hytale API. The production Hytale adapter `CivMineDecisionDiagnostics` implements the sink, filters coarse categories and writes enabled events through the existing plugin `HytaleLogger`.

The disabled sink is a no-op and is the default compatibility path. Decision code checks the sink before constructing event payloads. Logging must observe already-made decisions only: it must not perform extra random rolls, world queries or gameplay state transitions.

Events are deliberately decision-level rather than block-level. Hytale-specific execution failures are emitted from the adapter that encounters them; future Core generator choices should emit events at the Core decision site.

## Consequences

- Normal gameplay has no mine decision log stream unless explicitly enabled.
- Logs remain filterable without introducing a parallel logging backend.
- Core decision tests can capture the same structured events without running Hytale.
- Future mine layers can add new event types and use the existing coarse categories without moving logging responsibility into presentation code.
