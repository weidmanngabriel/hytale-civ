# Mine support prefab physics

Phase-1 mine supports deliberately split placement into two native Hytale paths.

The two vertical `Wood_Fir_Branch_Long` posts remain in `Civilizations/Mine/Mine_Support_01.prefab.json` and are placed through Hytale's `BlockSelection` API. Before `BlockSelection.placeNoReturn(...)`, the rotated selection receives Hytale's native support values through `BlockSelectionSupportUtil.applySupportValues(selection)`.

The four horizontal `Wood_Fir_Trunk` beam cells are no longer stored in the prefab. They are placed immediately afterwards through the same core block-operation pipeline used by normal player placement:

```java
BlockOperations.setBlock(..., flags = 256)
BlockPhysics.markDeco(...)
ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(...)
```

The rotation starts from the prefab beam rotation index `4` and is composed with the mine support's Y rotation, matching `BlockSelection.rotate(...)` semantics.

This split exists because `BlockSelection.placeNoReturn(...)` writes prefab blocks through a different low-level path than normal player placement. `BlockOperations.setBlock(...)` additionally performs Hytale's native block replacement bookkeeping, block-physics reset, area updates, block ticking, lighting, filler handling, block-entity handling and related notifications. The player path then applies `Deco` where supported and runs connected-block neighbour updates.

The beam remains a normal breakable block. There is no protection flag and no global or per-world scanner. Players and NPCs can still remove the support normally after placement.

The existing `MineSupportPhysics.markBeamAsDeco(...)` method name is retained for compatibility with the mine-support flow, but the method now performs the complete player-like beam placement pipeline rather than only setting a deco marker.

A dedicated real-Hytale runtime scenario named `minesupport` verifies placement, stability and native breakability without cascade. The self-hosted runtime runner still needs JDK 25 installed before that scenario can execute; normal repository CI compiles and tests the implementation with Java 25.
