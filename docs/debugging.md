# Runtime-Debugging

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
- `workTargetChecks`: Bäume, für die anschließend ein begehbarer Arbeitsstandplatz gesucht wurde.

Die Messung zählt den vorhandenen Gameplay-Suchpfad; sie startet keine zusätzlichen Baumsuchen. Die ältere `CivWoodcutterDiag`-Ausgabe bei erfolgloser Zielsuche ist davon getrennt und kann selbst zusätzliche Diagnosearbeit verursachen.

### Manueller Test

1. Einen oder mehrere Civ-Bewohner als Holzfäller einsetzen und in ein Gebiet mit normalen Hytale-Bäumen stellen.
2. `/civdebug woodscan` ausführen. Die Rückmeldung muss `enabled` enthalten und bei null beginnen.
3. Die Holzfäller mindestens zehn Sekunden arbeiten oder suchen lassen.
4. Im Server-Log nach `[CivWoodcutterPerf]` suchen und prüfen, dass `scans`, `positions` und die Zeitwerte steigen.
5. Den Test mit wenigen und anschließend mit vielen Holzfällern wiederholen. Für einen sinnvollen Vergleich dieselbe Weltregion und ungefähr dieselbe Laufzeit verwenden.
6. `/civdebug woodscan` erneut ausführen. Die Rückmeldung muss `disabled` sowie den finalen Snapshot enthalten.

Die Millisekundenwerte sind Runtime-Messwerte und hängen von Hardware, Serverlast, Weltzustand und JVM ab. Für CI-Budgets bleiben die deterministischen `SimulationMetrics` maßgeblich.
