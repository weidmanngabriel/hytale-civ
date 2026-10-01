package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VikingAppearanceGeneratorTest {

    @Test
    void cosmeticWithoutVariantsOrTexturesDoesNotThrow() {
        PlayerSkinPart part = new TestPlayerSkinPart(BsonDocument.parse("""
            {
              "Id": "Test",
              "Name": "Test",
              "Model": "Cosmetics/Test.blockymodel"
            }
            """));

        List<String> textures = assertDoesNotThrow(
            () -> VikingAppearanceGenerator.availableTextures(part)
        );

        assertEquals(List.of(), textures);
    }

    @Test
    void variantWithoutTextureMapDoesNotThrow() {
        PlayerSkinPart part = new TestPlayerSkinPart(BsonDocument.parse("""
            {
              "Id": "Test",
              "Name": "Test",
              "Variants": {
                "Both": {
                  "Model": "Cosmetics/Test.blockymodel"
                }
              }
            }
            """));

        List<String> textures = assertDoesNotThrow(
            () -> VikingAppearanceGenerator.availableTextures(part)
        );

        assertEquals(List.of(), textures);
    }

    private static final class TestPlayerSkinPart extends PlayerSkinPart {
        private TestPlayerSkinPart(BsonDocument document) {
            super(document);
        }
    }
}
