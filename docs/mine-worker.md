# Minenabbauer – Phase 1

Dieser Slice beschreibt den ersten autonomen Abbauer einer fertigen Mine.

## Produktverhalten

- Alle Minenphasen arbeiten vorerst auf derselben Höhe. Ein Upgrade verschiebt den Tunnelanschluss nicht nach unten.
- Phase 1 besitzt eine Arbeiterkapazität von 1, Phase 2 von 2 und Phase 3 von 3. Die Kapazität ist aktuell Metadatum und noch keine harte Zuweisungsgrenze.
- Ein Bewohner erhält über das Personenmenü den Beruf **Minenabbauer** und wird anschließend per Rechtsklick einer fertigen Mine zugewiesen.
- Als Development-Bootstrap erhält der Minenabbauer eine native Hytale-Eisenspitzhacke (`Tool_Pickaxe_Iron`). Eine spätere Werkzeugbeschaffung ist nicht Teil dieses Slices.
- Der Bewohner läuft beim Betreten beziehungsweise Wiedereintreten zuerst über `workplace_access` und danach zwingend zum `mine_tunnel_connector`. Erst wenn der Connector erreicht ist, darf Civ eine offene Arbeitsfront auswählen und als nächstes Navigationsziel setzen.
- Ein logisches Minensegment besteht immer aus zwei Teilen: einem 4×4 breiten und 4 Blöcke hohen **Hauptgang** mit 4 bis 12 Blöcken Länge und einer direkt anschließenden, immer 4×4×4 großen **Junction**.
- Die Junction wird unabhängig davon vollständig reserviert und ausgehoben, ob der nächste Abschnitt später geradeaus, links oder rechts weitergeht. Sie bleibt immer stützenfrei und bildet den sicheren Entscheidungs- und Wendebereich zwischen zwei Hauptgängen.
- `lengthBlocks` beschreibt nur die Länge des Hauptgangs. Die persistierte Segmentgeometrie und `blockCount()` umfassen zusätzlich immer die vier Junction-Tiefen. Ein Hauptgang der Länge 4 reserviert daher 8 Tunnelblöcke Tiefe, Länge 8 reserviert 12 und Länge 12 reserviert 16.
- Die Abbaugeschwindigkeit bleibt pro Block gleich wie im bisherigen Referenzsegment: 128 Blöcke eines 4×4×8-Hauptgangs entsprechen weiterhin 60 Sekunden. Die zusätzliche Junction braucht entsprechend zusätzliche reale Abbauzeit.
- Bei der Planung wird zunächst eine gewünschte Hauptganglänge im Bereich 4–12 gewählt. Passt Hauptgang **plus vollständige Junction** nicht, wird dieselbe Richtung mit kürzeren Hauptganglängen erneut geprüft. Erst wenn auch 4 Hauptblöcke plus 4 Junction-Blöcke nicht passen, fällt die Richtung aus der Auswahl.
- Bereits leere Blöcke werden übersprungen. Normale abbaubare Blöcke werden über Hytales nativen Harvest-Pfad abgebaut. Die entstehenden Drops bleiben in der Welt und können normal despawnen.
- Vor Reservierung eines Abschnitts prüft Civ den vollständigen tatsächlichen Körper aus Hauptgang und Junction gegen geschützte Civ-Gebäude und bereits reservierte beziehungsweise bestehende Minensegmente. Direkt vor einem einzelnen Abbau bleibt zusätzlich ein Schutzcheck bestehen.
- Die persistierte Segmentposition beschreibt den geplanten Stollen, nicht blind den aktuellen Weltzustand. Vor der Arbeit prüft der Minenabbauer die echten Blöcke des Stollens erneut und setzt seine Arbeitsfront auf den ersten wieder gefüllten Block zurück.
- Auch bereits abgeschlossene Segmente werden auf nachträgliche Blockierungen geprüft. Wird ein alter Abschnitt wieder zugemauert, wird er erneut geöffnet, bevor der Arbeiter an weiter hinten liegenden Segmenten fortsetzt.
- Der Abbau läuft Ebene für Ebene in Tunnelrichtung. Eine Ebene ist der vollständige 4×4-Querschnitt: zuerst werden alle 16 Blöcke dieser Arbeitsfront verarbeitet, erst danach rückt die Arbeitsfront um einen Block tiefer in den Stollen.
- Die Erreichbarkeit wird an der Arbeitsposition der aktuellen Front geprüft, nicht noch einmal pro einzelnem Block. Sobald der Minenabbauer die gültige Arbeitsposition erreicht hat, darf er die komplette aktuelle 4×4-Front bearbeiten.
- Eine Arbeitsposition gilt nur dann als erreicht, wenn auch die Höhe stimmt. Ein NPC direkt über dem Stollen darf deshalb nicht von der Oberfläche nach unten abbauen.
- Hytales native Navigation bleibt zuständig für den eigentlichen Weg. Civ setzt ein Bewegungsziel; `ReadPosition`/`Seek` übernimmt den Pfad.
- Erst wenn Hauptgang **und** Junction vollständig ausgehoben sind, entscheidet der Abbauer zwischen geradeaus, links und rechts. Ungültige Richtungen fallen aus der Auswahl. Geradeaus wird zunächst mit 60 %, links und rechts jeweils mit 20 % gewichtet. Ein direktes Umdrehen ist ausgeschlossen.
- Ein Kindsegment beginnt immer **außerhalb** der Junction seines Parents. Parent und Kind überlappen sich nicht mehr. Dadurch sind Junction, Stützen und nächste Arbeitsfront geometrisch eindeutig getrennt.
- Ist keine der drei Richtungen mit mindestens 4 Hauptblöcken plus ihrer 4-Block-Junction gültig, endet dieser Tunnelast vorerst.
- Aktiver horizontaler oder vertikaler Drift ist noch nicht Teil dieses Grundsystems. Die Segmentgeometrie bleibt über `start` und `direction` aufgebaut, damit spätere Startpunkt-Offsets ergänzt werden können, ohne den Junction-Vertrag zu ändern.

