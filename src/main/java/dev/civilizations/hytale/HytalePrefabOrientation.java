package dev.civilizations.hytale;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import dev.civilizations.core.BuildingOrientation;

/** Maps Civ's Hytale-independent building orientation onto Hytale's verified prefab rotation API. */
public final class HytalePrefabOrientation {
    private HytalePrefabOrientation() {
    }

    public static PrefabRotation prefabRotation(BuildingOrientation orientation) {
        return switch (orientation) {
            case NORTH -> PrefabRotation.ROTATION_0;
            // Hytale ROTATION_270 transforms (x,z) -> (-z,x), which is Civ's clockwise EAST.
            case EAST -> PrefabRotation.ROTATION_270;
            case SOUTH -> PrefabRotation.ROTATION_180;
            case WEST -> PrefabRotation.ROTATION_90;
        };
    }

    public static Rotation3f previewRotation(BuildingOrientation orientation) {
        return new Rotation3f(0.0f, prefabRotation(orientation).getYaw(), 0.0f);
    }
}
