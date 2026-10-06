# Produktkonzept

Hytale Civ ist als Strategie- und Simulations-Plugin für Hytale geplant. Einzelne Bewohner, lokale Warenbestände, Produktion und Logistik bilden den Kern des Spielerlebnisses.

## Zukünftige Richtung

Spätere Meilensteine können dauerhafte Civ-Bewohner, vollständige Routenplanung, Gebäudebau, Bewohner mit Berufen und Bedürfnissen, physische Waren, lokale Lager, Produktionsketten und Logistik umfassen.

## Aktueller Umfang

Der aktuelle Produkt-Meilenstein ist ein RTS-Prototyp mit steuerbaren NPCs zusätzlich zum ursprünglichen Plugin-Smoke-Test.

Für Entwicklung und Balancing besitzt die Hytale-unabhängige Simulation mehrere auswählbare Start-Szenarien. Der Desktop-Simulator startet standardmäßig mit <strong>Demo Settlement</strong>; weitere kleine Szenarien isolieren Holzfäller, Bauarbeiter, Bauer sowie absichtlich wartende Arbeiter. Ein Szenariowechsel oder Reset startet den jeweiligen definierten Weltzustand wieder bei Tick 0.

<code>/civtest</code> zeigt, dass das Plugin geladen ist.

<code>/civdebug</code> ist ein vorläufiger, rein lesender Entwicklungsbefehl. Er zeigt für die aktuelle Welt die Anzahl der Laufzeit- und persistent gespeicherten Civ-Gebäude sowie pro persistentem Gebäude ID, Typ, Anzahl gespeicherter Snapshot-Blöcke und semantische Trigger-Volume-Typen. Er verändert keinen Spielzustand und soll vor einem fertigen Release wieder entfernt oder deaktiviert werden.

<code>/civdebug path</code> schaltet für die aktuell geladenen Civ-Bewohner Hytales native NPC-Pfadvisualisierung <code>VisPath</code> ein beziehungsweise wieder aus. Die Darstellung stammt aus Hytales tatsächlichem Navigationspfad; Civ berechnet dafür keinen eigenen Debug-Pfad. Bereits gesetzte andere NPC-Debug-Flags bleiben erhalten. Beim Ausschalten entfernt Civ <code>VisPath</code> nur bei Bewohnern, bei denen Civ den Flag zuvor selbst gesetzt hat. Dieser Slice dient zunächst der Runtime-Verifikation und gilt nur für die beim Umschalten aktuell geladenen Civ-Bewohner.

<code>/civdebug activity</code> zeigt für die in der aktuellen Welt bereits verfolgten Civ-Bewohner den gemeinsamen Aktivitätszustand für manuelle Bewegung und autonome Berufsarbeit. Angezeigt werden Entity-Index, Zustand (<code>MANUAL_MOVE</code>, <code>RESUME_DELAY</code> oder <code>AUTONOMOUS</code>), Freigabe der autonomen Arbeit, verbleibende Wiederanlaufzeit und das aktuelle manuelle Ziel. Der Befehl ist rein lesend und dient dazu, Unterbrechung und automatische Wiederaufnahme direkt im Spiel zu prüfen.

<code>/civdebug status</code> zeigt für geladene Civ-Bewohner den aktuell abgeleiteten spielerseitigen Status sowie den vollständigen Text, den Civ auf Hytales native Nameplate schreibt. Der Befehl ist rein lesend und dient dazu, Statusableitung und sichtbare Namensplakette direkt im Spiel gegenzuprüfen.

<code>/civrtstest</code> schaltet eine feste, schräge RTS-Kamera mit sichtbarem Mauszeiger ein oder aus. Der Spieler wechselt dabei nicht in den Spectator-Modus.

<code>/civclaim</code> aktiviert den nächsten Linksklick unabhängig vom Kameramodus. Wird anschließend in First Person oder RTS ein vorhandener NPC angeklickt, wird er dauerhaft als Civ-Bewohner initialisiert. Beim ersten Initialisieren erhält er ein Geschlecht, einen dreiteiligen Wikinger-Namen und den Zustand arbeitslos. Diese Bewohnerzugehörigkeit, Identität und der Beruf werden mit der Hytale-Entität gespeichert. Wird derselbe Bewohner erneut mit `/civclaim` angeklickt, wird er aus der Civ freigegeben und verhält sich wieder wie ein normaler NPC. Der persistente Bewohnername bleibt die Identität; die sichtbare native Nameplate ergänzt ihn zur Laufzeit um einen kurzen Status in der Form <code>Name · Status</code>, beispielsweise <code>Eirik Wolfsson · Geht zum Baum</code>.

