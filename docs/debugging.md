# Runtime-Debugging

## Player-Rig-/Animations-Spike

`/civdebug playerrig` schaltet für alle aktuell geladenen Civ-Bewohner einen isolierten Player-Rig-Test ein oder wieder aus.

Beim Einschalten ersetzt Civ nur die Laufzeitdarstellung der bereits geladenen Bewohner durch Hytales natives `Player`-Model, weist über `PlayerSkinComponent` einen zufälligen nativen Player-Skin zu und startet einmal die bereits im Projekt verwendete Action-Animation `Alerted`. Das persistente `Civ_Inhabitant`-Rollen-Asset bleibt unverändert. Beim Ausschalten werden das vorherige Model und ein gegebenenfalls vorhandener Player-Skin wiederhergestellt.

Der Befehl meldet zusätzlich, wie viele Animations-Sets das zur Laufzeit geladene `Player`-Model besitzt, und zeigt einen begrenzten Ausschnitt der Set-Namen. Diese Ausgabe ist Diagnoseinformation aus dem tatsächlich geladenen Hytale-Asset und keine Civ-eigene Animationsliste.

### Manueller Test

1. Mindestens einen NPC mit `/civclaim` als Civ-Bewohner beanspruchen.
2. `/civdebug playerrig` ausführen. Der Bewohner muss auf das native Player-Model mit einem Player-Skin wechseln. Im Chat müssen die Anzahl der geladenen Bewohner und die Anzahl der Player-Animations-Sets erscheinen.
3. Prüfen, ob die einmal ausgelöste Action-Animation `Alerted` auf dem Player-Rig sichtbar abgespielt wird.
4. Dem Bewohner im RTS-Modus per Rechtsklick ein Bewegungsziel geben. Prüfen, ob Hytales native Idle-/Laufdarstellung auf dem Player-Rig plausibel funktioniert und die vorhandene Civ-Navigation unverändert bleibt.
5. Optional einen Beruf zuweisen und prüfen, ob bestehende Arbeitsanimationen auf dem Player-Rig sichtbar oder offensichtlich inkompatibel sind. Dieser Spike ändert keine Berufslogik.
6. `/civdebug playerrig` erneut ausführen. Der Bewohner muss auf seine ursprüngliche Darstellung zurückwechseln.

Der Spike belegt erst dann die Eignung als dauerhafte Civ-NPC-Basis, wenn Model/Skin, native Fortbewegung und mehrere relevante Arbeitsanimationen im Client tatsächlich funktionieren. Die Existenz der Server-Komponenten allein ist kein Beweis für vollständige Runtime-Kompatibilität.

## Holzfäller-Baumsuche messen

`/civdebug woodscan` schaltet die Laufzeitmessung der echten Hytale-Baumsuche ein oder aus.

Beim Einschalten werden die bisherigen Messwerte zurückgesetzt. Solange die Messung aktiv ist, schreibt der Server höchstens alle zehn Sekunden eine aggregierte Zeile mit Präfix `[CivWoodcutterPerf]` ins Log. Beim Ausschalten zeigt der Befehl den letzten Snapshot zusätzlich im Chat an.

Die Werte bedeuten:

- `scans`: ausgeführte Holzfäller-Baumsuchen,
- `avgMs`: durchschnittliche reale Laufzeit einer Suche in Millisekunden,
- `maxMs`: höchste gemessene Laufzeit einer einzelnen Suche,
- `positions`: geprüfte X/Y/Z-Positionen im normalen Suchraster,
- `treeBases`: als Stammfuß erkannte Positionen,
- `treesCollected`: ausgeführte Zusammenfassung einer Baumstruktur,
- `treeBlocks`: Summe der Holzblöcke in diesen gesammelten Baumstrukturen,
- `protected`: wegen Civ-Gebäudeschutz verworfene Bäume,
- `reserved`: wegen einer Reservierung durch einen anderen Holzfäller verworfene Bäume,
- `workTargetChecks`: Bäume, für die anschließend ein begehbarer Arbeitsstandplatz gesucht wurde,
- `noStand`: Bäume ohne gültigen Arbeitsstandplatz,
- `usable`: Bäume, die nach allen Prüfungen als nutzbare Kandidaten übrig blieben.

