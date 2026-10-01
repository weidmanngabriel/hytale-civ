# ADR 0003: Runtime engine-cost profiling stays in the Hytale adapter

## Status

Proposed

## Context

The headless simulation intentionally measures deterministic gameplay operations, not wall-clock CPU cost. Tree discovery is different: its actual cost depends on Hytale chunk access, block metadata, trigger volumes and the real server runtime. Measuring that cost in the headless simulator would produce numbers that look precise but do not represent the live game.

## Decision

Real engine-cost measurements stay in `dev.civilizations.hytale` and are opt-in development diagnostics.

`WoodcutterScanDiagnostics` measures only the existing `WoodcutterWorkSystem` tree-search execution. `/civdebug woodscan` toggles collection. While enabled, the server logs an aggregate snapshot at most every ten seconds. Disabling it reports the final snapshot.

The measured fields are:

- scan count,
- average and maximum wall-clock milliseconds per tree search,
- search-grid positions checked,
- tree bases found,
- tree structures collected,
- wood blocks contained in collected tree structures,
- trees rejected by building protection or another worker's reservation,
- work-position searches performed,
- trees rejected because no stand position exists,
- usable tree candidates.

The profiler does not trigger additional gameplay searches. The prior full second scan used only for `no-target` diagnostics is removed; rejection counters are collected during the authoritative gameplay search instead. When profiling is disabled, the normal search path only performs the cheap enabled check and does not allocate per-scan counters.

## Consequences

Headless `SimulationMetrics` remain deterministic and suitable for CI performance budgets. Runtime profiling answers a different question: how expensive those operations are inside Hytale on a real world and server. The two measurements must not be compared as if they used the same units.

The profiler itself has some overhead while enabled, so results are diagnostic rather than a zero-overhead production benchmark. If tree discovery becomes a measurable bottleneck, optimization should target the authoritative search mechanism instead of adding a second resource index before measurements justify it.