In First Person öffnet Rechtsklick/Benutzen auf einen beanspruchten Civ-Bewohner dessen Aktionsmenü. Darüber können aktuell die Berufe Holzfäller, Bauarbeiter und Bauer zugewiesen werden. Beim Bauer wird anschließend eine fertige Farm über deren Arbeitsbereich zugewiesen.

Während der RTS-Modus aktiv ist:

- Ein Linksklick auf eine beanspruchte Civ-Einheit wählt genau diese Person aus.
- Ein Linksklick auf freien Boden hebt die Auswahl auf.
- Nicht beanspruchte Einheiten können nicht ausgewählt werden.
- Ein Rechtsklick auf den aktuell ausgewählten Civ-Bewohner öffnet dessen Aktionsmenü.
- Die erste verfügbare Aktion weist den Beruf Holzfäller zu.
- Ein Rechtsklick auf einen Bodenblock gibt dem ausgewählten Bewohner ein direktes Bewegungsziel. Dieser manuelle Befehl pausiert seine automatische Berufsarbeit. Sobald der Bewohner das Ziel erreicht hat, endet der manuelle Auftrag; nach einer gemeinsamen Wiederanlaufpause von zwei Sekunden darf der bereits zugewiesene Beruf automatisch mit seinem unveränderten Zustand weiterarbeiten. Der Beruf muss nicht erneut zugewiesen werden.
- Der aktuell implementierte Minenabbauer-Vertical-Slice verwendet beim Wiedereintritt `workplace_access` und danach `mine_tunnel_connector`. Seine konkrete Abbaugeometrie besteht weiterhin aus 4×4-Hauptgängen mit 4–12 Blöcken Länge und einer zusätzlichen stützenfreien 4×4×4-Junction. Diese Geometrie ist der heutige Engine-/Gameplay-Slice und nicht die Zielgeometrie des geplanten Mine-Overhauls.
- Während autonomer Minenarbeit überwacht Civ vorläufig, ob ein Miner außerhalb der Mine und der bekannten Tunnelräume auf oder über das aktuelle Gebäudereferenzniveau gelangt. Bleibt dieser Zustand ungefähr 1,5 Sekunden bestehen, wird er über Hytales native Teleport-Komponente zum `mine_tunnel_connector` zurückgesetzt und kann von dort weiterarbeiten. Manuelle Spielerbefehle unterdrücken diese Recovery vollständig. Diese Y-basierte Oberflächenheuristik ist ausdrücklich temporär und muss bei terrainabhängiger Weltgeneration, Bergen und späteren Tunnelebenen durch eine terrainbewusste Erkennung ersetzt werden.
- Ein Rechtsklick auf ein fertig gebautes Civ-Gebäude öffnet dessen Gebäude-Interface, sofern nicht der bestehende Farm-Zuweisungsmodus eines ausgewählten Bauern greift.
- Das Gebäude-Interface zeigt Gebäudename, Phase sowie die aktuelle Arbeiterbelegung als `X/Y`. Zugeordnete Bewohner erscheinen als auswählbare Einträge; nicht belegte Kapazität erscheint als freier Arbeitsplatz.
- Ein Klick auf einen zugeordneten Bewohner schließt das Gebäude-Interface und übernimmt ihn in dieselbe RTS-Auswahl wie ein direkter Linksklick auf den Bewohner. Ein anschließender Rechtsklick auf den Boden verwendet den normalen manuellen Bewegungsbefehl. Die Arbeitsplatzzuweisung bleibt dabei bestehen.
- Bei einem Gebäude mit einer weiteren authored Phase zeigt das Gebäude-Interface zusätzlich eine Ausbauaktion. Für die Mine sind aktuell Phase 1 → 2 und Phase 2 → 3 umgesetzt.
- Wird eine Mine erweitert, bleiben Building-ID und persistente Arbeitsplatzzuweisungen erhalten. Aktuell geladene zugeordnete Miner werden unmittelbar nach draußen teleportiert und ihre laufenden Bewegungsziele abgebrochen. Solange der Ausbau läuft, gilt die Mine für Gameplay-Arbeitsplatzabfragen als nicht verfügbar; Miner können deshalb nicht automatisch wieder hineinlaufen. Nach Fertigstellung wird dieselbe Building-ID mit der neuen Phase und den neuen semantischen Volumes weiterverwendet.
- Die angezeigte Arbeiterkapazität ist in diesem Slice nur Metadaten. Sie verhindert oder validiert noch keine Arbeitsplatzzuweisung.
- <code>/civbuild</code> öffnet den Gebäudekatalog. Solange dieser geöffnet ist, sind normale RTS-Interaktionen mit der Welt pausiert. Die Mine wird dort als normales Gebäude angeboten und ein Neubau startet stets mit `Mine_01` beziehungsweise Phase 1.
- <code>/civwiki</code> öffnet das Ingame-Wiki.
- Gebäude im Katalog sind alphabetisch nach ihrem Anzeigenamen sortiert.

