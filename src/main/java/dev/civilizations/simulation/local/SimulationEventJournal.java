package dev.civilizations.simulation.local;

import dev.civilizations.simulation.SimulationRuntime;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only bounded trace of state transitions; does not change Core decisions. */
public final class SimulationEventJournal {
    private static final int LIMIT = 100;
    private final ArrayDeque<Event> recent = new ArrayDeque<>();
    private final Map<String, String> previousStates = new HashMap<>();

    public void observe(SimulationRuntime.WorldSnapshot snapshot) {
        for (var resident : snapshot.residents()) {
            String before = previousStates.put(resident.id(), resident.state());
            if (before != null && !before.equals(resident.state())) {
                recent.addLast(new Event(snapshot.tickCount(), resident.id(), before, resident.state()));
                while (recent.size() > LIMIT) recent.removeFirst();
            }
        }
    }

    public void reset() {
        recent.clear();
        previousStates.clear();
    }

    public List<Event> events() {
        return List.copyOf(recent);
    }

    public record Event(long tick, String resident, String from, String to) { }
}
