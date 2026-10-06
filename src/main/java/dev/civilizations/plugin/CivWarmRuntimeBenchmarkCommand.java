package dev.civilizations.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivUnitRegistry;

import java.util.Set;

/** Runtime-only benchmark that executes the available real worker probe in one server. */
final class CivWarmRuntimeBenchmarkCommand extends CommandBase {

    private final CivUnitRegistry unitRegistry;

    CivWarmRuntimeBenchmarkCommand(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry
    ) {
        super("civwarmruntimebenchmark", "Runs real Civ runtime probes in one warm Hytale server.");
        requireNoPermission();
        this.unitRegistry = unitRegistry;
    }

    @Override
    protected void executeSync(CommandContext context) {
        if (!CivRuntimeProbeSuite.begin(Set.of("woodcutter"))) {
            System.out.println("CIV_WARM_SUITE_FAIL scenario=suite reason=already-active");
            CivRuntimeProbeSuite.scenarioFailed("suite", "already-active");
            return;
        }

        System.out.println("CIV_WARM_RUNTIME_BENCHMARK_STARTED realScenarios=woodcutter warmProcess=true");
        try {
            new CivWoodcutterFixtureProbeCommand(unitRegistry).start();
        } catch (Throwable throwable) {
            System.out.println("CIV_WARM_RUNTIME_BENCHMARK_FAIL could not start real probes");
            throwable.printStackTrace(System.out);
            CivRuntimeProbeSuite.scenarioFailed("suite", "could not start real probes");
        }
    }
}
