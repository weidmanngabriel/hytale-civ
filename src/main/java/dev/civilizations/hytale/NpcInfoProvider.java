package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import dev.civilizations.core.AgeStage;
import dev.civilizations.core.Gender;
import dev.civilizations.core.Profession;

/** Builds player-facing NPC information only from authoritative Civ runtime/persistent state. */
public final class NpcInfoProvider {

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;

    public NpcInfoProvider(CivUnitRegistry unitRegistry, CivActivityRegistry activityRegistry) {
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
    }

    public NpcInfoSnapshot snapshot(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid() || !unitRegistry.isClaimed(ref)) {
            return null;
        }

        CivInhabitantData data = unitRegistry.getInhabitantData(ref);
        if (data == null || !data.hasIdentity()) {
            return null;
        }

        Profession profession = data.profession();
        String activity = CivInhabitantStatusText.derive(
            profession,
            activityRegistry.manualMovementIntent(ref) != null,
            activityRegistry.autonomousWorkAllowed(ref),
            unitRegistry.getMoveTarget(ref) != null
        );

        return new NpcInfoSnapshot(
            new NpcInfoSnapshot.Identity(
                data.fullName(),
                genderText(data.gender()),
                ageStageText(data.ageStage())
            ),
            new NpcInfoSnapshot.Work(
                professionText(profession),
                activity,
                data.professionXp(profession),
                data.workplaceId() == null ? NpcInfoSnapshot.UNAVAILABLE : data.workplaceId()
            ),
            new NpcInfoSnapshot.Needs(
                NpcInfoSnapshot.UNAVAILABLE,
                NpcInfoSnapshot.UNAVAILABLE,
                NpcInfoSnapshot.UNAVAILABLE
            ),
            new NpcInfoSnapshot.Family(
                NpcInfoSnapshot.UNAVAILABLE,
                NpcInfoSnapshot.UNAVAILABLE,
                NpcInfoSnapshot.UNAVAILABLE
            ),
            combatInfo(ref, profession)
        );
    }

    static String professionText(Profession profession) {
        if (profession == null) {
            return "Arbeitslos";
        }
        return switch (profession) {
            case UNEMPLOYED -> "Arbeitslos";
            case FARMER -> "Bauer";
            case WOODCUTTER -> "Holzfäller";
            case MINER -> "Minenabbauer";
            case CONSTRUCTION_WORKER -> "Bauarbeiter";
            case SOLDIER -> "Soldat";
        };
    }

    private static NpcInfoSnapshot.Combat combatInfo(
        Ref<EntityStore> ref,
        Profession profession
    ) {
        EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        String healthText = health == null
            ? NpcInfoSnapshot.UNAVAILABLE
            : formatNumber(health.get()) + " / " + formatNumber(health.getMax());

        if (profession != Profession.SOLDIER) {
            return new NpcInfoSnapshot.Combat(
                healthText,
                NpcInfoSnapshot.UNAVAILABLE,
                NpcInfoSnapshot.UNAVAILABLE,
                NpcInfoSnapshot.UNAVAILABLE
            );
        }

        Ref<EntityStore> target = combatTarget(ref);
        CombatSupport combatSupport = CombatSupport.get(ref, ref.getStore());
        String status;
        if (combatSupport != null && combatSupport.isExecutingAttack()) {
            status = "Im Kampf";
        } else if (target != null && target.isValid()) {
            status = "Verfolgt Gegner";
        } else {
            status = "Bereit";
        }

        return new NpcInfoSnapshot.Combat(
            healthText,
            equippedWeaponText(ref),
            status,
            targetDisplayName(target)
        );
    }

    private static String equippedWeaponText(Ref<EntityStore> ref) {
        ItemStack inHand = InventoryComponent.getItemInHand(ref.getStore(), ref);
        if (inHand == null || inHand.getItemId() == null || inHand.getItemId().isBlank()) {
            return NpcInfoSnapshot.UNAVAILABLE;
        }
        if (ProfessionBootstrapInventory.SOLDIER_SWORD_ITEM_ID.equals(inHand.getItemId())) {
            return "Eisenschwert";
        }
        return inHand.getItemId();
    }

    private static Ref<EntityStore> combatTarget(Ref<EntityStore> ref) {
        MarkedEntitySupport marked = MarkedEntitySupport.get(ref, ref.getStore());
        return marked == null ? null : marked.getMarkedEntityRef(SoldierWorkSystem.COMBAT_TARGET_SLOT);
    }

    private static String targetDisplayName(Ref<EntityStore> target) {
        if (target == null || !target.isValid()) {
            return NpcInfoSnapshot.UNAVAILABLE;
        }
        DisplayNameComponent displayName = target.getStore()
            .getComponent(target, DisplayNameComponent.getComponentType());
        if (displayName != null && displayName.getDisplayName() != null) {
            String raw = displayName.getDisplayName().getRawText();
            if (raw != null && !raw.isBlank()) {
                return raw;
            }
            String messageId = displayName.getDisplayName().getMessageId();
            if (messageId != null && !messageId.isBlank()) {
                return messageId;
            }
        }
        NPCEntity npc = target.getStore().getComponent(target, NPCEntity.getComponentType());
        return npc == null || npc.getRoleName() == null
            ? NpcInfoSnapshot.UNAVAILABLE
            : npc.getRoleName();
    }

    private static String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String genderText(Gender gender) {
        if (gender == null) {
            return NpcInfoSnapshot.UNAVAILABLE;
        }
        return switch (gender) {
            case MALE -> "Männlich";
            case FEMALE -> "Weiblich";
        };
    }

    private static String ageStageText(AgeStage ageStage) {
        if (ageStage == null) {
            return NpcInfoSnapshot.UNAVAILABLE;
        }
        return switch (ageStage) {
            case BABY -> "Baby";
            case CHILD -> "Kind";
            case ADULT -> "Erwachsen";
            case ELDER -> "Älter";
        };
    }
}
