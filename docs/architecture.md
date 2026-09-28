# Architektur

## Ziel

Die Simulation soll testbar bleiben, ohne Hytale starten zu müssen. Hytale ist eine Integrationsgrenze und nicht das Domänenmodell.

Vor Version 1 ist Rückwärtskompatibilität kein Ziel, wenn dafür Migrationen, parallele Altpfade, Kompatibilitäts-Defaults oder featurespezifische Ausnahmen nötig wären. Die aktuell dokumentierte Architektur und das Datenmodell sind maßgeblich. Diese Regel muss neu bewertet werden, bevor persistente Spielerwelten oder öffentliche stabile Releases Kompatibilität zu einer Produktanforderung machen.

## Schichten

~~~text
Spielerinput / UI
      ↓ Command
Core-Simulation
      ↓ Intent
Hytale-Adapter
      ↓
Hytale-Plugin / API
      ↓ Result / Event
Core-Simulation
~~~

Die Grenze ist verhaltensorientiert: UI und Hytale-Code übersetzen Eingaben und führen Engine-Arbeit aus, besitzen aber keine Civ-Spielregeln. Der Core entscheidet über Zustandswechsel, Prioritäten und Unterbrechungen. Ein Core-Intent beschreibt nur das gewünschte Ergebnis, zum Beispiel „Bewohner soll zu Ziel X laufen“; der Adapter setzt das mit Hytales nativer Navigation um und meldet Ankunft beziehungsweise Fehlschlag zurück.

Bewegung ist deshalb zweigeteilt. **Wer wann wohin und warum läuft** gehört zur Civ-Simulation. **Wie der NPC den Weg findet und physisch zurücklegt** bleibt Hytale überlassen. Civ baut keinen parallelen Wegfindungsalgorithmus, solange Hytales Navigation die Produktanforderung erfüllt.

### core

Reine Java-Simulation und Domänenregeln. Dieser Bereich darf <code>com.hypixel.hytale.*</code> nicht importieren. Bewohner, Berufe, Bedürfnisse, Inventare, Waren, Produktion, Gebäudestatus, Befehle, Wirtschaft und Simulations-Ticks gehören hierher. Der aktuell umgesetzte Berufszustand umfasst die Hytale-unabhängigen Typen <code>FarmBuilding</code>, <code>WoodcutterJob</code>, <code>BlockPosition</code> und <code>Profession</code>.

### hytale

Adapter zwischen Hytale-Konzepten und Core-Konzepten. Entitäten, NPCs, Weltzugriff, Navigation, Kamera, Eingabe, UI und Rendering gehören hierher.

Der aktuelle RTS-Validierungsprototyp sowie Farm- und Holzfäller-Slice enthalten bewusst kleine Hytale-nahe Komponenten:

- <code>RtsCameraController</code> setzt die feste schräge Cursor-Kamera und gibt die Kontrolle über Hytales nativen <code>CameraManager.resetCamera</code>-Lifecycle zurück. RTS-Modus schaltet nicht in den Spectator-Modus.
- <code>RtsInteractionController</code> verwaltet vorläufigen RTS-Eingabezustand pro Spieler. Die Auswahl ist bewusst auf eine Einheit begrenzt; auch Bau-Menü- und Platzierungszustand sind pro Spieler isoliert.
- <code>BuildingMenuPage</code>, <code>PersonActionsPage</code> und <code>WikiPage</code> verwenden Hytales <code>InteractiveCustomUIPage</code>-Ablauf für interaktive Civ-Menüs. Der RTS-Prototyp hängt bewusst nicht von einem nicht verifizierten Client-Anker für dauerhafte Buttons ab.
- Ein Rechtsklick auf den aktuell ausgewählten Civ-NPC öffnet <code>PersonActionsPage</code>. Die veraltete allgemeine Use/F-Interaktion wird nicht für RTS-Steuerung verwendet.
- <code>CivInhabitantData</code> ist eine serialisierbare Hytale-ECS-Komponente an Civ-Bewohner-Entitäten. Sie speichert Geschlecht, den konkret vergebenen dreiteiligen Namen, aktiven Beruf, getrennte Berufserfahrung und eine optionale zukünftige Arbeitsplatz-ID. Hytales native <code>UUIDComponent</code>-UUID bleibt die technische Entity-Identität.
- <code>CivInhabitantService</code> initialisiert diese persistenten Bewohnerdaten unabhängig vom RTS-Zustand und setzt den sichtbaren Namen über Hytales native <code>PersistentDisplayName</code>, <code>DisplayNameComponent</code> und <code>Nameplate</code>. Beim ersten Claim entsteht die Wikinger-Identität. Beim expliziten Unclaim wird die Civ-Komponente entfernt und Hytales nativer `DisplayNameSupport` stellt wieder einen Rollen-Namen her.
- <code>CivUnitRegistry</code> bleibt ein laufzeitgebundener Cache für geladene Civ-Bewohner und den jeweils an Hytale adaptierten Bewegungszielwert. Ob eine Entität ein Civ-Bewohner ist, wird ausschließlich durch die persistente <code>CivInhabitantData</code>-Komponente bestimmt. Persistente Berufsdaten werden über <code>CivInhabitantService</code> gelesen und geschrieben. Für die Rolle <code>Civ_Inhabitant</code> wird das Bewegungsziel in den einzelnen nativen Positionsslot <code>CivMoveTarget</code> geschrieben; <code>ReadPosition</code> und <code>Seek</code> delegieren Wegfindung und Bewegung danach an Hytale.
- <code>CivActivityRegistry</code> verbindet geladene Hytale-Entitäten mit Hytale-unabhängigem <code>InhabitantActivity</code>-Core-Zustand. <code>CivManualMovementSystem</code> führt dessen <code>MovementIntent</code> über den nativen Bewegungszielslot aus und meldet Ankunft zurück. Der Core-Zustand entscheidet dadurch, dass ein manueller Spielerbefehl autonome Berufsarbeit vorübergehend verdrängt.
- <code>WoodcutterWorkSystem</code> interpretiert die Intents des Hytale-unabhängigen <code>WoodcutterJob</code>. Weltabhängige Baumsuche und Arbeitsposition, native NPC-Navigation sowie <code>BlockHarvestUtils.performBlockDamage</code> bleiben im Adapter; Zustandsfolge und Arbeitsdauer bleiben im Core. Normale Drops, Break-Events und Blockphysik bleiben damit bei der Engine.
- <code>PrefabPlacementService</code> lädt Civ-Prefabs in Hytales Builder-Clipboard und aktiviert weiterhin das native <code>EditorTool_Paste</code> für die funktionierende Cursor-Ghost-Vorschau. Für eine scharf geschaltete Civ-Platzierung fängt <code>CivConstructionPlacementSystem</code> Hytales abbrechbares <code>PrefabPasteEvent</code> am Paste-Start ab, bevor <code>BlockSelection.place</code> die Welt verändert. <code>BuilderState.paste</code> setzt unmittelbar vor diesem Event eine temporäre neue Prefab-ID und die Cursorposition auf die aktive Selection; der Adapter identifiziert genau diesen Commit über diese temporäre ID. Der normale Paste wird abgebrochen und <code>PrefabPlacementService</code> erzeugt stattdessen eine native <code>PersistentPrefabPreview</code>-Entität als Baustellenvisualisierung. Die Baustellenposition liegt für die aktuellen Creator-Prefabs einen Block unter der vom Paste Tool gemeldeten Position.
- <code>BuildingPlacementRegistry</code> bleibt der bisherige Laufzeitmechanismus für bereits fertig platzierte Civ-Bauflächen. Die neue Baustellen-Platzierung ist zunächst ein separater Engine-Validierungsschritt: sie erzeugt noch kein fertiges Farmgebäude und registriert deshalb noch keine fertige <code>FarmBuilding</code>-Instanz.
- <code>FarmNpcWorkSystem</code> übersetzt die Core-Farmzustände in Bewegungsziele für Eingang und Ausgang und treibt die Produktion voran, solange der Bauer innen arbeitet.

