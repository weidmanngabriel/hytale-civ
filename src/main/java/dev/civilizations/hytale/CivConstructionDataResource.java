package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;

/** Persistent world resource for active Civ construction sites and their lightweight progress. */
public final class CivConstructionDataResource implements Resource<EntityStore> {

    public static final BuilderCodec<CivConstructionDataResource> CODEC =
        BuilderCodec.builder(CivConstructionDataResource.class, CivConstructionDataResource::new)
            .append(
                new KeyedCodec<>("Sites", Codec.STRING_ARRAY),
                (resource, value) -> resource.sites = value == null ? new String[0] : value,
                resource -> resource.sites
            )
            .add()
            .append(
                new KeyedCodec<>("Progress", Codec.STRING_ARRAY),
                (resource, value) -> resource.progress = value == null ? new String[0] : value,
                resource -> resource.progress
            )
            .add()
            .build();

    private String[] sites = new String[0];
    private String[] progress = new String[0];

    public String[] sites() {
        return Arrays.copyOf(sites, sites.length);
    }

    public void setSites(String[] sites) {
        this.sites = sites == null ? new String[0] : Arrays.copyOf(sites, sites.length);
    }

    public String[] progress() {
        return Arrays.copyOf(progress, progress.length);
    }

    public void setProgress(String[] progress) {
        this.progress = progress == null ? new String[0] : Arrays.copyOf(progress, progress.length);
    }

    @Override
    public CivConstructionDataResource clone() {
        CivConstructionDataResource copy = new CivConstructionDataResource();
        copy.setSites(sites);
        copy.setProgress(progress);
        return copy;
    }
}