RTS ist eine Bedienungsart und keine Voraussetzung für die Civ-Simulation. Bewohnerzugehörigkeit, Identität, Berufsdaten und eine optionale Arbeitsplatz-ID sind unabhängig von einer RTS-Session persistent. Auswahl, Bewegungsziele und laufende Arbeitsausführung bleiben vorläufige Laufzeitzustände.

## Gebäudephasen und Arbeiterkapazität

Fertige Civ-Gebäude besitzen neben ihrer stabilen Gebäude-ID und ihrem Typ eine persistente Phase. Neue Gebäude starten in Phase 1. Gebäudetypen können weitere authored Phasen definieren; der gemeinsame Phasenkatalog bestimmt, ob im Gebäude-Interface eine nächste Phase angeboten wird.

Für die Mine ist das Upgrade-Gameplay aktuell vollständig an den normalen Construction-v1-Ablauf angeschlossen: `Mine_01` wird als Neubau gesetzt, anschließend kann dieselbe Mine auf `Mine_02` und `Mine_03` erweitert werden. Das Upgrade erzeugt keine neue fachliche Building-ID. Dadurch bleiben Arbeitsplatzreferenzen der Bewohner stabil und die neue Phase ersetzt nach Fertigstellung den bestehenden Gebäudeeintrag.

Beim Start eines Minenausbaus werden aktuell geladene zugeordnete Arbeiter aus dem Gebäude an einen Punkt außerhalb des Minenzugangs teleportiert. Ihre manuellen und autonomen Bewegungsziele werden abgebrochen. Während der Ausbauzustand aktiv ist, ist das Gebäude über normale Gameplay-Lookups nicht als benutzbarer Arbeitsplatz auflösbar. Dadurch pausiert die Minenarbeit, bis die Bauarbeiter die nächste Phase fertiggestellt haben. Das Gebäude bleibt für Picking und das Gebäude-Interface sichtbar, damit der Spieler den Ausbauzustand weiterhin sehen kann.

Die Arbeiterkapazität wird aus Gebäudetyp und Phase abgeleitet und nicht im UI als Sonderregel hinterlegt. Für die Mine gilt: **Phase 1 = 1 Abbauer, Phase 2 = 2 Abbauer, Phase 3 = 3 Abbauer**. Nach einem Ausbau wächst die angezeigte Kapazität daher automatisch mit der neuen Phase.

Farm und Weizenfeld führen für den aktuellen Prototyp ebenfalls Kapazitätsmetadaten (Farm Phase 1: 1; Weizenfeld: 0). Diese beiden Werte sind keine Festlegung einer späteren Ausbaukurve.

## Holzfäller-Vertical-Slice

Einem ausgewählten, beanspruchten Civ-NPC kann über sein Personenaktionsmenü der Beruf Holzfäller zugewiesen werden. Beim Wechsel in den Beruf erhält der Bewohner vorläufig eine native Hytale-Eisenaxt (`Weapon_Axe_Iron`) in seiner Hotbar und hält sie aktiv in der Hand. Beim Wechsel in einen anderen Beruf oder beim Freigeben aus der Civ wird diese Bootstrap-Axt wieder entfernt. Diese automatische Werkzeugausgabe ist ein Development-Bootstrap und noch keine Materialbeschaffung.

Der aktuelle Arbeitsablauf fällt einen erkannten Baum als zusammenhängende Holzstruktur und schützt dabei fertige Civ-Gebäude vor automatischem Abbau:

