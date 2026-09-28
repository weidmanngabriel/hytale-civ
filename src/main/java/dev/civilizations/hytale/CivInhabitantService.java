package dev.civilizations.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
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
        ref.getStore().putComponent(
            ref,
            Nameplate.getComponentType(),
            new Nameplate(data.fullName())
        );
        markDirty(ref);
        return data;
    }

    public CivInhabitantData ensureInhabitant(
        Ref<EntityStore> ref,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (ref == null || !ref.isValid()) {
            return null;
        }

        CivInhabitantData data = commandBuffer.getComponent(ref, inhabitantDataType);
        if (data == null) {
            data = new CivInhabitantData();
            initializeIdentity(data);
            commandBuffer.putComponent(ref, inhabitantDataType, data);
        } else if (!data.hasIdentity()) {
            initializeIdentity(data);
        }

        Message displayName = Message.raw(data.fullName());
        commandBuffer.putComponent(
            ref,
            PersistentDisplayName.getComponentType(),
            new PersistentDisplayName(displayName)
        );
        commandBuffer.putComponent(
            ref,
            DisplayNameComponent.getComponentType(),
            new DisplayNameComponent(displayName)
        );
        commandBuffer.putComponent(
            ref,
            Nameplate.getComponentType(),
            new Nameplate(data.fullName())
        );
        markDirty(ref, commandBuffer);
        return data;
    }

    private void initializeIdentity(CivInhabitantData data) {
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

    public boolean releaseInhabitant(Ref<EntityStore> ref) {
        if (get(ref) == null) {
            return false;
        }

        ref.getStore().removeComponent(ref, inhabitantDataType);
        restoreNativeDisplayName(ref, ref.getStore());
        markDirty(ref);
        return true;
    }

    public boolean releaseInhabitant(
        Ref<EntityStore> ref,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer.getComponent(ref, inhabitantDataType) == null) {
            return false;
        }

        commandBuffer.removeComponent(ref, inhabitantDataType);
        restoreNativeDisplayName(ref, commandBuffer);
        markDirty(ref, commandBuffer);
        return true;
    }

    private static void restoreNativeDisplayName(
        Ref<EntityStore> ref,
        com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor
    ) {
        DisplayNameSupport support = accessor.getComponent(ref, DisplayNameSupport.getComponentType());
        if (support != null) {
            support.pickRandomDisplayName(ref, true, accessor);
            return;
        }

        accessor.removeComponent(ref, PersistentDisplayName.getComponentType());
        accessor.removeComponent(ref, DisplayNameComponent.getComponentType());
        accessor.removeComponent(ref, Nameplate.getComponentType());
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
            markDirty(ref);
        }
    }

    private static void markDirty(Ref<EntityStore> ref) {
        Dirty dirty = ref.getStore().getComponentConcurrent(ref, Dirty.getComponentType());
        if (dirty != null) {
            dirty.markDirty();
        }
    }

    private static void markDirty(
        Ref<EntityStore> ref,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        Dirty dirty = commandBuffer.getComponent(ref, Dirty.getComponentType());
        if (dirty != null) {
            dirty.markDirty();
        }
    }

    public Profession getProfession(Ref<EntityStore> ref) {
        CivInhabitantData data = get(ref);
        return data == null ? null : data.profession();
    }
}
