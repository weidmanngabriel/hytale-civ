package dev.civilizations.hytale;

import com.hypixel.hytale.protocol.PlayerSkin;
import dev.civilizations.core.AgeStage;
import dev.civilizations.core.Gender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivInhabitantDataTest {

    @Test
    void appearanceAndIdentityRoundTripThroughPersistentComponent() {
        CivInhabitantData data = new CivInhabitantData();
        data.setIdentity(Gender.FEMALE, "Astrid", "Ylva", "Eriksdottir");
        PlayerSkin skin = new PlayerSkin(
            "Muscular.02",
            "Bra.Red",
            "Face_MakeUp",
            "Medium_Eyes.BlueLight",
            "Default",
            "Mouth_Makeup",
            null,
            "Braid.Copper",
            "Thin.Copper",
            "Forest_Bermuda.Green",
            "LongSocks_BasicWrap.White",
            "FarmerTop.Brown",
            null,
            "LeatherBoots.Brown",
            null,
            null,
            null,
            null,
            "LeatherMittens.Brown",
            null
        );

        data.setAppearance(AgeStage.ADULT, skin);

        assertTrue(data.hasIdentity());
        assertTrue(data.hasAppearance());
        assertEquals(Gender.FEMALE, data.gender());
        assertEquals(AgeStage.ADULT, data.ageStage());

        PlayerSkin restored = data.playerSkin();
        assertNotNull(restored);
        assertEquals("Muscular.02", restored.bodyCharacteristic);
        assertEquals("Braid.Copper", restored.haircut);
        assertEquals("Thin.Copper", restored.eyebrows);
        assertNull(restored.facialHair);
        assertNull(restored.cape);

        CivInhabitantData clone = (CivInhabitantData) data.clone();
        assertEquals(data.fullName(), clone.fullName());
        assertEquals(AgeStage.ADULT, clone.ageStage());
        assertEquals(restored, clone.playerSkin());
    }
}
