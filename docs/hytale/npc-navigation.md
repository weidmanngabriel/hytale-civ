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
- Ein Minensegment gräbt nach seinem 4–12 Blöcke langen Hauptgang immer noch eine eigene 4×4×4-Junction in derselben Richtung vollständig aus. Erst danach erzeugt Civ das nächste Segment geradeaus, links oder rechts außerhalb dieser Junction. Parent und Kind überlappen nicht; dadurch braucht die Navigation keinen künstlichen Kurven-Zwischenpunkt mehr. Die eigentliche Wegfindung zwischen Arbeitsfronten bleibt vollständig bei Hytales `Seek`.
- Sobald Civ ein neues Ziel bestimmt, wird dieses einmal in `CivMinerMoveTarget` geschrieben.
- Solange dieses Ziel unverändert bleibt, löscht, retriggert oder ersetzt Civ das native Bewegungsziel nicht. `ReadPosition` und `Seek` behalten damit die vollständige Verantwortung für Pfadsuche und Bewegung.
- Der Miner verwendet `UseBestPath: false`, damit ein unvollständiger Ersatzpfad nicht als akzeptable Annäherung an ein unterirdisches Arbeitsziel dient.
- Erst bei tatsächlicher Ankunft, einem neuen Gameplay-Ziel oder einem expliziten Zustandswechsel darf der Adapter das Ziel ändern oder löschen.

Die gepinnte Server-JAR stellt am aktiven `MotionController` `getNavState()` sowie `setForceRecomputePath(boolean)` bereit. `NavState` enthält unter anderem `PROGRESSING`, `BLOCKED`, `AT_GOAL`, `ABORTED` und `DEFER`. Für Miner ist dieser native Zustand jetzt das primäre Failure-Signal: Bei `BLOCKED` oder `ABORTED` fordert Civ einmal eine native Pfadneuberechnung an. Bleibt danach ein terminaler Fehler bestehen, darf die Civ-Recovery greifen. `DEFER` wird ohne Runtime-Beleg nicht als terminaler Fehler behandelt.

Civ besitzt weiterhin keinen eigenen Voxel-Pathfinder und keinen allgemeinen Stillstands-Timer als primäres Failure-System. Ein eigener Watchdog wäre nur dann zulässig, wenn ein fokussierter Runtime-Test einen realen Hytale-Stuck-Fall nachweist, der keinen brauchbaren nativen `NavState` erreicht.

## Sichere Minen-Anker

Minen-Anker sind keine aus dem Planer abgeleiteten Wegpunkte. Ein regulärer Anchor wird nur aus tatsächlich beobachteter NPC-Bewegung erzeugt: Der Miner muss den Block durchlaufen haben, der Block muss exakt `BlockType.EMPTY` sein und der Kandidat muss ungefähr 10 Blöcke von umliegenden Anchors entfernt sein.

Civ persistiert damit nur ein kleines semantisches Netz bereits bestätigter sicherer Punkte. Hytale bleibt zwischen den Zielen für den physischen Pfad verantwortlich. Junction-, Raum- und Brücken-Anker verwenden dieselbe physische Validierung und erhalten nur zusätzlich ihre semantische Bedeutung. Die aktuelle Arbeitsfront verwendet keinen mitwandernden `WORK_FRONT`-Anchor; der vorhandene dynamische Work-Target bleibt das lokale Endziel.

Für lange Untertagewege gilt eine >50-Block-Schwelle als euklidische Luftlinie. Ein Miner, der von über Tage zurückkehrt, muss zuerst normal über `workplace_access` zum `mine_tunnel_connector` laufen; vorher darf kein Long-Distance-Teleport stattfinden. Danach beziehungsweise bei bereits unter Tage befindlichen Minern darf Civ einen bereits bekannten erreichbaren Anchor auf der gültigen Zielroute wählen, bevorzugt den Anchor mit der kleinsten Luftlinienentfernung zum Ziel. Direkt zum Arbeitsplatz wird nie teleportiert.

## Manuelle Befehle und Arbeit

