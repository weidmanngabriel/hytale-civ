package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineRoomPlannerTest {

    @Test
    void planningIsDeterministicAndAccommodationUsesMainTunnelSpacing() {
        UUID mineId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        MineNetworkGrowthPlanner.Plan plan = MineNetworkGrowthPlanner.plan(
            mineId,
            new BlockPosition(0, 20, 0),
            MineHeading.EAST,
            320,
            1,
            77123L
        );

        List<MineRoom> first = MineRoomPlanner.plan(plan);
        List<MineRoom> second = MineRoomPlanner.plan(plan);
        assertEquals(first, second);

        List<MineRoom> accommodation = first.stream()
            .filter(room -> room.type() == MineRoom.Type.REST_ACCOMMODATION)
            .toList();
        assertFalse(accommodation.isEmpty());
        assertTrue(accommodation.stream().allMatch(room ->
            room.tunnelId().equals(plan.network().mainTunnelId())));
        for (int i = 1; i < accommodation.size(); i++) {
            int delta = accommodation.get(i).attachmentSliceIndex()
                - accommodation.get(i - 1).attachmentSliceIndex();
            assertTrue(delta >= MineRoomPlanner.REST_MIN_SPACING_BLOCKS);
            assertTrue(delta <= MineRoomPlanner.REST_MAX_SPACING_BLOCKS + 20,
                "local placement rejection may delay the next accepted opportunity slightly");
        }
    }

    @Test
    void v1OnlyCreatesAuthoredRoomTypesAndUsesBranchForNiches() {
        boolean sawStorage = false;
        boolean sawNiche = false;
        for (long seed = 1; seed <= 40 && (!sawStorage || !sawNiche); seed++) {
            MineNetworkGrowthPlanner.Plan plan = MineNetworkGrowthPlanner.plan(
                UUID.nameUUIDFromBytes(("mine-" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new BlockPosition(0, 20, 0),
                MineHeading.EAST,
                260,
                48,
                seed
            );
            for (MineRoom room : MineRoomPlanner.plan(plan)) {
                assertTrue(room.type() == MineRoom.Type.REST_ACCOMMODATION
                    || room.type() == MineRoom.Type.MATERIAL_STORAGE
                    || room.type() == MineRoom.Type.SMALL_NICHE);
                MineTunnel tunnel = plan.network().tunnel(room.tunnelId());
                if (room.type() == MineRoom.Type.MATERIAL_STORAGE) {
                    sawStorage = true;
                    assertEquals(MineTunnel.Kind.MAIN, tunnel.kind());
                }
                if (room.type() == MineRoom.Type.SMALL_NICHE) {
                    sawNiche = true;
                    assertEquals(MineTunnel.Kind.BRANCH, tunnel.kind());
                }
            }
        }
        assertTrue(sawStorage, "test seeds should produce at least one material storage room");
        assertTrue(sawNiche, "test seeds should produce at least one branch niche");
    }

    @Test
    void configuredRatesStayInsideCanonicalRanges() {
        assertTrue(MineRoomPlanner.MATERIAL_STORAGE_CHANCE >= 0.25
            && MineRoomPlanner.MATERIAL_STORAGE_CHANCE <= 0.35);
        assertTrue(MineRoomPlanner.TOOL_WORKSHOP_CHANCE >= 0.10
            && MineRoomPlanner.TOOL_WORKSHOP_CHANCE <= 0.15);
        assertTrue(MineRoomPlanner.ORE_COLLECTION_CHANCE >= 0.15
            && MineRoomPlanner.ORE_COLLECTION_CHANCE <= 0.25);
        assertTrue(MineRoomPlanner.LARGE_NATURAL_CHAMBER_CHANCE >= 0.05
            && MineRoomPlanner.LARGE_NATURAL_CHAMBER_CHANCE <= 0.10);
        assertTrue(MineRoomPlanner.SMALL_NICHE_CHANCE >= 0.20
            && MineRoomPlanner.SMALL_NICHE_CHANCE <= 0.30);
        assertTrue(MineRoomPlanner.SUPPORT_SUPPLY_CHANCE >= 0.10
            && MineRoomPlanner.SUPPORT_SUPPLY_CHANCE <= 0.20);
        assertEquals(0.05, MineRoomPlanner.WATER_DRAINAGE_CHANCE);
        assertTrue(MineRoomPlanner.LARGE_WORK_HALL_CHANCE >= 0.03
            && MineRoomPlanner.LARGE_WORK_HALL_CHANCE <= 0.06);
    }
}
