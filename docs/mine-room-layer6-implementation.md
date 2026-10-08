# Mine Layer 6 - rooms and prefabs

Status: implemented V1 for rest/accommodation rooms, material storage rooms and small niches.

This document records the concrete room/prefab implementation. Product rules remain canonical in `docs/mine-design.md` and `docs/miner-npc-design.md`. The filename is intentionally room-specific because the repository already contains an older `docs/mine-layer6-implementation.md` for the previously named NPC obstacle/failure slice.

## Scope

Room work uses the persistent lifecycle:

`PLANNED -> EXCAVATING -> READY_TO_BUILD -> BUILT`

The first authored room set intentionally contains only three replaceable test prefabs:

- `REST_ACCOMMODATION`
- `MATERIAL_STORAGE`
- `SMALL_NICHE`

The remaining canonical room types stay in the domain model but are not generated until authored content or later-layer world semantics exist. In particular, `LARGE_NATURAL_CHAMBER` remains part of cave integration rather than being fabricated as an ordinary carved room.

## Planning

`MineRoomPlanner` is Hytale-independent and deterministic for a fixed mine plan/seed.

Current V1 rules:

- rest/accommodation opportunities occur on the main tunnel with a target spacing of 80-120 blocks;
- material storage uses a 30% opportunity chance and 60-80 block spacing on the main tunnel;
- small niches use a 25% opportunity chance on branch tunnels;
- canonical probabilities for deferred room types remain represented as tuning constants but those types are not emitted yet;
- room bodies are rejected when they collide with unrelated tunnel geometry;
- accepted room centres keep at least 18 blocks from other planned V1 room centres;
- a room is not executable until its attachment tunnel slice is physically excavated;
- no more than two rooms may be active in `EXCAVATING` or `READY_TO_BUILD` at the same time.

The test-prefab orientation is cardinal. Room opportunities use the dominant local tunnel direction and choose one of the two cardinal side directions.

## Excavation

`MineRoomGeometry` creates a short three-block-high access throat plus a rectangular room envelope. Current replaceable V1 envelopes:

- small niche: 5 x 4 x 4;
- material storage: 7 x 6 x 4;
- rest/accommodation: 9 x 8 x 5.

These are implementation envelopes for the first test assets, not final visual dimensions.

Room excavation is divided into semantic work units one or two blocks deep. Up to three miners may share an excavation unit. Block-level claims are transient. Actual block breaking uses the same native Hytale `BlockHarvestUtils.performBlockBreak(...)` path as tunnel excavation.

After every completed room excavation unit the workers reselect normal work. When the last excavation unit finishes, the room enters `READY_TO_BUILD`.

## Construction

Room work has normal base priority 8. Existing active normal work with capacity is still filled before opening new work, so an active tunnel front can be joined before a waiting priority-8 room. When new normal work is opened, room priority 8 outranks branch excavation 6 and main excavation 4.

The Hytale adapter maps the three room types to:

- `Civilizations/Mine/Rooms/Rest_Accommodation_01.prefab.json`
- `Civilizations/Mine/Rooms/Material_Storage_01.prefab.json`
- `Civilizations/Mine/Rooms/Small_Niche_01.prefab.json`

The adapter loads them through the verified `PrefabStore.getAssetPrefabFromAnyPack(...)` API, rotates the `BlockSelection` with the existing Civ/Hytale orientation mapping and places one occupied Y-layer per build section with native `BlockSelection.placeNoReturn(...)`.

Up to two miners may reserve different build sections. The first test assets contain only simple known Vanilla blocks and no room-specific entities, TriggerVolumes, inventories or gameplay production. Their visual contents are intentionally disposable.

## Persistence

Mine persistence format is now `N4`.

Each room persists:

- room/tunnel/type;
- room anchor;
- cardinal outward orientation;
- attachment slice;
- lifecycle state;
- next excavation work unit;
- completed prefab build sections.

`N3` and `N2` remain readable. Old five-field room records are restored as `PLANNED` with zero progress. Worker reservations, block claims and build-section claims are transient and are not persisted.

## Hytale boundary

The pinned `HytaleServer.jar` was rechecked for the APIs used here:

- `PrefabStore.get()`;
- `PrefabStore.getAssetPrefabFromAnyPack(String)`;
- `BlockSelection.cloneSelection()`;
- `BlockSelection.rotate(Axis, int, Vector3d)`;
- `BlockSelection.forEachBlock(...)`;
- `BlockSelection.placeNoReturn(World, Vector3i, ComponentAccessor)`.

Room planning, priorities, capacities and lifecycle remain Core/domain behavior. Hytale owns physical block breaking, movement, prefab loading, rotation and placement.

## Limitations / deferred

- only three room types have V1 prefabs;
- exact final room dimensions and visual variants remain later content work;
- natural cave/chamber classification remains Layer 7;
- rooms do not yet contain functional storage, beds, production stations or logistics;
- the room-prefab test assets are intentionally simple;
- a room whose native navigation or prefab placement fails is suppressed for the current runtime; a persistent room-specific unblock/recovery state is deferred;
- the full aging scheduler was deferred at the Layer-6 checkpoint and is implemented in Layer 8; room functional inventories/production remain separate future work.