Ein manueller Bewegungsauftrag liegt als `MovementIntent` im Hytale-unabhängigen `InhabitantActivity`-Zustand. Solange dieser Auftrag aktiv ist, verdrängt er autonome Berufsarbeit. Nach gemeldeter Ankunft darf die bisherige Arbeit fortgesetzt werden.

Da `CivUnitRegistry` den aktuell aktiven Beruf auswertet, landet auch ein manueller Bewegungsauftrag eines Minenabbauers im Miner-Slot. Bei einem Berufswechsel wird ein vorhandenes Bewegungsziel in den zum neuen Beruf passenden Slot übertragen und der andere Slot geleert.

## Geladene Welt

Weltabfragen innerhalb laufender ECS-Systemverarbeitung dürfen keine Chunks synchron laden, wenn dadurch der ECS-Store verändert werden könnte. Der Holzfäller verwendet deshalb für die Baumsuche `World.getChunkIfLoaded` und arbeitet nur mit bereits geladenen Chunks.

Auch der Minenabbauer prüft den tatsächlichen Tunnelzustand ausschließlich über bereits geladene Chunks. Der persistierte Tunnelplan darf durch nachträgliche Weltänderungen veraltet sein; deshalb wird die aktuelle Arbeitsfront aus den realen Blöcken neu abgeleitet, bevor der NPC weiterarbeitet. Dasselbe gilt für Anchor-Sicherheit: ein persistierter Anchor wird vor einem Teleport nur verwendet, wenn sein Zielblock weiterhin exakt `BlockType.EMPTY` ist.

## Projektgrenze

Hytale-Adapter führen Navigation, Weltabfragen und native Interaktionen aus. Gameplay-Prioritäten, Zustandsfolgen und Unterbrechungen bleiben im Core.

## Manueller Miner-Ausgang

Ein manueller Bewegungsbefehl für einen unterirdischen Minenabbauer verwendet Hytales native `Teleport`-ECS-Komponente, um den NPC zum `workplace_access` seiner zugewiesenen Mine zu setzen. Der bestehende `MovementIntent` wird nicht abgeschlossen; anschließend übernimmt wieder das normale native `ReadPosition`/`Seek` zum vom Spieler geklickten Ziel. Die gepinnte Server-JAR bestätigt die `Teleport(Vector3dc, Rotation3fc)`-Komponente und den NPC-Teleport-Lifecycle. Ob die konkrete Runtime-Positionierung im Spiel wie beabsichtigt wirkt, bleibt ein Ingame-Test.

## Vorläufige Oberflächen-Recovery des Miners

Als Sicherheitsnetz für beobachtete native Navigationsausreißer besitzt der Miner zusätzlich einen Hytale-seitigen Recovery-Watchdog. Dieser ersetzt keine Wegfindung: Er greift erst ein, nachdem ein autonom arbeitender Miner nachweislich den bekannten unterirdischen Minenraum erreicht hatte und anschließend für etwa 1,5 Sekunden außerhalb der eigenen `building_bounds` sowie aller bekannten `MineSegment`-Volumen auf oder über dem aktuellen Gebäude-Referenzniveau steht. Dann setzt Civ den NPC über dieselbe verifizierte native `Teleport`-ECS-Komponente zum `mine_tunnel_connector` zurück; das bestehende autonome Bewegungsziel bleibt erhalten.

Ein manueller Bewegungsauftrag oder die gemeinsame Wiederanlaufpause deaktiviert und entschärft diesen Watchdog vollständig. Damit kann ein vom Spieler herausgerufener Miner normal außerhalb der Mine laufen und später regulär zurückkehren; die Recovery wird erst wieder scharf, nachdem er erneut den Tunnelraum beziehungsweise eine Position unter dem aktuellen Minen-Referenzniveau erreicht hat.

Die Y-Klassifikation ist ausdrücklich eine temporäre Prototyp-Heuristik für den aktuellen flachen Minen-Slice. Sie ist **keine** verifizierte allgemeine Definition von „oberirdisch“. Sobald Weltgeneration, Berge oder vertikal driftende Tunnel relevant werden, muss diese Klassifikation terrainbewusst ersetzt werden, während die native Teleport-Grenze unverändert bleiben kann.
