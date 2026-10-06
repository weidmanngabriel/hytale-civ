# Architektur

## Ziel

Optionaler Entwicklungszugriff: `tools/hytale-mcp` besitzt stdio-MCP, Build/Deployment und den lokalen Serverprozess; `CivDevBridge` an der Plugin-Grenze bietet private Loopback-HTTP-Aktionen. Sie verwendet `CivUnitRegistry`, `CivActivityRegistry`, `NpcInfoProvider` und native Aufrufe auf dem World Thread. Der Core enthält keine MCP-/HTTP-Abhängigkeit. Details: [local-mcp.md](local-mcp.md), Begründung: [ADR 0010](decisions/0009-local-development-mcp.md).

Die Simulation soll testbar bleiben, ohne Hytale starten zu müssen. Hytale ist eine Integrationsgrenze und nicht das Domänenmodell.

Vor Version 1 ist Rückwärtskompatibilität kein Ziel, wenn dafür Migrationen, parallele Altpfade, Kompatibilitäts-Defaults oder featurespezifische Ausnahmen nötig wären. Die aktuell dokumentierte Architektur und das Datenmodell sind maßgeblich. Diese Regel muss neu bewertet werden, bevor persistente Spielerwelten oder öffentliche stabile Releases Kompatibilität zu einer Produktanforderung machen.

## Schichten

~~~text
Spielerinput / UI
      ↓ Command
Core-Simulation
      ↓ Intent
      ├──────────────→ Hytale-Adapter → Hytale-Plugin / API
      │                                      ↓ Result / Event
      └──────────────→ Headless-Simulation ──┘
                                             ↓
                                      Core-Simulation
~~~

Die Grenze ist verhaltensorientiert: UI und Hytale-Code übersetzen Eingaben und führen Engine-Arbeit aus, besitzen aber keine Civ-Spielregeln. Der Core entscheidet über Zustandswechsel, Prioritäten und Unterbrechungen. Ein Core-Intent beschreibt nur das gewünschte Ergebnis, zum Beispiel „Bewohner soll zu Ziel X laufen“; der Adapter setzt das mit Hytales nativer Navigation um und meldet Ankunft beziehungsweise Fehlschlag zurück.

Bewegung ist deshalb zweigeteilt. **Wer wann wohin und warum läuft** gehört zur Civ-Simulation. **Wie der NPC den Weg findet und physisch zurücklegt** bleibt Hytale überlassen. Civ baut keinen parallelen Wegfindungsalgorithmus, solange Hytales Navigation die Produktanforderung erfüllt.

### core

Reine Java-Simulation und Domänenregeln. Dieser Bereich darf <code>com.hypixel.hytale.*</code> nicht importieren. Bewohner, Berufe, Bedürfnisse, Inventare, Waren, Produktion, Gebäudestatus, Befehle, Wirtschaft und Simulations-Ticks gehören hierher. Der aktuell umgesetzte Berufszustand umfasst die Hytale-unabhängigen Typen <code>FarmBuilding</code>, <code>WoodcutterJob</code>, <code>BlockPosition</code> und <code>Profession</code>.

Gebäudetyp-Metadaten liegen ebenfalls Hytale-unabhängig im Core. <code>BuildingTypeDefinition</code> beschreibt die verfügbaren Phasen eines Typs; <code>BuildingTypes</code> ist der aktuelle zentrale Katalog. Phasenabhängige Eigenschaften wie <code>workerCapacity</code> sowie die fachliche Folge zur jeweils nächsten authored Phase werden dort abgeleitet und nicht in der UI oder in berufsspezifischen Hytale-Adaptern dupliziert. Die Kapazität ist derzeit nur beschreibende Metadaten und erzwingt noch keine Belegungsregel.

<code>WorkDecisionSchedule</code> bildet die gemeinsame Taktung teurer autonomer Entscheidungen ab. Ein Bewohner plant entweder aufgrund eines explizit angeforderten unmittelbaren Ereignisses oder nach Ablauf eines begrenzten Retry-Intervalls. Der Scheduler selbst führt keine Weltabfragen aus und ist deshalb sowohl vom Hytale-Adapter als auch von der Headless-Simulation verwendbar.

Für die aktuelle Mine entscheidet <code>MineFrontTaskScheduler</code> Hytale-unabhängig, welche ausführbare Tunnel-Front ein Miner als Nächstes übernimmt. Der Scheduler kennt nur semantische Frontzustände, Tunnelart, Belegung und Position: Bereits aktive normale Fronten mit freier Kapazität werden zuerst aufgefüllt; sonst wird Branch-Priorität 6 vor Main-Priorität 4 ausgewählt, danach Distanz und ein stabiler Tie-Breaker. Blockabfragen, Navigation und Abbau bleiben außerhalb des Core. Die vollständigen späteren Raum-/Infrastruktur-/Safety-Aufgabentypen werden nicht vorab in diese schmale Abstraktion gezwungen.

### simulation

<code>dev.civilizations.simulation</code> ist ein Hytale-unabhängiger zweiter Laufzeitpfad für Entwicklung und automatisierte Szenario-Tests. <code>SimulationRuntime</code> verwendet dieselben Core-Zustandsautomaten wie Hytale, ersetzt Engine-Schritte aber bewusst durch kleine deterministische Fixtures: geradlinige Fake-Bewegung, In-Memory-Bäume, Baustellen und Felder sowie kontrollierte Resultate.

Die Headless-Simulation ist keine zweite Gameplay-Implementierung und kein Ersatz für Hytales Navigation, Physik oder Weltmodell. Sie darf nur die Engine-Verträge simulieren, die ein Core-Ablauf tatsächlich benötigt. <code>SimulationMetrics</code> zählt dafür deterministische Operationen wie Planungsentscheidungen, Weltabfragen und Bewegungsanforderungen. Diese Zähler dienen als Performance-Budgets für Gameplay-Logik; reale CPU-, Rendering- und Hytale-Engine-Kosten bleiben Runtime-Messungen.

<code>SimulationScenario</code> beschreibt einen benannten, deterministischen Tick-0-Startzustand. <code>SimulationScenarios</code> hält die kleinen eingebauten Szenarien zentral, damit Viewer und automatisierte Szenario-Tests denselben Aufbau verwenden können. Ein Szenario enthält bewusst keine erwarteten Ergebnisse oder Test-Assertions; diese bleiben in den Tests.

<code>SimulationViewerApp</code> ist eine optionale Swing-/Java2D-Präsentationsschicht auf derselben Runtime. Sie liest unveränderliche <code>WorldSnapshot</code>-Daten, zeichnet Bewohner und einfache Weltobjekte und übersetzt Auswahl sowie manuelle Rechtsklick-Ziele in vorhandene Runtime-/Core-Befehle. Der Viewer startet über ein auswählbares <code>SimulationScenario</code>; Szenariowechsel und Reset erzeugen jeweils einen frischen Runtime-Zustand. Die Präsentationsschicht besitzt keine eigenen Gameplay-Regeln.

