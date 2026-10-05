package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;

/** Persistent serialized mine network metadata and current excavation segments for one world. */
public final class CivMineDataResource implements Resource<EntityStore> {

    public static final BuilderCodec<CivMineDataResource> CODEC =
        BuilderCodec.builder(CivMineDataResource.class, CivMineDataResource::new)
            .append(
                new KeyedCodec<>("Segments", Codec.STRING_ARRAY),
                (resource, value) -> resource.segments = value == null ? new String[0] : value,
                resource -> resource.segments
            )
            .add()
            .append(
                new KeyedCodec<>("Networks", Codec.STRING_ARRAY),
                (resource, value) -> resource.networks = value == null ? new String[0] : value,
                resource -> resource.networks
            )
            .add()
            .build();

    private String[] segments = new String[0];
    private String[] networks = new String[0];

    public String[] segments() {
        return Arrays.copyOf(segments, segments.length);
    }

    public void setSegments(String[] segments) {
        this.segments = segments == null ? new String[0] : Arrays.copyOf(segments, segments.length);
    }

    public String[] networks() {
        return Arrays.copyOf(networks, networks.length);
    }

    public void setNetworks(String[] networks) {
        this.networks = networks == null ? new String[0] : Arrays.copyOf(networks, networks.length);
    }

    @Override
    public CivMineDataResource clone() {
        CivMineDataResource copy = new CivMineDataResource();
        copy.setSegments(segments);
        copy.setNetworks(networks);
        return copy;
    }
}
