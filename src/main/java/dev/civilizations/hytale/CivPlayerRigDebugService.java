package dev.civilizations.hytale;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Development-only runtime spike for rendering loaded Civ inhabitants with Hytale's native
 * Player model and PlayerSkin replication path.
 *
 * <p>The service deliberately does not change the Civ NPC role asset. It swaps only runtime
 * visual components, probes one already-used generic Action animation, and restores the original
 * model/skin on the next toggle. This keeps the experiment isolated from gameplay behavior.</p>
 */
public final class CivPlayerRigDebugService {
    private static final String PLAYER_MODEL_ASSET_ID = "Player";
    private static final String PROBE_ACTION_ANIMATION = "Alerted";
    private static final int ANIMATION_SET_PREVIEW_LIMIT = 24;

    private final Archetype<EntityStore> civNpcQuery;
    private final Map<EntityKey, OriginalVisualState> originalStates = new ConcurrentHashMap<>();

    public CivPlayerRigDebugService(ComponentType<EntityStore, CivInhabitantData> inhabitantDataType) {
        this.civNpcQuery = Archetype.of(inhabitantDataType, NPCEntity.getComponentType());
    }

    public ToggleResult toggle(Store<EntityStore> store) {
        boolean enabled = originalStates.keySet().stream().anyMatch(key -> key.store() == store);
        return enabled ? restore(store) : enable(store);
    }

    private ToggleResult enable(Store<EntityStore> store) {
        ModelAsset playerAsset = ModelAsset.getAssetMap().getAsset(PLAYER_MODEL_ASSET_ID);
        if (playerAsset == null) {
            return new ToggleResult(
                false,
                0,
                0,
                0,
                List.of(),
                "Player model asset is unavailable"
            );
        }

        Model playerModel = Model.createUnitScaleModel(playerAsset);
        List<String> animationSets = playerAsset.getAnimationSetMap().keySet().stream()
            .sorted()
            .limit(ANIMATION_SET_PREVIEW_LIMIT)
            .toList();
        int[] matched = {0};
        int[] changed = {0};

        store.forEachChunk(civNpcQuery, (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (ref == null || !ref.isValid()) {
                    continue;
                }

                matched[0]++;
                EntityKey key = new EntityKey(store, ref.getIndex());
                if (originalStates.containsKey(key)) {
                    continue;
                }

                ModelComponent currentModel =
                    chunk.getComponent(index, ModelComponent.getComponentType());
                PlayerSkinComponent currentSkin =
                    chunk.getComponent(index, PlayerSkinComponent.getComponentType());
                originalStates.put(
                    key,
                    new OriginalVisualState(
                        currentModel == null ? null : currentModel.getModel(),
                        currentSkin == null ? null : currentSkin.getPlayerSkin(),
                        currentSkin != null
                    )
                );

                commandBuffer.putComponent(
                    ref,
                    ModelComponent.getComponentType(),
                    new ModelComponent(playerModel)
                );
                PlayerSkin randomSkin =
                    CosmeticsModule.get().generateRandomSkin(ThreadLocalRandom.current());
                commandBuffer.putComponent(
                    ref,
                    PlayerSkinComponent.getComponentType(),
                    new PlayerSkinComponent(randomSkin)
                );
                commandBuffer.run(nextStore -> AnimationUtils.playAnimation(
                    ref,
                    AnimationSlot.Action,
                    PROBE_ACTION_ANIMATION,
                    nextStore
                ));
                changed[0]++;
            }
        });

        return new ToggleResult(
            true,
            matched[0],
            changed[0],
            playerAsset.getAnimationSetMap().size(),
            animationSets,
            null
        );
    }

    private ToggleResult restore(Store<EntityStore> store) {
        int[] matched = {0};
        int[] changed = {0};

        store.forEachChunk(civNpcQuery, (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (ref == null || !ref.isValid()) {
                    continue;
                }

                matched[0]++;
                EntityKey key = new EntityKey(store, ref.getIndex());
                OriginalVisualState state = originalStates.remove(key);
                if (state == null) {
                    continue;
                }

                commandBuffer.run(nextStore ->
                    AnimationUtils.stopAnimation(ref, AnimationSlot.Action, nextStore)
                );
                if (state.model() == null) {
                    commandBuffer.tryRemoveComponent(ref, ModelComponent.getComponentType());
                } else {
                    commandBuffer.putComponent(
                        ref,
                        ModelComponent.getComponentType(),
                        new ModelComponent(state.model())
                    );
                }

                if (state.hadPlayerSkin()) {
                    commandBuffer.putComponent(
                        ref,
                        PlayerSkinComponent.getComponentType(),
                        new PlayerSkinComponent(state.playerSkin())
                    );
                } else {
                    commandBuffer.tryRemoveComponent(ref, PlayerSkinComponent.getComponentType());
                }
                changed[0]++;
            }
        });

        originalStates.keySet().removeIf(key -> key.store() == store);
        return new ToggleResult(false, matched[0], changed[0], 0, List.of(), null);
    }

    public record ToggleResult(
        boolean enabled,
        int matchedNpcCount,
        int changedNpcCount,
        int playerAnimationSetCount,
        List<String> animationSetPreview,
        String error
    ) {
    }

    private record OriginalVisualState(
        Model model,
        PlayerSkin playerSkin,
        boolean hadPlayerSkin
    ) {
    }

    private record EntityKey(Store<EntityStore> store, int entityIndex) {
    }
}
