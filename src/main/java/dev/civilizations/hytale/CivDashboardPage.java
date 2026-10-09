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
import java.util.Map;
import java.util.Locale;

/** First management screen: paged, static client slots (no unsafe appendInline). */
public final class CivDashboardPage extends InteractiveCustomUIPage<CivDashboardPage.ActionData> {
    private static final int ROWS = 7;
    private final PlayerRef playerRef;
    private final List<Entry> buildings;
    private final List<Entry> residents;
    private final String tab;
    private final CivPerformanceRecorder profiler;
    private final int page;

    public record Entry(String title, String detail, Runnable select) {}

    public CivDashboardPage(PlayerRef playerRef, List<Entry> buildings, List<Entry> residents) {
        this(playerRef, buildings, residents, "buildings", 0);
    }

    private CivDashboardPage(PlayerRef playerRef, List<Entry> buildings, List<Entry> residents,
                             String tab, int page) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.playerRef = playerRef;
        this.profiler = CivPerformanceRecorder.current();
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
        boolean performance = tab.equals("performance");
        commands.set("#PerfPanel.Visible", performance);
        commands.set("#SectionTitle.Visible", !performance);
        commands.set("#Pager.Visible", !performance);
        commands.set("#SectionTitle.Text", tab.equals("buildings") ? "Gebäude" : "Bewohner");
        commands.set("#Counters.Text", buildings.size() + " Gebäude  |  " + residents.size() + " Bewohner");
        commands.set("#Pager.Text", "Seite " + (current + 1) + " / " + pages);
        commands.set("#EmptyNotice.Visible", !performance && entries.isEmpty());
        commands.set("#PreviousButton.Visible", !performance && current > 0);
        commands.set("#NextButton.Visible", !performance && current + 1 < pages);
        for (int i = 0; i < ROWS; i++) {
            int at = current * ROWS + i;
            commands.set("#Entry" + i + ".Visible", !performance && at < entries.size());
            if (at >= entries.size()) continue;
            Entry entry = entries.get(at);
            commands.set("#EntryTitle" + i + ".Text", entry.title());
            commands.set("#EntryDetail" + i + ".Text", entry.detail());
            bind(events, "#EntryButton" + i, "select:" + at);
        }
        if (performance) {
            Map<String, Object> status = profiler == null ? Map.of("active", false) : profiler.status();
            boolean active = Boolean.TRUE.equals(status.get("active"));
            Map<String, Object> report = profiler == null ? Map.of() : profiler.report();
            commands.set("#PerfState.Text", active ? "● TRACKING AKTIV"
                : "Tracking inaktiv");
            commands.set("#PerfTime.Text", active
                ? "Verbleibend: " + status.get("remainingSeconds") + " s (maximal 15 min)"
                : "Aufzeichnung bei Bedarf starten · maximal 15 Minuten");
            commands.set("#PerfStartButton.Visible", !active && profiler != null);
            commands.set("#PerfStopButton.Visible", active);
            commands.set("#PerfEntities.Text", "Geladene Hytale-Entitäten: "
                + report.getOrDefault("loadedHytaleEntities", "—")
                + " · Civ-Bewohner: " + report.getOrDefault("loadedCivResidents", "—"));
            commands.set("#PerfOverhead.Text", "Profiler-Eigenaufwand: "
                + number(report.get("profilerBookkeepingMsPerSecond")) + " ms/s");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = report.get("systems") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
            for (int i = 0; i < 5; i++) {
                commands.set("#PerfRow" + i + ".Text", i < rows.size()
                    ? rows.get(i).get("system") + "  ·  "
                        + number(rows.get(i).get("msPerSecond")) + " ms/s  ·  "
                        + number(rows.get(i).get("callsPerSecond")) + " Aufr./s"
                    : "—");
            }
            bind(events, "#PerfStartButton", "perf-start");
            bind(events, "#PerfStopButton", "perf-stop");
            bind(events, "#PerfRefreshButton", "perf-refresh");
        }
        bind(events, "#PerformanceButton", "performance");
        bind(events, "#BuildingsButton", "buildings");
        bind(events, "#ResidentsButton", "residents");
        bind(events, "#PreviousButton", "previous");
        bind(events, "#NextButton", "next");
        bind(events, "#CloseButton", "close");
    }

    private static String number(Object value) {
        return value instanceof Number n ? String.format(Locale.GERMAN, "%.2f", n.doubleValue()) : "0,00";
    }

    private List<Entry> entries() {
        return tab.equals("performance") ? List.of() : tab.equals("buildings") ? buildings : residents;
    }

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
            case "buildings", "residents", "performance" -> { nextTab = data.action; nextPage = 0; }
            case "perf-start" -> {
                if (profiler != null) profiler.start();
                nextTab = "performance";
                nextPage = 0;
            }
            case "perf-stop" -> {
                if (profiler != null) profiler.stop();
                nextTab = "performance";
                nextPage = 0;
            }
            case "perf-refresh" -> { nextTab = "performance"; nextPage = 0; }
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
