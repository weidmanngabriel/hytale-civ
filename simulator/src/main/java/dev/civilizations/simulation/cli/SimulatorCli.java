package dev.civilizations.simulation.cli;

import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;

/** Command-line entry point for standalone deterministic headless tests. */
public final class SimulatorCli {
    private SimulatorCli() {}

    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "woodcutter-basic";
        long ticks = args.length > 1 ? Long.parseLong(args[1]) : 1000;
        if (ticks < 0 || ticks > 10_000_000L) {
            throw new IllegalArgumentException("Ticks must be between 0 and 10000000");
        }
        SimulationScenario scenario = SimulationScenarios.all().stream()
            .filter(s -> s.id().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + name));
        SimulationRuntime runtime = scenario.createRuntime();
        runtime.runTicks(ticks);
        System.out.println("SIMULATION_OK scenario=" + name + " ticks=" + runtime.tickCount());
        System.out.println("snapshot=" + runtime.worldSnapshot());
    }
}
