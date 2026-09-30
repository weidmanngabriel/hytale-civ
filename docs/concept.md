# Produktkonzept

Hytale Civ ist als Strategie- und Simulations-Plugin für Hytale geplant. Einzelne Bewohner, lokale Warenbestände, Produktion und Logistik bilden den Kern des Spielerlebnisses.

## Zukünftige Richtung

Spätere Meilensteine können dauerhafte Civ-Bewohner, vollständige Routenplanung, Gebäudebau, Bewohner mit Berufen und Bedürfnissen, physische Waren, lokale Lager, Produktionsketten und Logistik umfassen.

## Aktueller Umfang

Der aktuelle Produkt-Meilenstein ist ein RTS-Prototyp mit steuerbaren NPCs zusätzlich zum ursprünglichen Plugin-Smoke-Test.

Für Entwicklung und Balancing besitzt die Hytale-unabhängige Simulation mehrere auswählbare Start-Szenarien. Der Desktop-Simulator startet standardmäßig mit <strong>Demo Settlement</strong>; weitere kleine Szenarien isolieren Holzfäller, Bauarbeiter, Bauer sowie absichtlich wartende Arbeiter. Ein Szenariowechsel oder Reset startet den jeweiligen definierten Weltzustand wieder bei Tick 0.

<code>/civtest</code> zeigt, dass das Plugin geladen ist.\n\n<code>/civdebug</code> ist ein vorläufiger, rein lesender Entwicklungsbefehl. Er zeigt für die aktuelle Welt die Anzahl der Laufzeit- und persistent gespeicherten Civ-Gebäude sowie pro persistentem Gebäude ID, Typ, Anzahl gespeicherter Snapshot-Blöcke und semantische Trigger-Volume-Typen. Er verändert keinen Spielzustand und soll vor einem fertigen Release wieder entfernt oder deaktiviert werden.

<code>/civdebug path</code> schaltet für die aktuell geladenen Civ-Bewohner Hytales native NPC-Pfadvisualisierung <code>VisPath</code> ein beziehungsweise wieder aus. Die Darstellung stammt aus Hytales tatsächlichem Navigationspfad; Civ berechnet dafür keinen eigenen Debug-Pfad. Bereits gesetzte andere NPC-Debug-Flags bleiben erhalten. Beim Ausschalten entfernt Civ <code>VisPath</code> nur bei Bewohnern, bei denen Civ den Flag zuvor selbst gesetzt hat. Dieser Slice dient zunächst der Runtime-Verifikation und gilt nur für die beim Umschalten aktuell geladenen Civ-Bewohner.

<code>/civrtstest</code> schaltet eine feste, schräge RTS-Kamera mit sichtbarem Mauszeiger ein oder aus. Der Spieler wechselt dabei nicht in den Spectator-Modus.

<code>/civclaim</code> aktiviert den nächsten Linksklick unabhängig vom Kameramodus. Wird anschließend in First Person oder RTS ein vorhandener NPC angeklickt, wird er dauerhaft als Civ-Bewohner initialisiert. Beim ersten Initialisieren erhält er ein Geschlecht, einen dreiteiligen Wikinger-Namen und den Zustand arbeitslos. Diese Bewohnerzugehörigkeit, Identität und der Beruf werden mit der Hytale-Entität gespeichert. Wird derselbe Bewohner erneut mit `/civclaim` angeklickt, wird er aus der Civ freigegeben und verhält sich wieder wie ein normaler NPC. Der vollständige Bewohnername wird als sichtbare Namensplakette des NPCs verwendet.

In First Person öffnet Rechtsklick/Benutzen auf einen beanspruchten Civ-Bewohner dessen Aktionsmenü. Darüber können aktuell die Berufe Holzfäller, Bauarbeiter und Bauer zugewiesen werden. Beim Bauer wird anschließend eine fertige Farm über deren Arbeitsbereich zugewiesen.

Während der RTS-Modus aktiv ist:

