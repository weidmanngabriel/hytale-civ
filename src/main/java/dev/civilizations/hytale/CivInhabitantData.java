package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.EnumMapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.Gender;
import dev.civilizations.core.Profession;

import java.util.EnumMap;
import java.util.Map;

/**
 * Persistent Civ state stored directly on a Hytale inhabitant entity.
 *
 * <p>Transient selection, movement and job execution state deliberately stay outside this
 * component. The Hytale UUIDComponent is the inhabitant's stable entity identity.</p>
 */
public final class CivInhabitantData implements Component<EntityStore> {

    private static final EnumMapCodec<Profession, Integer> PROFESSION_PROGRESS_CODEC =
        new EnumMapCodec<>(Profession.class, Codec.INTEGER);

    public static final BuilderCodec<CivInhabitantData> CODEC =
        BuilderCodec.builder(CivInhabitantData.class, CivInhabitantData::new)
            .append(new KeyedCodec<>("Gender", Codec.STRING),
                (data, value) -> data.gender = value, data -> data.gender).add()
            .append(new KeyedCodec<>("FirstName", Codec.STRING),
                (data, value) -> data.firstName = value, data -> data.firstName).add()
            .append(new KeyedCodec<>("MiddleName", Codec.STRING),
                (data, value) -> data.middleName = value, data -> data.middleName).add()
            .append(new KeyedCodec<>("LastName", Codec.STRING),
                (data, value) -> data.lastName = value, data -> data.lastName).add()
            .append(new KeyedCodec<>("Profession", Codec.STRING),
                (data, value) -> data.profession = value, data -> data.profession).add()
            .append(new KeyedCodec<>("ProfessionProgress", PROFESSION_PROGRESS_CODEC),
                CivInhabitantData::setProfessionProgress,
                data -> data.professionProgress).add()
            .append(new KeyedCodec<>("WorkplaceId", Codec.STRING),
                (data, value) -> data.workplaceId = value, data -> data.workplaceId).add()
            .build();

    private String gender = "";
    private String firstName = "";
    private String middleName = "";
    private String lastName = "";
    private String profession = Profession.UNEMPLOYED.name();
    private Map<Profession, Integer> professionProgress = new EnumMap<>(Profession.class);
    private String workplaceId = "";

    public CivInhabitantData() {
    }

    private CivInhabitantData(CivInhabitantData other) {
        this.gender = other.gender;
        this.firstName = other.firstName;
        this.middleName = other.middleName;
        this.lastName = other.lastName;
        this.profession = other.profession;
        this.professionProgress = new EnumMap<>(other.professionProgress);
        this.workplaceId = other.workplaceId;
    }

    public boolean hasIdentity() {
        return gender() != null
            && !firstName().isBlank()
            && !middleName().isBlank()
            && !lastName().isBlank();
    }

    public Gender gender() {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        try {
            return Gender.valueOf(gender);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void setIdentity(Gender gender, String firstName, String middleName, String lastName) {
        this.gender = gender.name();
        this.firstName = requireName(firstName, "firstName");
        this.middleName = requireName(middleName, "middleName");
        this.lastName = requireName(lastName, "lastName");
    }

    public String firstName() {
        return firstName == null ? "" : firstName;
    }

    public String middleName() {
        return middleName == null ? "" : middleName;
    }

    public String lastName() {
        return lastName == null ? "" : lastName;
    }

    public String fullName() {
        return firstName() + " " + middleName() + " " + lastName();
    }

    public Profession profession() {
        if (profession == null || profession.isBlank()) {
            return Profession.UNEMPLOYED;
        }
        try {
            return Profession.valueOf(profession);
        } catch (IllegalArgumentException ignored) {
            return Profession.UNEMPLOYED;
        }
    }

    public void setProfession(Profession profession) {
        this.profession = profession == null ? Profession.UNEMPLOYED.name() : profession.name();
    }

    public int professionXp(Profession profession) {
        if (profession == null || profession == Profession.UNEMPLOYED) {
            return 0;
        }
        return Math.max(0, professionProgress.getOrDefault(profession, 0));
    }

    public void setProfessionXp(Profession profession, int xp) {
        if (profession == null || profession == Profession.UNEMPLOYED) {
            throw new IllegalArgumentException("Unemployed does not have profession XP.");
        }
        if (xp < 0) {
            throw new IllegalArgumentException("Profession XP cannot be negative.");
        }
        if (xp == 0) {
            professionProgress.remove(profession);
        } else {
            professionProgress.put(profession, xp);
        }
    }

    public String workplaceId() {
        return workplaceId == null || workplaceId.isBlank() ? null : workplaceId;
    }

    public void setWorkplaceId(String workplaceId) {
        this.workplaceId = workplaceId == null ? "" : workplaceId;
    }

    private void setProfessionProgress(Map<Profession, Integer> progress) {
        professionProgress = new EnumMap<>(Profession.class);
        if (progress == null) {
            return;
        }
        progress.forEach((profession, xp) -> {
            if (profession != null && profession != Profession.UNEMPLOYED && xp != null && xp >= 0) {
                if (xp > 0) {
                    professionProgress.put(profession, xp);
                }
            }
        });
    }

    private static String requireName(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }

    @Override
    public Component<EntityStore> clone() {
        return new CivInhabitantData(this);
    }
}