1. Der Holzfäller sucht in geladenen Chunks nach einem geeigneten Baum. Berührt ein Holzblock des erkannten Baums ein Trigger Volume mit `civ.type=building_bounds`, wird der gesamte Baum als Arbeitsziel verworfen.
2. Ein Baum wird für genau einen Holzfäller reserviert. Weitere Holzfäller überspringen alle Holzblöcke dieses reservierten Baums.
3. Der Holzfäller wählt einen freien Standplatz bis zu drei Blöcke um den Stamm. Unter seinen Füßen muss ein fester Nicht-Holz-Block liegen; Standplatz und Boden dürfen nicht in einem geschützten `building_bounds`-Volume liegen. Gültige Plätze werden zuerst danach bewertet, wie nah sie an den unteren Stammblöcken liegen; erst bei gleich guten Plätzen entscheidet der kürzere Laufweg des Bewohners. Dadurch steht der Holzfäller möglichst direkt am sichtbaren Stamm, ohne sichere Ausweichplätze bei großen oder unregelmäßigen Bäumen zu verlieren. Die Navigation läuft bis auf höchstens einen Block an diesen Arbeitspunkt heran; erst dann beginnt die Arbeitsphase. Gibt es keinen solchen Platz, wird der Baum nicht bearbeitet.
4. Während der Arbeit bleibt der Holzfäller an diesem Standplatz und spielt Hytales native horizontale Player-Axtanimation `SwingLeft` in einer Civ-Animation-Schleife ab. Civ startet die Animation einmal beim Beginn der Arbeitsphase und stoppt sie beim Ende oder bei einer Unterbrechung; die einzelnen Schläge werden nicht serverseitig pro Tick neu ausgelöst.
5. Die Arbeitsdauer wächst mit der Zahl der erkannten Holzblöcke: aktuell vier Sekunden Grundzeit plus 0,05 Sekunden pro Holzblock, gedeckelt bei 20 Sekunden. Diese Werte sind vorläufige Balanceparameter.
6. Unmittelbar vor dem Fällen wird der Gebäudeschutz erneut geprüft. Danach werden die erkannten Holzblöcke über Hytales nativen Block-Damage-/Harvest-Pfad abgebaut, damit Hytales normale Drops, Break-Events und Baum-/Blattverhalten weiterhin maßgeblich bleiben.
7. Erkannte Wurzel-Holzblöcke unterhalb des Stammfußes werden ebenfalls entfernt. Für diese Positionen merkt sich Civ vor dem Fällen einen passenden häufigen Nicht-Holz-Nachbarblock und setzt ihn nach dem Abbau zurück, sofern die Position leer und weiterhin ungeschützt ist. Dadurch soll der Holzfäller Wurzellöcher schließen, ohne selbst in sie hinabzusteigen.
8. Danach wird die Reservierung freigegeben und der Holzfäller sucht den nächsten Baum.

Die Baumerkennung beginnt weiterhin bei einem Hytale-Holzblock mit Gather-Type `Woods`, dessen Asset-ID `trunk` enthält, und sammelt anschließend direkt zusammenhängende `Woods`-Blöcke innerhalb begrenzter Baumdimensionen. Eine öffentliche native Hytale-API zum serverseitigen vollständigen Fällen eines ganzen Baums ist für die aktuell gepinnte Runtime nicht verifiziert; deshalb grenzt Civ die zu bearbeitende Holzstruktur selbst ab, delegiert den eigentlichen Blockabbau aber weiterhin an Hytale.

Das Einsammeln der Holz-Drops ins Bewohnerinventar, eine größenabhängig balancierte Holzausbeute, das Tragen zu einem gemeinsamen Ablage-/Lagerort und Arbeitsbereiche sind noch nicht Teil dieses Schritts.

## Farm-Vertical-Slice

> **Status:** Der derzeit spielbare Ablauf mit fünf Sekunden Innenarbeit, abstraktem lokalem Weizen und Stopp bei zehn Einheiten ist ein Engine-Validierungsprototyp, nicht die festgelegte Zielmechanik der Farm. Die nächste Produktiteration soll sichtbare Feldarbeit und Hytales native Trigger-, NPC- und Containermechanismen bevorzugen. Lokale Waren sollen nach Möglichkeit in echten Hytale-Containern liegen statt in parallelen Civ-Zählern.