### hytale

Adapter zwischen Hytale-Konzepten und Core-Konzepten. Entitäten, NPCs, Weltzugriff, Navigation, Kamera, Eingabe, UI und Rendering gehören hierher.

Der aktuelle RTS-Validierungsprototyp sowie Farm- und Holzfäller-Slice enthalten bewusst kleine Hytale-nahe Komponenten:

- <code>RtsCameraController</code> setzt die feste schräge Cursor-Kamera und gibt die Kontrolle über Hytales nativen <code>CameraManager.resetCamera</code>-Lifecycle zurück. RTS-Modus schaltet nicht in den Spectator-Modus.
- <code>RtsInteractionController</code> verwaltet vorläufigen RTS-Eingabezustand pro Spieler. Die Auswahl ist bewusst auf eine Einheit begrenzt; auch Bau-Menü- und Platzierungszustand sind pro Spieler isoliert. Das Starten eines Gebäude-Upgrades wird hier in den bestehenden Baustellen-Lifecycle übersetzt; die fachliche Phasenfolge kommt aus dem Core-Katalog.
- <code>BuildingMenuPage</code>, <code>BuildingActionsPage</code>, <code>PersonActionsPage</code> und <code>WikiPage</code> verwenden Hytales <code>InteractiveCustomUIPage</code>-Ablauf für interaktive Civ-Menüs. Der RTS-Prototyp hängt bewusst nicht von einem nicht verifizierten Client-Anker für dauerhafte Buttons ab.
- <code>BuildingActionsPage</code> erhält Gebäudename, Phase, bereits abgeleitete Worker-Kapazität, nächste Phase, laufenden Upgrade-Zustand und die aktuell zugeordneten geladenen Bewohner vom Controller. Wegen eines verifizierten Clientfehlers mit dynamischem <code>appendInline</code> deklariert <code>CivBuildingActions.ui</code> die aktuell benötigten drei Worker-Slots sowie Upgrade- und Aktionscontrols statisch; Java setzt nur bekannte Properties und bindet Events. Ein Worker-Klick übernimmt nur die bestehende RTS-Auswahl; der anschließende Boden-Rechtsklick läuft unverändert durch den vorhandenen manuellen Bewegungsbefehl. Die UI besitzt damit keine eigene Arbeitsplatz-, Phasen- oder Bewegungsregel.
- Ein Rechtsklick auf den aktuell ausgewählten Civ-NPC öffnet <code>PersonActionsPage</code>. Die veraltete allgemeine Use/F-Interaktion wird nicht für RTS-Steuerung verwendet.
- <code>CivInhabitantData</code> ist eine serialisierbare Hytale-ECS-Komponente an Civ-Bewohner-Entitäten. Sie speichert Geschlecht, den konkret vergebenen dreiteiligen Namen, aktiven Beruf, getrennte Berufserfahrung und eine optionale persistente Arbeitsplatz-ID. Hytales native <code>UUIDComponent</code>-UUID bleibt die technische Entity-Identität.
- <code>CivInhabitantService</code> initialisiert diese persistenten Bewohnerdaten unabhängig vom RTS-Zustand, schreibt Arbeitsplatzänderungen in <code>CivInhabitantData</code> und setzt den sichtbaren Namen über Hytales native <code>PersistentDisplayName</code>, <code>DisplayNameComponent</code> und <code>Nameplate</code>. Beim ersten Claim entsteht die Wikinger-Identität. Beim expliziten Unclaim wird die Civ-Komponente entfernt und Hytales nativer `DisplayNameSupport` stellt wieder einen Rollen-Namen her.
- <code>CivUnitRegistry</code> bleibt ein laufzeitgebundener Cache für geladene Civ-Bewohner und den jeweils an Hytale adaptierten Bewegungszielwert. Ob eine Entität ein Civ-Bewohner ist, wird ausschließlich durch die persistente <code>CivInhabitantData</code>-Komponente bestimmt. Persistente Berufsdaten und Arbeitsplatzreferenzen werden über <code>CivInhabitantService</code> gelesen und geschrieben. <code>workersAt(buildingId)</code> projiziert für das Gebäude-Interface die aktuell geladenen Bewohner mit passender persistenter Arbeitsplatz-ID; es entsteht keine zweite Worker-Liste als konkurrierende Wahrheit. Für die Rolle <code>Civ_Inhabitant</code> wird das Bewegungsziel in den einzelnen nativen Positionsslot <code>CivMoveTarget</code> geschrieben; <code>ReadPosition</code> und <code>Seek</code> delegieren Wegfindung und Bewegung danach an Hytale.
- <code>CivActivityRegistry</code> verbindet geladene Hytale-Entitäten mit Hytale-unabhängigem <code>InhabitantActivity</code>-Core-Zustand. <code>CivManualMovementSystem</code> führt dessen <code>MovementIntent</code> über den nativen Bewegungszielslot aus und meldet Ankunft zurück. Der Core-Zustand entscheidet dadurch, dass ein manueller Spielerbefehl autonome Berufsarbeit vorübergehend verdrängt.
- <code>CivNameplateStatusSystem</code> ist reine Hytale-Präsentationslogik. Es liest den bereits autoritativen Bewohnernamen, Beruf, gemeinsamen Aktivitätszustand und vorhandenen nativen Bewegungszielzustand und schreibt daraus nur den Laufzeittext der nativen Hytale-<code>Nameplate</code> in der Form <code>Name · Status</code>. <code>PersistentDisplayName</code>, <code>DisplayNameComponent</code> und <code>CivInhabitantData</code> bleiben davon unberührt; der Status wird weder persistiert noch als paralleler Gameplay-Zustand geführt. <code>CivInhabitantStatusText</code> hält die kleine player-facing Textableitung testbar getrennt von der Engine-Komponente.
- <code>WoodcutterWorkSystem</code> interpretiert die Intents des Hytale-unabhängigen <code>WoodcutterJob</code>. Weltabhängige Baumsuche und Arbeitsposition, native NPC-Navigation sowie <code>BlockHarvestUtils.performBlockDamage</code> bleiben im Adapter; Zustandsfolge und Arbeitsdauer bleiben im Core. Die Baumsuche liest während des ECS-Ticks ausschließlich bereits geladene Chunks über <code>World.getChunkIfLoaded</code>; sie darf durch Blockabfragen keinen Chunk synchron laden, weil Chunk-Aktivierung den ECS-Store während laufender Systemverarbeitung verändern kann. Normale Drops, Break-Events und Blockphysik bleiben damit bei der Engine. Gültige Arbeitspositionen innerhalb des bestehenden Drei-Block-Radius werden zuerst nach horizontaler Nähe zu den unteren Stammblöcken und erst bei Gleichstand nach Entfernung vom Bewohner bewertet. Dafür betrachtet der Adapter nur den Stammfuß bis drei Blöcke darüber, damit große Kronen die Position nicht vom sichtbaren Stamm wegziehen und die Bewertung auch bei großen Bäumen begrenzt bleibt. Für die sichtbare Fällarbeit startet der Adapter beim Eintritt in die Chop-Phase einmal Hytales ItemPlayerAnimations-Set <code>Civ_Woodcutter_Axe</code> mit <code>SwingLeft</code> im Action-Slot und stoppt es beim Verlassen der Phase. Das Asset erbt von Hytales nativer <code>Axe</code>-Animationsfamilie und setzt nur diesen horizontalen Schlag auf Looping; einzelne Schläge werden nicht serverseitig pro Tick neu ausgelöst.
- <code>PrefabPlacementService</code> besitzt den Civ-Baustellen-Placement-Lifecycle selbst. Während der Mausbewegung erzeugt beziehungsweise verschiebt es eine native <code>PersistentPrefabPreview</code>-Entität am validierten Civ-Anker. Linksklick übernimmt diese Preview als Baustelle, ohne <code>BlockSelection.place</code> aufzurufen; Rechtsklick entfernt sie. Das Builder-Paste-Tool und <code>PrefabPasteEvent</code> sind nicht mehr Teil dieses Civ-Commit-Pfads. Für die aktuellen Creator-Prefabs liegt der logische Civ-Bauanker einen Block unter dem anvisierten Oberflächenblock. `groundSinkBlocks` wird bei der Erzeugung dieses terrain-relativen Civ-Ankers angewendet. `PrefabPlacementService` übersetzt diesen anschließend zentral in den von Hytales Prefab-APIs erwarteten Placement-Origin, weil `BlockSelection.place` den übergebenen Vektor zusätzlich mit dem internen Prefab-Anchor verrechnet. Preview, materialisierte Bau-Layer und finales natives Prefab verwenden dieselbe Übersetzung, damit ihre sichtbare Höhe übereinstimmt. Für Gebäude-Upgrades erzeugt derselbe Service eine Baustelle am unveränderten Anker und in derselben Orientierung des bestehenden Gebäudes; der Footprint wird aus der Zielphase neu bestimmt, während der ursprüngliche Terrain-Snapshot der Erstplatzierung für einen späteren Abriss erhalten bleibt. Im aktuellen Preview-Spike wird die alte blockweise Kollisions-/Terrainprüfung bewusst nicht vor dem Preview-Spawn ausgeführt: sie stammt aus dem Sofort-Paste-Pfad und verhindert auf eingesenkten Baustellenankern die isolierte Verifikation von <code>PersistentPrefabPreview</code>. Die Kollisionsregeln müssen nach erfolgreicher Runtime-Verifikation passend zu Baustellen neu eingeführt werden.
- <code>BuildingPlacementRegistry</code> ist der Laufzeitindex für Baustellenreservierungen und fertig platzierte Civ-Gebäude. Eine fertige <code>BuildingInstance</code> besitzt stabile Gebäude-ID, Gebäudetyp, Phase, Bounds, semantische Volumes und Placement-Snapshot. <code>workerCapacity()</code> wird aus Typ und Phase über den Core-Katalog abgeleitet und nicht separat persistiert. Ein laufender Upgrade-Zustand bleibt ebenfalls in diesem Registry: Das Gebäude bleibt für Picking sichtbar, wird über normale Gameplay-Lookups aber temporär als nicht verfügbar behandelt. So können bestehende Worker-Systeme während des Baus keinen Arbeitsplatz in der gesperrten Mine auflösen. Bei Fertigstellung ersetzt die nächste Phase die bestehende Instanz unter derselben Building-ID.
- <code>FarmFieldRegistry</code> registriert fertig gebaute Weizenfelder anhand ihres Civ-Footprints. Für den ersten Farm-Slice wird das zur Farm nächstgelegene fertige Feld automatisch als Arbeitsfeld verwendet.
- <code>ProfessionBootstrapInventory</code> bündelt vorläufige berufsgebundene NPC-Ausrüstung an der Hytale-Grenze. Farmer erhalten weiterhin vier native <code>Plant_Seeds_Wheat</code>-Seed-Bags über bestätigte Inventartransaktionen. Holzfäller erhalten die native Eisenaxt <code>Weapon_Axe_Iron</code> nur in einen vorhandenen passenden oder freien Hotbar-Slot; Hytales <code>InventoryHelper.useItem</code> setzt diesen Slot anschließend aktiv. Beim Berufswechsel oder Unclaim räumt derselbe Mechanismus das Bootstrap-Equipment des vorherigen Berufs wieder auf. Eine volle Hotbar wird nicht durch die Axt überschrieben.
- <code>FarmNpcWorkSystem</code> übersetzt die Core-Farmzustände in native Bewegungsziele und reale Hytale-Feldarbeit. Der Bauer delegiert das Pflanzen von <code>Plant_Seeds_Wheat</code> an <code>FarmPlantingService</code>, eine kleine Hytale-Grenze. Für die aktuell gepinnte Hytale-Runtime ist kein verifizierter nativer serverseitiger NPC-Pflanzpfad verfügbar; der Adapter meldet deshalb <code>UNSUPPORTED</code>, statt eine Spieler-Interaction zu simulieren oder Hytales Farming-Regeln nachzubauen. Reife Pflanzen werden weiterhin über Hytales native <code>FarmingUtil.harvest</code>-Funktion geerntet; Civ erzeugt keinen parallelen Wachstumstimer und keinen künstlichen Weizen-Output. Das ausgewählte Feld bleibt während des laufenden Saat-/Erntezyklus gebunden und wird nur neu gesucht, wenn es nicht mehr registriert ist.

