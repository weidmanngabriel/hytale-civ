# Mine support prefab physics

Phase-1 mine supports use the prefab `Civilizations/Mine/Mine_Support_01.prefab.json` and are placed through Hytale's native `BlockSelection` API.

Before `BlockSelection.placeNoReturn(...)`, the rotated selection receives Hytale's native block-physics support values through:

```java
BlockSelectionSupportUtil.applySupportValues(selection);
```

The call is intentionally made after rotating the prefab so support values describe the orientation that is actually placed in the world.

After placement, the four horizontal `Wood_Fir_Trunk` beam cells are additionally marked through Hytale's native placed-block physics metadata:

```java
BlockPhysics.markDeco(...)
```

This is handled by `MineSupportPhysics.markBeamAsDeco(...)`. Only the top beam is marked; the two vertical posts keep their normal block-physics behavior.

The support is not recorded as logically placed until the beam could be marked successfully. If the prefab exists in the world but the deco marking failed, the next normal due-support pass retries only that still-unrecorded beam marking instead of introducing a separate global or per-world scanner.

The goal is to prevent a freshly placed horizontal trunk beam from being treated like unsupported generated tree wood and cascading away immediately after placement. `Deco` is physics metadata, not protection: the beam remains a normal breakable block for players and NPCs.

A dedicated real-Hytale runtime scenario named `minesupport` places the prefab, marks all four beam cells as deco, waits for native block physics, then natively breaks one beam block and expects exactly the other three beam blocks to remain. The local self-hosted runner currently requires a Java 25 installation before that scenario can execute; normal repository CI still compiles and tests the implementation with Java 25.