Im RTS-Modus öffnet <code>/civbuild</code> den Gebäudekatalog. Wird **Farm** ausgewählt, schließt sich der Katalog und die Platzierung beginnt. Nach der Auswahl wird das Prefab an Hytales natives Paste Tool übergeben. Damit soll dieselbe Ghost-Vorschau und Cursor-Platzierung verwendet werden, die der normale Hytale-Prefab-Browser beim Verwenden eines Prefabs zeigt. Die konkrete Runtime-UX dieses Pfads wird mit Farm und Feld im Client validiert. <code>/civfarm</code> bleibt als Debug-Abkürzung für denselben Platzierungsmodus erhalten.

Die Creator-Prefabs werden ohne zusätzlichen Civ-Höhenoffset an das native Paste Tool übergeben; dessen Anchor-Position ist für die sichtbare Platzierung maßgeblich. Eigene Civ-Platzierungsregeln dürfen erst wieder vor den Commit geschaltet werden, wenn der native Paste-Commit zuverlässig abgefangen beziehungsweise validiert werden kann.

Nach dem Platzieren kann ein beanspruchter Civ-NPC ausgewählt und über den Arbeitsbereich der Farm als Bauer zugewiesen werden. Gibt es mehrere mögliche Zugänge, wird aktuell der zum Bewohner nächstgelegene verwendet. Bei erfolgreicher Zuweisung speichert der Bewohner zusätzlich die stabile Building-ID der Farm als Arbeitsplatz.

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

### Baustellen statt Sofortbau

Die funktionierende native Paste-Tool-Vorschau bleibt die Platzierungsoberfläche. Beim Bestätigen einer von Civ gestarteten Farm- oder Feldplatzierung soll das fertige Prefab jedoch nicht sofort in die Welt eingefügt werden. Der aktuelle Baustellen-Slice bricht den nativen Paste vor der Weltmutation ab und setzt an der bestätigten Position eine persistente Hytale-Prefab-Vorschau als Baustelle. Die eigentliche schrittweise Materialisierung durch Bau-NPCs ist der nächste Slice und wird nicht durch einen sofortigen versteckten Paste simuliert.

A confirmed Civ building is initially represented as a construction blueprint rather than a finished functional building. The blueprint must be cancellable and must not activate the building's trigger volumes. Trigger volumes become active only when construction is completed. The current construction spike does not yet implement NPC-driven progressive block placement.

## Construction v1

Ein beanspruchter Civ-Bewohner kann über sein Personenaktionsmenü den Beruf **Bauarbeiter** erhalten. Bauarbeiter suchen automatisch die nächstgelegene freie Civ-Baustelle in ihrer Welt. Eine Baustelle wird jeweils von höchstens einem Bauarbeiter reserviert.

Der Bauarbeiter läuft mit Hytales nativer NPC-Wegfindung zu einem freien Arbeitspunkt direkt außerhalb des Gebäudegrundrisses. Dort bleibt er während des Baus stehen. Als vorläufige sichtbare Arbeitsdarstellung wird eine vorhandene generische Action-Animation abgespielt; sie ist ausdrücklich ein austauschbarer Platzhalter für eine spätere Hammer-/Bauanimation.

Das Gebäude materialisiert sich währenddessen schrittweise von unten nach oben. Ein Bauschritt entspricht im aktuellen Engine-Validierungsprototyp einer belegten Y-Ebene des Prefabs und dauert eine Sekunde. Die echte Prefab-Geometrie ersetzt dabei auch die vorgesehenen Bodenblöcke. Nach der letzten Ebene führt Civ einmal den vollständigen nativen Prefab-Placement-Pfad aus, damit Prefab-Entities und Trigger Volumes erst für das fertige Gebäude entstehen. Danach sucht der Bauarbeiter die nächste freie Baustelle.

Baumaterialien, Bauarbeiter-XP, mehrere Arbeiter an derselben Baustelle, individuelle Block-Arbeitspositionen und eine dauerhafte Baustellenzuweisung sind noch nicht Teil von Construction v1.

### Gebäude-Bounds

Ein fertiges Civ-Gebäude besitzt eine vom Creator im Hytale Trigger Volume Tool gezeichnete Gebäudezone. Das Volume trägt `civ.type=building_bounds` und `civ.building=<Gebäudetyp>`. Diese Zone bestimmt nach Fertigstellung, welcher Raum logisch zum Gebäude gehört. Sie wird für Gebäude-Picking, Überschneidungsschutz bei weiteren Platzierungen und Schutz vor direktem Blockabbau bzw. Blockplatzieren verwendet.

