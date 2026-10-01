package dev.civilizations.hytale;

import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinGradientSet;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import dev.civilizations.core.AgeStage;
import dev.civilizations.core.Gender;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Generates persistent Viking PlayerSkin values from the curated CharacterCreator catalog.
 *
 * <p>The catalog stores Hytale cosmetic ids directly. Civ only owns the age/gender/culture rules
 * and the selection itself; actual cosmetic resolution remains delegated to Hytale's native
 * {@link CosmeticRegistry}.</p>
 */
public final class VikingAppearanceGenerator {
    private static final String RESOURCE = "/appearance/viking_appearance_v5.json";
    private static final List<String> SLOT_ORDER = List.of(
        "bodyCharacteristic", "underwear", "face", "eyes", "ears", "mouth",
        "facialHair", "haircut", "eyebrows", "pants", "overpants", "undertop",
        "overtop", "shoes", "headAccessory", "faceAccessory", "earAccessory",
        "skinFeature", "gloves", "cape"
    );

    private final Catalog catalog;

    public VikingAppearanceGenerator() {
        this.catalog = loadCatalog();
    }

    public GeneratedAppearance generate(Gender gender, RandomGenerator random) {
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(random, "random");

        AgeStage age = AgeStage.values()[random.nextInt(AgeStage.values().length)];
        String hairColor = pick(catalog.hairColors().get(age), random);
        String skinColor = pick(catalog.skinColors(), random);
        String eyeColor = pick(catalog.eyeColors(), random);
        String earAccessoryColor = pick(catalog.earAccessoryColors(), random);

        Map<String, String> selected = new LinkedHashMap<>();
        for (String slot : SLOT_ORDER) {
            SlotOption option = pickValidOption(slot, gender, age, hairColor, random);
            if (option == null || option.assetId() == null) {
                selected.put(slot, null);
                continue;
            }

            if (isRawAssetSlot(slot)) {
                selected.put(slot, option.assetId());
                continue;
            }

            String forcedColor = switch (slot) {
                case "bodyCharacteristic" -> skinColor;
                case "haircut", "facialHair", "eyebrows" -> hairColor;
                case "eyes" -> eyeColor;
                case "earAccessory" -> earAccessoryColor;
                default -> null;
            };
            selected.put(slot, resolveAttachment(slot, option, forcedColor, random));
        }

        PlayerSkin skin = new PlayerSkin(
            selected.get("bodyCharacteristic"),
            selected.get("underwear"),
            selected.get("face"),
            selected.get("eyes"),
            selected.get("ears"),
            selected.get("mouth"),
            selected.get("facialHair"),
            selected.get("haircut"),
            selected.get("eyebrows"),
            selected.get("pants"),
            selected.get("overpants"),
            selected.get("undertop"),
            selected.get("overtop"),
            selected.get("shoes"),
            selected.get("headAccessory"),
            selected.get("faceAccessory"),
            selected.get("earAccessory"),
            selected.get("skinFeature"),
            selected.get("gloves"),
            selected.get("cape")
        );

        try {
            CosmeticsModule.get().validateSkin(skin);
        } catch (CosmeticsModule.InvalidSkinException exception) {
            throw new IllegalStateException(
                "Generated Viking skin is invalid at " + exception.getPartType()
                    + ": " + exception.getPartId(),
                exception
            );
        }
        return new GeneratedAppearance(age, skin);
    }

    private SlotOption pickValidOption(
        String slot,
        Gender gender,
        AgeStage age,
        String hairColor,
        RandomGenerator random
    ) {
        List<SlotOption> options = catalog.slots().getOrDefault(slot, List.of());
        if (options.isEmpty()) {
            return null;
        }
        List<SlotOption> valid = options.stream()
            .filter(option -> option.matches(gender, age, hairColor))
            .toList();
        if (valid.isEmpty()) {
            throw new IllegalStateException(
                "No valid Viking appearance option for slot=" + slot
                    + ", gender=" + gender + ", age=" + age
            );
        }
        return valid.get(random.nextInt(valid.size()));
    }

    private String resolveAttachment(
        String slot,
        SlotOption option,
        String forcedTexture,
        RandomGenerator random
    ) {
        PlayerSkinPart part = partMap(slot).get(option.assetId());
        if (part == null) {
            throw new IllegalStateException(
                "Unknown Hytale cosmetic id " + option.assetId() + " for slot " + slot
            );
        }

        String texture = forcedTexture;
        if (texture == null) {
            texture = chooseNativeTexture(part, random);
        } else if (!availableTextures(part).contains(texture)) {
            throw new IllegalStateException(
                "Hytale cosmetic " + option.assetId() + " does not support texture " + texture
            );
        }

        String variant = null;
        Map<String, PlayerSkinPart.Variant> nativeVariants = part.getVariants();
        if (nativeVariants != null && !nativeVariants.isEmpty()) {
            List<String> variants = option.variants().isEmpty()
                ? List.copyOf(nativeVariants.keySet())
                : option.variants();
            List<String> validVariants = variants.stream()
                .filter(nativeVariants::containsKey)
                .toList();
            if (validVariants.isEmpty()) {
                throw new IllegalStateException(
                    "No valid variant for Hytale cosmetic " + option.assetId()
                );
            }
            variant = validVariants.get(random.nextInt(validVariants.size()));
        }

        return option.assetId() + "." + texture + (variant == null ? "" : "." + variant);
    }

