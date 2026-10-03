# Minenabbauer – Phase 1

Dieser Slice beschreibt den ersten autonomen Abbauer einer fertigen Mine.

## Produktverhalten

- Alle Minenphasen arbeiten vorerst auf derselben Höhe. Ein Upgrade verschiebt den Tunnelanschluss nicht nach unten.
- Phase 1 besitzt eine Arbeiterkapazität von 1, Phase 2 von 2 und Phase 3 von 3. Die Kapazität ist aktuell Metadatum und noch keine harte Zuweisungsgrenze.
- Ein Bewohner erhält über das Personenmenü den Beruf **Minenabbauer** und wird anschließend per Rechtsklick einer fertigen Mine zugewiesen.
- Als Development-Bootstrap erhält der Minenabbauer eine native Hytale-Eisenspitzhacke (`Tool_Pickaxe_Iron`). Eine spätere Werkzeugbeschaffung ist nicht Teil dieses Slices.
- Der Bewohner läuft zuerst über `workplace_access` und arbeitet anschließend am `mine_tunnel_connector`.
- Ein Tunnelabschnitt ist 4 Blöcke breit, 4 Blöcke hoch und 8 Blöcke lang. Das sind 128 mögliche Abbaublöcke.
- Für die aktuelle Testphase beträgt die Zielzeit eines vollständig gefüllten Abschnitts 60 Sekunden. Balancewerte liegen zentral in `MineTuning`; vor dem finalen Balancing kann dieser Wert wieder erhöht werden.
- Bereits leere Blöcke werden übersprungen. Normale abbaubare Blöcke werden über Hytales nativen Harvest-Pfad abgebaut. Die entstehenden Drops bleiben in der Welt und können normal despawnen.
- Vor Reservierung eines Abschnitts prüft Civ, ob dessen Raum mit geschützten Civ-Gebäuden oder bereits reservierten beziehungsweise bestehenden Minensegmenten kollidiert. Direkt vor einem einzelnen Abbau bleibt zusätzlich ein Schutzcheck bestehen.
- Die persistierte Segmentposition beschreibt den geplanten Stollen, nicht blind den aktuellen Weltzustand. Vor der Arbeit prüft der Minenabbauer die echten Blöcke des Stollens erneut und setzt seine Arbeitsfront auf den ersten wieder gefüllten Block zurück. Dadurch arbeitet er sich nach nachträglichem Auffüllen wieder von vorne durch den betroffenen Abschnitt.
- Auch bereits abgeschlossene Segmente werden auf nachträgliche Blockierungen geprüft. Wird ein alter Abschnitt wieder zugemauert, wird er erneut geöffnet, bevor der Arbeiter an weiter hinten liegenden Segmenten fortsetzt.
- Der Abbau läuft Ebene für Ebene in Tunnelrichtung. Eine Ebene ist aktuell der vollständige 4×4-Querschnitt: zuerst werden alle 16 Blöcke dieser Arbeitsfront verarbeitet, einschließlich der obersten Reihe, erst danach rückt die Arbeitsfront um einen Block tiefer in den Stollen.
- Die Erreichbarkeit wird an der Arbeitsposition der aktuellen Front geprüft, nicht noch einmal pro einzelnem Block. Sobald der Minenabbauer die gültige Arbeitsposition erreicht hat, darf er die komplette aktuelle 4×4-Front bearbeiten. Dadurch kann ein hoher oder seitlicher Block innerhalb derselben Front den Fortschritt nicht dauerhaft blockieren.
- Eine Arbeitsposition gilt nur dann als erreicht, wenn auch die Höhe stimmt. Ein NPC direkt über dem Stollen darf deshalb nicht von der Oberfläche nach unten abbauen.
- Hytales native Navigation bleibt zuständig für den eigentlichen Weg. Civ setzt ein neues Bewegungsziel einmal und lässt ein unverändertes Ziel anschließend von Hytales `ReadPosition`/`Seek` ausführen; Civ löscht oder setzt dasselbe Ziel nicht mehr aufgrund eines eigenen Stall-Timers neu.
- Nach einem fertigen Abschnitt entscheidet der Abbauer selbst zwischen geradeaus, links und rechts. Ungültige Richtungen fallen aus der Auswahl. Geradeaus wird zunächst mit 60 %, links und rechts jeweils mit 20 % gewichtet. Ein direktes Umdrehen ist ausgeschlossen.
- Ist keine der drei Richtungen gültig, endet dieser Tunnelast vorerst.
- Die Tunnelstruktur, Reservierung, Richtung und der Abbaufortschritt werden persistent gespeichert, damit ein Serverneustart den begonnenen Stollen nicht vergisst.

