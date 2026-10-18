package dev.civilizations.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Conserved wood units across ground piles, workers and warehouse. No engine inventory rules. */
public final class WoodCargoLedger {
    public static final int TRANSPORT_SLOTS = 2;
    public static final int UNITS_PER_SIMULATED_SLOT = 100;
    public static final int CAPACITY = TRANSPORT_SLOTS * UNITS_PER_SIMULATED_SLOT;
    public static final int MIN_FREE_CAPACITY_FOR_TREE = 60;

    private final Map<BlockPosition, Integer> piles = new LinkedHashMap<>();
    private final Map<String, Integer> carried = new LinkedHashMap<>();
    private int warehouse;

    public void fell(BlockPosition position, int units) {
        Objects.requireNonNull(position, "position");
        if (units <= 0) throw new IllegalArgumentException("units must be > 0");
        piles.merge(position, units, Math::addExact);
    }

    public int pickUp(String workerId, BlockPosition position) {
        Objects.requireNonNull(workerId, "workerId");
        Integer amount = piles.get(position);
        if (amount == null) return 0;
        int current = carried(workerId);
        int taken = Math.min(amount, CAPACITY - current);
        if (taken <= 0) return 0;
        carried.put(workerId, current + taken);
        if (taken == amount) piles.remove(position);
        else piles.put(position, amount - taken);
        return taken;
    }

    public int deposit(String workerId) {
        int amount = carried(workerId);
        warehouse = Math.addExact(warehouse, amount);
        carried.remove(workerId);
        return amount;
    }

    public int carried(String workerId) { return carried.getOrDefault(workerId, 0); }
    public int warehouse() { return warehouse; }
    public Map<BlockPosition, Integer> piles() { return Map.copyOf(piles); }
    public int ground() { return piles.values().stream().mapToInt(Integer::intValue).sum(); }
    public int total() { return warehouse + ground() + carried.values().stream().mapToInt(Integer::intValue).sum(); }
    public boolean shouldReturn(String workerId) {
        return carried(workerId) > 0 && CAPACITY - carried(workerId) < MIN_FREE_CAPACITY_FOR_TREE;
    }
}