Funktionsbereiche wie `workplace_access` oder `output_storage` bleiben eigene Trigger Volumes. Bei der Farm kann dadurch ein Rechtsklick auf einen beliebigen Block innerhalb der Gebäudezone die Farm treffen; der Arbeitszugang bleibt trotzdem das Ziel, zu dem der Bauer läuft. Das separat platzierte Feld verwendet weiterhin sein vorhandenes `civ.type=field`-Volume.

### Gebäudeaktionen und Abriss

Ein Rechtsklick auf ein fertig gebautes Civ-Gebäude innerhalb seiner `building_bounds` öffnet grundsätzlich die Gebäudeaktionen. Die bestehende Farm-Zuweisung bleibt vorläufig eine Ausnahme: Ist ein Bewohner ausgewählt und wird eine Farm rechtsgeklickt, hat die Arbeitsplatzzuweisung aktuell Vorrang.

Das Gebäude-Interface zeigt die persistente Phase und die aus dem Gebäudetyp abgeleitete Arbeiterkapazität. Bereits zugeordnete, aktuell geladene Bewohner werden über ihre persistente Workplace-ID angezeigt und können direkt ausgewählt werden. Die Kapazitätsanzeige ist derzeit ausdrücklich keine Gameplay-Grenze.

Besitzt der Gebäudetyp eine weitere Phase, zeigt dasselbe Interface eine **Erweitern**-Aktion. Für die Mine startet diese Aktion die nächste Phase als normale Civ-Baustelle am unveränderten Gebäudeanker. Während des Ausbaus bleibt das Gebäude im Interface sichtbar, kann aber von den Arbeitsplatzsystemen nicht aufgelöst werden; ein weiterer Ausbau oder Abriss ist in diesem Zustand gesperrt. Bei Fertigstellung bleibt die Building-ID erhalten und die persistierte Phase wird erhöht.

Über **Abreißen** und eine separate Bestätigung kann ein Gebäude vollständig entfernt werden. Civ entfernt dabei die Prefab-Blöcke und Trigger Volumes, stellt jede vom Prefab überschriebene Position auf ihren Zustand vor dem Bau zurück und gibt die Fläche im Gebäuderegister wieder frei. Arbeitsplatzreferenzen aktuell geladener Bewohner auf das abgerissene Gebäude werden dabei entfernt. Separat platzierte Gebäude wie ein Weizenfeld gehören nicht automatisch zum Abriss eines anderen Gebäudes.

Fertige Civ-Gebäude und der für einen späteren Abriss benötigte ursprüngliche Boden bleiben über Welt-/Server-Neustarts erhalten. Beim erneuten Betreten der Welt werden Schutz, Gebäudeinteraktion und gebäudespezifische Laufzeitindizes aus den gespeicherten Gebäudedaten rekonstruiert.

Beim Development-Bootstrap werden die vier Seeds als ein nativer ItemStack angefordert und die NPC-Inventarbereiche Storage, Hotbar und Backpack nacheinander verwendet. Ein Seed gilt nur dann als ausgegeben, wenn Hytale die Einlagerung bestätigt; lehnt ein Inventarbereich das Item ab, wird der nächste Bereich versucht.

### Farmer-Test-Saatgut

Bis die allgemeine Materialbeschaffung umgesetzt ist, erhält ein Bewohner beim Wechsel in den Beruf Bauer vorläufig vier native Hytale-`Plant_Seeds_Wheat`-Seed-Bags in sein NPC-Inventar. Der Bauer verwendet diese jetzt tatsächlich auf dem fertig gebauten Weizenfeld: Der native Crop-Block wird auf der freien Position direkt über dem Ackerboden gesetzt, Hytale übernimmt den nativen Wachstumszyklus und reife Pflanzen werden über Hytales Farming-Ernte geerntet; der dadurch real im NPC-Inventar ankommende Weizen wird anschließend zum Farmcontainer gebracht. Beim Wechsel aus dem Beruf Bauer werden bis zu vier verbliebene Bootstrap-Seed-Bags wieder entfernt. Nur die automatische Ausgabe der vier Seed-Bags ist Development-Bootstrap; Säen, Wachstum und Ernte sind der aktuelle Farmablauf.