<code>CivUnitRegistry</code> identifiziert eine Laufzeitentität über ihren <code>Store</code> plus Entitätsindex und behält gleichzeitig die ursprüngliche <code>Ref</code> zur Validierung. Dadurch wird nicht auf Java-Objektidentität wiederholt erzeugter <code>Ref</code>-Instanzen vertraut und veraltete Entitätsslots werden nicht als gültige Civ-Einheiten behandelt.

Bewohnerzugehörigkeit, Identität, aktiver Beruf, getrennte Berufserfahrung und eine optionale Arbeitsplatz-ID liegen in der serialisierbaren <code>CivInhabitantData</code>-Komponente auf der NPC-Entität und hängen nicht von einer RTS-Session ab. Persistente Änderungen markieren Hytales native <code>Dirty</code>-Komponente, damit die Entity-Saving-Pipeline sie schreibt. Auswahl und Arbeitsziele bleiben bewusst laufzeitgebunden. Manuelle RTS-Bewegungsziele liegen als <code>MovementIntent</code> im Hytale-unabhängigen <code>InhabitantActivity</code>-Zustand. Solange dieser Auftrag aktiv ist, erlaubt der Core keine autonome Berufsarbeit; nach gemeldeter Ankunft wird der Auftrag abgeschlossen und die bereits zugewiesene Berufsarbeit darf mit ihrem unveränderten Zustand fortfahren. Ein manueller Bewegungsbefehl löscht die persistente Arbeitsplatz-ID nicht.

