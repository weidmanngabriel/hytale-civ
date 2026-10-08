package dev.civilizations.core;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Selects the nearest usable accommodation without creating a room-occupancy simulation. */
public final class MineIdleDestinationSelector {
    private MineIdleDestinationSelector() {}

    public static MineRoom select(
        List<MineRoom> rooms,
        double x, double y, double z,
        UUID currentRoomId,
        Set<UUID> excludedRooms,
        Predicate<MineRoom> usable
    ) {
        if (rooms == null || usable == null) return null;
        // A miner already waiting in a usable room does not relocate merely because
        // another room was built closer.
        if (currentRoomId != null) {
            for (MineRoom room : rooms) {
                if (room.id().equals(currentRoomId) && eligible(room, excludedRooms, usable)) return room;
            }
        }
        return rooms.stream()
            .filter(room -> eligible(room, excludedRooms, usable))
            .min(Comparator
                .comparingDouble((MineRoom room) -> distanceSquared(room.position(), x, y, z))
                .thenComparing(MineRoom::id))
            .orElse(null);
    }

    private static boolean eligible(
        MineRoom room, Set<UUID> excludedRooms, Predicate<MineRoom> usable
    ) {
        return room.type() == MineRoom.Type.REST_ACCOMMODATION
            && room.state() == MineRoom.State.BUILT
            && (excludedRooms == null || !excludedRooms.contains(room.id()))
            && usable.test(room);
    }

    private static double distanceSquared(BlockPosition position, double x, double y, double z) {
        double dx = position.x() + 0.5 - x;
        double dy = position.y() - y;
        double dz = position.z() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
