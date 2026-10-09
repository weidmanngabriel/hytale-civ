package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.List;

/** First management screen: paged, static client slots (no unsafe appendInline). */
public final class CivDashboardPage extends InteractiveCustomUIPage<CivDashboardPage.ActionData> {
    private static final int ROWS = 7;
    private final PlayerRef playerRef;
    private final List<Entry> buildings;
    private final List<Entry> residents;
    private final String tab;
    private final int page;

    public record Entry(String title, String detail, Runnable select) {}

    public CivDashboardPage(PlayerRef playerRef, List<Entry> buildings, List<Entry> residents) {
        this(playerRef, buildings, residents, "buildings", 0);
    }

    private CivDashboardPage(PlayerRef playerRef, List<Entry> buildings, List<Entry> residents,
                             String tab, int page) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.playerRef = playerRef;
        this.buildings = List.copyOf(buildings);
        this.residents = List.copyOf(residents);
        this.tab = tab;
        this.page = page;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/CivDashboard.ui");
        List<Entry> entries = entries();
        int pages = Math.max(1, (entries.size() + ROWS - 1) / ROWS);
        int current = Math.min(page, pages - 1);
        commands.set("#SectionTitle.Text", tab.equals("buildings") ? "Gebäude" : "Bewohner");
        commands.set("#Counters.Text", buildings.size() + " Gebäude  |  " + residents.size() + " Bewohner");
        commands.set("#Pager.Text", "Seite " + (current + 1) + " / " + pages);
        commands.set("#EmptyNotice.Visible", entries.isEmpty());
        commands.set("#PreviousButton.Visible", current > 0);
        commands.set("#NextButton.Visible", current + 1 < pages);
        for (int i = 0; i < ROWS; i++) {
            int at = current * ROWS + i;
            commands.set("#Entry" + i + ".Visible", at < entries.size());
            if (at >= entries.size()) continue;
            Entry entry = entries.get(at);
            commands.set("#EntryTitle" + i + ".Text", entry.title());
            commands.set("#EntryDetail" + i + ".Text", entry.detail());
            bind(events, "#EntryButton" + i, "select:" + at);
        }
        bind(events, "#BuildingsButton", "buildings");
        bind(events, "#ResidentsButton", "residents");
        bind(events, "#PreviousButton", "previous");
        bind(events, "#NextButton", "next");
        bind(events, "#CloseButton", "close");
    }

    private List<Entry> entries() { return tab.equals("buildings") ? buildings : residents; }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
            EventData.of("Action", action), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ActionData data) {
        if (data.action == null) return;
        if (data.action.equals("close")) { close(); return; }
        String nextTab = tab;
        int nextPage = page;
        switch (data.action) {
            case "buildings", "residents" -> { nextTab = data.action; nextPage = 0; }
            case "next" -> nextPage = Math.min(page + 1, Math.max(0, (entries().size() - 1) / ROWS));
            case "previous" -> nextPage = Math.max(0, page - 1);
            default -> {
                if (!data.action.startsWith("select:")) return;
                try {
                    int index = Integer.parseInt(data.action.substring(7));
                    if (index >= 0 && index < entries().size()) {
                        Entry entry = entries().get(index);
                        close();
                        entry.select().run();
                    }
                } catch (NumberFormatException ignored) { }
                return;
            }
        }
        var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player != null) player.getPageManager().openCustomPage(ref, store,
            new CivDashboardPage(playerRef, buildings, residents, nextTab, nextPage));
    }

    public static final class ActionData {
        public static final BuilderCodec<ActionData> CODEC = BuilderCodec.builder(ActionData.class, ActionData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING),
                (data, value) -> data.action = value, data -> data.action)
            .add().build();
        private String action;
    }
}
