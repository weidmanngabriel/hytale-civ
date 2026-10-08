package dev.civilizations.core;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
final class MineGenerationPolicyTest {
 @Test void identitiesAndHeadingsAreDeterministic() {
  UUID mine = UUID.randomUUID();
  assertEquals(MineGenerationPolicy.tunnelId(mine, 3), MineGenerationPolicy.tunnelId(mine, 3));
  assertNotEquals(MineGenerationPolicy.tunnelId(mine, 2), MineGenerationPolicy.tunnelId(mine, 3));
  assertEquals(MineHeading.SOUTH, MineGenerationPolicy.heading(MineHeading.NORTH, 2));
 }
 @Test void clippedPathStopsAtYTen() {
  List<MinePathPoint> points = List.of(point(0, 12), point(1, 11), point(2, 10), point(3, 9));
  MineTunnelPath path = new MineTunnelPath(MineTunnel.Kind.MAIN, 123L, points, List.of());
  MineTunnelPath capped = MineGenerationPolicy.capAtMinimumY(path);
  assertEquals(3, capped.points().size());
  assertTrue(MineGenerationPolicy.reachedMinimumY(capped));
  assertEquals(10, Math.round(capped.points().getLast().y()));
 }
 @Test void refreshNeedsTenMinutes() {
  assertFalse(MineGenerationPolicy.due(1000, 600999));
  assertTrue(MineGenerationPolicy.due(1000, 601000));
 }
 private static MinePathPoint point(int n, double y) {
  return new MinePathPoint(n, n, y, 0, 6, 6, MineHeading.NORTH, -90, 0);
 }
}