Civ berechnet Bewegung nicht selbst pro Tick. Die vom Ersteller bearbeitbare Rolle <code>Civ_Inhabitant</code> definiert genau einen Positionsslot namens <code>CivMoveTarget</code>. Dieser liegt bewusst an Slot-Index 0 und bildet einen Java-/Asset-Vertrag, der durch einen automatisierten Asset-Test geschützt wird. <code>CivUnitRegistry</code> schreibt oder löscht diese gespeicherte Position über Hytales <code>MarkedEntitySupport</code>; die Rolle verarbeitet sie über <code>ReadPosition</code> und <code>Seek</code> mit Hytales nativer Wegfindung und Walk-Bewegung. <code>CivManualMovementSystem</code> und die Berufsadapter prüfen lediglich, ob das vom Core angeforderte Ziel erreicht wurde, damit sie den entsprechenden Abschluss an den Core zurückmelden können. Fremde Hytale-Rollen werden nicht über diesen Vertrag gesteuert.

Die feste RTS-Kamera ist eine Hytale-Custom-Kamera und kein Spectator-Modus. Eine verifizierte native API zum Ausblenden nur des eigenen Spielermodells in diesem Kameramodus wurde noch nicht gefunden. Deshalb wird die Spielerentität nicht über einen unbestätigten Workaround despawnt oder versteckt.

### plugin

Hytale-Bootstrap und Lifecycle. Hier werden Adapter und Services verdrahtet sowie Hytale-nahe Befehle und Systeme registriert. Spiellogik soll hier so wenig wie möglich liegen.

<code>CivilizationsPlugin</code> erledigt aktuell:

- Registrierung der serialisierbaren ECS-Komponente <code>CivInhabitantData</code>, bevor Civ-Register und Systeme verdrahtet werden.
- Registrierung der Tick-Systeme für Farm, Holzfäller und die laufzeitgebundene NPC-Nameplate-Statusdarstellung. Allgemeine Bewegung eines <code>Civ_Inhabitant</code> wird über natives <code>ReadPosition</code>/<code>Seek</code> erledigt und nicht durch ein eigenes Civ-Bewegungssystem.
- Bereitstellung der Befehle <code>/civtest</code>, <code>/civrtstest</code>, <code>/civclaim</code>, <code>/civfarm</code>, <code>/civbuild</code>, <code>/civwiki</code> und <code>/civdebug</code>. Der Debug-Unterbefehl <code>/civdebug path</code> delegiert ausschließlich an Hytales natives <code>RoleDebugFlags.VisPath</code>; Civ berechnet keinen parallelen Navigationspfad. <code>/civdebug status</code> liest ausschließlich die vom Nameplate-System bereits abgeleitete Runtime-Darstellung. Die Debug-Befehle verändern keine Core-Zustände.
- Verdrahtung von Mausbutton-, Mausbewegungs- und Disconnect-Events mit dem Interaction-Controller. In First Person wird ein mit <code>/civclaim</code> scharf geschalteter Linksklick auf einen NPC über Hytales <code>Damage</code>-ECS-Pipeline erkannt. <code>CivClaimDamageSystem</code> läuft in <code>DamageModule.getFilterDamageGroup()</code>, akzeptiert nur NPC-Ziele mit einem Spieler als <code>Damage.EntitySource</code>, verbraucht den scharf geschalteten Claim und bricht den Schaden vor der Anwendung ab. Strukturelle ECS-Änderungen des Claims werden dabei über den vom Damage-System gelieferten <code>CommandBuffer</code> gepuffert; direkte Store-Schreibzugriffe während der Systemverarbeitung sind verboten. Danach reicht es das NPC-Ziel an denselben Claim-Handler weiter, den RTS verwendet. First-Person-Rechtsklick/Benutzen läuft über Hytales <code>UseEntityEvent.Pre</code>. Da dieses Event auf dem handelnden Spieler dispatcht wird, verwendet <code>CivInhabitantUseSystem</code> bewusst <code>Archetype.empty()</code>, liest den Spieler aus der Event-Entity und das Ziel aus <code>event.getTargetEntity()</code>. Nur für einen beanspruchten Civ-Bewohner wird dieselbe <code>PersonActionsPage</code> wie im RTS geöffnet und das Use-Event anschließend abgebrochen. Civ überschreibt dafür keine globalen Unarmed-/Empty-Interaction-Assets. RTS-Auswahl, Bewegung, Bau und Wiki bleiben RTS-spezifische Bedienpfade.

## Abhängigkeitsregel

Abhängigkeiten zeigen in Richtung Core. <code>core</code> ist Hytale-unabhängig. <code>simulation</code> darf von <code>core</code> abhängen, aber nicht von der Hytale-API. <code>hytale</code> darf von <code>core</code> und der Hytale-API abhängen. <code>plugin</code> darf von beiden und der Hytale-API abhängen.

Dadurch bleibt der Großteil des Verhaltens in normalen JUnit-Tests ausführbar. Hytale wird nur dort benötigt, wo das Engine-Verhalten selbst geprüft wird.

### Headless Ablaufsteuerung

Mehrstufige Gameplay-Abläufe sollen als Core-Zustand plus kleine Commands, Intents und Ergebnisse modelliert werden, wenn dadurch eine echte Engine-Grenze entsteht. Ein Test darf Engine-Ergebnisse wie „angekommen“ künstlich zurückmelden und dadurch denselben Zustandsautomaten weitertreiben, den der Hytale-Adapter im Spiel bedient. <code>SimulationRuntime</code> ist der erste wiederverwendbare Harness für solche Abläufe. Er läuft standardmäßig mit einem festen 50-ms-Simulationsschritt und zählt fachliche Arbeit statt Wall-Clock-Zeit, damit zum Beispiel „wie oft sucht ein wartender Bauarbeiter nach einer Baustelle?“ reproduzierbar als Regression geprüft werden kann.

Der erste konkrete Beweisfall ist die Bewohnerbewegung mit Holzfällerarbeit:

~~~text
Spielerbefehl: manuelles Ziel
        ↓
Core: manueller Bewegungsauftrag hat Vorrang
        ↓ MovementIntent
Hytale: CivMoveTarget / ReadPosition / Seek
        ↓ Ankunft
Core: manuellen Auftrag abschließen
        ↓
vorherige autonome Berufsarbeit darf fortfahren

Holzfäller-Core
        ↓ SearchTreeIntent
Hytale: Welt nach geeignetem Baum + Arbeitsposition abfragen
        ↓ Ziel gefunden
Core
        ↓ MovementIntent
Hytale: native NPC-Navigation
        ↓ Ankunft
Core
        ↓ Chop/Fell-Intent
Hytale: nativer BlockHarvestUtils-Pfad
        ↓ Ergebnis
Core: nächster Arbeitszyklus
~~~

Die Baum- und Blockabfrage bleibt dabei Hytale-spezifisch, weil sie die reale Weltgeometrie und Blocktypen benötigt. Der Ablauf und seine Zustandsübergänge bleiben Core-Logik. Dasselbe Muster soll später für Bedürfnisse, Produktion, Transport und andere unterbrechbare Tätigkeiten wiederverwendet werden, ohne dafür vorab ein universelles Aktionsframework zu erfinden.

