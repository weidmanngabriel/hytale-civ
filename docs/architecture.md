# Architektur

## Ziel

Die Simulation soll testbar bleiben, ohne Hytale starten zu müssen. Hytale ist eine Integrationsgrenze und nicht das Domänenmodell.

Vor Version 1 ist Rückwärtskompatibilität kein Ziel, wenn dafür Migrationen, parallele Altpfade, Kompatibilitäts-Defaults oder featurespezifische Ausnahmen nötig wären. Die aktuell dokumentierte Architektur und das Datenmodell sind maßgeblich. Diese Regel muss neu bewertet werden, bevor persistente Spielerwelten oder öffentliche stabile Releases Kompatibilität zu einer Produktanforderung machen.

## Schichten

~~~text
Core-Simulation
      ↓
Hytale-Adapter
      ↓
Hytale-Plugin / API
~~~

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
- <code>CivInhabitantService</code> initialisiert diese persistenten Bewohnerdaten unabhängig vom RTS-Zustand und setzt den sichtbaren Namen über Hytales natives <code>PersistentDisplayName</code>. Beim ersten Claim entsteht die Wikinger-Identität genau einmal.
- <code>CivUnitRegistry</code> bleibt ein laufzeitgebundenes Register für ausdrücklich beanspruchte NPCs und Bewegungsziele. Persistente Berufsdaten werden über <code>CivInhabitantService</code> gelesen und geschrieben. Für die Rolle <code>Civ_Inhabitant</code> wird das Bewegungsziel in den einzelnen nativen Positionsslot <code>CivMoveTarget</code> geschrieben; <code>ReadPosition</code> und <code>Seek</code> delegieren Wegfindung und Bewegung danach an Hytale.
- <code>WoodcutterWorkSystem</code> sucht natürliche Hytale-Stammblöcke in der Nähe, führt einen HOLZFÄLLER an eine benachbarte Arbeitsposition und verwendet Hytales nativen <code>BlockHarvestUtils.performBlockDamage</code>-Weg zum Fällen des Basisblocks. Normale Drops, Break-Events und Blockphysik bleiben damit bei der Engine.
- <code>FarmPrefabService</code> löst das Farm-Prefab über Hytales durchsuchbare Prefab-Orte auf, lädt es über <code>PrefabStore</code>, validiert Gelände und Kollisionen anhand der tatsächlich belegten Prefab-Blockzellen, rendert die Platzierungsvorschau pro Spieler, versenkt den Prefab-Boden einen Block im Gelände und löst neu eingefügte Farm-Arbeitsbereiche über Hytales <code>TriggerVolumeManager</code> auf. Zusätzlich werden die durch den eingelassenen Boden ersetzten Weltblöcke gespeichert.
- <code>FarmBuildingRegistry</code> verbindet platzierte Farm-Instanzen und zugewiesene NPC-Referenzen mit dem Core-Zustand <code>FarmBuilding</code>, merkt Platzierungsflächen für Überschneidungsprüfungen und behält pro Instanz den Schnappschuss der ersetzten Bodenblöcke.
- <code>FarmNpcWorkSystem</code> übersetzt die Core-Farmzustände in Bewegungsziele für Eingang und Ausgang und treibt die Produktion voran, solange der Bauer innen arbeitet.

<code>CivUnitRegistry</code> identifiziert eine Laufzeitentität über ihren <code>Store</code> plus Entitätsindex und behält gleichzeitig die ursprüngliche <code>Ref</code> zur Validierung. Dadurch wird nicht auf Java-Objektidentität wiederholt erzeugter <code>Ref</code>-Instanzen vertraut und veraltete Entitätsslots werden nicht als gültige Civ-Einheiten behandelt.

Ansprüche, Auswahl und Arbeitsziele bleiben bewusst laufzeitgebunden. Bewohneridentität, aktiver Beruf und getrennte Berufserfahrung liegen dagegen in der serialisierbaren <code>CivInhabitantData</code>-Komponente auf der NPC-Entität und hängen nicht von einer RTS-Session ab. Arbeitsplatzzuweisung bleibt laufzeitgebunden, weil platzierte Gebäude noch keine stabile dauerhafte Identität besitzen. Ein späterer Bewohner-Lifecycle soll den Debug-Anspruchsmechanismus ersetzen.

