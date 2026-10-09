package dev.civilizations.hytale;

/** Shared maximum missing-floor span for dry and non-lava flooded mine bridges. */
final class MineBridgeSpanPolicy {
    static final int MAX_SPAN = 50;

    private MineBridgeSpanPolicy() {}

    static boolean supports(int missingFloorSlices) {
        return missingFloorSlices > 0 && missingFloorSlices <= MAX_SPAN;
    }
}
