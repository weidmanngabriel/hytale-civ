package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Small in-game handbook for the Civ features that are implemented today.
 */
public final class WikiPage extends InteractiveCustomUIPage<WikiPage.ActionData> {

    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_HOME = "home";
    private static final String ACTION_PROFESSIONS = "professions";
    private static final String ACTION_RESOURCES = "resources";
    private static final String ACTION_BUILDINGS = "buildings";
    private static final String ACTION_ANIMALS = "animals";

    private final PlayerRef playerRef;
    private final Screen screen;

    public WikiPage(PlayerRef playerRef) {
        this(playerRef, Screen.HOME);
    }

    private WikiPage(PlayerRef playerRef, Screen screen) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.playerRef = playerRef;
        this.screen = screen;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        commands.append(screen.layout);
        bind(events, "#CloseButton", ACTION_CLOSE);

        for (Map.Entry<String, String> binding : screen.bindings.entrySet()) {
            bind(events, binding.getKey(), binding.getValue());
        }
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull ActionData data
    ) {
        if (ACTION_CLOSE.equals(data.action)) {
            close();
            return;
        }

        Screen target = switch (data.action) {
            case ACTION_HOME -> Screen.HOME;
            case ACTION_PROFESSIONS -> Screen.PROFESSIONS;
            case ACTION_RESOURCES -> Screen.RESOURCES;
            case ACTION_BUILDINGS -> Screen.BUILDINGS;
            case ACTION_ANIMALS -> Screen.ANIMALS;
            default -> null;
        };
        if (target == null || target == screen) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        player.getPageManager().openCustomPage(ref, store, new WikiPage(playerRef, target));
    }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            selector,
            EventData.of("Action", action),
            false
        );
    }

    private enum Screen {
        HOME(
            "Pages/CivWikiHome.ui",
            Map.of(
                "#ProfessionsButton", ACTION_PROFESSIONS,
                "#ResourcesButton", ACTION_RESOURCES,
                "#BuildingsButton", ACTION_BUILDINGS,
                "#AnimalsButton", ACTION_ANIMALS
            )
        ),
        PROFESSIONS(
            "Pages/CivWikiProfessions.ui",
            Map.of(
                "#HomeButton", ACTION_HOME,
                "#WoodResourceLink", ACTION_RESOURCES,
                "#FarmBuildingLink", ACTION_BUILDINGS,
                "#WheatResourceLink", ACTION_RESOURCES
            )
        ),
        RESOURCES(
            "Pages/CivWikiResources.ui",
            Map.of(
                "#HomeButton", ACTION_HOME,
                "#WoodcutterProfessionLink", ACTION_PROFESSIONS,
                "#FarmerProfessionLink", ACTION_PROFESSIONS,
                "#FarmBuildingLink", ACTION_BUILDINGS
            )
        ),
        BUILDINGS(
            "Pages/CivWikiBuildings.ui",
            Map.of(
                "#HomeButton", ACTION_HOME,
                "#FarmerProfessionLink", ACTION_PROFESSIONS,
                "#WheatResourceLink", ACTION_RESOURCES
            )
        ),
        ANIMALS(
            "Pages/CivWikiAnimals.ui",
            Map.of("#HomeButton", ACTION_HOME)
        );

        private final String layout;
        private final Map<String, String> bindings;

        Screen(String layout, Map<String, String> bindings) {
            this.layout = layout;
            this.bindings = bindings;
        }
    }

    public static final class ActionData {
        public static final BuilderCodec<ActionData> CODEC =
            BuilderCodec.builder(ActionData.class, ActionData::new)
                .append(
                    new KeyedCodec<>("Action", Codec.STRING),
                    (data, value) -> data.action = value,
                    data -> data.action
                )
                .add()
                .build();

        private String action;
    }
}