    private String chooseNativeTexture(PlayerSkinPart part, RandomGenerator random) {
        List<String> textures = availableTextures(part);
        if (textures.isEmpty()) {
            throw new IllegalStateException("Hytale cosmetic has no selectable texture: " + part.getId());
        }
        return textures.get(random.nextInt(textures.size()));
    }

    static List<String> availableTextures(PlayerSkinPart part) {
        if (part.getGradientSet() != null && !part.getGradientSet().isBlank()) {
            PlayerSkinGradientSet set = CosmeticsModule.get().getRegistry().getGradientSets()
                .get(part.getGradientSet());
            if (set == null || set.getGradients().isEmpty()) {
                return List.of();
            }
            return List.copyOf(set.getGradients().keySet());
        }

        var directTextures = part.getTextures();
        if (directTextures != null && !directTextures.isEmpty()) {
            return List.copyOf(directTextures.keySet());
        }

        Map<String, PlayerSkinPart.Variant> variants = part.getVariants();
        if (variants != null && !variants.isEmpty()) {
            return variants.values().stream()
                .flatMap(variant -> {
                    var variantTextures = variant.getTextures();
                    return variantTextures == null
                        ? java.util.stream.Stream.<String>empty()
                        : variantTextures.keySet().stream();
                })
                .distinct()
                .toList();
        }
        return List.of();
    }

    private Map<String, PlayerSkinPart> partMap(String slot) {
        CosmeticRegistry registry = CosmeticsModule.get().getRegistry();
        return switch (slot) {
            case "bodyCharacteristic" -> registry.getBodyCharacteristics();
            case "underwear" -> registry.getUnderwear();
            case "eyes" -> registry.getEyes();
            case "facialHair" -> registry.getFacialHairs();
            case "haircut" -> registry.getHaircuts();
            case "eyebrows" -> registry.getEyebrows();
            case "pants" -> registry.getPants();
            case "overpants" -> registry.getOverpants();
            case "undertop" -> registry.getUndertops();
            case "overtop" -> registry.getOvertops();
            case "shoes" -> registry.getShoes();
            case "headAccessory" -> registry.getHeadAccessories();
            case "faceAccessory" -> registry.getFaceAccessories();
            case "earAccessory" -> registry.getEarAccessories();
            case "skinFeature" -> registry.getSkinFeatures();
            case "gloves" -> registry.getGloves();
            case "cape" -> registry.getCapes();
            default -> throw new IllegalArgumentException("Unsupported attachment slot: " + slot);
        };
    }

    private static boolean isRawAssetSlot(String slot) {
        return "face".equals(slot) || "ears".equals(slot) || "mouth".equals(slot);
    }

    private static Catalog loadCatalog() {
        try (InputStream input = VikingAppearanceGenerator.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing Viking appearance resource " + RESOURCE);
            }
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            BsonDocument root = BsonDocument.parse(json);
            BsonDocument colors = root.getDocument("colors");
            BsonDocument hair = colors.getDocument("hair").getDocument("byAge");
            EnumMap<AgeStage, List<String>> hairByAge = new EnumMap<>(AgeStage.class);
            for (AgeStage age : AgeStage.values()) {
                hairByAge.put(age, strings(hair.getArray(age.configName())));
            }

            BsonDocument slotsDocument = root.getDocument("slots");
            Map<String, List<SlotOption>> slots = new LinkedHashMap<>();
            for (String slot : SLOT_ORDER) {
                BsonArray values = slotsDocument.getArray(slot, new BsonArray());
                List<SlotOption> parsed = new ArrayList<>();
                for (BsonValue value : values) {
                    BsonDocument option = value.asDocument();
                    BsonValue asset = option.get("assetId");
                    parsed.add(new SlotOption(
                        asset == null || asset.isNull() ? null : asset.asString().getValue(),
                        strings(option.getArray("sex", new BsonArray())),
                        strings(option.getArray("ages", new BsonArray())),
                        strings(option.getArray("variants", new BsonArray())),
                        strings(option.getArray("requiresBaseColor", new BsonArray()))
                    ));
                }
                slots.put(slot, List.copyOf(parsed));
            }

            return new Catalog(
                Map.copyOf(hairByAge),
                strings(colors.getDocument("skin").getArray("allowed")),
                strings(colors.getDocument("eyes").getArray("allowed")),
                strings(colors.getDocument("earAccessory").getArray("allowed")),
                Map.copyOf(slots)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load Viking appearance catalog", exception);
        }
    }

    private static List<String> strings(BsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        return array.stream().map(value -> value.asString().getValue()).toList();
    }

    private static <T> T pick(List<T> values, RandomGenerator random) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("Appearance catalog contains an empty required pool");
        }
        return values.get(random.nextInt(values.size()));
    }

    public record GeneratedAppearance(AgeStage ageStage, PlayerSkin playerSkin) {
    }

    private record Catalog(
        Map<AgeStage, List<String>> hairColors,
        List<String> skinColors,
        List<String> eyeColors,
        List<String> earAccessoryColors,
        Map<String, List<SlotOption>> slots
    ) {
    }

    private record SlotOption(
        String assetId,
        List<String> sexes,
        List<String> ages,
        List<String> variants,
        List<String> requiredBaseColors
    ) {
        boolean matches(Gender gender, AgeStage age, String hairColor) {
            String genderName = gender == Gender.MALE ? "Male" : "Female";
            return sexes.contains(genderName)
                && ages.contains(age.configName())
                && (requiredBaseColors.isEmpty() || requiredBaseColors.contains(hairColor));
        }
    }
}
