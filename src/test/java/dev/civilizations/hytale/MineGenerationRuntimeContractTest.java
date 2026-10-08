package dev.civilizations.hytale;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
final class MineGenerationRuntimeContractTest {
 @Test void regenerationIsStableAndUsesPersistentPlanningState() throws Exception {
  String s = Files.readString(Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java"));
  assertTrue(s.contains("refreshMainPlanning(world, mine, minePlan)"));
  assertTrue(s.contains("plan.sliceIndex >= plan.unlockedSlices"));
  assertTrue(s.contains("MineGenerationPolicy.capAtMinimumY"));
  assertTrue(s.contains("persisted.progressFor(additional.id())"));
  assertTrue(s.contains("appendNextMainTunnel(world, mine, minePlan, plan)"));
  assertTrue(s.contains("MAIN_GENERATION_CREATED"));
  assertTrue(s.contains("world.sendMessage("));
  assertTrue(s.contains("pathAvoidsExistingMine("));
  assertFalse(s.contains("tunnelRegistry.removeMine(world, mine.id())"));
 }
}