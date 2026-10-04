# Minenabbauer – Phase 1

Dieser Slice beschreibt den ersten autonomen Abbauer einer fertigen Mine.

## Produktverhalten

- Alle Minenphasen arbeiten vorerst auf derselben Höhe. Ein Upgrade verschiebt den Tunnelanschluss nicht nach unten.
- Phase 1 besitzt eine Arbeiterkapazität von 1, Phase 2 von 2 und Phase 3 von 3. Die Kapazität ist aktuell Metadatum und noch keine harte Zuweisungsgrenze.
- Ein Bewohner erhält über das Personenmenü den Beruf **Minenabbauer** und wird anschließend per Rechtsklick einer fertigen Mine zugewiesen.
- Als Development-Bootstrap erhält der Minenabbauer eine native Hytale-Eisenspitzhacke (`Tool_Pickaxe_Iron`). Eine spätere Werkzeugbeschaffung ist nicht Teil dieses Slices.
- Der Bewohner läuft beim Betreten beziehungsweise Wiedereintreten zuerst über `workplace_access` und danach zwingend zum `mine_tunnel_connector`. Erst wenn der Connector erreicht ist, darf Civ eine offene Arbeitsfront auswählen und als nächstes Navigationsziel setzen.
- Tunnelabschnitte bleiben 4 Blöcke breit und 4 Blöcke hoch, besitzen aber keine feste Länge mehr. Neue Segmente werden mit 4 bis 12 Blöcken Länge geplant. Gerade Segmente dürfen die vollen 4–12 Blöcke nutzen; bei 90°-Kurven sind mindestens 5 Blöcke nötig, weil die ersten 4 Blöcke den gemeinsamen 4×4×4-Kurvenbereich bilden.
- Die Abbaugeschwindigkeit bleibt pro Block gleich wie im bisherigen Referenzsegment: 128 Blöcke eines 4×4×8-Segments entsprechen weiterhin 60 Sekunden. Kurze Segmente dauern daher entsprechend kürzer, lange entsprechend länger.
- Bei der Planung wird zunächst eine gewünschte Länge im erlaubten Bereich gewählt. Passt das vollständige Segment nicht, wird dieselbe Richtung mit kürzeren Längen erneut geprüft. Erst wenn auch die kleinste sinnvolle Länge nicht passt, fällt die Richtung aus der Auswahl.
- Bereits leere Blöcke werden übersprungen. Normale abbaubare Blöcke werden über Hytales nativen Harvest-Pfad abgebaut. Die entstehenden Drops bleiben in der Welt und können normal despawnen.
- Vor Reservierung eines Abschnitts prüft Civ den vollständigen tatsächlichen Segmentkörper gegen geschützte Civ-Gebäude und bereits reservierte beziehungsweise bestehende Minensegmente. Direkt vor einem einzelnen Abbau bleibt zusätzlich ein Schutzcheck bestehen.
- Die persistierte Segmentposition beschreibt den geplanten Stollen, nicht blind den aktuellen Weltzustand. Vor der Arbeit prüft der Minenabbauer die echten Blöcke des Stollens erneut und setzt seine Arbeitsfront auf den ersten wieder gefüllten Block zurück. Dadurch arbeitet er sich nach nachträglichem Auffüllen wieder von vorne durch den betroffenen Abschnitt.
- Auch bereits abgeschlossene Segmente werden auf nachträgliche Blockierungen geprüft. Wird ein alter Abschnitt wieder zugemauert, wird er erneut geöffnet, bevor der Arbeiter an weiter hinten liegenden Segmenten fortsetzt.
- Der Abbau läuft Ebene für Ebene in Tunnelrichtung. Eine Ebene ist der vollständige 4×4-Querschnitt: zuerst werden alle 16 Blöcke dieser Arbeitsfront verarbeitet, erst danach rückt die Arbeitsfront um einen Block tiefer in den Stollen.
- Die Erreichbarkeit wird an der Arbeitsposition der aktuellen Front geprüft, nicht noch einmal pro einzelnem Block. Sobald der Minenabbauer die gültige Arbeitsposition erreicht hat, darf er die komplette aktuelle 4×4-Front bearbeiten.
- Eine Arbeitsposition gilt nur dann als erreicht, wenn auch die Höhe stimmt. Ein NPC direkt über dem Stollen darf deshalb nicht von der Oberfläche nach unten abbauen.
- Hytales native Navigation bleibt zuständig für den eigentlichen Weg. Civ setzt ein Bewegungsziel; `ReadPosition`/`Seek` übernimmt den Pfad.
- Nach einem fertigen Abschnitt entscheidet der Abbauer selbst zwischen geradeaus, links und rechts. Ungültige Richtungen fallen aus der Auswahl. Geradeaus wird zunächst mit 60 %, links und rechts jeweils mit 20 % gewichtet. Ein direktes Umdrehen ist ausgeschlossen.
- Bei einer 90°-Kurve läuft der Miner vor dem eigentlichen Abbau einmal zum Mittelpunkt der bereits offenen gemeinsamen 4×4-Kreuzungsfläche. Erst danach wird die neue seitliche Arbeitsfront als Ziel gesetzt. So muss Hytales Pathfinder nie direkt eine noch geschlossene Seitenwand als erreichbares Bewegungsziel behandeln.
- Ist keine der drei Richtungen mit mindestens der erforderlichen Mindestlänge gültig, endet dieser Tunnelast vorerst.
- Die Tunnelstruktur, Reservierung, Richtung, Segmentlänge und der Abbaufortschritt werden persistent gespeichert. Alte persistierte Datensätze ohne Längenfeld werden weiterhin als 8-Blöcke-Segmente geladen.