- Ein Linksklick auf eine beanspruchte Civ-Einheit wählt genau diese Person aus.
- Ein Linksklick auf freien Boden hebt die Auswahl auf.
- Nicht beanspruchte Einheiten können nicht ausgewählt werden.
- Ein Rechtsklick auf den aktuell ausgewählten Civ-Bewohner öffnet dessen Aktionsmenü.
- Die erste verfügbare Aktion weist den Beruf Holzfäller zu.
- Ein Rechtsklick auf einen Bodenblock gibt dem ausgewählten Bewohner ein direktes Bewegungsziel. Dieser manuelle Befehl pausiert seine automatische Berufsarbeit bis zum Erreichen des Ziels; danach nimmt er sie wieder auf.
- <code>/civbuild</code> öffnet den Gebäudekatalog. Solange dieser geöffnet ist, sind normale RTS-Interaktionen mit der Welt pausiert.
- <code>/civwiki</code> öffnet das Ingame-Wiki.
- Gebäude im Katalog sind alphabetisch nach ihrem Anzeigenamen sortiert.

RTS ist eine Bedienungsart und keine Voraussetzung für die Civ-Simulation. Bewohnerzugehörigkeit, Identität und Berufsdaten sind unabhängig von einer RTS-Session persistent. Auswahl, Bewegungsziele und laufende Arbeitsausführung bleiben vorläufige Laufzeitzustände.

## Holzfäller-Vertical-Slice

Einem ausgewählten, beanspruchten Civ-NPC kann über sein Aktionsmenü der Beruf Holzfäller zugewiesen werden.

Der aktuelle Arbeitsablauf fällt einen erkannten Baum als zusammenhängende Holzstruktur und schützt dabei Civ-/Hytale-Trigger-Volumes vor automatischem Abbau:

1. Der Holzfäller sucht in geladenen Chunks nach einem geeigneten Baum. Berührt ein Holzblock des erkannten Baums irgendein aktives Trigger Volume, wird der gesamte Baum als Arbeitsziel verworfen.
2. Ein Baum wird für genau einen Holzfäller reserviert. Weitere Holzfäller überspringen alle Holzblöcke dieses reservierten Baums.
3. Der Holzfäller wählt einen freien Standplatz direkt neben dem Stamm. Unter seinen Füßen muss ein fester Nicht-Holz-Block liegen; Standplatz und Boden dürfen nicht in einem Trigger Volume liegen. Gibt es keinen solchen Platz, wird der Baum nicht bearbeitet.
4. Während der Arbeit bleibt der Holzfäller an diesem Standplatz und spielt vorläufig die austauschbare generische Action-Animation `Alerted` ab.
5. Die Arbeitsdauer wächst mit der Zahl der erkannten Holzblöcke: aktuell zwei Sekunden Grundzeit plus 0,5 Sekunden pro Holzblock. Diese Werte sind vorläufige Balanceparameter.
6. Unmittelbar vor dem Fällen wird der Trigger-Volume-Schutz erneut geprüft. Danach werden die erkannten Holzblöcke über Hytales nativen Block-Damage-/Harvest-Pfad abgebaut, damit Hytales normale Drops, Break-Events und Baum-/Blattverhalten weiterhin maßgeblich bleiben.
7. Erkannte Wurzel-Holzblöcke unterhalb des Stammfußes werden ebenfalls entfernt. Für diese Positionen merkt sich Civ vor dem Fällen einen passenden häufigen Nicht-Holz-Nachbarblock und setzt ihn nach dem Abbau zurück, sofern die Position leer und weiterhin ungeschützt ist. Dadurch soll der Holzfäller Wurzellöcher schließen, ohne selbst in sie hinabzusteigen.
8. Danach wird die Reservierung freigegeben und der Holzfäller sucht den nächsten Baum.

Die Baumerkennung beginnt weiterhin bei einem Hytale-Holzblock mit Gather-Type `Woods`, dessen Asset-ID `trunk` enthält, und sammelt anschließend direkt zusammenhängende `Woods`-Blöcke innerhalb begrenzter Baumdimensionen. Eine öffentliche native Hytale-API zum serverseitigen vollständigen Fällen eines ganzen Baums ist für die aktuell gepinnte Runtime nicht verifiziert; deshalb grenzt Civ die zu bearbeitende Holzstruktur selbst ab, delegiert den eigentlichen Blockabbau aber weiterhin an Hytale.

Das Einsammeln der Holz-Drops ins Bewohnerinventar, eine größenabhängig balancierte Holzausbeute, das Tragen zu einem gemeinsamen Ablage-/Lagerort, Arbeitsbereiche und eine dauerhafte Arbeitsplatzzuweisung sind noch nicht Teil dieses Schritts.

## Farm-Vertical-Slice

