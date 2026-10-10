package dev.civilizations.simulation.local;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Deterministic bounded event history for local simulation diagnostics.
 * A log belongs to one simulation run and never affects gameplay decisions.
 */
public final class SimulationEventLog {
    private static final int CAPACITY = 200;
    private final ArrayDeque<Event> history = new ArrayDeque<>();

    public synchronized void record(long tick, String kind, String message) {
        if (tick < 0 || kind == null || kind.isBlank() || message == null)
            throw new IllegalArgumentException("Invalid event");
        if (history.size() == CAPACITY) history.removeFirst();
        history.addLast(new Event(tick, kind, message));
    }

    public synchronized List<Event> snapshot() {
        return List.copyOf(history);
    }

    public synchronized void clear() {
        history.clear();
    }

    public record Event(long tick, String kind, String message) {}
}
