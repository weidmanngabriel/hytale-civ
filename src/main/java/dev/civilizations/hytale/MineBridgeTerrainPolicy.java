package dev.civilizations.hytale;

/** Pure terrain-profile validation before mandatory bridge construction. */
final class MineBridgeTerrainPolicy {
    private MineBridgeTerrainPolicy() {}

    static boolean isLevelAcross(int[] walkHeights, int from, int to) {
        if (walkHeights == null || from < 0 || to >= walkHeights.length || from > to) {
            return false;
        }
        int elevation = walkHeights[from];
        for (int index = from + 1; index <= to; index++) {
            if (walkHeights[index] != elevation) return false;
        }
        return true;
    }
}