Bewegungsbefehle verwenden keinen Civ-eigenen Steuerloop pro Tick. Die vom Ersteller bearbeitbare Rolle <code>Civ_Inhabitant</code> definiert genau einen Positionsslot namens <code>CivMoveTarget</code>. Dieser liegt bewusst an Slot-Index 0 und bildet einen Java-/Asset-Vertrag, der durch einen automatisierten Asset-Test geschützt wird. <code>CivUnitRegistry</code> schreibt oder löscht diese gespeicherte Position über Hytales <code>MarkedEntitySupport</code>; die Rolle verarbeitet sie über <code>ReadPosition</code> und <code>Seek</code> mit Hytales nativer Wegfindung und Walk-Bewegung. Fremde Hytale-Rollen werden nicht über diesen Vertrag gesteuert. Farm- und Holzfällersysteme behalten dennoch ihre simulationsseitigen Ankunftsprüfungen, damit Job-Zustandswechsel aus Civ-Sicht deterministisch bleiben.

Die feste RTS-Kamera ist eine Hytale-Custom-Kamera und kein Spectator-Modus. Eine verifizierte native API zum Ausblenden nur des eigenen Spielermodells in diesem Kameramodus wurde noch nicht gefunden. Deshalb wird die Spielerentität nicht über einen unbestätigten Workaround despawnt oder versteckt.

### plugin

Hytale-Bootstrap und Lifecycle. Hier werden Adapter und Services verdrahtet sowie Hytale-nahe Befehle und Systeme registriert. Spiellogik soll hier so wenig wie möglich liegen.

<code>CivilizationsPlugin</code> erledigt aktuell:

- Registrierung der serialisierbaren ECS-Komponente <code>CivInhabitantData</code>, bevor Civ-Register und Systeme verdrahtet werden.
- Registrierung der Tick-Systeme für Farm und Holzfäller. Allgemeine Bewegung eines <code>Civ_Inhabitant</code> wird über natives <code>ReadPosition</code>/<code>Seek</code> erledigt und nicht durch ein eigenes Civ-Bewegungssystem.
- Bereitstellung der Befehle <code>/civtest</code>, <code>/civrtstest</code>, <code>/civclaim</code>, <code>/civfarm</code>, <code>/civbuild</code> und <code>/civwiki</code>.
- Verdrahtung von Mausbutton-, Mausbewegungs- und Disconnect-Events mit dem Interaction-Controller. In First Person wird ein mit <code>/civclaim</code> scharf geschalteter Linksklick auf einen NPC über Hytales <code>Damage</code>-ECS-Pipeline erkannt. <code>CivClaimDamageSystem</code> läuft in <code>DamageModule.getFilterDamageGroup()</code>, akzeptiert nur NPC-Ziele mit einem Spieler als <code>Damage.EntitySource</code>, verbraucht den scharf geschalteten Claim und bricht den Schaden vor der Anwendung ab. Danach reicht es das NPC-Ziel an denselben Claim-Handler weiter, den RTS über <code>PlayerMouseButtonEvent</code> verwendet. RTS-Auswahl, Bewegung, Bau und Wiki bleiben RTS-spezifische Bedienpfade.

## Abhängigkeitsregel

Abhängigkeiten zeigen in Richtung Core. <code>core</code> ist Hytale-unabhängig. <code>hytale</code> darf von <code>core</code> und der Hytale-API abhängen. <code>plugin</code> darf von beiden und der Hytale-API abhängen.

Dadurch bleibt der Großteil des Verhaltens in normalen JUnit-Tests ausführbar. Hytale wird nur dort benötigt, wo das Engine-Verhalten selbst geprüft wird.

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

Gameplay-Daten sollen nur dann ins Asset Pack wandern, wenn ein konkreter Hytale-Asset-Typ dies verlangt. Core-Simulationsregeln und Domänenzustand bleiben in der bestehenden Java-Architektur, solange ein späteres Feature keine andere Grenze festlegt.

## Farm-Produktions-Vertical-Slice

Das erste echte Produktionsfeature bleibt bewusst konkret und führt noch kein spekulatives allgemeines Gebäudeframework ein.

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
