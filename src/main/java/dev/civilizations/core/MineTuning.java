package dev.civilizations.core;

/** Small balance values shared by the current mine runtime. */
public final class MineTuning {

    /** Excavation throughput: twice the original 128 blocks/minute per active miner. */
    private static final double SECONDS_PER_BLOCK = 30.0 / 128.0;

    private MineTuning() {
    }

    public static double secondsPerBlock() {
        return SECONDS_PER_BLOCK;
    }
}