<code>CivUnitRegistry</code> identifiziert eine Laufzeitentität über ihren <code>Store</code> plus Entitätsindex und behält gleichzeitig die ursprüngliche <code>Ref</code> zur Validierung. Dadurch wird nicht auf Java-Objektidentität wiederholt erzeugter <code>Ref</code>-Instanzen vertraut und veraltete Entitätsslots werden nicht als gültige Civ-Einheiten behandelt.

Bewohnerzugehörigkeit, Identität, aktiver Beruf und getrennte Berufserfahrung liegen in der serialisierbaren <code>CivInhabitantData</code>-Komponente auf der NPC-Entität und hängen nicht von einer RTS-Session ab. Persistente Änderungen markieren Hytales native <code>Dirty</code>-Komponente, damit die Entity-Saving-Pipeline sie schreibt. Auswahl und Arbeitsziele bleiben bewusst laufzeitgebunden. Manuelle RTS-Bewegungsziele liegen als <code>MovementIntent</code> im Hytale-unabhängigen <code>InhabitantActivity</code>-Zustand. Solange dieser Auftrag aktiv ist, erlaubt der Core keine autonome Berufsarbeit; nach gemeldeter Ankunft wird der Auftrag abgeschlossen und Farm- beziehungsweise Holzfällerarbeit darf mit ihrem unveränderten Zustand fortfahren. Arbeitsplatzzuweisung bleibt laufzeitgebunden, weil platzierte Gebäude noch keine stabile dauerhafte Identität besitzen. Ein späterer Bewohner-Lifecycle soll den Debug-Anspruchsmechanismus ersetzen.

Civ berechnet Bewegung nicht selbst pro Tick. Die vom Ersteller bearbeitbare Rolle <code>Civ_Inhabitant</code> definiert genau einen Positionsslot namens <code>CivMoveTarget</code>. Dieser liegt bewusst an Slot-Index 0 und bildet einen Java-/Asset-Vertrag, der durch einen automatisierten Asset-Test geschützt wird. <code>CivUnitRegistry</code> schreibt oder löscht diese gespeicherte Position über Hytales <code>MarkedEntitySupport</code>; die Rolle verarbeitet sie über <code>ReadPosition</code> und <code>Seek</code> mit Hytales nativer Wegfindung und Walk-Bewegung. <code>CivManualMovementSystem</code> und die Berufsadapter prüfen lediglich, ob das vom Core angeforderte Ziel erreicht wurde, damit sie den entsprechenden Abschluss an den Core zurückmelden können. Fremde Hytale-Rollen werden nicht über diesen Vertrag gesteuert.

Die feste RTS-Kamera ist eine Hytale-Custom-Kamera und kein Spectator-Modus. Eine verifizierte native API zum Ausblenden nur des eigenen Spielermodells in diesem Kameramodus wurde noch nicht gefunden. Deshalb wird die Spielerentität nicht über einen unbestätigten Workaround despawnt oder versteckt.

### plugin

Hytale-Bootstrap und Lifecycle. Hier werden Adapter und Services verdrahtet sowie Hytale-nahe Befehle und Systeme registriert. Spiellogik soll hier so wenig wie möglich liegen.

<code>CivilizationsPlugin</code> erledigt aktuell:

- Registrierung der serialisierbaren ECS-Komponente <code>CivInhabitantData</code>, bevor Civ-Register und Systeme verdrahtet werden.
- Registrierung der Tick-Systeme für Farm und Holzfäller. Allgemeine Bewegung eines <code>Civ_Inhabitant</code> wird über natives <code>ReadPosition</code>/<code>Seek</code> erledigt und nicht durch ein eigenes Civ-Bewegungssystem.
- Bereitstellung der Befehle <code>/civtest</code>, <code>/civrtstest</code>, <code>/civclaim</code>, <code>/civfarm</code>, <code>/civbuild</code> und <code>/civwiki</code>.
- Verdrahtung von Mausbutton-, Mausbewegungs- und Disconnect-Events mit dem Interaction-Controller. In First Person wird ein mit <code>/civclaim</code> scharf geschalteter Linksklick auf einen NPC über Hytales <code>Damage</code>-ECS-Pipeline erkannt. <code>CivClaimDamageSystem</code> läuft in <code>DamageModule.getFilterDamageGroup()</code>, akzeptiert nur NPC-Ziele mit einem Spieler als <code>Damage.EntitySource</code>, verbraucht den scharf geschalteten Claim und bricht den Schaden vor der Anwendung ab. Strukturelle ECS-Änderungen des Claims werden dabei über den vom Damage-System gelieferten <code>CommandBuffer</code> gepuffert; direkte Store-Schreibzugriffe während der Systemverarbeitung sind verboten. Danach reicht es das NPC-Ziel an denselben Claim-Handler weiter, den RTS verwendet. First-Person-Rechtsklick/Benutzen läuft über Hytales <code>UseEntityEvent.Pre</code>. Da dieses Event auf dem handelnden Spieler dispatcht wird, verwendet <code>CivInhabitantUseSystem</code> bewusst <code>Archetype.empty()</code>, liest den Spieler aus der Event-Entity und das Ziel aus <code>event.getTargetEntity()</code>. Nur für einen beanspruchten Civ-Bewohner wird dieselbe <code>PersonActionsPage</code> wie im RTS geöffnet und das Use-Event anschließend abgebrochen. Civ überschreibt dafür keine globalen Unarmed-/Empty-Interaction-Assets. RTS-Auswahl, Bewegung, Bau und Wiki bleiben RTS-spezifische Bedienpfade.

## Abhängigkeitsregel

Abhängigkeiten zeigen in Richtung Core. <code>core</code> ist Hytale-unabhängig. <code>hytale</code> darf von <code>core</code> und der Hytale-API abhängen. <code>plugin</code> darf von beiden und der Hytale-API abhängen.

Dadurch bleibt der Großteil des Verhaltens in normalen JUnit-Tests ausführbar. Hytale wird nur dort benötigt, wo das Engine-Verhalten selbst geprüft wird.

### Headless Ablaufsteuerung

Mehrstufige Gameplay-Abläufe sollen als Core-Zustand plus kleine Commands, Intents und Ergebnisse modelliert werden, wenn dadurch eine echte Engine-Grenze entsteht. Ein Test darf Engine-Ergebnisse wie „angekommen“ künstlich zurückmelden und dadurch denselben Zustandsautomaten weitertreiben, den der Hytale-Adapter im Spiel bedient.

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

Platzierte Gebäudeinstanzen behalten die ursprünglichen Weltblock-IDs, die durch ihren eingelassenen Boden ersetzt wurden. Dieser Schnappschuss ist laufzeitgebunden, solange die Gebäude selbst laufzeitgebunden sind. Werden platzierte Gebäude später persistent, muss der Geländeschnappschuss gemeinsam mit derselben Gebäudeinstanz gespeichert werden, damit ein späterer Abriss das vorherige Gelände wiederherstellen kann.

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
- Hytale stellt darüber hinaus `PersistentRef`, `PersistentMetaKey`, Entity-`UUID`-Auflösung und codec-basierte ECS-Komponenten bereit. Dass diese Infrastruktur existiert, belegt jedoch noch nicht, dass jede beliebige Civ-Gebäudezuweisung ohne zusätzliche Lifecycle-Arbeit korrekt gespeichert wird. Dieser konkrete Pfad muss vor einer dauerhaften Gebäude-/Arbeitsplatzidentität praktisch validiert werden.
- Trigger Volumes sind native Logikträger und nicht nur Civ-Marker. Die aktuelle Serverversion bietet Volume-Ereignisse wie ENTER, EXIT, TICK und SIGNAL_RECEIVED sowie Conditions, Cooldowns und Effects. Dazu gehören unter anderem Signale, Interactions, Item-/Block-/Prefab-Effekte und NPC-Marker. Civ soll solche nativen Mechanismen bevorzugen, bevor äquivalente Java-Ticklogik gebaut wird.

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
