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

The repository pins the server JAR but does not contain the full licensed Vanilla `Assets.zip` catalog. The exact IDs for barrel, crate, tool, chain and lantern variants therefore must not be guessed as compile-time facts.

`MineBlockPlacement.resolveAsset(...)` resolves against the native loaded `BlockType` map:

1. try a short list of expected IDs;
2. otherwise find a loaded ID containing the required semantic fragments;
3. if no suitable block exists, return no placement and allow optional decoration to be skipped.

Fir timber piles use the already-established `Wood_Fir_Trunk` asset and Deco metadata.

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

The main tunnel may resolve barrels, crates, timber piles, tools, material piles, hanging chains and hanging lanterns.

Branch tunnels deliberately remain rougher: crates, timber piles and material piles only. Their regular lighting stays wall-torch-only. Branch supports use lighter Fir branch timber and do not expand outward into cave pockets.

## Runtime-dependent evidence

JAR signatures prove API availability, not the actual contents or collision shapes of the installed Vanilla asset pack.

The focused Hytale-Local scenario `mineatmosphere` is therefore the reusable runtime diagnostic for this layer. It reports the actual resolved asset IDs, places each main decoration type with the production placement path and verifies that a real Civ NPC can traverse the reserved central corridor afterward.

Keep any confirmed runtime asset IDs or newly discovered placement restrictions on this page when the scenario provides stable evidence.
