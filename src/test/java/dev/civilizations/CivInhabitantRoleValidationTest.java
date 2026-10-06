package dev.civilizations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivInhabitantRoleValidationTest {

    private static final Path ROLE_PATH = Path.of(
        "asset-pack/Server/NPC/Roles/Intelligent/Passive/Civ_Inhabitant.json"
    );
    private static final Path SOLDIER_CAE_PATH = Path.of(
        "asset-pack/Server/NPC/Balancing/Intelligent/CAE_Civ_Soldier.json"
    );

    @Test
    void civInhabitantDeclaresOrdinaryAndMinerNativeMovementSlots() throws Exception {
        JsonNode role = new ObjectMapper().readTree(Files.readString(ROLE_PATH));

        assertEquals("Generic", role.path("Type").asText());
        assertTrue(role.path("NameTranslationKey").isTextual());
        assertEquals("Walk", role.path("MotionControllerList").path(0).path("Type").asText());

        JsonNode idleInstructions = role.path("Instructions").path(0).path("Instructions");
        JsonNode normalMovement = idleInstructions.path(0);
        JsonNode minerMovement = idleInstructions.path(1);

        assertEquals("ReadPosition", normalMovement.path("Sensor").path("Type").asText());
        assertEquals("CivMoveTarget", normalMovement.path("Sensor").path("Slot").asText());
        assertEquals("Seek", normalMovement.path("BodyMotion").path("Type").asText());
        assertTrue(normalMovement.path("BodyMotion").path("UsePathfinder").asBoolean());
        assertFalse(
            normalMovement.path("BodyMotion").has("UseBestPath"),
            "ordinary inhabitants must keep Hytale's default Seek behavior"
        );

        assertEquals("ReadPosition", minerMovement.path("Sensor").path("Type").asText());
        assertEquals("CivMinerMoveTarget", minerMovement.path("Sensor").path("Slot").asText());
        assertEquals("Seek", minerMovement.path("BodyMotion").path("Type").asText());
        assertTrue(minerMovement.path("BodyMotion").path("UsePathfinder").asBoolean());
        assertFalse(
            minerMovement.path("BodyMotion").path("UseBestPath").asBoolean(true),
            "miners must reject Hytale best-effort partial paths"
        );
    }

    @Test
    void soldierUsesNativeCombatActionEvaluatorSwordPath() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode role = mapper.readTree(Files.readString(ROLE_PATH));
        JsonNode cae = mapper.readTree(Files.readString(SOLDIER_CAE_PATH));

        assertEquals("CAE_Civ_Soldier", role.path("CombatConfig").asText());
        assertFalse(
            role.toString().contains("Root_NPC_Attack_Melee"),
            "Civ soldiers must not fall back to the generic NPC melee root"
        );

        JsonNode combatInstruction = role.path("Instructions").path(1);
        assertEquals("State", combatInstruction.path("Sensor").path("Type").asText());
        assertEquals("Combat", combatInstruction.path("Sensor").path("State").asText());

        JsonNode targetInstruction = combatInstruction.path("Instructions").path(0);
        assertEquals(
            "HasHostileTargetMemory",
            targetInstruction.path("Sensor").path("Type").asText()
        );

        JsonNode defaultSubState = targetInstruction.path("Instructions").path(0);
        assertEquals("State", defaultSubState.path("Sensor").path("Type").asText());
        assertEquals(".Default", defaultSubState.path("Sensor").path("State").asText());

        JsonNode inRange = defaultSubState.path("Instructions").path(0);
        assertEquals("CombatActionEvaluator", inRange.path("Sensor").path("Type").asText());
        assertTrue(inRange.path("Sensor").path("TargetInRange").asBoolean());
        assertEquals("CombatAbility", inRange.path("Actions").path(0).path("Type").asText());

        JsonNode outOfRange = defaultSubState.path("Instructions").path(1);
        assertEquals("CombatActionEvaluator", outOfRange.path("Sensor").path("Type").asText());
        assertFalse(outOfRange.path("Sensor").path("TargetInRange").asBoolean(true));
        assertEquals("Seek", outOfRange.path("BodyMotion").path("Type").asText());
        assertTrue(outOfRange.path("BodyMotion").path("UsePathfinder").asBoolean());

        assertEquals("CombatActionEvaluator", cae.path("Type").asText());
        JsonNode evaluator = cae.path("CombatActionEvaluator");
        JsonNode selector = evaluator.path("AvailableActions").path("SelectSwordTarget");
        assertEquals("SelectBasicAttackTarget", selector.path("Type").asText());
        assertEquals(0, selector.path("WeaponSlot").asInt(-1));

        JsonNode basicAttacks = evaluator.path("ActionSets").path("Default").path("BasicAttacks");
        assertEquals("Sword_Attack", basicAttacks.path("Attacks").path(0).asText());
        assertTrue(basicAttacks.path("MaxRange").asDouble() > 0.0);
    }
}