> **Status:** Der derzeit spielbare Ablauf mit fünf Sekunden Innenarbeit, abstraktem lokalem Weizen und Stopp bei zehn Einheiten ist ein Engine-Validierungsprototyp, nicht die festgelegte Zielmechanik der Farm. Die nächste Produktiteration soll sichtbare Feldarbeit und Hytales native Trigger-, NPC- und Containermechanismen bevorzugen. Lokale Waren sollen nach Möglichkeit in echten Hytale-Containern liegen statt in parallelen Civ-Zählern.

Im RTS-Modus öffnet <code>/civbuild</code> den Gebäudekatalog. Wird **Farm** ausgewählt, schließt sich der Katalog und die Platzierung beginnt. Nach der Auswahl wird das Prefab an Hytales natives Paste Tool übergeben. Damit soll dieselbe Ghost-Vorschau und Cursor-Platzierung verwendet werden, die der normale Hytale-Prefab-Browser beim Verwenden eines Prefabs zeigt. Die konkrete Runtime-UX dieses Pfads wird mit Farm und Feld im Client validiert. <code>/civfarm</code> bleibt als Debug-Abkürzung für denselben Platzierungsmodus erhalten.

Die Creator-Prefabs werden ohne zusätzlichen Civ-Höhenoffset an das native Paste Tool übergeben; dessen Anchor-Position ist für die sichtbare Platzierung maßgeblich. Eigene Civ-Platzierungsregeln dürfen erst wieder vor den Commit geschaltet werden, wenn der native Paste-Commit zuverlässig abgefangen beziehungsweise validiert werden kann.

Nach dem Platzieren kann ein beanspruchter Civ-NPC ausgewählt und über den Arbeitsbereich der Farm als Bauer zugewiesen werden. Gibt es mehrere mögliche Zugänge, wird aktuell der zum Bewohner nächstgelegene verwendet.

Der erste zusammenhängende Produktionsablauf verwendet eine fertig gebaute Farm und ein separat fertig gebautes Weizenfeld. Das automatische Pflanzen durch den Bauer ist im aktuellen Hytale-Runtime-Stand vorläufig pausiert, weil dafür noch kein verifizierter nativer serverseitiger NPC-Pflanzpfad verfügbar ist; die Civ-Farm baut Hytales Seed-Placement nicht nach.

1. Der Spieler baut Farm und Weizenfeld über `/civbuild`.
2. Ein Bauer wird über den Arbeitsbereich der Farm zugewiesen. Dafür muss mindestens ein fertiges Weizenfeld vorhanden sein.
3. Der Bauer läuft zuerst zur Farm und danach zum nächstgelegenen fertigen Weizenfeld.
4. Auf dem Feld arbeitet er fünf Sekunden.
5. Danach läuft er zuerst wieder über `workplace_access` und anschließend zum `output_storage` der Farm, wo er eine echte Hytale-Weizen-ItemStack-Einheit in der vorhandenen Farmtruhe einlagert.
6. Erst nach erfolgreicher Einlagerung läuft er erneut über `workplace_access` und beginnt den nächsten Gang zum Feld. Ist die Truhe voll oder nicht verfügbar, wartet der Zyklus beim Einlagern.

Die Laufwege kommen zusätzlich zu den fünf Sekunden Feldarbeit hinzu. Eingangswaren, Träger und die Suche nach Materialquellen sind noch nicht umgesetzt; der gemeinsame Produktionskern hält Inputs jedoch bereits als Rezeptdaten getrennt von ihrer späteren Beschaffungslogik.

## Ingame-Wiki

<code>/civwiki</code> öffnet ein modales Ingame-Wiki mit vier Bereichen: **Berufe**, **Ressourcen**, **Gebäude** und **Tiere**.

Das Wiki beschreibt nur bereits umgesetztes Civ-Verhalten und verknüpft verwandte Einträge miteinander. Aktuell gibt es Einträge zu Holzfäller, Bauer, Holz, Weizen und Farm. Der Bereich Tiere weist ausdrücklich darauf hin, dass Tiere derzeit noch keine Civ-spezifische Gameplay-Rolle besitzen.

Wird das Wiki während einer aktiven Farm-Platzierung geöffnet, wird die Platzierung vorher abgebrochen.


### Farm und Feld bauen

