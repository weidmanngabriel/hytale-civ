package dev.civilizations.hytale;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.Profession;
import dev.civilizations.core.VikingNameGenerator;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns persistent Civ inhabitant state independently from RTS input/session state.
 */
public final class CivInhabitantService {

    private final ComponentType<EntityStore, CivInhabitantData> inhabitantDataType;
    private final VikingNameGenerator nameGenerator;

    public CivInhabitantService(
        ComponentType<EntityStore, CivInhabitantData> inhabitantDataType,
        VikingNameGenerator nameGenerator
    ) {
        this.inhabitantDataType = inhabitantDataType;
        this.nameGenerator = nameGenerator;
    }

    public CivInhabitantData ensureInhabitant(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }

        CivInhabitantData data = ref.getStore().ensureAndGetComponent(ref, inhabitantDataType);
        if (!data.hasIdentity()) {
            VikingNameGenerator.GeneratedName generated =
                nameGenerator.generate(ThreadLocalRandom.current());
            data.setIdentity(
                generated.gender(),
                generated.firstName(),
                generated.middleName(),
                generated.lastName()
            );
            data.setProfession(Profession.UNEMPLOYED);
        }

        Message displayName = Message.raw(data.fullName());
        ref.getStore().putComponent(
            ref,
            PersistentDisplayName.getComponentType(),
            new PersistentDisplayName(displayName)
        );
        // HydrateDisplayName only creates the runtime component when an entity is added.
        // Claims happen on an already loaded NPC, so update the runtime component now too.
        ref.getStore().putComponent(
            ref,
            DisplayNameComponent.getComponentType(),
            new DisplayNameComponent(displayName)
        );
        return data;
    }

    public CivInhabitantData get(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref.getStore().getComponentConcurrent(ref, inhabitantDataType);
    }

    public void assignProfession(Ref<EntityStore> ref, Profession profession) {
        CivInhabitantData data = get(ref);
        if (data != null) {
            data.setProfession(profession);
        }
    }

    public Profession getProfession(Ref<EntityStore> ref) {
        CivInhabitantData data = get(ref);
        return data == null ? null : data.profession();
    }
}
