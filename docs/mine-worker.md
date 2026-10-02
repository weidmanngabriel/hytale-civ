# Minenabbauer – Phase 1

Dieser Slice beschreibt den ersten autonomen Abbauer einer fertigen Mine.

## Produktverhalten

- Alle Minenphasen arbeiten vorerst auf derselben Höhe. Ein Upgrade verschiebt den Tunnelanschluss nicht nach unten.
- Phase 1 besitzt eine Arbeiterkapazität von 1, Phase 2 von 2 und Phase 3 von 3. Die Kapazität ist aktuell Metadatum und noch keine harte Zuweisungsgrenze.
- Ein Bewohner erhält über das Personenmenü den Beruf **Minenabbauer** und wird anschließend per Rechtsklick einer fertigen Mine zugewiesen.
- Als Development-Bootstrap erhält der Minenabbauer eine native Hytale-Eisenspitzhacke (`Tool_Pickaxe_Iron`). Eine spätere Werkzeugbeschaffung ist nicht Teil dieses Slices.
- Der Bewohner läuft zuerst über `workplace_access` und arbeitet anschließend am `mine_tunnel_connector`.
- Ein Tunnelabschnitt ist 4 Blöcke breit, 4 Blöcke hoch und 8 Blöcke lang. Das sind 128 mögliche Abbaublöcke.
- Die Zielzeit eines vollständig gefüllten Abschnitts beträgt zunächst 600 Sekunden. Balancewerte liegen zentral in `MineTuning`.
- Bereits leere Blöcke werden übersprungen. Normale abbaubare Blöcke werden über Hytales nativen Harvest-Pfad abgebaut. Die entstehenden Drops bleiben in der Welt und können normal despawnen.
- Vor Reservierung eines Abschnitts prüft Civ, ob dessen Raum mit geschützten Civ-Gebäuden oder bereits reservierten beziehungsweise bestehenden Minensegmenten kollidiert. Direkt vor einem einzelnen Abbau bleibt zusätzlich ein Schutzcheck bestehen.
- Nach einem fertigen Abschnitt entscheidet der Abbauer selbst zwischen geradeaus, links und rechts. Ungültige Richtungen fallen aus der Auswahl. Geradeaus wird zunächst mit 60 %, links und rechts jeweils mit 20 % gewichtet. Ein direktes Umdrehen ist ausgeschlossen.
- Ist keine der drei Richtungen gültig, endet dieser Tunnelast vorerst.
- Die Tunnelstruktur, Reservierung, Richtung und der Abbaufortschritt werden persistent gespeichert, damit ein Serverneustart den begonnenen Stollen nicht vergisst.

## Stützbalken

Das Asset `Civilizations/Mine/Mine_Support_01` ist ein 4 Blöcke hoher Holzrahmen und sitzt innerhalb des 4×4-Stollens. Civ setzt ihn über Hytales native Prefab-API statt die Balken selbst blockweise zu erzeugen.

Stützen stehen zunächst alle 4 Tunnelblöcke. Ein 8-Blöcke-Segment erhält daher einen Rahmen nach Block 4 und einen am Segmentende nach Block 8. Spezielle Kurven-, Kreuzungs- oder hochwertige Volksvarianten folgen später.

## Native Hytale-Grenze

Civ entscheidet über Segmentwahl, Reservierung, Timing, Fortschritt und Persistenz. Hytale bleibt zuständig für NPC-Navigation, Block-Harvest und Drops, Werkzeugdarstellung, Animation sowie Prefab-Platzierung.

Die Arbeitsanimation verwendet das Civ-Animationset `Civ_Miner_Pickaxe` mit `SwingDown`, das auf Hytales vorhandene Pickaxe-Mining-Animationen verweist. Wie beim Holzfäller wird die Animation für die Arbeitsphase gestartet und beim Verlassen der Arbeitsphase gestoppt.

## Bewusst noch nicht enthalten

- Einsammeln oder Rücktransport der Drops
- Erzsuche oder erzabhängige Sonderlogik
- unterschiedliche Materialhärten und Werkzeugboni
- Wasser-, Lava- oder Höhlen-Sonderbehandlung
- spezielle Stützkonstruktionen für Kurven/Kreuzungen
- harte Durchsetzung der Arbeiterkapazität
- unterschiedliche Tunnelebenen je Gebäudephase
