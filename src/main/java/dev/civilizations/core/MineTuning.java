package dev.civilizations.core;

/** Small balance values shared by the current mine runtime. */
public final class MineTuning {

    /** Keeps the established mining throughput after removing the fixed segment model. */
    private static final double SECONDS_PER_BLOCK = 60.0 / 128.0;

    private MineTuning() {
    }

    public static double secondsPerBlock() {
        return SECONDS_PER_BLOCK;
    }
}
