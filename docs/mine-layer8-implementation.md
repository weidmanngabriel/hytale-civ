# Mine Layer 8 - atmosphere and normal-task aging

Status: implemented V1 for main/branch tunnel atmosphere plus the canonical normal-task aging rules.

Product rules remain canonical in `docs/mine-design.md` and `docs/miner-npc-design.md`.

## Scope

Layer 8 turns completed safe tunnel space into a more developed mine without changing routing or excavation geometry.

The existing lighting distinction remains:

- main tunnel: recurring stone pillar + lantern;
- branch tunnel: wall torches only.

Layer 8 adds optional `PLACE_DECORATION` work at base priority 2 and simplifies normal branch supports. Decoration never becomes passability work, never blocks a front and never causes `BLOCKED` or `ABANDONED`.

## Unified normal-task scheduler

`MineNormalTaskSelector` now owns all normal mine categories:

- room work: base 8;
- branch excavation: base 6;
- recurring support/light infrastructure: base 5;
- main excavation: base 4;
- decoration: base 2.

Priority 10 remains outside this normal pool and is the only acute/interruption class.

Selection preserves the canonical active-first rule:

1. fill executable already-active normal tasks that still have capacity;
2. otherwise open one waiting normal task by current effective priority;
3. then distance;
4. then stable task ID.

## Aging

Aging is persisted per concrete task ID in `MineNetwork.normalTaskPriorityBonuses`.

When a normal selection decision must open waiting work:

- every available, executable waiting task that is not selected gains +1;
- active tasks do not age;
- blocked, invalid, completed or not-yet-unlocked tasks are absent from the candidate set and do not age;
- effective normal priority is capped at 9;
- priority 10 is never reached through aging.

The bonus survives restart so low-priority work cannot be starved merely by reloading the server.

A bonus is cleared when the concrete work unit completes or becomes unavailable. For repeated semantic work, the next work unit starts again from its base priority; for example, a newly opened tunnel slice does not inherit the age of the previous slice.

Mine persistence format is `N5`; `N4`, `N3` and `N2` remain readable.

## Atmosphere planning

`MineInfrastructurePlanner` plans atmosphere deterministically from the tunnel geometry seed.

Main tunnel:

- decoration spacing: 10-18 slices;
- variants: barrel, crate, timber pile, tools, material pile, hanging chain, hanging lantern;
- main supports keep the existing stronger trunk-beam appearance.

Branch tunnels:

- decoration spacing: 18-30 slices;
- variants: crate, timber pile, material pile;
- no hanging chain or hanging lantern;
- simpler supports use Fir branch timber for posts and top beam and do not grow into nearby cave side pockets.

Decoration candidates avoid already-planned support/light/step locations by at least one neighbouring slice.

These values are V1 tuning values.

## Native placement and navigation safety

Decoration is resolved only after its tunnel slice is already available behind excavation progress.

The Hytale adapter uses the existing native `MineBlockPlacement` path rather than a second decoration engine:

1. resolve a loaded native `BlockType`;
2. `BlockOperations.setBlock(...)`;
3. apply `BlockPhysics.markDeco(...)` where needed for built Fir;
4. notify connected blocks through `ConnectedBlocksUtil`.

Known Fir timber blocks are used for timber piles. Barrel, crate, tool, chain, lantern and material objects are resolved against the actually loaded Hytale block asset map. Missing or unsuitable optional decoration is skipped.

Every candidate preserves the guaranteed navigation core. Floor/wall/ceiling variants are placed only at side positions outside the central +/-1 lane. That same lane remains free for the later main-tunnel rail layer. Hanging objects also reject any cell that belongs to the navigation core.

Optional decoration uses the existing nearby +/-3 slice fallback. If no safe placement exists, the task completes as skipped rather than blocking mine progress.

## Runtime verification

The focused `mineatmosphere` Hytale-Local scenario creates a deterministic artificial main-tunnel shell, resolves and places all seven main decoration kinds through production code, then sends a real Civ NPC through the central corridor using Hytale-native navigation.

The scenario exists to verify loaded asset IDs, placement support/orientation contracts and central-corridor passability. It is diagnostic evidence rather than a merge gate.

## Deferred / limitations

- exact final visual composition and asset variants remain art/tuning work;
- decoration is currently block-asset based rather than using authored multi-block decoration prefabs;
- barrels/crates/tools/material piles are visual only and have no inventory or production semantics;
- rooms are not given a separate decoration pass in Layer 8;
- rails/minecarts remain the next independent layer;
- material consumption remains deferred.
