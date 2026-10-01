package dev.civilizations.hytale;

import dev.civilizations.core.AgeStage;
import dev.civilizations.core.Gender;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VikingAppearanceCatalogTest {

    @Test
    void everyConfiguredSlotCoversEveryGenderAndAgeCombination() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/appearance/viking_appearance_v5.json")) {
            assertNotNull(input);
            BsonDocument root = BsonDocument.parse(
                new String(input.readAllBytes(), StandardCharsets.UTF_8)
            );
            BsonDocument slots = root.getDocument("slots");

            for (String slot : slots.keySet()) {
                BsonArray options = slots.getArray(slot);
                if (options.isEmpty()) {
                    continue;
                }
                for (Gender gender : Gender.values()) {
                    for (AgeStage age : AgeStage.values()) {
                        String sex = gender == Gender.MALE ? "Male" : "Female";
                        boolean covered = options.stream()
                            .map(BsonValue::asDocument)
                            .anyMatch(option -> strings(option, "sex").contains(sex)
                                && strings(option, "ages").contains(age.configName()));
                        assertTrue(
                            covered,
                            () -> "Missing appearance option for slot=" + slot
                                + ", gender=" + gender + ", age=" + age
                        );
                    }
                }
            }
        }
    }

    @Test
    void curatedColorRulesStayWithinTheAgreedPools() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/appearance/viking_appearance_v5.json")) {
            assertNotNull(input);
            BsonDocument colors = BsonDocument.parse(
                new String(input.readAllBytes(), StandardCharsets.UTF_8)
            ).getDocument("colors");

            assertTrue(strings(colors.getDocument("skin"), "allowed")
                .containsAll(List.of("02", "08", "09", "15", "48")));
            assertFalse(strings(colors.getDocument("skin"), "allowed").contains("01"));
            assertTrue(strings(colors.getDocument("eyes"), "allowed")
                .containsAll(List.of("Blue", "BlueLight", "Turquoise", "GreenLight", "Grey")));
            assertTrue(strings(colors.getDocument("hair").getDocument("byAge"), "Elder")
                .containsAll(List.of("Grey", "White")));
        }
    }

    private static List<String> strings(BsonDocument document, String key) {
        BsonArray values = document.getArray(key, new BsonArray());
        return values.stream().map(value -> value.asString().getValue()).toList();
    }
}
