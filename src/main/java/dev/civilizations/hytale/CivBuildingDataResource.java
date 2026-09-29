package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;

public final class CivBuildingDataResource implements Resource<EntityStore> {

    public static final BuilderCodec<CivBuildingDataResource> CODEC =
        BuilderCodec.builder(CivBuildingDataResource.class, CivBuildingDataResource::new)
            .append(
                new KeyedCodec<>("Buildings", Codec.STRING_ARRAY),
                (resource, value) -> resource.buildings = value == null ? new String[0] : value,
                resource -> resource.buildings
            )
            .add()
            .build();

    private String[] buildings = new String[0];

    public String[] buildings() {
        return Arrays.copyOf(buildings, buildings.length);
    }

    public void setBuildings(String[] buildings) {
        this.buildings = buildings == null ? new String[0] : Arrays.copyOf(buildings, buildings.length);
    }

    @Override
    public CivBuildingDataResource clone() {
        CivBuildingDataResource copy = new CivBuildingDataResource();
        copy.setBuildings(buildings);
        return copy;
    }
}