### Gemeinsamer Gebäude-Lifecycle

Farm und Weizenfeld sind gleichermaßen persistente Civ-Gebäude. Beide werden nach Fertigstellung über dieselbe stabile Building-ID, Gebäudetyp, Phase, Bounds/Footprint, semantische Volumes und Terrain-Snapshot gespeichert. Upgradefähige Gebäude verwenden denselben Lifecycle: Eine Erweiterung ersetzt die bestehende Building-Instanz unter derselben ID durch die nächste Phase, statt ein zweites Gebäude anzulegen. Der ursprüngliche Terrain-Snapshot der Erstplatzierung bleibt erhalten, damit ein späterer Abriss weiterhin auf den Zustand vor Phase 1 zurückführen kann.

Der Feld-Registry ist nur ein Runtime-Arbeitsindex und wird nach Weltbeitritt aus den persistenten Gebäudedaten rekonstruiert. Schutz und Abriss laufen über dieselbe Building-Infrastruktur; Abriss stellt den gespeicherten ursprünglichen Boden wieder her. Der Schutz des fertigen Weizenfelds verhindert weiterhin sämtliche direkten Spieler-Abbauversuche am geschützten Konstrukt sowie Änderungen am Feldboden und fremde Blockplatzierung. Normales Pflanzen von Weizensamen oberhalb des Feldbodens bleibt erlaubt.

### Weizenfeld als eigenständiges Gebäude

Das Weizenfeld besitzt einen eigenen `wheat_field`-Gebäudebereich für Auswahl, Schutz, Persistenz und Abriss. Sein `field`-Marker ist davon getrennt und dient ausschließlich als Arbeitsziel für Farmer. Terrain-Snapshots für den Abriss speichern stabile Block-Asset-IDs, damit ein Neustart keine laufzeitabhängigen numerischen Block-IDs als falsche Blöcke wiederherstellt.

### Bewohnerinventar ansehen

Im Personenaktionsmenü eines beanspruchten Civ-Bewohners gibt es **Inventar ansehen**. Die Ansicht zeigt das tatsächliche Hytale-Inventar des Bewohners inklusive der von Hytale zusammengefassten relevanten Inventarbereiche. Sie ist zunächst schreibgeschützt: Der Spieler kann kontrollieren, welche Gegenstände der Bewohner trägt, aber über diese Ansicht keine Items hineinlegen, herausnehmen oder verschieben.

## Aktueller Minenabbauer-Vertical-Slice

Der derzeit spielbare Minenabbauer gräbt weiterhin 4x4-Stollen in variablen Abschnitten von 4 bis 12 Blöcken. Passt die zunächst gewählte Länge wegen eines Gebäudes oder vorhandenen Stollens nicht, wird derselbe Verlauf zunächst kürzer geplant, bevor eine andere Richtung gewählt wird. Kurven benötigen wegen ihres gemeinsamen 4x4x4-Übergangs mindestens 5 Blöcke.

Ein Spieler kann einen unterirdischen Minenabbauer jederzeit manuell herausrufen: Ein normaler Bewegungsbefehl setzt ihn am `workplace_access` seiner Mine ab, danach läuft er zum angeklickten Ziel. Nimmt er später die Arbeit wieder auf, darf er sich eine andere offene Arbeitsfront oder einen neuen gültigen Tunnelast suchen und muss nicht exakt zum unterbrochenen Block zurückkehren.

Dieser Slice dient als heutige Implementierung und Engine-Validierung. Die geplante Mine verwendet dagegen die variable Tunnelgeometrie, das MineNetwork und die Miner-Aufgabenlogik aus `docs/mine-design.md` und `docs/miner-npc-design.md`.

## Geplante Mine- und Mehrminer-Zielmechanik

> **Status:** beschlossenes Zielverhalten, noch nicht vollständig implementiert. Die kanonischen Details stehen in `docs/mine-design.md` und `docs/miner-npc-design.md`.

Die neue Mine soll nicht aus dauerhaft festen 4x4-Stollen bestehen. Der Haupttunnel liegt grob im Bereich 6–8 Blöcke Breite/Höhe, Seitentunnel grob bei 3–5 Blöcken. Formphasen verändern Querschnitt, Drift und Höhe schrittweise, sodass Arbeitsfronten immer die tatsächlich geplante Tunnelgeometrie verwenden.

