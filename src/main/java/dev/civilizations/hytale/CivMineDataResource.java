package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;

/** Persistent serialized mine-network state for one world. */
public final class CivMineDataResource implements Resource<EntityStore> {

    public static final BuilderCodec<CivMineDataResource> CODEC =
        BuilderCodec.builder(CivMineDataResource.class, CivMineDataResource::new)
            .append(
                new KeyedCodec<>("Networks", Codec.STRING_ARRAY),
                (resource, value) -> resource.networks = value == null ? new String[0] : value,
                resource -> resource.networks
            )
            .add()
            .build();

    private String[] networks = new String[0];

    public String[] networks() {
        return Arrays.copyOf(networks, networks.length);
    }

    public void setNetworks(String[] networks) {
        this.networks = networks == null ? new String[0] : Arrays.copyOf(networks, networks.length);
    }

    @Override
    public CivMineDataResource clone() {
        CivMineDataResource copy = new CivMineDataResource();
        copy.setNetworks(networks);
        return copy;
    }
}
