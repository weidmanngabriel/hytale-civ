package dev.civilizations.hytale;

import org.joml.Vector3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime handoff from Hytale navigation observation to miner gameplay work selection.
 */
public final class MinerNavigationFailureRegistry {

    private static final double TARGET_EPSILON_SQUARED = 0.0001;

    private final Map<CivUnitRegistry.UnitKey, Vector3d> failures = new ConcurrentHashMap<>();

    public void report(CivUnitRegistry.UnitKey worker, Vector3d target) {
        if (worker == null || target == null) return;
        failures.put(worker, new Vector3d(target));
    }

    public boolean consumeIfMatches(CivUnitRegistry.UnitKey worker, Vector3d currentTarget) {
        if (worker == null) return false;
        Vector3d failedTarget = failures.remove(worker);
        return failedTarget != null
            && currentTarget != null
            && failedTarget.distanceSquared(currentTarget) <= TARGET_EPSILON_SQUARED;
    }

    public void forget(CivUnitRegistry.UnitKey worker) {
        if (worker != null) failures.remove(worker);
    }
}