Miner werden nicht global und nicht in persistenten Teams koordiniert. Die **zugewiesene Mine ist die Koordinationsgrenze**: Nur Miner derselben Mine konkurrieren oder kooperieren um deren Aufgaben. Andere Minen und andere Fraktionen besitzen ihre eigene unabhängige Arbeitsauswahl.

Eine Mine besitzt mehrere mögliche Aufgaben und offene Arbeitsfronten. Für V1 gilt als Auswahlprinzip:

1. **Priorität 10** ist akut und hat immer Vorrang. Sie ist ausschließlich für verpflichtende Sicherheits-/Passierbarkeitsarbeit reserviert; normale Aufgaben können höchstens Priorität 9 erreichen.
2. Gibt es keinen 10er-Job, werden zuerst **bereits aktive Aufgaben mit freier Kapazität** aufgefüllt. Das gilt bewusst auch dann, wenn eine noch nicht begonnene normale Aufgabe eine höhere Priorität zwischen 1 und 9 besitzt.
3. Unter mehreren aktiven Aufgaben mit freier Kapazität gewinnt die höhere Priorität, danach die nähere Aufgabe und schließlich ein Tie-Breaker.
4. Erst wenn keine aktive normale Aufgabe mehr freie Kapazität besitzt, wird die höchste wartende normale Aufgabe begonnen.

Dadurch entstehen Arbeitsgruppen automatisch statt durch feste Teamformationen. Eine normale Tunnel-Arbeitsfront hat zunächst Kapazität 2, Raumaushub bis 3, Raumbau meist 2 und einzelne Infrastruktur-/Dekorationsjobs normalerweise 1. Die Mine selbst bleibt zusätzlich durch ihre Gebäudephase begrenzt: Phase 1 = 1, Phase 2 = 2, Phase 3 = 3 Abbauer.

Mehrere Miner an derselben Arbeitsfront leisten echte parallele Arbeit an unterschiedlichen noch offenen Teilen derselben Excavation Slice. Sie dürfen nicht denselben Block gleichzeitig beanspruchen. Solche Block-Claims sind kurzfristige Koordination und keine festen linken/rechten Arbeitsplätze. Wenn ein Miner die Front verlässt, arbeitet der andere normal weiter und der freigewordene Kapazitätsplatz kann später neu besetzt werden.

Normale Arbeit wird bis zum Ende der aktuellen Work Unit weitergeführt. Priorität 10 darf dagegen sofort unterbrechen. Es werden nur so viele Miner abgezogen, wie der akute Job tatsächlich benötigt; freie Miner werden zuerst verwendet, danach möglichst Miner aus niedriger priorisierter normaler Arbeit. Nach dem Akutjob wird nichts zwangsweise wieder aufgenommen: Der Miner entscheidet erneut nach den normalen Regeln.

Aging schützt wartende normale Arbeit vor dauerhaftem Verhungern, maximal bis Priorität 9. Eine Aufgabe altert nicht weiter, solange bereits mindestens ein Miner aktiv daran arbeitet.

Unfertige Arbeitszustände, Fortschritt und Prioritäten gehören zur Mine beziehungsweise zum Task und sollen einen Server-Neustart überleben. Temporäre Miner-Zuordnungen, Arbeitsgruppen und Block-Claims werden nicht persistiert. Nach einem Restart wählen die Miner aus den wiederhergestellten offenen Arbeiten neu. Fertige Tasks werden aus dem aktiven/persistierten Task-System entfernt, sobald ihr dauerhaftes Ergebnis im MineNetwork und/oder in der Hytale-Welt repräsentiert ist.

Für einen Miner, der zuvor an die Oberfläche gerufen wurde, ist die geplante autonome Rückkehr gestaffelt: `workplace_access -> mine_tunnel_connector -> Arbeitsposition`. Liegt die gewählte Arbeitsposition mehr als 20 Blöcke vom Connector entfernt, teleportiert der Connector den Miner stattdessen zum passenden bereits vorhandenen Navigation Anchor des Zielzweigs; von dort übernimmt wieder Hytales native Navigation. Anchors existieren im Zielmodell ungefähr alle 10 Blöcke und zusätzlich an wichtigen Topologiepunkten. Ein geeigneter Anchor soll möglichst höchstens 20 Blöcke von der Arbeitsposition entfernt sein; fehlt ein solcher, wird der beste gültige Anchor des Zielzweigs verwendet.
