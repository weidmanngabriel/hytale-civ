package dev.civilizations.hytale;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Opt-in runtime profiler for the real Hytale woodcutter tree search.
 *
 * <p>The counters measure the adapter's actual search work. They intentionally do not live in the
 * headless simulation because wall-clock timings and Hytale world-query costs are runtime concerns.</p>
 */
public final class WoodcutterScanDiagnostics {

    private static final long LOG_INTERVAL_NANOS = 10_000_000_000L;

    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicLong scans = new AtomicLong();
    private final AtomicLong totalNanos = new AtomicLong();
    private final AtomicLong maxNanos = new AtomicLong();
    private final AtomicLong positionsChecked = new AtomicLong();
    private final AtomicLong treeBases = new AtomicLong();
    private final AtomicLong treesCollected = new AtomicLong();
    private final AtomicLong treeBlocksCollected = new AtomicLong();
    private final AtomicLong protectedTrees = new AtomicLong();
    private final AtomicLong reservedTrees = new AtomicLong();
    private final AtomicLong workTargetChecks = new AtomicLong();
    private final AtomicLong noStandPositionTrees = new AtomicLong();
    private final AtomicLong usableTrees = new AtomicLong();
    private final AtomicLong lastLogNanos = new AtomicLong();

    public boolean enabled() {
        return enabled.get();
    }

    public ToggleResult toggle() {
        boolean next = !enabled.get();
        enabled.set(next);
        if (next) {
            reset();
            lastLogNanos.set(System.nanoTime());
        }
        return new ToggleResult(next, snapshot());
    }

    public void record(
        long elapsedNanos,
        long checkedPositions,
        long foundTreeBases,
        long collectedTrees,
        long collectedTreeBlocks,
        long rejectedProtectedTrees,
        long rejectedReservedTrees,
        long checkedWorkTargets,
        long rejectedNoStandPositionTrees,
        long foundUsableTrees
    ) {
        if (!enabled.get()) {
            return;
        }

        scans.incrementAndGet();
        totalNanos.addAndGet(Math.max(0L, elapsedNanos));
        maxNanos.accumulateAndGet(Math.max(0L, elapsedNanos), Math::max);
        positionsChecked.addAndGet(checkedPositions);
        treeBases.addAndGet(foundTreeBases);
        treesCollected.addAndGet(collectedTrees);
        treeBlocksCollected.addAndGet(collectedTreeBlocks);
        protectedTrees.addAndGet(rejectedProtectedTrees);
        reservedTrees.addAndGet(rejectedReservedTrees);
        workTargetChecks.addAndGet(checkedWorkTargets);
        noStandPositionTrees.addAndGet(rejectedNoStandPositionTrees);
        usableTrees.addAndGet(foundUsableTrees);

        maybeLog();
    }

    public Snapshot snapshot() {
        long scanCount = scans.get();
        long nanos = totalNanos.get();
        return new Snapshot(
            scanCount,
            nanos,
            maxNanos.get(),
            positionsChecked.get(),
            treeBases.get(),
            treesCollected.get(),
            treeBlocksCollected.get(),
            protectedTrees.get(),
            reservedTrees.get(),
            workTargetChecks.get(),
            noStandPositionTrees.get(),
            usableTrees.get()
        );
    }

    private void maybeLog() {
        long now = System.nanoTime();
        long previous = lastLogNanos.get();
        if (now - previous < LOG_INTERVAL_NANOS || !lastLogNanos.compareAndSet(previous, now)) {
            return;
        }
        System.out.println("[CivWoodcutterPerf] " + snapshot().summary());
    }

    private void reset() {
        scans.set(0L);
        totalNanos.set(0L);
        maxNanos.set(0L);
        positionsChecked.set(0L);
        treeBases.set(0L);
        treesCollected.set(0L);
        treeBlocksCollected.set(0L);
        protectedTrees.set(0L);
        reservedTrees.set(0L);
        workTargetChecks.set(0L);
        noStandPositionTrees.set(0L);
        usableTrees.set(0L);
    }

    public record ToggleResult(boolean enabled, Snapshot snapshot) {
    }

    public record Snapshot(
        long scans,
        long totalNanos,
        long maxNanos,
        long positionsChecked,
        long treeBases,
        long treesCollected,
        long treeBlocksCollected,
        long protectedTrees,
        long reservedTrees,
        long workTargetChecks,
        long noStandPositionTrees,
        long usableTrees
    ) {
        public double averageMillis() {
            return scans == 0L ? 0.0 : (totalNanos / 1_000_000.0) / scans;
        }

        public double maxMillis() {
            return maxNanos / 1_000_000.0;
        }

        public String summary() {
            return String.format(
                Locale.ROOT,
                "scans=%d avgMs=%.3f maxMs=%.3f positions=%d treeBases=%d treesCollected=%d treeBlocks=%d protected=%d reserved=%d workTargetChecks=%d noStand=%d usable=%d",
                scans,
                averageMillis(),
                maxMillis(),
                positionsChecked,
                treeBases,
                treesCollected,
                treeBlocksCollected,
                protectedTrees,
                reservedTrees,
                workTargetChecks,
                noStandPositionTrees,
                usableTrees
            );
        }
    }
}
