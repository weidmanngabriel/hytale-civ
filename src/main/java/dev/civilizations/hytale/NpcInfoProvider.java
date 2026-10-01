package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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
            )
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
            case CONSTRUCTION_WORKER -> "Bauarbeiter";
        };
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
