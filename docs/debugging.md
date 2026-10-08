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

## Mine-Debugging

Die aktuelle Mine besitzt zwei getrennte, opt-in Diagnosepfade: strukturierte Entscheidungslogs für das **Warum** und eine player-lokale Ingame-Visualisierung für das **Wo**. Beide beobachten nur bereits vorhandenen Generator-/Runtime-Zustand und dürfen Gameplay oder Zufallsfolgen nicht verändern.

### Entscheidungslogs

- `/civdebug mine logs on` aktiviert alle Kategorien.
- `/civdebug mine logs on PLANNING,ROOM` aktiviert nur die genannten Kategorien.
- `/civdebug mine logs status` zeigt Filter und verfügbare Kategorien.
- `/civdebug mine logs off` deaktiviert die Ausgabe.

Alternativ aktiviert `-Dcivilizations.mineDebug=true` beim Pluginstart alle Kategorien. Die Ausgabe läuft über Hytales Plugin-Logger:

```text
[Civ Mine][mine=<id>][front=<id>][PLANNING][HEADING_SELECTED] kind=MAIN from=NORTH to=NORTHWEST reason=BOUNDARY_PRESSURE leftWeight=... straightWeight=... rightWeight=... roll=... totalWeight=...
[Civ Mine][mine=<id>][front=<id>][PLANNING][BRANCH_CONTINUATION] decision=CONTINUE roll=0.43 chance=0.71 lengthBefore=...
[Civ Mine][mine=<id>][front=<id>][ROOM][ROOM_REJECTED] type=MATERIAL_STORAGE slice=... reason=INSUFFICIENT_SPACE
[Civ Mine][mine=<id>][front=<id>][ENVIRONMENT][FRONT_ABANDONED] reason=LAVA_GAP failureKind=HAZARDOUS_FLUID slice=...
```

Die Kategorien sind:

- `PLANNING`: Richtungswahl, Boundary Pressure, Branch-Chancen/-Fortsetzung, Task-/Frontauswahl.
- `GEOMETRY`: Formphasen mit Länge, Breite, Höhe, lateralem Offset und vertikalem Schritt.
- `ROOM`: Raumchancen, Erzeugung, konkrete Seiten-/Platzablehnungen und Raumarbeit.
- `ENVIRONMENT`: Höhlen, Fluids, Brücken-/Gap-Entscheidungen und umweltbedingte Frontabbrüche.
- `NAVIGATION`: endgültig unerreichbare Fronten/Räume und andere native Navigationsfehler.
- `ADAPTER`: Hytale-spezifische Ausführungsfehler.

Die Planner protokollieren die **bereits für die Gameplayentscheidung verwendeten** Zufallswerte. Es werden keine zusätzlichen RNG-Aufrufe für Diagnosezwecke ausgeführt. Ein Regressionstest vergleicht deshalb denselben Seed mit und ohne aktiven `MineDecisionSink` und verlangt identische Tunnel- und Raumpläne.

### Ingame-Visualisierung

- `/civdebug mine info` zeigt den persistenten Zustand der nächstgelegenen Mine.
- `/civdebug mine show` zeigt die aktuellen Arbeitsfronten.
- `/civdebug mine show anchors` zeigt zusätzlich semantische Runtime-Anker: aktive Frontpunkte, Miner-Navigationsziele, offene Raumanker und noch nicht abgeschlossene Infrastrukturanker.
- `/civdebug mine show bounds` zeigt Fronten plus den 500×500-Designbereich.
- `/civdebug mine show all` zeigt Fronten, Runtime-Anker und 500×500-Designbereich zusammen.
- `/civdebug mine hide` entfernt alle von Civ für diesen Spieler gesetzten Mine-Debuganzeigen.

Die Darstellung ist nur für den anfragenden Spieler sichtbar und verändert keine Weltblöcke. Farben: gelb = Arbeitsfront, grün = Navigationsziel, violett = Raum, weiß = Infrastruktur; die bestehenden Main-/Branch- und Bounds-Farben bleiben erhalten.

Die Marker sind bewusst Momentaufnahmen. Bei fortschreitender Arbeit den Befehl erneut ausführen, um den aktuellen Runtime-Zustand neu darzustellen.
