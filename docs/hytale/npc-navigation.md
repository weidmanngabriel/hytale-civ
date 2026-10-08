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
- Die aktuelle Live-Arbeitsfront verwendet direkt eine `MineTunnelGeometry.Slice` aus der neuen Mine-Geometrie. Nach vollständigem Abbau einer Slice wird die semantische `MineWorkFront` auf die nächste Main-Tunnel-Slice verschoben. Der Miner navigiert auf die bereits freigelegte Seite der Front; Hytales `Seek` bleibt für den physischen Weg verantwortlich und Civ berechnet keinen parallelen Pathfinder.
- Sobald Civ ein neues Ziel bestimmt, wird dieses einmal in `CivMinerMoveTarget` geschrieben.
- Solange dieses Ziel unverändert bleibt, löscht, retriggert oder ersetzt Civ das native Bewegungsziel nicht. `ReadPosition` und `Seek` behalten damit die vollständige Verantwortung für Pfadsuche und Bewegung.
- Der Miner verwendet `UseBestPath: false`, damit ein unvollständiger Ersatzpfad nicht als akzeptable Annäherung an ein unterirdisches Arbeitsziel dient.
- Erst bei tatsächlicher Ankunft, einem neuen Gameplay-Ziel oder einem expliziten Zustandswechsel darf der Adapter das Ziel ändern oder löschen.

Die gepinnte Server-JAR stellt am aktiven `MotionController` `getNavState()` sowie `setForceRecomputePath(boolean)` bereit. `NavState` enthält unter anderem `PROGRESSING`, `BLOCKED`, `AT_GOAL`, `ABORTED` und `DEFER`. Für Miner ist dieser native Zustand das primäre Failure-Signal: Bei `BLOCKED` oder `ABORTED` fordert Civ genau einmal eine native Pfadneuberechnung an. Bleibt dasselbe Ziel danach terminal fehlgeschlagen, meldet `MinerNavigationSystem` den Fehler über den flüchtigen `MinerNavigationFailureRegistry` an `MinerWorkSystem`. Dort liegt die Gameplay-Reaktion: Arbeitsfront/Pflichtarbeit blockieren oder normale Infrastruktur überspringen. `DEFER` wird ohne Runtime-Beleg nicht als terminaler Fehler behandelt.

Civ besitzt weiterhin keinen eigenen Voxel-Pathfinder und keinen allgemeinen Stillstands-Timer als primäres Failure-System. Ein eigener Watchdog wäre nur dann zulässig, wenn ein fokussierter Runtime-Test einen realen Hytale-Stuck-Fall nachweist, der keinen brauchbaren nativen `NavState` erreicht.

Für die opt-in Diagnose protokolliert `MinerNavigationSystem` genau diese vorhandene native Recovery-Kette: Zielwechsel, `REPATH_REQUESTED` mit dem beobachteten `NavState`, danach gegebenenfalls `NAVIGATION_FAILED` und bei Main-Tunnel-Recovery `NAVIGATION_RECOVERY` mit dem verwendeten Safe-Anchor. Die Diagnostik löst selbst weder Repath noch Teleport aus; sie beobachtet nur die ohnehin ausgeführten Schritte.

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

### Aktueller Layer-4/5/6-Stand

Die semantische Tunnelzugehörigkeit für Miner ist inzwischen an das persistente `MineNetwork` und die deterministisch regenerierte `MineTunnelGeometry` angebunden. `MinerNavigationSystem` prüft Tunnelmitgliedschaft gegen die Layer-3-Geometrie; Anchor-Erzeugung, Long-Distance-Auswahl und native `NavState`-Recovery benötigen keine Legacy-`MineSegment`-Geometrie mehr.

Infrastruktur aus Layer 5 verändert diesen Vertrag nicht. Supports, Licht, Treppen und Brücken liefern lediglich neue Arbeitsziele; die physische Bewegung zwischen diesen Zielen bleibt bei Hytales `ReadPosition`/`Seek`. Eine unfertige verpflichtende Brücke oder Treppe sperrt den semantischen Weiterbau, statt einen Civ-eigenen Pathfinder einzuführen.

NPC-Ebene 6 ergänzt keine eigene Wegfindung. Nach dem einmaligen nativen Recompute wird ein terminaler Ziel-Fehler nur als Adapter-Ergebnis an den Work-Lifecycle übergeben. Ein optionaler Safe-Anchor-Teleport im Hauptkorridor bleibt Positions-Recovery; die fehlgeschlagene Arbeitsfront wird dadurch nicht automatisch erneut geöffnet.

## NPC-Ebene 7: Ruhestellen

Leerlaufziel ist entweder die Position eines fertiggebauten Unterkunftsankers (sofern geladen, muss dessen Block leer sein; nicht geladene entfernte Räume werden nicht pauschal ausgeschlossen) oder der vorhandene Tunnel-Connector mit anschließendem `workplace_access`. Die Civ-Auswahl entscheidet nur, welches semantische Ziel gilt; Bewegung wird weiterhin über `CivMinerMoveTarget` / natives `ReadPosition` / `Seek` ausgeführt. `NavState`-Fehler eines Aufenthaltsziels führen zum Verwerfen dieses Ziels statt zum Blockieren einer Abbaufront. Die vorhandene Long-Distance-Ankerlogik bleibt unverändert; Laufzeit-Erreichbarkeit von Raumankern ist im echten Client separat zu prüfen.

## Miner-Restart an unterirdischer Position

Beim erneuten Laden eines autonomen Miners kann die native Hytale-Entity-Position bereits in der Mine liegen. Vor einer erzwungenen Oberflächen-Eintrittsroute baut Civ das deterministische Mine-Runtime-Layout auf und erkennt Positionen in bekannten Tunnel-/Raum-Voxelvolumen, sofern der Fußblock im geladenen Hytale-Weltzustand exakt EMPTY ist. Dann gelten workplace_access und mine_tunnel_connector für diese Aufenthaltsposition als bereits durchlaufen; es erfolgt keine zusätzliche Teleportation und keine alternative Civ-Wegfindung. Für außerhalb der Mine geladene NPCs bleibt die gestaffelte Eintrittsroute unverändert. Dieser Status wird nur im flüchtigen WorkerRuntime gehalten und beim nächsten LOAD neu abgeleitet. Native Bewegungs-/Teleport- und LOAD-Lifecycle-Semantik ist weiterhin in einem Ingame-Test zu verifizieren.
