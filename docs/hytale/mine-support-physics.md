# Mine support prefab physics

Phase-1 mine supports use the prefab `Civilizations/Mine/Mine_Support_01.prefab.json` and are placed through Hytale's native `BlockSelection` API.

Before `BlockSelection.placeNoReturn(...)`, the rotated selection must receive Hytale's native block-physics support values through:

```java
BlockSelectionSupportUtil.applySupportValues(selection);
```

The call is intentionally made after rotating the prefab so support values describe the orientation that is actually placed in the world.

This was introduced while diagnosing horizontal `Wood_Fir_Trunk` beam blocks that appeared correctly immediately after placement but then began breaking almost instantly. The mine keeps the trunk beam unchanged so runtime testing can determine whether missing selection support values were the cause rather than masking the issue by changing the block type.

This does not make mine supports protected or unbreakable. Players and NPCs can still remove support blocks normally after placement.