## Manueller Ausgang und Wiederaufnahme

Ein Minenabbauer kann tief im dynamisch gegrabenen Stollen durch Hytales Navigation keinen zuverlässigen Weg zurück an die Oberfläche finden. Deshalb besitzt nur der Miner einen gezielten manuellen Ausgang:

- Erteilt der Spieler einem unterirdischen Minenabbauer einen manuellen Bewegungsbefehl, teleportiert Civ ihn über Hytales native `Teleport`-ECS-Komponente zum `workplace_access` seiner zugewiesenen Mine.
- Der ursprüngliche Bewegungsbefehl bleibt bestehen. Im nächsten Tick läuft der NPC vom Minenzugang mit der normalen nativen Navigation weiter zum angeklickten Ziel.
- Befindet sich der Miner bereits auf Höhe des Minenzugangs oder darüber, wird nicht teleportiert.
- Der Teleport gilt nur für manuelle Bewegungsbefehle. Das autonome Graben wird nicht über Teleports gesteuert.
- Nach dem manuellen Auftrag muss der Miner nicht zwingend zu exakt der Front zurückkehren, an der er unterbrochen wurde. Beim Wiederaufnehmen läuft er zuerst über `workplace_access` und `mine_tunnel_connector`; erst danach sucht Civ nach einer anderen begonnenen Arbeitsfront. Gibt es keine, kann an einem abgeschlossenen Segment ein neuer gültiger Tunnelast angelegt werden. Nur wenn keine Alternative existiert, wird der unterbrochene Abschnitt wieder aufgenommen.

## Stützbalken

Das Asset `Civilizations/Mine/Mine_Support_01.prefab.json` ist ein 4 Blöcke hoher Holzrahmen und sitzt innerhalb des 4×4-Stollens. Das Prefab ist auf einen lokalen 4×4-Querschnitt normalisiert: Anker `(0,0,0)`, Rahmenebene `x=0`, Breite `z=0..3`. Civ setzt den Anker direkt auf `MineSegment.supportOrigin(depth)` und dreht die Auswahl abhängig von der Tunnelrichtung mit Hytales diskreten Gradwerten: Ost `0°`, Nord `90°`, West `180°`, Süd `270°`.

Stützen stehen ausschließlich im Hauptgang und dort bei jedem vierten Tunnelblock: Block 4, 8 und 12, soweit der Hauptgang lang genug ist. Damit erhält ein 4er-Hauptgang eine Stütze bei Block 4, ein 8er-Hauptgang Stützen bei Block 4 und 8 und ein 12er-Hauptgang Stützen bei Block 4, 8 und 12. Eine Stütze darf damit am Ende des Hauptgangs stehen, weil danach immer noch die vollständige 4×4×4-Junction folgt. Innerhalb der Junction werden grundsätzlich keine Stützen platziert.

Stützen besitzen keinen eigenen Schutzstatus. Ihre Holzblöcke bleiben normale abbaubare Weltblöcke. Bei der Tunnel-Reconciliation werden korrekt positionierte Stützenblöcke lediglich nicht als nachträgliches Zumauern eines bereits gegrabenen Stollens interpretiert.

## Schutz vor Gebäudegrenzen

Bevor ein Segment reserviert wird, prüft Civ immer das vollständige geplante Volumen aus Hauptgang **plus Junction** gegen die `building_bounds` aller fertigen Civ-Gebäude, einschließlich der eigenen Mine. Kollidiert die gewünschte Hauptganglänge, versucht Civ zunächst kürzere Varianten derselben Richtung. Kollidieren selbst 4 Hauptblöcke plus die anschließenden 4 Junction-Blöcke, wird diese Richtung verworfen. Nur das initiale Segment am `mine_tunnel_connector` darf die Bounds der eigenen Mine ignorieren, damit der Stollen den authored Ausgang sauber verlassen kann. Dieselbe Schutzregel gilt beim tatsächlichen Blockabbau.

## Native Hytale-Grenze

Civ entscheidet über Segmentwahl, Hauptganglänge, Junction-Reservierung, Wiederaufnahme, Timing, Fortschritt und Persistenz. Hytale bleibt zuständig für NPC-Navigation, nativen Teleport bei manueller Ausfahrt, Block-Harvest und Drops, Werkzeugdarstellung, Animation sowie Prefab-Platzierung. Civ implementiert keinen eigenen Pathfinder.

Die Arbeitsanimation folgt demselben Muster wie beim Holzfäller: Civ startet für die Arbeitsphase das ItemPlayerAnimations-Set `Civ_Miner_Pickaxe` mit `SwingDown` im `Action`-Slot und stoppt es beim Verlassen der Arbeitsphase.

## Bewusst noch nicht enthalten

- unregelmäßige Wände, Nischen oder natürliche Ausgrabungsmasken
- horizontaler oder vertikaler Tunnel-Drift
- Einsammeln oder Rücktransport der Drops
- Erzsuche oder erzabhängige Sonderlogik
- unterschiedliche Materialhärten und Werkzeugboni
- Wasser-, Lava- oder Höhlen-Sonderbehandlung
- spezielle Stützkonstruktionen für Kurven/Kreuzungen
- harte Durchsetzung der Arbeiterkapazität
- unterschiedliche Tunnelebenen je Gebäudephase
- besondere Abbaumechaniken, Verzierungen oder größere Querschnitte für spätere Fraktionen wie Zwerge
