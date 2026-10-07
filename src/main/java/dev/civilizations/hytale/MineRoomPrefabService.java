package dev.civilizations.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.MineRoom;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Hytale adapter for the three replaceable Layer-6 test room prefabs. */
public final class MineRoomPrefabService {

    private MineRoomPrefabService() {
    }

    public static String prefabKey(MineRoom.Type type) {
        return switch (type) {
            case SMALL_NICHE -> "Civilizations/Mine/Rooms/Small_Niche_01.prefab.json";
            case MATERIAL_STORAGE -> "Civilizations/Mine/Rooms/Material_Storage_01.prefab.json";
            case REST_ACCOMMODATION -> "Civilizations/Mine/Rooms/Rest_Accommodation_01.prefab.json";
            default -> null;
        };
    }

    public static int sectionCount(MineRoom room) {
        BlockSelection source = load(room);
        if (source == null) return 0;
        return occupiedLayers(source).size();
    }

    public static boolean placeSection(
        World world,
        MineRoom room,
        int sectionIndex,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (world == null || room == null || commandBuffer == null) return false;
        BlockSelection source = load(room);
        if (source == null) return false;
        List<Integer> layers = occupiedLayers(source);
        if (sectionIndex < 0 || sectionIndex >= layers.size()) return false;

        int sourceY = layers.get(sectionIndex);
        BlockSelection section = new BlockSelection();
        section.copyPropertiesFrom(source);
        section.setAnchor(source.getAnchorX(), source.getAnchorY(), source.getAnchorZ());
        source.forEachBlock((x, y, z, holder) -> {
            if (y != sourceY) return;
            section.addBlockAtWorldPos(
                x, y, z,
                holder.blockId(),
                holder.rotation(),
                holder.filler(),
                holder.supportValue(),
                holder.holder()
            );
        });
        section.placeNoReturn(
            world,
            new Vector3i(room.position().x(), room.position().y(), room.position().z()),
            commandBuffer
        );
        return true;
    }

    static BlockSelection load(MineRoom room) {
        String key = prefabKey(room.type());
        if (key == null) return null;
        BlockSelection original = PrefabStore.get().getAssetPrefabFromAnyPack(key);
        if (original == null) return null;

        BlockSelection selection = original.cloneSelection();
        BuildingOrientation orientation = orientation(room.outwardHeading());
        int degrees = HytalePrefabOrientation.blockSelectionDegrees(orientation);
        if (degrees == 0) return selection;
        return selection.rotate(
            Axis.Y,
            degrees,
            new Vector3d(selection.getAnchorX(), selection.getAnchorY(), selection.getAnchorZ())
        );
    }

    private static List<Integer> occupiedLayers(BlockSelection selection) {
        TreeSet<Integer> layers = new TreeSet<>();
        selection.forEachBlock((x, y, z, holder) -> layers.add(y));
        return new ArrayList<>(layers);
    }

    private static BuildingOrientation orientation(MineHeading heading) {
        return switch (heading) {
            case NORTH -> BuildingOrientation.NORTH;
            case EAST -> BuildingOrientation.EAST;
            case SOUTH -> BuildingOrientation.SOUTH;
            case WEST -> BuildingOrientation.WEST;
            default -> throw new IllegalArgumentException("Room prefab heading must be cardinal.");
        };
    }
}