## Mehrspielerinteraktion und Welthoheit

Spielerbezogener vorläufiger Zustand wird nach Spieler-UUID getrennt. Auswahl, modale Bauinteraktion und aktive Platzierungsvorschauen dürfen niemals als ein globaler RTS-Zustand für alle Spieler gespeichert werden.

Die Vorschau ist nur beratende Client-UX. Jede Aktion, die gemeinsamen Weltzustand verändert, muss beim tatsächlichen Commit serverseitig erneut gegen die aktuelle Welt und das Gebäuderegister geprüft werden. Dadurch können nicht zwei Spieler erfolgreich überlappende Gebäude setzen, nur weil beide vorher eine gültige Vorschau gesehen haben.

Fertige Gebäudeinstanzen speichern ihre Civ-Metadaten in der serialisierten EntityStore-Resource `CivBuildingData`: stabile Gebäude-ID, Gebäudetyp, Phase, Placement-Anker, Footprint, Trigger-Volume-Referenzen und die ursprünglichen Weltblock-IDs jeder vom Prefab überschriebenen Position. <code>workerCapacity</code> wird nicht als redundanter Wert persistiert, sondern aus Typ und Phase abgeleitet. Ein Upgrade behält dieselbe stabile Building-ID und denselben ursprünglichen Terrain-Snapshot; nur Phase, aktuelle Bounds/semantische Volumes und Placement-Metadaten der fertigen Zielphase werden weitergeschrieben. Der laufende Upgrade-Zustand selbst ist derzeit Laufzeitzustand der Baustellen-/Registry-Schicht und kein eigener persistierter Zwischenzustand. Beim Weltbeitritt wird der Laufzeitindex aus den fertigen Gebäudedaten rekonstruiert; Farm- und Weizenfeld-Laufzeitdaten werden aus denselben gespeicherten Gebäude-IDs und semantischen Volumes erneut aufgebaut. Der Abriss entfernt die belegten Prefab-Blöcke und zugehörigen Trigger Volumes, stellt ausschließlich diese gespeicherten Positionen auf ihren Zustand vor dem Bau zurück, entfernt geladene Bewohner-Referenzen auf das abgerissene Gebäude und schreibt anschließend den aktualisierten Gebäudebestand zurück. Die Weltmutation arbeitet nur auf bereits geladenen Chunks und löst keine Chunk-Ladevorgänge aus.

## Aktueller Meilenstein

Der Bootstrap-Smoke-Test bleibt über <code>/civtest</code> verfügbar.

Der aktuelle Engine-Validierungs-Meilenstein prüft den ersten steuerbaren Civ-NPC-Ablauf:

1. In eine feste schräge Cursor-Kamera hinein- und wieder herauswechseln.
2. Einen vorhandenen <code>NPCEntity</code> ausdrücklich als vorläufige Civ-Testeinheit beanspruchen.
3. Eine beanspruchte Civ-Einheit per Linksklick als einzige Einheit auswählen.
4. Den ausgewählten NPC per Rechtsklick anklicken und dessen Aktionsmenü öffnen.
5. Den Beruf Holzfäller aus dem Menü zuweisen.
6. Den NPC einen Baum in der Nähe finden lassen, mit Hytales nativer Wegfindung und Bewegung daneben laufen lassen und ihn über Hytales nativen Block-Ernte-/Physik-Weg fällen lassen.
7. Per Rechtsklick auf den Boden der ausgewählten Einheit ein natives Bewegungsziel geben.
8. Ein fertiges Civ-Gebäude per Rechtsklick öffnen, seine phaseabhängige Worker-Anzeige sehen und einen zugeordneten Bewohner daraus in dieselbe RTS-Auswahl übernehmen.
9. Eine Mine aus dem normalen Baukatalog als Phase 1 errichten, anschließend im Gebäude-Interface auf Phase 2 und Phase 3 erweitern; zugeordnete Miner werden beim Upgrade evakuiert und erhalten während der Baustelle keinen auflösbaren Arbeitsplatz.

Nicht beanspruchte Tiere, Monster oder andere NPCs werden nicht allein deshalb steuerbar, weil sie <code>NPCEntity</code>-Instanzen sind. Der Debug-Befehl kann absichtlich jeden kompatiblen NPC zum Testen beanspruchen.

NPC-Spawning, dauerhafter Civ-Besitz, eigene Hindernis-Routenplanung, visuelle Auswahlmarkierungen, Drag-Auswahl, Zoom und Kamera-Panning gehören nicht zu diesem Meilenstein.

## Distributionsgrenze

Java-Laufzeitcode und vom Ersteller bearbeitbare Hytale-Assets werden getrennt innerhalb eines gemeinsamen Download-Archivs verteilt.

Repository-Struktur:

~~~text
src/main/...           Java-Plugin-Code und Plugin-Manifest
asset-pack/            eigenständiges bearbeitbares Hytale Asset Pack
~~~

Release-Struktur:

~~~text
hytale-civ-<release>.zip
├── hytale-civ.jar
└── hytale-civ-assets/
    └── manifest.json
~~~

Das äußere ZIP ist nur das herunterladbare Release-Bundle. Hytale erhält das Java-Plugin weiterhin als JAR und die Assets als eigenständigen Asset-Pack-Ordner. Dadurch bleiben Asset-Änderungen unabhängig vom Java-Build: Nach der Installation können Dateien in <code>hytale-civ-assets/</code> geändert werden, ohne das Plugin-JAR neu zu bauen.

Gameplay-Daten sollen bevorzugt Hytales vorhandene native Asset-, ECS-, Interaktions-, Inventar- und Persistenzmechanismen verwenden, wenn deren Semantik zum Feature passt. Civ soll keinen parallelen Speicher-, Navigations-, Interaktions- oder Inventarmechanismus einführen, nur weil ein Java-Modell einfacher erscheint. Eigener Core-Code bleibt für Civ-spezifische Regeln und für Engine-Lücken zuständig; vor einer solchen Implementierung wird der native Hytale-Pfad anhand der projektgebundenen Server-JAR geprüft.

### Native Gebäudezustände und Inventare

Die direkte Untersuchung der projektgebundenen `HytaleServer.jar` bestätigt für die aktuelle Serverversion folgende Engine-Bausteine:

- Hytale besitzt persistierbare Block-Entitäten im `ChunkStore`. `BlockType` kann einen `BlockEntity`-Holder tragen.
- `ItemContainerBlock` ist eine serialisierbare `ChunkStore`-Komponente mit eigenem `SimpleItemContainer`, konfigurierbarer `Capacity` und optionaler `Droplist`. Sein Codec serialisiert den Containerinhalt selbst.
- `ItemContainerSystems` bindet Containeränderungen an den Blockzustand. Das Entfernen wegen `UNLOAD` wird ausdrücklich anders behandelt als ein tatsächliches Entfernen des Blocks. Damit ist ein nativer Containerblock der bevorzugte Kandidat für lokale, physische Gebäudewaren.
- `OpenContainerInteraction` öffnet Hytales normale Containeroberfläche für einen Containerblock. `BlockType` besitzt native Interaction-Zuordnungen.
- Hytale stellt darüber hinaus `PersistentRef`, `PersistentMetaKey`, Entity-`UUID`-Auflösung und codec-basierte ECS-Komponenten bereit. Civ verwendet für Gebäude jedoch bewusst eine eigene stabile Gebäude-ID in `CivBuildingData`, weil sie bereits den persistenten Gebäude-Lifecycle, Abriss-Snapshot und Bewohner-Arbeitsplatzreferenzen verbindet. Diese Domänenidentität ersetzt keine nativen Hytale-Entity- oder Container-Identitäten.
- Trigger Volumes sind native Logikträger und nicht nur Civ-Marker. Die aktuelle Serverversion bietet Volume-Ereignisse wie ENTER, EXIT, TICK und SIGNAL_RECEIVED sowie Conditions, Cooldowns und Effects. Dazu gehören unter anderem Signale, Interactions, Item-/Block-/Prefab-Effekte und NPC-Marker. Civ soll solche nativen Mechanismen bevorzugen, bevor äquivalente Java-Ticklogik gebaut wird.
- Für die Evakuierung geladener Minenarbeiter beim Upgrade bestätigt die gepinnte JAR die native ECS-Komponente <code>Teleport</code> mit Zielposition und Rotation. Civ nutzt diesen Engine-Pfad statt Positionsdaten direkt zu manipulieren. Eine allgemeine native temporäre No-Go-Zone für einen beliebigen Gebäudeinnenraum ist dagegen nicht verifiziert und wird deshalb nicht erfunden.

Konsequenz für Gebäude: räumliche Bedeutung und Engine-Verhalten sollen möglichst im Prefab bzw. in nativen Hytale-Assets liegen; physische Waren sollen möglichst in nativen Hytale-Containern liegen. Civ-Code soll primär die Civ-spezifische Entscheidungsschicht verbinden. Welche konkreten Trigger-, Marker- und Containerkonfigurationen ein Gebäudetyp benötigt, wird pro Vertical Slice verifiziert und nicht vorab als allgemeines Gebäudeframework erfunden.

## Farm-Produktions-Vertical-Slice

Das erste echte Produktionsfeature bleibt bewusst konkret und führt noch kein spekulatives allgemeines Gebäudeframework ein.

Der derzeit implementierte Farm-Ablauf ist ein Engine-Validierungsprototyp und **kein beschlossenes Zielmodell der Farmproduktion**. Insbesondere die im aktuellen Code enthaltenen Werte und Abläufe „5 Sekunden innen“, „+1 abstrakter Weizen“, „bei 10 stoppen“ und das feste Außenziel zwei Blöcke südlich dürfen nicht allein aus ihrer Implementierung als dauerhafte Produktregel abgeleitet werden. Die nächste Farm-Iteration soll zuerst prüfen, welche räumlichen Arbeitsabläufe, Trigger, NPC-Aktionen und lokalen Warenbestände direkt durch Hytale-Assets, Trigger Volumes und native Container ausgedrückt werden können.

~~~text
Farm-Prefab im Asset Pack
        ↓
RTS-Menü → modaler Katalog → Platzierungsvorschau pro Spieler
        ↓
FarmPrefabService validiert Gelände, versenkt den Boden um einen Block und platziert die Farm in Hytale
        ↓
FarmBuildingRegistry erzeugt ein Core-FarmBuilding
        ↓
ein beanspruchter NPC erhält Profession.FARMER
        ↓
FarmNpcWorkSystem steuert:
Eingang → 5 s innen → +1 lokaler Weizen → Ausgang → Wiederholung
        ↓
bei 10 lokalem Weizen draußen stoppen
~~~

Der Arbeitszugang der Farm wird direkt im Prefab über mindestens ein natives Hytale Trigger Volume mit den Tags <code>civ.type=workplace_access</code> und <code>civ.building=farm</code> definiert. Hytale wandelt die Prefab-Transportentität beim Einfügen in einen Laufzeit-<code>VolumeEntry</code> um. Civ löst das neu registrierte markierte Volume auf und leitet daraus das Laufziel ab. Der veraltete Markerblock <code>Civ_BuildingEntrance</code> wird nicht mehr verwendet.

Ein Farm-Prefab muss mindestens ein <code>workplace_access</code>-Trigger-Volume für <code>farm</code> registrieren. Mehrere passende Volumes werden unterstützt. Für die aktuelle Farm mit genau einem Bauern wählt das Register den Arbeitsplatz mit der geringsten Luftlinienentfernung zum zugewiesenen NPC. Danach übernimmt dessen normaler Hytale-Bewegungscontroller den Weg. Die Auswahl berücksichtigt noch keine tatsächlichen Pfadkosten.

Der NPC bleibt eine normale Hytale-Entität. „Innen“ ist aktuell ein Simulationszustand, der erreicht wird, wenn seine Position das ausgewählte Eingangsziel erreicht. Der Prototyp versteckt, despawnt oder teleportiert den NPC während der Arbeit nicht.

Das Farm-Prefab ist unter <code>asset-pack/Server/Prefabs/Civilizations/Farm/Farm_01.prefab.json</code> bearbeitbar. Der Prefab-Anker dient nur der Platzierungsmetadaten und bestimmt nicht länger den Eingang. Während der RTS-Platzierung gilt die angeklickte Geländeoberfläche als fertige Bodenhöhe. Deshalb wird der Prefab-Anker einen Block nach unten verschoben und der Prefab-Boden ersetzt diese Geländeschicht. Die ersetzten Block-IDs bleiben an der platzierten Farm gespeichert, damit sie bei einem späteren Abriss wiederhergestellt werden können. Das aktuelle Außenziel liegt weiterhin zwei Blöcke südlich des gewählten Eingangs, da die Farm-Ausrichtung noch fest ist. Rotationsabhängige Richtungsmetadaten werden erst mit drehbarer Gebäudeplatzierung eingeführt.

### Ingame-Wiki

<code>/civwiki</code> öffnet das Ingame-Wiki im aktiven RTS-Modus. Der Befehl bleibt der aktuell verifizierte Einstiegspunkt, bis ein natives interaktives HUD- oder Hotkey-Verfahren bestätigt ist.

<code>WikiPage</code> ist eine Hytale-nahe <code>InteractiveCustomUIPage</code> und bleibt außerhalb der Core-Simulation. Die UI-Layouts liegen im bearbeitbaren Asset Pack unter <code>Common/UI/Custom/Pages/CivWiki*.ui</code>. Navigation ersetzt die aktuelle Custom Page über Hytales nativen Page Manager durch eine andere Wiki-Seite. Der Inhalt ist bewusst auf bereits umgesetztes Verhalten begrenzt, damit das Hilfesystem keine spekulative zweite Quelle für Domänenregeln wird.

