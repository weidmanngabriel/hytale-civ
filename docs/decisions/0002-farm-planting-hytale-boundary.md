# 0002-farm-planting-hytale-boundary

## Status

Accepted

## Context

The Civ farmer needs to sow wheat on the field. The pinned Hytale runtime provides native farming assets such as `Seed_Place`, but the verified server-side NPC path for executing a seed placement without a client is not established.

The project must not simulate a player interaction or duplicate Hytale's farming rules merely to make an NPC place a crop.

## Decision

Keep the Civ farmer work cycle and field selection in Civ, and isolate the engine operation behind the Hytale-only `FarmPlantingService`.

Until a verified native server-side NPC seed-placement API is available, the service returns `UNSUPPORTED`. The farmer then pauses its sowing cycle through the existing Core state transition instead of repeatedly attempting a known-invalid interaction.

Harvesting continues to use Hytale's native `FarmingUtil.harvest` path.

When Hytale later exposes a suitable native NPC planting operation, only the Hytale adapter needs to change; the farmer work cycle, field registry and Civ farming rules remain unchanged.

## Consequences

- No player-client interaction is simulated.
- No `BlockTypeToPlace` lookup or custom seed-to-crop mapping is required.
- The current farmer cannot automatically sow wheat until a supported engine path is available.
- The planting boundary is explicit and replaceable without changing the Core farm model.