Der Spieler platziert Farmgebäude und Weizenfeld getrennt über das Gebäudemenü. Beide verwenden denselben Vorschau-, Validierungs- und Platzierungsablauf. Das Feld wird nicht automatisch durch die Farm erzeugt. Fertig gebaute Felder werden über ihren Prefab-Marker `civ.building=farm` und `civ.type=field` als Farmfelder registriert. Dieser Trigger-Marker bestimmt zugleich das Arbeitsziel des Bauern; Civ berechnet dafür keine Position mehr aus der Feldgeometrie. Der erste Farmer-Loop verwendet automatisch das nächstgelegene fertige Feld zur zugewiesenen Farm; eine manuelle Farm-Feld-Verknüpfung gibt es in diesem Build noch nicht.
\n\n### Baustellen statt Sofortbau\n\nDie funktionierende native Paste-Tool-Vorschau bleibt die Platzierungsoberfläche. Beim Bestätigen einer von Civ gestarteten Farm- oder Feldplatzierung soll das fertige Prefab jedoch nicht sofort in die Welt eingefügt werden. Der aktuelle Baustellen-Slice bricht den nativen Paste vor der Weltmutation ab und setzt an der bestätigten Position eine persistente Hytale-Prefab-Vorschau als Baustelle. Die eigentliche schrittweise Materialisierung durch Bau-NPCs ist der nächste Slice und wird nicht durch einen sofortigen versteckten Paste simuliert.\n

A confirmed Civ building is initially represented as a construction blueprint rather than a finished functional building. The blueprint must be cancellable and must not activate the building's trigger volumes. Trigger volumes become active only when construction is completed. The current construction spike does not yet implement NPC-driven progressive block placement.

## Construction v1

Ein beanspruchter Civ-Bewohner kann über sein Personenaktionsmenü den Beruf **Bauarbeiter** erhalten. Bauarbeiter suchen automatisch die nächstgelegene freie Civ-Baustelle in ihrer Welt. Eine Baustelle wird jeweils von höchstens einem Bauarbeiter reserviert.

Der Bauarbeiter läuft mit Hytales nativer NPC-Wegfindung zu einem freien Arbeitspunkt direkt außerhalb des Gebäudegrundrisses. Dort bleibt er während des Baus stehen. Als vorläufige sichtbare Arbeitsdarstellung wird eine vorhandene generische Action-Animation abgespielt; sie ist ausdrücklich ein austauschbarer Platzhalter für eine spätere Hammer-/Bauanimation.

Das Gebäude materialisiert sich währenddessen schrittweise von unten nach oben. Ein Bauschritt entspricht im aktuellen Engine-Validierungsprototyp einer belegten Y-Ebene des Prefabs und dauert eine Sekunde. Die echte Prefab-Geometrie ersetzt dabei auch die vorgesehenen Bodenblöcke. Nach der letzten Ebene führt Civ einmal den vollständigen nativen Prefab-Placement-Pfad aus, damit Prefab-Entities und Trigger Volumes erst für das fertige Gebäude entstehen. Danach sucht der Bauarbeiter die nächste freie Baustelle.

Baumaterialien, Bauarbeiter-XP, mehrere Arbeiter an derselben Baustelle, individuelle Block-Arbeitspositionen und eine dauerhafte Baustellen-/Arbeitsplatzzuweisung sind noch nicht Teil von Construction v1.


### Gebäude-Bounds

Ein fertiges Civ-Gebäude besitzt eine vom Creator im Hytale Trigger Volume Tool gezeichnete Gebäudezone. Das Volume trägt `civ.type=building_bounds` und `civ.building=<Gebäudetyp>`. Diese Zone bestimmt nach Fertigstellung, welcher Raum logisch zum Gebäude gehört. Sie wird für Gebäude-Picking, Überschneidungsschutz bei weiteren Platzierungen und Schutz vor direktem Blockabbau bzw. Blockplatzieren verwendet.

Funktionsbereiche wie `workplace_access` oder `output_storage` bleiben eigene Trigger Volumes. Bei der Farm kann dadurch ein Rechtsklick auf einen beliebigen Block innerhalb der Gebäudezone die Farm treffen; der Arbeitszugang bleibt trotzdem das Ziel, zu dem der Bauer läuft. Das separat platzierte Feld verwendet weiterhin sein vorhandenes `civ.type=field`-Volume.


### Gebäude abreißen

