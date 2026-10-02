package dev.civilizations.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import dev.civilizations.core.Gender;
import dev.civilizations.core.Profession;
import dev.civilizations.core.VikingNameGenerator;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Owns persistent Civ inhabitant state independently from RTS input/session state.
 */
public final class CivInhabitantService {
    private static final String PLAYER_APPEARANCE = "Player";

    private final ComponentType<EntityStore, CivInhabitantData> inhabitantDataType;
    private final VikingNameGenerator nameGenerator;
    private final VikingAppearanceGenerator appearanceGenerator;

    public CivInhabitantService(
        ComponentType<EntityStore, CivInhabitantData> inhabitantDataType,
        VikingNameGenerator nameGenerator,
        VikingAppearanceGenerator appearanceGenerator
    ) {
        this.inhabitantDataType = inhabitantDataType;
        this.nameGenerator = nameGenerator;
        this.appearanceGenerator = appearanceGenerator;
    }

    public CivInhabitantData ensureInhabitant(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }

        CivInhabitantData data = ref.getStore().ensureAndGetComponent(ref, inhabitantDataType);
        initializeMissingPersistentData(data);
        applyPresentation(ref, data, ref.getStore());
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
            initializeMissingPersistentData(data);
            commandBuffer.putComponent(ref, inhabitantDataType, data);
        } else {
            initializeMissingPersistentData(data);
        }

        applyPresentation(ref, data, commandBuffer);
        markDirty(ref, commandBuffer);
        return data;
    }

    private void initializeMissingPersistentData(CivInhabitantData data) {
        RandomGenerator random = ThreadLocalRandom.current();
        if (!data.hasIdentity()) {
            VikingNameGenerator.GeneratedName generated = nameGenerator.generate(random);
            data.setIdentity(
                generated.gender(),
                generated.firstName(),
                generated.middleName(),
                generated.lastName()
            );
            data.setProfession(Profession.UNEMPLOYED);
        }

        if (!data.hasAppearance()) {
            Gender gender = data.gender();
            if (gender == null) {
                throw new IllegalStateException("Civ inhabitant has no valid gender for appearance generation");
            }
            VikingAppearanceGenerator.GeneratedAppearance appearance =
                appearanceGenerator.generate(gender, random);
            data.setAppearance(appearance.ageStage(), appearance.playerSkin());
        }
    }

    private static void applyPresentation(
        Ref<EntityStore> ref,
        CivInhabitantData data,
        ComponentAccessor<EntityStore> accessor
    ) {
        Message displayName = Message.raw(data.fullName());
        accessor.putComponent(
            ref,
            PersistentDisplayName.getComponentType(),
            new PersistentDisplayName(displayName)
        );
        accessor.putComponent(
            ref,
            DisplayNameComponent.getComponentType(),
            new DisplayNameComponent(displayName)
        );
        accessor.putComponent(
            ref,
            Nameplate.getComponentType(),
            new Nameplate(data.fullName())
        );

        PlayerSkin skin = data.playerSkin();
        if (skin == null) {
            throw new IllegalStateException("Civ inhabitant appearance is not initialized");
        }
        if (!NPCEntity.setAppearance(ref, PLAYER_APPEARANCE, accessor)) {
            throw new IllegalStateException("Hytale Player appearance is unavailable");
        }
        accessor.putComponent(
            ref,
            PlayerSkinComponent.getComponentType(),
            new PlayerSkinComponent(skin)
        );
    }

    public boolean releaseInhabitant(Ref<EntityStore> ref) {
        if (get(ref) == null) {
            return false;
        }

        restoreNativeAppearance(ref, ref.getStore());
        restoreNativeDisplayName(ref, ref.getStore());
        ref.getStore().removeComponent(ref, inhabitantDataType);
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

        restoreNativeAppearance(ref, commandBuffer);
        restoreNativeDisplayName(ref, commandBuffer);
        commandBuffer.removeComponent(ref, inhabitantDataType);
        markDirty(ref, commandBuffer);
        return true;
    }

    private static void restoreNativeAppearance(
        Ref<EntityStore> ref,
        ComponentAccessor<EntityStore> accessor
    ) {
        NPCEntity npc = accessor.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null && npc.getRole() != null) {
            String appearance = npc.getRole().getAppearanceName();
            if (appearance != null && !appearance.isBlank()) {
                NPCEntity.setAppearance(ref, appearance, accessor);
            }
        }
        accessor.tryRemoveComponent(ref, PlayerSkinComponent.getComponentType());
    }

    private static void restoreNativeDisplayName(
        Ref<EntityStore> ref,
        ComponentAccessor<EntityStore> accessor
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

    public boolean isInhabitant(
        Ref<EntityStore> ref,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        return ref != null
            && ref.isValid()
            && commandBuffer.getComponent(ref, inhabitantDataType) != null;
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
            Profession next = profession == null ? Profession.UNEMPLOYED : profession;
            if (data.profession() != next) {
                data.setWorkplaceId(null);
            }
            data.setProfession(next);
            markDirty(ref);
        }
    }

    public void assignWorkplace(Ref<EntityStore> ref, UUID buildingId) {
        CivInhabitantData data = get(ref);
        if (data == null) {
            return;
        }
        data.setWorkplaceId(buildingId == null ? null : buildingId.toString());
        markDirty(ref);
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
