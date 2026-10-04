# NPCs und Navigation

## Grundregel

Civ entscheidet im Core, **wer wann wohin und warum** laufen soll. Hytale entscheidet, **wie** ein NPC dieses Ziel physisch erreicht.

Civ implementiert deshalb keine parallele Wegfindung, solange Hytales native Navigation die Produktanforderung erfüllt.

## Aktueller Projektvertrag

Die Rolle `Civ_Inhabitant` besitzt zwei native Positionsslots für Bewegung. `CivMoveTarget` liegt bewusst an Slot-Index 0 und bleibt der Standard für gewöhnliche Bewohnerbewegung. `CivMinerMoveTarget` liegt an Slot-Index 1 und wird ausschließlich für Minenabbauer verwendet. Die Slot-Reihenfolge bildet einen Java-/Asset-Vertrag und wird automatisiert getestet.

`CivUnitRegistry` schreibt oder löscht das aktive Ziel über Hytales `MarkedEntitySupport` und hält den jeweils anderen Slot leer. Die Rolle verarbeitet beide Ziele anschließend über `ReadPosition` und `Seek`; Navigation und Walk-Bewegung bleiben damit bei Hytale.

Der normale `CivMoveTarget` verwendet Hytales Standardverhalten. Der Miner-Slot verwendet ebenfalls den nativen `Seek`-Pathfinder, setzt aber `UseBestPath: false`. Dadurch darf Hytale für ein nicht erreichbares unterirdisches Ziel keinen nur geometrisch näheren Teilpfad als Ersatz akzeptieren. Diese strengere Einstellung gilt bewusst nicht für andere Berufe.

`CivManualMovementSystem` sowie Berufsadapter prüfen nur, ob ein vom Core angefordertes Ziel erreicht wurde, und melden den Abschluss an den Core zurück.

## Erreichbarkeit und Pfadneuberechnung

Berufsadapter dürfen eine native Bewegung nicht dadurch ersetzen, dass sie selbst einen zweiten Pathfinder implementieren. Sie dürfen aber sicherstellen, dass Arbeit nur an einer tatsächlich erreichten Position ausgeführt wird.

Der Minenabbauer verwendet deshalb folgende Navigationsregeln:

- Ankunft wird dreidimensional geprüft; gleiche X/Z-Koordinaten auf einer anderen Höhe zählen nicht als erreicht.
- Die Navigation zielt auf die Arbeitsposition der aktuellen Tunnel-Front. Ist diese Position erreicht, darf der Arbeiter die dazugehörige Arbeitsfront bearbeiten; einzelne Blöcke derselben Front erhalten keine zusätzliche künstliche Civ-Reichweitengrenze.
- Beim autonomen Betreten oder Wiedereintreten einer Mine wird die Navigation gestaffelt: zuerst `workplace_access`, danach `mine_tunnel_connector`, erst danach eine Tunnel-Arbeitsposition. Dadurch bekommt der native Pathfinder nicht direkt von oberhalb des Gebäudes ein tiefes unterirdisches Ziel.
- Bei einer Links-/Rechtskurve dient der Mittelpunkt der bereits offenen gemeinsamen 4×4-Kreuzungsfläche einmalig als Zwischenziel, bevor Civ die neue seitliche Arbeitsfront setzt. Die eigentliche Wegfindung zwischen diesen semantischen Zielen bleibt vollständig bei Hytales `Seek`.
- Sobald Civ ein neues Ziel bestimmt, wird dieses einmal in `CivMinerMoveTarget` geschrieben.
- Solange dieses Ziel unverändert bleibt, löscht, retriggert oder ersetzt Civ das native Bewegungsziel nicht. `ReadPosition` und `Seek` behalten damit die vollständige Verantwortung für Pfadsuche und Bewegung.
- Der Miner verwendet `UseBestPath: false`, damit ein unvollständiger Ersatzpfad nicht als akzeptable Annäherung an ein unterirdisches Arbeitsziel dient.
- Erst bei tatsächlicher Ankunft, einem neuen Gameplay-Ziel oder einem expliziten Zustandswechsel darf der Adapter das Ziel ändern oder löschen.

Damit besitzt Civ keinen eigenen Stillstands-Timer, keinen eigenen Repath-Versuch und keinen zeitbasierten Abbruch einer laufenden Hytale-Navigation. Falls ein natives Ziel trotz geometrisch offenem Weg nicht erreicht wird, muss die Hytale-Navigation beziehungsweise der Ziel-/Nav-Weltzustand diagnostiziert werden, statt die Route durch Civ regelmäßig zurückzusetzen.

## Manuelle Befehle und Arbeit

Ein manueller Bewegungsauftrag liegt als `MovementIntent` im Hytale-unabhängigen `InhabitantActivity`-Zustand. Solange dieser Auftrag aktiv ist, verdrängt er autonome Berufsarbeit. Nach gemeldeter Ankunft darf die bisherige Arbeit fortgesetzt werden.

Da `CivUnitRegistry` den aktuell aktiven Beruf auswertet, landet auch ein manueller Bewegungsauftrag eines Minenabbauers im Miner-Slot. Bei einem Berufswechsel wird ein vorhandenes Bewegungsziel in den zum neuen Beruf passenden Slot übertragen und der andere Slot geleert.

## Geladene Welt

Weltabfragen innerhalb laufender ECS-Systemverarbeitung dürfen keine Chunks synchron laden, wenn dadurch der ECS-Store verändert werden könnte. Der Holzfäller verwendet deshalb für die Baumsuche `World.getChunkIfLoaded` und arbeitet nur mit bereits geladenen Chunks.

Auch der Minenabbauer prüft den tatsächlichen Tunnelzustand ausschließlich über bereits geladene Chunks. Der persistierte Tunnelplan darf durch nachträgliche Weltänderungen veraltet sein; deshalb wird die aktuelle Arbeitsfront aus den realen Blöcken neu abgeleitet, bevor der NPC weiterarbeitet.

## Projektgrenze

Hytale-Adapter führen Navigation, Weltabfragen und native Interaktionen aus. Gameplay-Prioritäten, Zustandsfolgen und Unterbrechungen bleiben im Core.

## Manueller Miner-Ausgang

Ein manueller Bewegungsbefehl für einen unterirdischen Minenabbauer verwendet Hytales native `Teleport`-ECS-Komponente, um den NPC zum `workplace_access` seiner zugewiesenen Mine zu setzen. Der bestehende `MovementIntent` wird nicht abgeschlossen; anschließend übernimmt wieder das normale native `ReadPosition`/`Seek` zum vom Spieler geklickten Ziel. Die gepinnte Server-JAR bestätigt die `Teleport(Vector3dc, Rotation3fc)`-Komponente und den NPC-Teleport-Lifecycle. Ob die konkrete Runtime-Positionierung im Spiel wie beabsichtigt wirkt, bleibt ein Ingame-Test.
