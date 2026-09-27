package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.Profession;

/**
 * Persistent per-inhabitant Civ state stored directly on the Hytale entity.
 *
 * <p>Transient movement and job execution state deliberately stay outside this component.</p>
 */
public final class CivInhabitantData implements Component<EntityStore> {

    public static final BuilderCodec<CivInhabitantData> CODEC =
        BuilderCodec.builder(CivInhabitantData.class, CivInhabitantData::new)
            .append(
                new KeyedCodec<>("Profession", Codec.STRING),
                (data, value) -> data.profession = value,
                data -> data.profession
            )
            .add()
            .append(
                new KeyedCodec<>("ProfessionXP", Codec.INTEGER),
                (data, value) -> data.professionXp = value,
                data -> data.professionXp
            )
            .add()
            .append(
                new KeyedCodec<>("WorkplaceId", Codec.STRING),
                (data, value) -> data.workplaceId = value,
                data -> data.workplaceId
            )
            .add()
            .build();

    private String profession = "";
    private int professionXp;
    private String workplaceId = "";

    public CivInhabitantData() {
    }

    private CivInhabitantData(CivInhabitantData other) {
        this.profession = other.profession;
        this.professionXp = other.professionXp;
        this.workplaceId = other.workplaceId;
    }

    public Profession profession() {
        if (profession == null || profession.isBlank()) {
            return null;
        }

        try {
            return Profession.valueOf(profession);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void setProfession(Profession profession) {
        this.profession = profession == null ? "" : profession.name();
    }

    public int professionXp() {
        return professionXp;
    }

    public void setProfessionXp(int professionXp) {
        if (professionXp < 0) {
            throw new IllegalArgumentException("Profession XP cannot be negative.");
        }
        this.professionXp = professionXp;
    }

    public String workplaceId() {
        return workplaceId == null || workplaceId.isBlank() ? null : workplaceId;
    }

    public void setWorkplaceId(String workplaceId) {
        this.workplaceId = workplaceId == null ? "" : workplaceId;
    }

    @Override
    public Component<EntityStore> clone() {
        return new CivInhabitantData(this);
    }
}