Die Messung zählt ausschließlich den vorhandenen Gameplay-Suchpfad und startet keine zusätzlichen Baumsuchen. Die früher verwendete vollständige zweite Diagnose-Suche bei `no-target` wurde entfernt; die entsprechenden Ablehnungsgründe werden jetzt während derselben echten Suche mitgezählt.

### Manueller Test

1. Einen oder mehrere Civ-Bewohner als Holzfäller einsetzen und in ein Gebiet mit normalen Hytale-Bäumen stellen.
2. `/civdebug woodscan` ausführen. Die Rückmeldung muss `enabled` enthalten und bei null beginnen.
3. Die Holzfäller mindestens zehn Sekunden arbeiten oder suchen lassen.
4. Im Server-Log nach `[CivWoodcutterPerf]` suchen und prüfen, dass `scans`, `positions` und die Zeitwerte steigen.
5. Den Test mit wenigen und anschließend mit vielen Holzfällern wiederholen. Für einen sinnvollen Vergleich dieselbe Weltregion und ungefähr dieselbe Laufzeit verwenden.
6. `/civdebug woodscan` erneut ausführen. Die Rückmeldung muss `disabled` sowie den finalen Snapshot enthalten.

Die Millisekundenwerte sind Runtime-Messwerte und hängen von Hardware, Serverlast, Weltzustand und JVM ab. Für CI-Budgets bleiben die deterministischen `SimulationMetrics` maßgeblich.

## Mine-Entscheidungslogs

Die Mine besitzt opt-in strukturierte Entscheidungslogs. Standardmäßig sind sie deaktiviert; der normale Mining-Pfad schreibt deshalb keine Entscheidungsausgaben. Aktivierung und Filterung laufen über den bestehenden Debug-Befehl:

- `/civdebug mine logs on` aktiviert alle Kategorien.
- `/civdebug mine logs on PLANNING,GEOMETRY` aktiviert nur die genannten Kategorien.
- `/civdebug mine logs status` zeigt den aktuellen Filter und alle verfügbaren Kategorien.
- `/civdebug mine logs off` deaktiviert die Ausgabe wieder.

Alternativ aktiviert die JVM-Property `-Dcivilizations.mineDebug=true` beim Pluginstart alle Kategorien. Die Ausgabe verwendet Hytales vorhandenen Plugin-Logger und hat das Format

```text
[Civ Mine][mine=<id>][front=<id>][PLANNING][DIRECTION_SELECTED] direction=NORTH weight=50 totalWeight=100 probability=0.500 roll=17
```

Die groben Filterkategorien sind `PLANNING`, `GEOMETRY`, `ROOM`, `ENVIRONMENT`, `NAVIGATION` und `ADAPTER`. Nicht jede Kategorie erzeugt im aktuellen Legacy-Minenpfad bereits Events; die noch nicht implementierten Generator-Layer sollen später dieselben Kategorien verwenden.

Aktuell instrumentiert sind nur Entscheidungen und Fehler, die wirklich existieren: Auswahl beziehungsweise Wiederaufnahme einer Arbeitsfront, initiale Richtung, gewichtete Geradeaus-/Links-/Rechts-Auswahl, abgelehnte Segmentkandidaten, fehlende gültige Fortsetzung, blockierte Fronten sowie Fehler bei der Stützen-Prefab-Ausführung. Es gibt bewusst keine Ausgabe pro Mining-Tick oder abgebautem Block.

`MineDecisionSink` liegt im Hytale-unabhängigen Core als optionale Beobachtergrenze. Der Hytale-Adapter `CivMineDecisionDiagnostics` filtert die Events und schreibt sie über `JavaPlugin.getLogger()` in das normale Serverlog. Der Sink darf keine Gameplay-Zustände verändern und darf insbesondere keine zusätzlichen Zufallswerte ziehen; Probability/Roll-Werte werden nur aus den ohnehin bereits ausgeführten Entscheidungen protokolliert.