Ein Rechtsklick auf ein fertig gebautes Farmgebäude innerhalb seiner `building_bounds` öffnet die Gebäudeaktionen, wenn kein Civ-Bewohner ausgewählt ist. Ist ein Bewohner ausgewählt, hat die Arbeitsplatzzuweisung Vorrang und das Gebäude-Interface öffnet sich nicht. Über **Abreißen** und eine separate Bestätigung kann die Farm vollständig entfernt werden. Civ entfernt dabei die Prefab-Blöcke und Trigger Volumes, stellt jede vom Farm-Prefab überschriebene Position auf ihren Zustand vor dem Bau zurück und gibt die Fläche im Gebäuderegister wieder frei. Das separat platzierte Weizenfeld gehört nicht zum Farmabriss und bleibt bestehen.
\n\nFertige Farmgebäude und der für einen späteren Abriss benötigte ursprüngliche Boden bleiben über Welt-/Server-Neustarts erhalten. Beim erneuten Betreten der Welt werden Schutz, Gebäudeinteraktion und Farm-Arbeitsplatz aus den gespeicherten Gebäudedaten rekonstruiert.\n

Beim Development-Bootstrap werden die vier Seeds als ein nativer ItemStack angefordert und die NPC-Inventarbereiche Storage, Hotbar und Backpack nacheinander verwendet. Ein Seed gilt nur dann als ausgegeben, wenn Hytale die Einlagerung bestätigt; lehnt ein Inventarbereich das Item ab, wird der nächste Bereich versucht.\n\n### Farmer-Test-Saatgut

Bis die allgemeine Materialbeschaffung umgesetzt ist, erhält ein Bewohner beim Wechsel in den Beruf Bauer vorläufig vier native Hytale-`Plant_Seeds_Wheat`-Seed-Bags in sein NPC-Inventar. Der Bauer verwendet diese jetzt tatsächlich auf dem fertig gebauten Weizenfeld: Der native Crop-Block wird auf der freien Position direkt über dem Ackerboden gesetzt, Hytale übernimmt den nativen Wachstumszyklus und reife Pflanzen werden über Hytales Farming-Ernte geerntet. Der dadurch real im NPC-Inventar ankommende Weizen wird anschließend zum Farmcontainer gebracht. Beim Wechsel aus dem Beruf Bauer werden bis zu vier verbliebene Bootstrap-Seed-Bags wieder entfernt. Nur die automatische Ausgabe der vier Seed-Bags ist Development-Bootstrap; Säen, Wachstum und Ernte sind der aktuelle Farmablauf.


### Gemeinsamer Gebäude-Lifecycle

Farm und Weizenfeld sind gleichermaßen persistente Civ-Gebäude. Beide werden nach Fertigstellung über dieselbe stabile Building-ID, Bounds/Footprint, semantische Volumes und Terrain-Snapshot gespeichert. Der Feld-Registry ist nur ein Runtime-Arbeitsindex und wird nach Weltbeitritt aus den persistenten Gebäudedaten rekonstruiert. Schutz und Abriss laufen über dieselbe Building-Infrastruktur; Abriss stellt den gespeicherten ursprünglichen Boden wieder her. Der Schutz des fertigen Weizenfelds verhindert weiterhin sämtliche direkten Spieler-Abbauversuche am geschützten Konstrukt sowie Änderungen am Feldboden und fremde Blockplatzierung. Normales Pflanzen von Weizensamen oberhalb des Feldbodens bleibt erlaubt.


### Weizenfeld als eigenständiges Gebäude

Das Weizenfeld besitzt einen eigenen `wheat_field`-Gebäudebereich für Auswahl, Schutz, Persistenz und Abriss. Sein `field`-Marker ist davon getrennt und dient ausschließlich als Arbeitsziel für Farmer. Terrain-Snapshots für den Abriss speichern stabile Block-Asset-IDs, damit ein Neustart keine laufzeitabhängigen numerischen Block-IDs als falsche Blöcke wiederherstellt.


### Bewohnerinventar ansehen

Im Personenaktionsmenü eines beanspruchten Civ-Bewohners gibt es **Inventar ansehen**. Die Ansicht zeigt das tatsächliche Hytale-Inventar des Bewohners inklusive der von Hytale zusammengefassten relevanten Inventarbereiche. Sie ist zunächst schreibgeschützt: Der Spieler kann kontrollieren, welche Gegenstände der Bewohner trägt, aber über diese Ansicht keine Items hineinlegen, herausnehmen oder verschieben.
