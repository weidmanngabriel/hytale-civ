package dev.civilizations.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nullable;

/**
 * Resolves the NPC under the player's First Person crosshair when the Civ
 * unarmed Secondary interaction asks to open the person-actions page.
 */
public final class CivPersonActionsPageSupplier
    implements OpenCustomUIInteraction.CustomPageSupplier {

    private final RtsInteractionController interactionController;

    public CivPersonActionsPageSupplier(RtsInteractionController interactionController) {
        this.interactionController = interactionController;
    }

    @Nullable
    @Override
    public CustomUIPage tryCreate(
        Ref<EntityStore> playerEntityRef,
        ComponentAccessor<EntityStore> componentAccessor,
        PlayerRef playerRef,
        InteractionContext context
    ) {
        Ref<EntityStore> target = TargetUtil.getTargetEntity(playerEntityRef, componentAccessor);
        if (!interactionController.isClaimed(target)) {
            return null;
        }

        return interactionController.createFirstPersonActionsPage(playerRef, target);
    }
}
