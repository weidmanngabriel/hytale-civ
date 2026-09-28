package dev.civilizations.hytale;

import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseInputType;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3f;

/**
 * Applies the experimental isometric-style cursor camera used by the RTS interaction spike.
 */
public final class RtsCameraController {

    private static final float YAW_RADIANS = (float) Math.toRadians(-45.0);
    private static final float PITCH_RADIANS = (float) Math.toRadians(-60.0);

    public void enable(PlayerRef playerRef) {
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.positionLerpSpeed = 0.2f;
        settings.rotationLerpSpeed = 0.2f;
        settings.distance = 18.0f;
        settings.displayCursor = true;
        settings.displayReticle = false;
        settings.isFirstPerson = false;
        settings.movementForceRotationType = MovementForceRotationType.Custom;
        settings.movementForceRotation = new Direction(YAW_RADIANS, 0.0f, 0.0f);
        settings.eyeOffset = true;
        settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction(YAW_RADIANS, PITCH_RADIANS, 0.0f);
        settings.mouseInputType = MouseInputType.LookAtPlane;
        settings.planeNormal = new Vector3f(0.0f, 1.0f, 0.0f);

        playerRef.getPacketHandler().writeNoCache(
            new SetServerCamera(ClientCameraView.Custom, true, settings)
        );
    }

    public void disable(PlayerRef playerRef) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        CameraManager cameraManager = playerEntityRef.getStore().getComponent(
            playerEntityRef,
            CameraManager.getComponentType()
        );
        if (cameraManager != null) {
            cameraManager.resetCamera(playerRef);
        }
    }
}
