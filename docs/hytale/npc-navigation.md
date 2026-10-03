# NPCs und Navigation

## Grundregel

Civ entscheidet im Core, **wer wann wohin und warum** laufen soll. Hytale entscheidet, **wie** ein NPC dieses Ziel physisch erreicht.

Civ implementiert deshalb keine parallele Wegfindung, solange Hytales native Navigation die Produktanforderung erfüllt.

## Aktueller Projektvertrag

Die Rolle `Civ_Inhabitant` besitzt genau einen Positionsslot namens `CivMoveTarget`. Dieser liegt bewusst an Slot-Index 0 und bildet einen Java-/Asset-Vertrag, der automatisiert getestet wird.

`CivUnitRegistry` schreibt oder löscht dieses Ziel über Hytales `MarkedEntitySupport`. Die Rolle verarbeitet das Ziel anschließend über `ReadPosition` und `Seek`; Navigation und Walk-Bewegung bleiben damit bei Hytale.

`CivManualMovementSystem` sowie Berufsadapter prüfen nur, ob ein vom Core angefordertes Ziel erreicht wurde, und melden den Abschluss an den Core zurück.

## Erreichbarkeit und Pfadneuberechnung

Berufsadapter dürfen eine native Bewegung nicht dadurch ersetzen, dass sie selbst einen zweiten Pathfinder implementieren. Sie dürfen aber sicherstellen, dass Arbeit nur an einer tatsächlich erreichten Position ausgeführt wird.

Der Minenabbauer verwendet deshalb folgende Navigationsregeln:

- Ankunft wird dreidimensional geprüft; gleiche X/Z-Koordinaten auf einer anderen Höhe zählen nicht als erreicht.
- Die Navigation zielt auf die Arbeitsposition der aktuellen Tunnel-Front. Ist diese Position erreicht, darf der Arbeiter die dazugehörige Arbeitsfront bearbeiten; einzelne Blöcke derselben Front erhalten keine zusätzliche künstliche Civ-Reichweitengrenze.
- Sobald Civ ein neues Ziel bestimmt, wird dieses einmal in `CivMoveTarget` geschrieben.
- Solange dieses Ziel unverändert bleibt, löscht, retriggert oder ersetzt Civ das native Bewegungsziel nicht. `ReadPosition` und `Seek` behalten damit die vollständige Verantwortung für Pfadsuche und Bewegung.
- Erst bei tatsächlicher Ankunft, einem neuen Gameplay-Ziel oder einem expliziten Zustandswechsel darf der Adapter das Ziel ändern oder löschen.

Damit besitzt Civ keinen eigenen Stillstands-Timer, keinen eigenen Repath-Versuch und keinen zeitbasierten Abbruch einer laufenden Hytale-Navigation. Falls ein natives Ziel trotz geometrisch offenem Weg nicht erreicht wird, muss die Hytale-Navigation beziehungsweise der Ziel-/Nav-Weltzustand diagnostiziert werden, statt die Route durch Civ regelmäßig zurückzusetzen.

## Manuelle Befehle und Arbeit

Ein manueller Bewegungsauftrag liegt als `MovementIntent` im Hytale-unabhängigen `InhabitantActivity`-Zustand. Solange dieser Auftrag aktiv ist, verdrängt er autonome Berufsarbeit. Nach gemeldeter Ankunft darf die bisherige Arbeit fortgesetzt werden.

## Geladene Welt

Weltabfragen innerhalb laufender ECS-Systemverarbeitung dürfen keine Chunks synchron laden, wenn dadurch der ECS-Store verändert werden könnte. Der Holzfäller verwendet deshalb für die Baumsuche `World.getChunkIfLoaded` und arbeitet nur mit bereits geladenen Chunks.

Auch der Minenabbauer prüft den tatsächlichen Tunnelzustand ausschließlich über bereits geladene Chunks. Der persistierte Tunnelplan darf durch nachträgliche Weltänderungen veraltet sein; deshalb wird die aktuelle Arbeitsfront aus den realen Blöcken neu abgeleitet, bevor der NPC weiterarbeitet.

## Projektgrenze

Hytale-Adapter führen Navigation, Weltabfragen und native Interaktionen aus. Gameplay-Prioritäten, Zustandsfolgen und Unterbrechungen bleiben im Core.
