# Mine atmosphere and decorative block placement

## Native boundary

Layer-8 atmosphere deliberately reuses the same native placement contract as other mine infrastructure.

The pinned `HytaleServer.jar` exposes the APIs already used by `MineBlockPlacement`:

- `BlockType.getAssetMap()` for loaded block assets;
- `BlockOperations.setBlock(...)`;
- `BlockPhysics.markDeco(...)` / `isDeco(...)`;
- `ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(...)`;
- `RotationTuple` / `Rotation`.

No separate Civ decoration renderer, collision system or pathfinder is introduced.

## Asset resolution

Layer 8 now has verified native asset IDs for its authored decoration set:

- normal barrel: `Furniture_Tavern_Barrel`;
- damaged barrel variant: `Furniture_Ancient_Barrel`;
- small crate/chest: `Furniture_Crude_Chest_Small`;
- chain: `Deco_Iron_Chain_Small`;
- lantern: `Deco_Lantern`;
- ore material blocks: `Ore_Iron_Stone`, `Ore_Copper_Stone`, `Ore_Gold_Stone`;
- timber: `Wood_Fir_Trunk`.

The supplied Lantern asset explicitly accepts `Deco_Iron_Chain_Small` as support, so the hanging chain -> lantern combination follows native support metadata. No suitable tool/tool-rack decoration asset exists for this layer, so that planned variant was removed rather than guessed.

## Navigation contract

Decoration placement is a Civ validity rule layered on native blocks:

- never place into a slice's `navigationCoreBlocks`;
- keep the central +/-1 tunnel lane free;
- require solid floor for floor objects;
- require a solid wall for wall-mounted objects;
- require a solid ceiling for hanging objects;
- reject occupied target cells.

The central lane reservation protects both current NPC traversal and the later main-tunnel rail layer. Hytale remains responsible for actual NPC route calculation.

## Main versus branch presentation

The main tunnel may place barrels, small crates/chests, timber piles, ore-material blocks, hanging chains and hanging lanterns.

Branch tunnels deliberately remain rougher: crates, timber piles and material piles only. Their regular lighting stays wall-torch-only. Branch supports use lighter Fir branch timber and do not expand outward into cave pockets.

## Runtime-dependent evidence

JAR signatures prove API availability, not the actual contents or collision shapes of the installed Vanilla asset pack.

The focused Hytale-Local scenario `mineatmosphere` is therefore the reusable runtime diagnostic for this layer. It reports the actual resolved asset IDs, places each main decoration type with the production placement path and verifies that a real Civ NPC can traverse the reserved central corridor afterward.

The ore material blocks are normal native ore blocks, including their normal gathering/drop behaviour. Likewise, the crude chest is a native container block. Civ does not add separate Layer-8 storage or ore-economy semantics on top of those native behaviours.


Native placement reference: `MineBlockPlacement` passes a `ChunkStore.getChunkSectionReferenceAtBlock(...)` **section** reference to `BlockOperations.setBlock`, native connected-block notification and Deco physics. A chunk-column reference is not valid for these operations. See `mine-infrastructure-building.md`.
