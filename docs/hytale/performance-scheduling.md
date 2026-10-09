# Performance und Worker-Scheduling

## Verifizierte Hytale-Mechanik

Die gepinnte `HytaleServer.jar` enthält `com.hypixel.hytale.component.system.tick.DelayedEntitySystem`. Der Konstruktor erhält ein Intervall in Sekunden. Das System sammelt die Engine-`dt`-Werte, bis das Intervall erreicht ist, setzt den internen Zähler zurück und führt danach den normalen Entity-Tick mit der **aufsummierten vergangenen Zeit** aus.

Für Civ bedeutet das: Zeitbasierte Core-State-Machines können in größeren Abständen ausgewertet werden, ohne dass Arbeitsdauer verloren geht. Native Hytale-Navigation läuft zwischen diesen Civ-Sessions weiter, solange das gesetzte Bewegungsziel bestehen bleibt.

## Civ-Regel

Periodische Civ-Logik soll nicht allein deshalb in jedem Engine-Tick laufen, weil ein NPC aktiv ist. Bevorzugt wird:

1. ereignisgesteuerte Verarbeitung für Spielerinput, TriggerVolumes und konkrete Zustandsänderungen,
2. `DelayedEntitySystem` für periodische Worker-Entscheidungen,
3. häufigere Ausführung nur für Logik, die tatsächlich eine hohe zeitliche Auflösung benötigt.

Kurze Arbeits-Sessions mit deutlichen Pausen sind gegenüber vielen kleinen Sessions in kurzer Folge zu bevorzugen. Teure Welt-Scans, Segment-Reconciliation oder Schutzprüfungen gehören insbesondere nicht in einen ungedrosselten Entity-Tick.

## Aktuelle Worker-Intervalle

- Bauarbeiter: `0,50 s`
- Holzfäller: `0,50 s`
- Minenarbeiter: `0,50 s`
- Farmer: `0,75 s`
- ausgewählte Compact-HUDs: `0,75 s` Sicherheits-/Live-Refresh; Auswahländerungen selbst bleiben sofort ereignisgesteuert.

Diese Intervalle sind keine Gameplay-Konstanten für Produktionsgeschwindigkeit. Zeitbasierte Jobs erhalten beim verzögerten Aufruf das akkumulierte `dt`. Die Intervalle steuern primär, wie oft Civ Hytale-/Weltzustand erneut auswertet.

## Sicherheitsprüfungen

Drosselung darf keine finale Sicherheitsprüfung entfernen. Beispiel Holzfäller: Ein Zielbaum wird periodisch revalidiert, unmittelbar vor dem tatsächlichen Fällen aber nochmals gegen geschützte Bereiche geprüft.

Bei Minenarbeit bleibt die Welt-Reconciliation vor dem nächsten Arbeitsfortschritt erhalten; sie wird lediglich nicht mehr in jedem Engine-Tick wiederholt.

## Profiling

Runtime-Kosten von Engine-Zugriffen gehören in den Hytale-Adapter. Bestehende Diagnostik soll die autoritative Gameplay-Suche messen und keine zweite identische Suche nur für Messzwecke auslösen. Siehe außerdem ADR `docs/decisions/0003-runtime-engine-cost-profiling.md`.

Die gewählten Intervalle sind eine konservative Ausgangsbasis. Weitere Vergrößerungen sollten anhand echter Servermessungen erfolgen, nicht anhand angenommener Tick-Kosten.

## Opt-in Civ-Profiler

Die gepinnte Hytale-JAR bestätigt `Store.getEntityCount()` für geladene Hytale-Entitäten; damit braucht die Performance-Ansicht hierfür keinen eigenen Welt-Scan. `CivPerformanceRecorder` enthält ausschließlich bounded Aggregation und eine 15-minütige maximale Laufzeit. Worker-Ticks und einige teure, bereits ausgeführte Mine-/Baumsuchpfade erhalten umschließende Messungen; im deaktivierten Zustand wird keine Zeit erfasst. Ein Spieler-Refresh im Intervall 1 s prüft die Ablaufzeit und pflegt den Anzeige-HUD; bei völlig inaktivem Server ohne ausgeführte Refresh-/Worker-Operationen erfolgt die Prüfung beim nächsten Zugriff.

`perf-report` liefert die inklusive Zeit pro System in ms/s, Aufrufe/s, Mittelwert, Maximum und Histogramm-approximiertes p95. Weil tiefere Arbeit in einem umfassenden Worker-Tick enthalten ist, dürfen Unter- und Oberkategorien nicht summiert werden. Die erfasste Buchhaltungsdauer stellt einen näherungsweisen Eigenaufwand dar und ersetzt **keinen** unabhängigen Profiling-an/aus-Vergleich. Der JUnit-Vergleichstest protokolliert Referenz- und instrumentierte Schleifen ohne unstabile CI-Laufzeitgrenzwerte.