### Native ghost / Civ click-cancel spike

Runtime diagnostics showed that the RTS camera does not emit generic `PlayerMouseMotionEvent` updates while the cursor moves, so a server-driven moving `PersistentPrefabPreview` cannot use that event as its position source. The current focused spike therefore delegates only the moving ghost to Hytale's native Paste tool. Civ still owns the normal mouse-button event: on confirmation it cancels that event and creates a `PersistentPrefabPreview` construction site from the click's `targetBlock` instead of intentionally invoking `BlockSelection.place`. The clipboard selection anchor is shifted upward by the configured sink amount so the native ghost itself renders the prefab one block lower. Whether cancelling the normal mouse event also suppresses the Builder tool's separate paste packet remains a runtime contract to verify.

### Construction blueprint lifecycle

The placed construction blueprint uses Hytale's `PersistentPrefabPreview` only as a whole-prefab pre-construction visualization. JAR inspection confirms that this component supports whole-entity removal and visible-layer-count updates, but not per-block ghost removal. Civ therefore owns the preview entity reference and removes it through `PersistentPrefabPreview.remove(...)` when the site is cancelled, when its owner disconnects, or before progressive real-block construction begins. Trigger volumes remain a completion concern; Hytale's trigger-volume prefab handlers materialize them during real prefab placement rather than as part of the preview entity.

### Progressive Construction

`ConstructionJob` bildet den Hytale-unabhängigen Ablauf eines Bauarbeiters ab: Baustelle suchen, zum Arbeitspunkt laufen, zeitgesteuerte Bauschritte ausführen und die Fertigstellung melden. Die Anzahl der Schritte wird vom Adapter aus den tatsächlich belegten Y-Ebenen des Prefabs geliefert; der Core kennt keine Hytale-Prefab- oder Blocktypen.

`ConstructionWorkSystem` reserviert pro Baustelle höchstens einen geladenen Bauarbeiter, wählt einen freien Arbeitspunkt außerhalb des Footprints und übersetzt die Core-Intents in vorhandene Engine-Funktionen. Bewegung läuft weiterhin über `CivMoveTarget` / `ReadPosition` / `Seek`. Sichtbare Arbeit verwendet Hytales `AnimationUtils` im Action-Slot. Für die Runtime-Diagnose verwendet Construction v1 vorläufig das im Klops-Model vorhandene AnimationSet `Alerted`; die eigentliche Bau-/Hammeranimation bleibt austauschbar. Der Adapter startet die Animation unmittelbar beim Eintritt in `BUILDING`, während die Blockmaterialisierung unabhängig davon weiterläuft.

Die projektgebundene `HytaleServer.jar` bestätigt für diesen Slice `PersistentPrefabPreview.updateLayers(...)`, `BlockSelection.forEachBlock(...)`, blockhaltende Teil-`BlockSelection`-Instanzen, `BlockSelection.placeNoReturn(...)` sowie `AnimationUtils.playAnimation(...)` und `stopAnimation(...)`. Es wurde keine native Construction-Site-Queue oder native Prefab-Baureihenfolge gefunden. Deshalb bleibt nur die Civ-spezifische Reservierungs- und Reihenfolgeentscheidung eigener Code.

`PrefabPlacementService` materialisiert während des Baus ausschließlich Block-Ebenen ohne Prefab-Entities. Beim ersten realen Bauschritt wird die `PersistentPrefabPreview` entfernt. Nach der letzten Ebene wird der bestehende vollständige native Prefab-Placement-Pfad einmal ausgeführt, damit enthaltene Entities und Trigger Volumes korrekt von Hytale erzeugt werden. Eine Farm wird erst danach im `FarmBuildingRegistry` registriert. Gebäude-Upgrades verwenden genau denselben Construction-v1-Pfad; sie besitzen keinen zweiten Sonder-Baualgorithmus.

### Allgemeine Civ-Gebäudegrenze

Fertige Civ-Gebäude definieren ihre autoritative räumliche Gebäudezone im Prefab über ein natives Hytale Trigger Volume mit `civ.type=building_bounds`. Der Gebäudetyp bleibt über `civ.building=<type>` getaggt. Creator zeichnen und taggen diese Zone direkt mit Hytales Trigger Volume Tool; Civ berechnet die fertige Gebäudegrenze nicht aus den sichtbaren Prefab-Blöcken.

Während der Bauphase reserviert `BuildingPlacementRegistry` weiterhin den blockbasierten Placement-Footprint der Baustelle. Nach dem finalen nativen Prefab-Placement liest `PrefabPlacementService` die Welt-AABB der neu erzeugten Trigger Volumes über Hytales `TriggerVolumeShape.getWorldAABB(...)`. Ist ein `building_bounds` vorhanden, ersetzt diese authored Zone die temporäre Baustellenreservierung und wird zur Runtime-Autorität für allgemeines Gebäude-Picking, Placement-Overlap und Blockschutz.

Weitere Trigger Volumes wie `workplace_access` und `output_storage` bleiben semantische Unterbereiche derselben platzierten Prefab-Instanz. Sie definieren Funktionen, nicht die allgemeine Gebäudegrenze. Das separat platzierte Weizenfeld verwendet seinen `field`-Marker zugleich als Lifecycle-/Schutzgrenze. Der gemeinsame Blockschutz bleibt für sämtliche direkten Spieler-Abbauversuche sowie Boden- und Fremdblockplatzierung aktiv. Innerhalb eines fertigen Weizenfelds ist ausschließlich native Weizen-Saatgut-Platzierung oberhalb des Feldbodens vom Placement-Schutz ausgenommen; Ernteausnahmen werden nicht über den allgemeinen Break-Schutz modelliert. Der Schutz greift ausschließlich bei Spielerentitäten (`PlayerRef`); NPC-Placement wird dadurch nicht blockiert.

Die Gebäudeinstanz-ID verwendet für einen Neubau die UUID der zugehörigen Baustelle als stabile Civ-Gebäude-ID. Bei einem Upgrade bleibt die bestehende ID ausdrücklich erhalten. Sie wird zusammen mit Gebäudetyp und Phase in `CivBuildingData` persistiert und ist die Referenz, die ein Bewohner optional als Arbeitsplatz-ID speichert.

### Gemeinsamer Produktionszyklus

`ProductionRecipe` beschreibt Hytale-unabhängig benötigte Inputs, erzeugte Outputs und eine Grundarbeitszeit. `ProductionJob` besitzt den gemeinsamen semantischen Ablauf vom Arbeitsplatz über Input-Bereitschaft und Arbeitsort bis zum Rücktransport und Einlagern des Outputs. Berufs- oder Erfahrungsunterschiede verändern die Arbeitsgeschwindigkeit über einen Multiplikator, ohne den Ablauf zu duplizieren.