## Stützbalken

Das Asset `Civilizations/Mine/Mine_Support_01.prefab.json` ist ein 4 Blöcke hoher Holzrahmen und sitzt innerhalb des 4×4-Stollens. Civ setzt ihn über Hytales native Prefab-API statt die Balken selbst blockweise zu erzeugen. Für Nord-/Süd-Stollen wird das Prefab über `BlockSelection.rotate(Axis.Y, 90)` um 90 Grad gedreht; die Hytale-API erwartet hier diskrete Gradwerte (`0`, `90`, `180`, `270`) und keine Vierteldrehungs-Zahl.

Stützen stehen zunächst alle 4 Tunnelblöcke. Ein 8-Blöcke-Segment erhält daher einen Rahmen nach Block 4 und einen am Segmentende nach Block 8. Beim erneuten Prüfen eines bereits gegrabenen Abschnitts werden die bekannten Holzstützen nicht fälschlich als neu zu entfernende Blockierung behandelt. Civ prüft außerdem die tatsächlich vorhandenen Rahmen gegen den erreichten Tunnelstand: fehlt eine bereits fällige Stütze, wird sie nachgesetzt und der persistierte `supportsPlaced`-Stand wieder mit der realen Welt synchronisiert. Dadurch repariert die Mine auch Segmente, bei denen ein früherer Prefab-Lookup fehlgeschlagen ist, obwohl der Support-Zähler bereits erhöht worden war.

## Native Hytale-Grenze

Civ entscheidet über Segmentwahl, Reservierung, Timing, Fortschritt und Persistenz. Hytale bleibt zuständig für NPC-Navigation, Block-Harvest und Drops, Werkzeugdarstellung, Animation sowie Prefab-Platzierung. Bei einem unveränderten Bewegungsziel greift Civ nicht in die laufende native Pfadausführung ein.

Die Arbeitsanimation folgt demselben Muster wie beim Holzfäller: Civ startet für die Arbeitsphase das ItemPlayerAnimations-Set `Civ_Miner_Pickaxe` mit `SwingDown` im `Action`-Slot und stoppt es beim Verlassen der Arbeitsphase. Das Civ-Set erbt von Hytales Pickaxe-Basis `Pickaxe` und verweist auf die nativen Third-Person-Mining-Dateien `Mine.blockyanim` beziehungsweise `Mine_Moving.blockyanim`. Der Miner merkt sich dabei lokal, ob er selbst die Animation gestartet hat; nur in diesem Fall darf er den `Action`-Slot wieder stoppen. Nicht-Miner erhalten vom `MinerWorkSystem` keinen Animationsbefehl, damit andere Berufsanimationen wie die Holzfäller-Axt nicht überschrieben werden.

## Bewusst noch nicht enthalten

- Einsammeln oder Rücktransport der Drops
- Erzsuche oder erzabhängige Sonderlogik
- unterschiedliche Materialhärten und Werkzeugboni
- Wasser-, Lava- oder Höhlen-Sonderbehandlung
- spezielle Stützkonstruktionen für Kurven/Kreuzungen
- harte Durchsetzung der Arbeiterkapazität
- unterschiedliche Tunnelebenen je Gebäudephase
- besondere Abbaumechaniken, Verzierungen oder größere Querschnitte für spätere Fraktionen wie Zwerge