## Manueller Ausgang und Wiederaufnahme

Ein Minenabbauer kann tief im dynamisch gegrabenen Stollen durch Hytales Navigation keinen zuverlässigen Weg zurück an die Oberfläche finden. Deshalb besitzt nur der Miner einen gezielten manuellen Ausgang:

- Erteilt der Spieler einem unterirdischen Minenabbauer einen manuellen Bewegungsbefehl, teleportiert Civ ihn über Hytales native `Teleport`-ECS-Komponente zum `workplace_access` seiner zugewiesenen Mine.
- Der ursprüngliche Bewegungsbefehl bleibt bestehen. Im nächsten Tick läuft der NPC vom Minenzugang mit der normalen nativen Navigation weiter zum angeklickten Ziel.
- Befindet sich der Miner bereits auf Höhe des Minenzugangs oder darüber, wird nicht teleportiert.
- Der Teleport gilt nur für manuelle Bewegungsbefehle. Das autonome Graben wird nicht über Teleports gesteuert.
- Nach dem manuellen Auftrag muss der Miner nicht zwingend zu exakt der Front zurückkehren, an der er unterbrochen wurde. Beim Wiederaufnehmen läuft er zuerst über `workplace_access` und `mine_tunnel_connector`; erst danach sucht Civ nach einer anderen begonnenen Arbeitsfront. Gibt es keine, kann an einem abgeschlossenen Segment ein neuer gültiger Tunnelast angelegt werden. Nur wenn keine Alternative existiert, wird der unterbrochene Abschnitt wieder aufgenommen.

## Stützbalken

Das Asset `Civilizations/Mine/Mine_Support_01.prefab.json` ist ein 4 Blöcke hoher Holzrahmen und sitzt innerhalb des 4×4-Stollens. Das Prefab ist auf einen lokalen 4×4-Querschnitt normalisiert: Anker `(0,0,0)`, Rahmenebene `x=0`, Breite `z=0..3`. Civ setzt den Anker direkt auf `MineSegment.supportOrigin(depth)` und dreht die Auswahl abhängig von der Tunnelrichtung mit Hytales diskreten Gradwerten: Ost `0°`, Nord `90°`, West `180°`, Süd `270°`.

Stützen stehen vorerst alle 4 Tunnelblöcke, jedoch nie direkt am Segmentende. Die Anzahl richtet sich deshalb nach der tatsächlichen Segmentlänge. Ein 4-Blöcke-Segment erhält keinen regulären Rahmen, ein 8-Blöcke-Segment einen Rahmen bei Block 4 und ein 12-Blöcke-Segment Rahmen bei Block 4 und 8. Der Übergangsbereich bleibt dadurch für gerade Fortsetzungen und insbesondere Links-/Rechtskurven frei. Spezielle Stützkonstruktionen für Kurven und Kreuzungen sind noch nicht umgesetzt.

Stützen besitzen keinen eigenen Schutzstatus. Ihre Holzblöcke bleiben normale abbaubare Weltblöcke. Bei der Tunnel-Reconciliation werden korrekt positionierte Stützenblöcke lediglich nicht als nachträgliches Zumauern eines bereits gegrabenen Stollens interpretiert.

## Schutz vor Gebäudegrenzen

Bevor ein Folgesegment reserviert wird, prüft Civ den vollständigen geplanten Tunnelkörper gegen die `building_bounds` aller fertigen Civ-Gebäude, einschließlich der eigenen Mine. Kollidiert die gewünschte Länge, versucht Civ zunächst kürzere Varianten derselben Richtung. Kollidiert auch die Mindestlänge, wird diese Richtung verworfen. Nur das initiale Segment am `mine_tunnel_connector` darf die Bounds der eigenen Mine ignorieren, damit der Stollen den authored Ausgang sauber verlassen kann. Dieselbe Schutzregel gilt beim tatsächlichen Blockabbau, sodass bereits gespeicherte problematische Folgesegmente nicht weiter in ein Gebäude hinein gegraben werden.

## Native Hytale-Grenze

Civ entscheidet über Segmentwahl, Länge, Reservierung, Wiederaufnahme, Timing, Fortschritt und Persistenz. Hytale bleibt zuständig für NPC-Navigation, nativen Teleport bei manueller Ausfahrt, Block-Harvest und Drops, Werkzeugdarstellung, Animation sowie Prefab-Platzierung. Civ implementiert keinen eigenen Pathfinder.

Die Arbeitsanimation folgt demselben Muster wie beim Holzfäller: Civ startet für die Arbeitsphase das ItemPlayerAnimations-Set `Civ_Miner_Pickaxe` mit `SwingDown` im `Action`-Slot und stoppt es beim Verlassen der Arbeitsphase.

## Bewusst noch nicht enthalten

- Einsammeln oder Rücktransport der Drops
- Erzsuche oder erzabhängige Sonderlogik
- unterschiedliche Materialhärten und Werkzeugboni
- Wasser-, Lava- oder Höhlen-Sonderbehandlung
- spezielle Stützkonstruktionen für Kurven/Kreuzungen
- harte Durchsetzung der Arbeiterkapazität
- unterschiedliche Tunnelebenen je Gebäudephase
- besondere Abbaumechaniken, Verzierungen oder größere Querschnitte für spätere Fraktionen wie Zwerge