Die Quelle benötigter Inputs gehört ausdrücklich nicht in `ProductionJob`. Eine spätere Goods-/Logistics-Schicht darf Waren aus Arbeitsplatzcontainern, anderen Gebäuden oder Welt-Drops wählen und reservieren. Der Hytale-Adapter führt Bewegung, Weltaktionen und native Containertransaktionen aus.

Die Farm verwendet diesen Produktionskern als ersten konkreten Adapter. Ihr `output_storage`-Marker lokalisiert den bereits im Prefab enthaltenen nativen `ItemContainerBlock`. Nach fünf Sekunden Feldarbeit trägt der Bauer semantisch eine Einheit Output zurück; erst eine erfolgreiche native `ItemStack`-Einlagerung schließt den Zyklus ab. Ein voller oder nicht geladener Container erzeugt keinen parallelen Civ-Bestand.

### Building identity and demolition snapshots

Every completed Civ placeable that participates in protection/persistence/demolition owns an authored `civ.type=building_bounds` volume with its own `civ.building` type. Gameplay markers such as a wheat field's `civ.type=field` are separate semantic volumes of that building. Terrain snapshots persist stable Hytale block asset keys rather than runtime numeric block indices so demolition remains valid across server restarts and asset-index changes. The persisted Civ building record also carries the stable building ID and phase; type-and-phase metadata such as worker capacity is derived rather than duplicated in persistence. Upgrades preserve the original terrain snapshot while replacing the completed phase metadata under the same building ID.

### Semantic arrival via native TriggerVolumes

Farm production uses Hytale's native `TriggerVolumeEvent ENTER` events as the primary signal that an assigned NPC reached authored semantic work areas such as `workplace_access`, `field`, and `output_storage`. Runtime registries retain the placed trigger-volume IDs so an ENTER event can be matched to the worker's currently intended semantic target. Navigation still belongs to the Hytale adapter; Core only advances the production phase after the matching semantic arrival. Distance checks remain a defensive fallback rather than the primary arrival contract.

### NPC-Inventaransicht

Das Personenaktionsmenü kann das tatsächliche Hytale-Inventar eines beanspruchten Civ-Bewohners schreibgeschützt öffnen. Der Hytale-Adapter verwendet dafür Hytales native `InventoryComponent.getCombined(..., HOTBAR_FIRST)`-Zusammenstellung und zeigt sie in einem nativen `ContainerWindow`. Wie bei Hytales eigenem `invsee`-Pfad liegt ein `DelegateItemContainer` mit `DENY_ALL` vor dem echten Container, sodass diese Ansicht keine Civ-Inventarregeln dupliziert und keine Items verändert.

### Native Farming Interactions

The farmer keeps the Civ-level work cycle, field selection and inventory policy, but does not recreate Hytale's seed placement. For wheat sowing, the Hytale adapter resolves the active item's native Secondary interaction through InteractionContext and InteractionManager, supplies the selected tilled-soil block as the interaction target, and lets Hytale execute the configured seed interaction chain. This preserves Hytale's Seed_Condition/Seed_Place behavior and its item consumption rules instead of duplicating them in Civ. The Civ farm currently exposes only Plant_Seeds_Wheat; additional crop types are a future Civ data decision, not a replacement for Hytale's native placement mechanism.

## Mine work fronts and manual exit

`MinerWorkSystem` executes the deterministic Layer-4 mine plan directly. It regenerates all planned main/branch `MineTunnelGeometry` objects from the stable mine ID, creates or resumes one persistent `MineWorkFront` per planned tunnel and executes one Layer-3 slice at a time. The Hytale world remains authoritative for which planned blocks are already empty; the persisted front position/state is the semantic restart progress.

`MineFrontCoordinator` remains Hytale-independent short-lived coordination for one shared front. A normal tunnel front has capacity two; each worker receives only a claim on one still-open block, so two miners can make real parallel progress without permanent left/right worker slots. Front membership and block claims are runtime coordination rather than persisted team composition. After every completed slice the coordinator releases that front and workers run the Core `MineFrontTaskScheduler` again. The scheduler fills already active normal work first; otherwise it opens Branch priority 6 before Main priority 4 and then uses distance/stable tie-breaking. Rooms, supports, lights, steps, bridges, decoration, priority 10 and aging remain outside this current tunnel-only scheduler.

`MineTunnelRegistry` also holds the deterministically regenerated Layer-3 geometry as runtime-only data keyed by world/mine/tunnel. `MinerNavigationSystem` and `MinerSurfaceRecoverySystem` use this geometry for semantic tunnel membership. This cache is not persisted and is not a second excavation truth: actual block state stays in Hytale, while trusted navigation anchors still require observed traversal of exact `BlockType.EMPTY` positions.

Manual movement remains a Core `MovementIntent`. When the assigned miner is below its mine access, the Hytale adapter adds the native `Teleport` ECS component to move it to `workplace_access`, keeps the original manual intent active, and lets native `ReadPosition`/`Seek` handle the clicked destination from there. Autonomous front membership is released during the interruption. On resume, the miner stages through access and connector again and selects from the then-current executable fronts rather than restoring a hard per-miner task pointer.

Beim Start eines Minen-Upgrades verwendet derselbe verifizierte native `Teleport`-Pfad einen sicheren Punkt außerhalb des Gebäudes. Danach bleibt die persistente Arbeitsplatz-ID bestehen, aber das Registry liefert die upgrading Building-ID bis zur Fertigstellung nicht an normale Gameplay-Lookups aus. Damit erzeugt der Minenadapter während der Baustelle keine autonomen Re-Entry-Ziele. Eine allgemeine physische Player-/Entity-Barriere wird mangels verifizierter nativer API derzeit nicht simuliert.

## Browser-Simulation-Viewer

`simulation.recording.SimulationRecordingExporter` führt die bestehenden Core-Fixtures headless aus. Der versionierte `SimulationRecording`-Vertrag enthält Startvoxels, geordnete Weltänderungen, semantische Marker, Arbeiterzustände und Metrics. Er enthält keine Hytale-Objekte. Die Mine wird in vier Ausrichtungen bis zum Abschluss aufgezeichnet; die allgemeinen Szenarien verwenden jeweils 600 feste 50-ms-Ticks. Eine Mine-Framezeit bezeichnet semantische Schritte, keine Hytale-Laufzeit.

`web-viewer/` ist eine eigenständige Vite-/Three.js-Präsentation außerhalb des Plugin-Classpaths. `Replay` rekonstruiert ausschließlich die aufgezeichneten Änderungen. Der Renderer verwendet gerichtete solid/air-Grenzflächen für die freie Spectator-Kamera. Kamera und Playback besitzen keine Gameplay-Regeln. Die Swing-Viewer bleiben als lokale Entwicklungshilfen verfügbar.

Die Actions-/Pages-Grenze und die begrenzte Aufbewahrung sind in [ADR 0010](decisions/0010-browser-simulation-recordings.md) beschrieben. Der veröffentlichte Viewer stammt aus `main`; Branch-Aufzeichnungen identifizieren ihren exakten Quellcode-Commit und Run-Attempt. Der Browser benötigt keine GitHub-Zugangsdaten.
