package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.EnumMapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.AgeStage;
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
            .append(new KeyedCodec<>("AgeStage", Codec.STRING),
                (data, value) -> data.ageStage = value, data -> data.ageStage).add()
            .append(new KeyedCodec<>("SkinBodyCharacteristic", Codec.STRING),
                (data, value) -> data.skinBodyCharacteristic = value,
                data -> data.skinBodyCharacteristic).add()
            .append(new KeyedCodec<>("SkinUnderwear", Codec.STRING),
                (data, value) -> data.skinUnderwear = value, data -> data.skinUnderwear).add()
            .append(new KeyedCodec<>("SkinFace", Codec.STRING),
                (data, value) -> data.skinFace = value, data -> data.skinFace).add()
            .append(new KeyedCodec<>("SkinEyes", Codec.STRING),
                (data, value) -> data.skinEyes = value, data -> data.skinEyes).add()
            .append(new KeyedCodec<>("SkinEars", Codec.STRING),
                (data, value) -> data.skinEars = value, data -> data.skinEars).add()
            .append(new KeyedCodec<>("SkinMouth", Codec.STRING),
                (data, value) -> data.skinMouth = value, data -> data.skinMouth).add()
            .append(new KeyedCodec<>("SkinFacialHair", Codec.STRING),
                (data, value) -> data.skinFacialHair = value, data -> data.skinFacialHair).add()
            .append(new KeyedCodec<>("SkinHaircut", Codec.STRING),
                (data, value) -> data.skinHaircut = value, data -> data.skinHaircut).add()
            .append(new KeyedCodec<>("SkinEyebrows", Codec.STRING),
                (data, value) -> data.skinEyebrows = value, data -> data.skinEyebrows).add()
            .append(new KeyedCodec<>("SkinPants", Codec.STRING),
                (data, value) -> data.skinPants = value, data -> data.skinPants).add()
            .append(new KeyedCodec<>("SkinOverpants", Codec.STRING),
                (data, value) -> data.skinOverpants = value, data -> data.skinOverpants).add()
            .append(new KeyedCodec<>("SkinUndertop", Codec.STRING),
                (data, value) -> data.skinUndertop = value, data -> data.skinUndertop).add()
            .append(new KeyedCodec<>("SkinOvertop", Codec.STRING),
                (data, value) -> data.skinOvertop = value, data -> data.skinOvertop).add()
            .append(new KeyedCodec<>("SkinShoes", Codec.STRING),
                (data, value) -> data.skinShoes = value, data -> data.skinShoes).add()
            .append(new KeyedCodec<>("SkinHeadAccessory", Codec.STRING),
                (data, value) -> data.skinHeadAccessory = value,
                data -> data.skinHeadAccessory).add()
            .append(new KeyedCodec<>("SkinFaceAccessory", Codec.STRING),
                (data, value) -> data.skinFaceAccessory = value,
                data -> data.skinFaceAccessory).add()
            .append(new KeyedCodec<>("SkinEarAccessory", Codec.STRING),
                (data, value) -> data.skinEarAccessory = value,
                data -> data.skinEarAccessory).add()
            .append(new KeyedCodec<>("SkinFeature", Codec.STRING),
                (data, value) -> data.skinFeature = value, data -> data.skinFeature).add()
            .append(new KeyedCodec<>("SkinGloves", Codec.STRING),
                (data, value) -> data.skinGloves = value, data -> data.skinGloves).add()
            .append(new KeyedCodec<>("SkinCape", Codec.STRING),
                (data, value) -> data.skinCape = value, data -> data.skinCape).add()
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
    private String ageStage = "";
    private String skinBodyCharacteristic = "";
    private String skinUnderwear = "";
    private String skinFace = "";
    private String skinEyes = "";
    private String skinEars = "";
    private String skinMouth = "";
    private String skinFacialHair = "";
    private String skinHaircut = "";
    private String skinEyebrows = "";
    private String skinPants = "";
    private String skinOverpants = "";
    private String skinUndertop = "";
    private String skinOvertop = "";
    private String skinShoes = "";
    private String skinHeadAccessory = "";
    private String skinFaceAccessory = "";
    private String skinEarAccessory = "";
    private String skinFeature = "";
    private String skinGloves = "";
    private String skinCape = "";
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
        this.ageStage = other.ageStage;
        this.skinBodyCharacteristic = other.skinBodyCharacteristic;
        this.skinUnderwear = other.skinUnderwear;
        this.skinFace = other.skinFace;
        this.skinEyes = other.skinEyes;
        this.skinEars = other.skinEars;
        this.skinMouth = other.skinMouth;
        this.skinFacialHair = other.skinFacialHair;
        this.skinHaircut = other.skinHaircut;
        this.skinEyebrows = other.skinEyebrows;
        this.skinPants = other.skinPants;
        this.skinOverpants = other.skinOverpants;
        this.skinUndertop = other.skinUndertop;
        this.skinOvertop = other.skinOvertop;
        this.skinShoes = other.skinShoes;
        this.skinHeadAccessory = other.skinHeadAccessory;
        this.skinFaceAccessory = other.skinFaceAccessory;
        this.skinEarAccessory = other.skinEarAccessory;
        this.skinFeature = other.skinFeature;
        this.skinGloves = other.skinGloves;
        this.skinCape = other.skinCape;
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

    public AgeStage ageStage() {
        if (ageStage == null || ageStage.isBlank()) {
            return null;
        }
        try {
            return AgeStage.valueOf(ageStage);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean hasAppearance() {
        return ageStage() != null
            && !value(skinBodyCharacteristic).isBlank()
            && !value(skinUnderwear).isBlank()
            && !value(skinFace).isBlank()
            && !value(skinEyes).isBlank()
            && !value(skinEars).isBlank()
            && !value(skinMouth).isBlank();
    }

    public void setAppearance(AgeStage ageStage, PlayerSkin skin) {
        if (ageStage == null || skin == null) {
            throw new IllegalArgumentException("Age stage and PlayerSkin are required.");
        }
        this.ageStage = ageStage.name();
        this.skinBodyCharacteristic = value(skin.bodyCharacteristic);
        this.skinUnderwear = value(skin.underwear);
        this.skinFace = value(skin.face);
        this.skinEyes = value(skin.eyes);
        this.skinEars = value(skin.ears);
        this.skinMouth = value(skin.mouth);
        this.skinFacialHair = value(skin.facialHair);
        this.skinHaircut = value(skin.haircut);
        this.skinEyebrows = value(skin.eyebrows);
        this.skinPants = value(skin.pants);
        this.skinOverpants = value(skin.overpants);
        this.skinUndertop = value(skin.undertop);
        this.skinOvertop = value(skin.overtop);
        this.skinShoes = value(skin.shoes);
        this.skinHeadAccessory = value(skin.headAccessory);
        this.skinFaceAccessory = value(skin.faceAccessory);
        this.skinEarAccessory = value(skin.earAccessory);
        this.skinFeature = value(skin.skinFeature);
        this.skinGloves = value(skin.gloves);
        this.skinCape = value(skin.cape);
    }

    public PlayerSkin playerSkin() {
        if (!hasAppearance()) {
            return null;
        }
        return new PlayerSkin(
            nullable(skinBodyCharacteristic), nullable(skinUnderwear), nullable(skinFace),
            nullable(skinEyes), nullable(skinEars), nullable(skinMouth),
            nullable(skinFacialHair), nullable(skinHaircut), nullable(skinEyebrows),
            nullable(skinPants), nullable(skinOverpants), nullable(skinUndertop),
            nullable(skinOvertop), nullable(skinShoes), nullable(skinHeadAccessory),
            nullable(skinFaceAccessory), nullable(skinEarAccessory), nullable(skinFeature),
            nullable(skinGloves), nullable(skinCape)
        );
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

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public Component<EntityStore> clone() {
        return new CivInhabitantData(this);
    }
}
