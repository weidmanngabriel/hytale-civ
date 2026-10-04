package dev.civilizations.plugin;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Shared lifecycle for runtime probes that execute in one warm Hytale process. */
final class CivRuntimeProbeSuite {

    private static final float WARM_TIME_DILATION = 4.0f;
    private static final AtomicReference<State> ACTIVE = new AtomicReference<>();

    private CivRuntimeProbeSuite() {
    }

    static boolean begin(Set<String> expectedScenarios) {
        if (expectedScenarios == null || expectedScenarios.isEmpty()) {
            throw new IllegalArgumentException("expectedScenarios must not be empty");
        }
        State state = new State(Set.copyOf(expectedScenarios), System.nanoTime());
        if (!ACTIVE.compareAndSet(null, state)) {
            return false;
        }
        System.out.println(
            "CIV_WARM_SUITE_STARTED scenarios=" + String.join(",", state.expectedScenarios)
                + " dilation=" + WARM_TIME_DILATION
        );
        return true;
    }

    static boolean isActive() {
        return ACTIVE.get() != null;
    }

    static void applyWarmDilation(World world) {
        if (isActive()) {
            World.setTimeDilation(WARM_TIME_DILATION, world.getEntityStore().getStore());
        }
    }

    static void scenarioPassed(String scenario) {
        State state = ACTIVE.get();
        if (state == null) {
            HytaleServer.get().shutdownServer();
            return;
        }
        if (!state.expectedScenarios.contains(scenario)) {
            scenarioFailed(scenario, "unexpected scenario completed");
            return;
        }
        if (!state.completedScenarios.add(scenario)) {
            scenarioFailed(scenario, "scenario reported PASS more than once");
            return;
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startedNanos);
        System.out.println(
            "CIV_WARM_SUITE_SCENARIO_PASS scenario=" + scenario
                + " completed=" + state.completedScenarios.size()
                + "/" + state.expectedScenarios.size()
                + " elapsedMs=" + elapsedMs
        );
        if (state.completedScenarios.containsAll(state.expectedScenarios)) {
            if (ACTIVE.compareAndSet(state, null)) {
                System.out.println(
                    "CIV_WARM_SUITE_RESULT scenarios=" + state.expectedScenarios.size()
                        + " elapsedMs=" + elapsedMs
                        + " dilation=" + WARM_TIME_DILATION
                );
                System.out.println("CIV_WARM_SUITE_PASS");
                HytaleServer.get().shutdownServer();
            }
        }
    }

    static void scenarioFailed(String scenario, String reason) {
        State state = ACTIVE.getAndSet(null);
        if (state != null) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startedNanos);
            System.out.println(
                "CIV_WARM_SUITE_FAIL scenario=" + scenario
                    + " reason=" + reason
                    + " elapsedMs=" + elapsedMs
            );
        }
        HytaleServer.get().shutdownServer();
    }

    private static final class State {
        private final Set<String> expectedScenarios;
        private final Set<String> completedScenarios = ConcurrentHashMap.newKeySet();
        private final long startedNanos;

        private State(Set<String> expectedScenarios, long startedNanos) {
            this.expectedScenarios = expectedScenarios;
            this.startedNanos = startedNanos;
        }
    }
}
