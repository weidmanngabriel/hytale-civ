# Produktkonzept

Hytale Civ ist als Strategie- und Simulations-Plugin für Hytale geplant. Einzelne Bewohner, lokale Warenbestände, Produktion und Logistik bilden den Kern des Spielerlebnisses.

## Zukünftige Richtung

Spätere Meilensteine können dauerhafte Civ-Bewohner, vollständige Routenplanung, Gebäudebau, Bewohner mit Berufen und Bedürfnissen, physische Waren, lokale Lager, Produktionsketten und Logistik umfassen.

## Aktueller Umfang

Der aktuelle Produkt-Meilenstein ist ein RTS-Prototyp mit steuerbaren NPCs zusätzlich zum ursprünglichen Plugin-Smoke-Test.

Für Entwicklung und Balancing besitzt die Hytale-unabhängige Simulation mehrere auswählbare Start-Szenarien. Der Desktop-Simulator startet standardmäßig mit <strong>Demo Settlement</strong>; weitere kleine Szenarien isolieren Holzfäller, Bauarbeiter, Bauer sowie absichtlich wartende Arbeiter. Ein Szenariowechsel oder Reset startet den jeweiligen definierten Weltzustand wieder bei Tick 0.

<code>/civtest</code> zeigt, dass das Plugin geladen ist.

<code>/civdebug</code> ist ein vorläufiger, rein lesender Entwicklungsbefehl. Er zeigt für die aktuelle Welt die Anzahl der Laufzeit- und persistent gespeicherten Civ-Gebäude sowie pro persistentem Gebäude ID, Typ, Anzahl gespeicherter Snapshot-Blöcke und semantische Trigger-Volume-Typen. Er verändert keinen Spielzustand und soll vor einem fertigen Release wieder entfernt oder deaktiviert werden.

Für die Mine erweitert `/civdebug mine` diese Diagnose gezielt. `info` zeigt die nächstgelegene Mine und ihre Tunnel-/Frontzustände. `logs on [Kategorien]` aktiviert strukturierte Generatorentscheidungen mit Gründen und den tatsächlich verwendeten Zufallswerten. `show` visualisiert Arbeitsfronten; `show anchors` ergänzt aktuelle Navigations-, Raum- und Infrastrukturanker, `show bounds` den 500×500-Designbereich und `show all` kombiniert alle Darstellungen. Diese Debuganzeigen sind nur für den anfragenden Spieler sichtbar und verändern weder Weltblöcke noch Generatorzustand.

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
- Der aktuelle Minenabbauer verwendet beim Wiedereintritt weiterhin `workplace_access` und danach `mine_tunnel_connector`. Die eigentliche Arbeit läuft auf persistenten `MineWorkFront`- und `MineRoom`-Einträgen des neuen Minensystems und verwendet direkt die variable Layer-3-Tunnelgeometrie. Eine Hauptstollen-Front nimmt maximal drei Miner auf, eine Seitenstollen-Front maximal zwei. Raumaushub kann bis zu drei Miner, Raumbau bis zu zwei aufnehmen. Bereits aktive normale Aufgaben mit freier Kapazität werden zuerst aufgefüllt; erst beim Öffnen neuer Arbeit entscheiden Prioritäten. Räume haben Priorität 8, Seitenstollen 6 und der Hauptstollen 4. Layer-6 V1 erzeugt Unterkunftsräume, Materiallager und kleine Nischen; die Räume werden erst nach offenem Tunnelanschluss ausgehoben und anschließend aus einfachen austauschbaren Prefab-Bauabschnitten aufgebaut.
- Die physische Minennavigation bleibt Hytales native `ReadPosition`/`Seek`-Navigation; der Wiedereintritt wird weiterhin über `workplace_access` und `mine_tunnel_connector` gestaffelt. Tunnelzugehörigkeit, Anchor-Routing und Surface-Recovery verwenden die deterministisch regenerierte Layer-3-`MineTunnelGeometry`. Trusted Anchors entstehen trotzdem ausschließlich aus tatsächlich durchlaufenen, exakt leeren `BlockType.EMPTY`-Positionen; geplante Geometrie allein gilt nicht als Sicherheitsbeweis.
- Während autonomer Minenarbeit überwacht Civ vorläufig, ob ein Miner außerhalb der Mine und der bekannten Tunnelräume auf oder über dem aktuellen Gebäudereferenzniveau gelangt. Bleibt dieser Zustand ungefähr 1,5 Sekunden bestehen, wird er über Hytales native Teleport-Komponente zum `mine_tunnel_connector` zurückgesetzt und kann von dort weiterarbeiten. Manuelle Spielerbefehle unterdrücken diese Recovery vollständig. Diese Y-basierte Oberflächenheuristik ist ausdrücklich temporär und muss bei terrainabhängiger Weltgeneration, Bergen und späteren Tunnelebenen durch eine terrainbewusste Erkennung ersetzt werden.
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

Der spielbare Minenabbauer verwendet die variable Layer-2/3/4-Minenplanung. Aus der stabilen Mine-ID wird deterministisch ein `MineNetwork` mit Haupt- und Seitenstollen geplant. Jeder geplante Tunnel besitzt eine persistente `MineWorkFront`; die konkrete Layer-3-Geometrie wird nach einem Restart deterministisch neu erzeugt, während die Hytale-Welt die Wahrheit darüber bleibt, welche Blöcke bereits entfernt wurden.

Ein Spieler kann einen unterirdischen Minenabbauer jederzeit manuell herausrufen. Autonome Arbeit und Front-Reservierung werden dabei freigegeben. Bei späterer Wiederaufnahme läuft der Miner erneut über `workplace_access` und `mine_tunnel_connector` und wählt dann aus den aktuell ausführbaren Tunnel-Fronten neu; er muss nicht starr zu seiner früheren Front zurückkehren.

Für Tunnelarbeit gilt weiterhin: aktive Fronten mit freier Kapazität werden zuerst aufgefüllt, eine Hauptstollen-Front fasst höchstens drei und eine Seitenstollen-Front höchstens zwei Miner, und beim Öffnen neuer normaler Abbauarbeit hat ein Seitenstollen Priorität 6 gegenüber dem Hauptstollen mit Priorität 4. Layer 5 ergänzt nun echte Infrastrukturarbeit: Miner bauen dynamische Holzstützen, Steintreppen, Minenbrücken und Beleuchtung blockweise mit 0,5 Sekunden pro Block. Verpflichtende Treppen/Brücken sind Priorität 10 und können den Weiterabbau unterbrechen beziehungsweise sperren; Licht ist Priorität 5. Layer 8 führt nun den gemeinsamen normalen Scheduler vollständig über Tunnel, Räume, wiederkehrende Supports/Lichter und Dekoration. Wartende ausführbare Aufgaben altern bei echten Öffnungsentscheidungen um +1 bis maximal 9; der Bonus wird pro konkretem Task persistent gespeichert. Priority 10 bleibt ausschließlich für akute Passierbarkeit.

Die Navigation verwendet den sicheren Anchor-Unterbau: Tatsächlich durchlaufene `BlockType.EMPTY`-Positionen werden in ungefähr 10 Blöcken Abstand persistiert. Für Untertageziele über 50 Blöcke Luftlinie kann der Miner zu einem bereits bekannten erreichbaren Anchor in Zielnähe teleportiert werden; bei einer Rückkehr von der Oberfläche ist dies erst nach Erreichen des `mine_tunnel_connector` erlaubt. `BLOCKED` und `ABORTED` werden aus Hytales nativem `NavState` gelesen und lösen zuerst genau einen nativen Repath aus. Scheitert dasselbe Arbeitsziel danach weiter, wird eine Arbeitsfront beziehungsweise Pflicht-Infrastruktur `BLOCKED` und der Miner wählt andere Arbeit; normale Support-/Lichtarbeit wird stattdessen übersprungen.

NPC-Ebene 6 behandelt außerdem problematische Weltgeometrie: konservativ überbrückbare Wasserlücken erhalten eine Pflicht-Brücke, nicht überbrückbare Lücken und Lava führen zu `ABANDONED`. Miner schwimmen in V1 nicht durch geflutete Arbeitskorridore. Normale Supports, Beleuchtung und optionale Dekoration dürfen bei ungeeignetem Wunschpunkt bis zu drei Slices vor oder zurück nach einer sicheren Ersatzposition suchen.

Layer 7 klassifiziert nun natürliche Höhlen aus den tatsächlich geladenen Hytale-Weltblöcken außerhalb des geplanten Tunnelkorridors. Kleine sichere Öffnungen werden ohne zusätzliche Aufgabe integriert. Große nutzbare natürliche Höhlen erscheinen als persistente natürliche Kammer im Minennetz und werden weder ausgehoben noch als Prefab gebaut. Die Gegenseite einer Brücke wird weiterhin nur entlang des geplanten Tunnels gesucht und muss mehrere sichere, trockene Gehspalten sowie geplante Fortsetzung besitzen.

## Geplante Mine- und Mehrminer-Zielmechanik

> **Status:** Tunnelplanung, variable Geometrie, Haupt-/Seitenstollen-Fronten, Layer-5-Infrastruktur, NPC-Hindernis-/Fehlerbehandlung, Layer-6-V1-Räume, Layer-7-Höhlen-/Gefahrenintegration sowie Layer-8-Atmosphäre und normales Aging sind umgesetzt. Hauptstollen erhalten dichte entwickelte Atmosphäre, Seitenstollen bleiben mit Fackeln, leichteren Stützen und sparsamer Deko rauer. Weitere authored Raumtypen, Rails und explizites Unblock/Recovery bleiben offen. Details: `docs/mine-design.md`, `docs/miner-npc-design.md` und die Layer-Implementierungsdokumente bis `docs/mine-layer8-implementation.md`. 

Die Hytale-unabhängigen Grundlagen des Mine-Overhauls sind bis einschließlich Layer 4 vorhanden: `MineNetwork` bildet Hauptstollen und verschachtelte Seitenstollen semantisch ab, `MinePathPlanner` plant variable Formphasen, `MineTunnelVoxelizer` erzeugt die konkrete sichere Voxelgeometrie und `MineNetworkGrowthPlanner` erzeugt daraus deterministische verzweigte Netzpläne mit sinkender Branch-/Weiterbauwahrscheinlichkeit, Mindestabständen, seltenen Kreuzungen und Main-Tunnel-Fairness. Der Live-`MinerWorkSystem` führt diese geplanten Tunnel über persistente WorkFronts aus.

Die neue Mine besteht nicht aus dauerhaft festen 4x4-Stollen. Der Haupttunnel liegt grob im Bereich 6–8 Blöcke Breite/Höhe, Seitentunnel grob bei 3–5 Blöcken. Formphasen verändern Querschnitt, Drift und Höhe schrittweise, sodass Arbeitsfronten immer die tatsächlich geplante Tunnelgeometrie verwenden.

Miner werden nicht global und nicht in persistenten Teams koordiniert. Die **zugewiesene Mine ist die Koordinationsgrenze**: Nur Miner derselben Mine konkurrieren oder kooperieren um deren Aufgaben. Andere Minen und andere Fraktionen besitzen ihre eigene unabhängige Arbeitsauswahl.

Eine Mine besitzt mehrere mögliche Aufgaben und offene Arbeitsfronten. Für V1 gilt als Auswahlprinzip:

1. **Priorität 10** ist akut und hat immer Vorrang. Sie ist ausschließlich für verpflichtende Sicherheits-/Passierbarkeitsarbeit reserviert; normale Aufgaben können höchstens Priorität 9 erreichen.
2. Gibt es keinen 10er-Job, werden zuerst **bereits aktive Aufgaben mit freier Kapazität** aufgefüllt. Das gilt bewusst auch dann, wenn eine noch nicht begonnene normale Aufgabe eine höhere Priorität zwischen 1 und 9 besitzt.
3. Unter mehreren aktiven Aufgaben mit freier Kapazität gewinnt die höhere Priorität, danach die nähere Aufgabe und schließlich ein Tie-Breaker.
4. Erst wenn keine aktive normale Aufgabe mehr freie Kapazität besitzt, wird die höchste wartende normale Aufgabe begonnen.

Der gemeinsame normale Mine-Task-Selektor umfasst Tunnel, Räume, wiederkehrende Infrastruktur und Layer-8-Dekoration. Basisprioritäten sind Raum 8, Seitenstollen 6, Support/Licht 5, Hauptstollen 4 und Dekoration 2. Wartende ausführbare Tasks altern pro Öffnungsentscheidung bis maximal 9; bereits aktive Arbeit mit freier Kapazität bleibt trotzdem zuerst zu füllen. Priority-10-Passierbarkeit wird separat akut behandelt. Layer 8 ergänzt im Hauptstollen Fässer, kleine Kisten, Holz, Erzmaterial, Ketten und Hängelaternen; Branches bleiben sparsamer und verwenden keine Hängelichter. Die Erzmaterial-Deko verwendet echte Eisen-, Kupfer- oder Golderzblöcke und bleibt daher normal abbaubar.

Dadurch entstehen Arbeitsgruppen automatisch statt durch feste Teamformationen. Eine normale Tunnel-Arbeitsfront hat zunächst Kapazität 2, Raumaushub bis 3, Raumbau meist 2 und einzelne Infrastruktur-/Dekorationsjobs normalerweise 1. Die Mine selbst bleibt zusätzlich durch ihre Gebäudephase begrenzt: Phase 1 = 1, Phase 2 = 2, Phase 3 = 3 Abbauer.

Mehrere Miner an derselben Arbeitsfront leisten echte parallele Arbeit an unterschiedlichen noch offenen Teilen derselben Excavation Slice. Sie dürfen nicht denselben Block gleichzeitig beanspruchen. Solche Block-Claims sind kurzfristige Koordination und keine festen linken/rechten Arbeitsplätze. Wenn ein Miner die Front verlässt, arbeitet der andere normal weiter und der freigewordene Kapazitätsplatz kann später neu besetzt werden.

Normale Arbeit wird bis zum Ende der aktuellen Work Unit weitergeführt. Priorität 10 darf dagegen sofort unterbrechen. Es werden nur so viele Miner abgezogen, wie der akute Job tatsächlich benötigt; freie Miner werden zuerst verwendet, danach möglichst Miner aus niedriger priorisierter normaler Arbeit. Nach dem Akutjob wird nichts zwangsweise wieder aufgenommen: Der Miner entscheidet erneut nach den normalen Regeln.

Aging schützt wartende normale Arbeit vor dauerhaftem Verhungern, maximal bis Priorität 9. Eine Aufgabe altert nicht weiter, solange bereits mindestens ein Miner aktiv daran arbeitet.

Unfertige Arbeitszustände, Fortschritt und Prioritäten gehören zur Mine beziehungsweise zum Task und sollen einen Server-Neustart überleben. Temporäre Miner-Zuordnungen, Arbeitsgruppen und Block-Claims werden nicht persistiert. Nach einem Restart wählen die Miner aus den wiederhergestellten offenen Arbeiten neu. Fertige Tasks werden aus dem aktiven/persistierten Task-System entfernt, sobald ihr dauerhaftes Ergebnis im MineNetwork und/oder in der Hytale-Welt repräsentiert ist.

Navigation Anchors entstehen nicht aus der geplanten Tunnelgeometrie, sondern erst an tatsächlich von einem Miner durchlaufenen, exakt leeren (`BlockType.EMPTY`) Blöcken und regulär in ungefähr 10 Blöcken Abstand. Junction-, Raum- und Brücken-Anker verwenden dieselbe physische Validierung. Civ speichert nur dieses kleine sichere semantische Netz; Hytale bleibt für den tatsächlichen lokalen Pfad zuständig. Bei mehreren gültigen Anchor-Routen wählt Civ die kürzeste sichere Graphroute.

Für einen Miner, der zuvor an die Oberfläche gerufen wurde, bleibt die autonome Rückkehr gestaffelt: `workplace_access -> mine_tunnel_connector -> Arbeitsposition`. Er läuft über Tage immer bis zum Connector und darf vorher nicht teleportiert werden. Erst am Connector beziehungsweise bei einem bereits unter Tage befindlichen Miner greift die Long-Distance-Regel: Liegt das nächste Ziel mehr als 50 Blöcke Luftlinie vom relevanten sicheren Bezugspunkt entfernt, kann Civ zum erreichbaren sicheren Anchor auf der gültigen Zielroute teleportieren, der dem Ziel am nächsten liegt. Direkt zur Arbeitsposition wird nie teleportiert; von dort übernimmt wieder Hytales native Navigation. Dasselbe Prinzip gilt für lange Untertagewege zurück Richtung Oberfläche, während der tatsächliche Ausgang weiterhin über den Connector führt.

Navigation Failure stützt sich primär auf Hytales nativen `NavState`: Bei `BLOCKED` oder `ABORTED` wird zunächst genau eine native Pfadneuberechnung angefordert. Scheitert dasselbe Arbeitsziel danach weiterhin, meldet der Navigation-Adapter dies an den Miner-Arbeitsfluss zurück. Eine betroffene Abbaufront oder Pflicht-Infrastruktur wird `BLOCKED`, normale Support-/Lichtarbeit wird übersprungen. Ein sicherer Anchor-Teleport im Hauptkorridor bleibt eine Positions-Recovery, öffnet aber die fehlgeschlagene Arbeit nicht automatisch erneut. `BLOCKED` wird in V1 nicht periodisch wiederprobiert.

## Entwicklungswerkzeug: Browser-Simulation-Lab

Das öffentliche Browser-Lab dient der Prüfung aufgezeichneter Core-Szenarien. Ein Branch-/Lauf-/Szenario-Katalog öffnet einen konkreten Quellcode-Stand; freie Kamera und Spectator-Sicht machen Minenhohlräume untersuchbar. Start/Pause, Einzelschritt, Zeitleiste und Inspector zeigen aufgezeichnete Blockänderungen und Arbeiterzustände. Touch-Steuerung ermöglicht dieselbe Beobachtung auf Mobilgeräten.

Das Lab verändert kein Gameplay und führt keine neuen Befehle während eines Replays aus. Seine vereinfachte Grafik und Fake-Bewegung sind Entwicklungshilfen; Hytales Navigation, Grafik und Physik bleiben Engine-Verträge.


## Entwicklungszugriff auf die normale Spielwelt

Der lokale Einzelspieler-Besitzer kann mit `/civmcp on` eine lokale Entwicklungsverbindung freigeben und mit `/civmcp off` schließen. Ein verbundener Agent kann nahe NPCs beobachten, Civ-Bewohner gezielt auswählen und normale Bewegungs-/Berufsaufträge erteilen oder Test-NPCs erzeugen. Reset löscht ausschließlich selbst erzeugte geladene NPCs. Diese Aktionen wirken auf den echten Spielstand; der Agent besitzt oder stoppt den Spielprozess nicht. Einrichtung und noch offene praktische Client-Abnahme: [local-mcp.md](local-mcp.md).

## Mine persistence and performance (Layer 10)

Mines persist their semantic tunnel network, work fronts, rooms, trusted navigation anchors, completed infrastructure task IDs and normal-task priority bonuses in a native Hytale world resource. Excavated voxels are stored by the Hytale world, not duplicated as Civ mine saves; planned geometry is deterministically regenerated. Layer 10 avoids rewriting unchanged network snapshots and improves large anchor batch updates without altering gameplay or requiring an additional database. Native autosave and world shutdown remain responsible for durable resource writes. Rails remain deferred.

## Minenabbauer: Aufenthaltsräume (NPC-Ebene 7)

Ohne ausführbare Minenarbeit gehen Miner selbstständig in die nächstgelegene nutzbare, fertiggestellte Unterkunft. Dort warten sie ohne Pausen- oder Schlafsimulation; mehrere Miner können denselben Raum nutzen. Gibt es keinen passenden Raum, gehen sie über den Tunnelanschluss zum Mineneingang. Neue Arbeit oder ein manueller Spielerbefehl unterbrechen den Leerlauf sofort. Die normale Bewegung und sichere Langstrecken-Anker-Teleports bleiben Hytale-basiert. Materiallager, Nischen und natürliche Kammern haben noch kein eigenständiges NPC-Verhalten. Warenlogistik und komplexe Unterkunftsaktivitäten sind spätere Features.

## Minenabbauer: Neustart und Weiterarbeit (NPC-Ebene 8)

Nach einem Serverneustart oder dem erneuten Laden eines entfernten NPCs bleiben Minenzugehörigkeit, Beruf, der bereits gespeicherte Tunnel-/Raum-/Infrastrukturfortschritt und sichere Mine-Anker erhalten. Miner nehmen von ihrer Hytale-Position aus automatisch verfügbare Arbeit auf und verteilen sich entsprechend den normalen Prioritäten und Arbeitslimits neu. Vorherige Arbeitsreservierungen, angefangene Abbauzeiten und alte manuelle Zielbefehle werden nicht wiederhergestellt. Bereits unter Tage stehende Miner werden nicht für den Wiedereintritt zum Mineneingang zurückgeschickt; ohne Arbeit nutzen sie die Aufenthaltsregeln von NPC-Ebene 7. Nicht geladene Miner arbeiten nicht im Hintergrund. Bei Abstürzen kann Fortschritt seit dem letzten gespeicherten Hytale-Zustand verloren gehen; die Mod führt keine zusätzliche Datenbank.


### Warten bei ausgelasteter Mine

Sind alle aktuell ausführbaren Minenaufgaben vollständig mit Arbeitern belegt, bleiben weitere bereits unter Tage befindliche Miner vor Ort und warten auf freie Kapazität. Erst wenn keine ausführbare Arbeit vorhanden ist, gelten die bisherigen Aufenthaltsraum- oder Eingangsrouten. Hauptstollen haben maximal drei Miner pro Front; Seitenstollen weiterhin zwei.


Für Stützen, Beleuchtung und Dekoration nutzt die Mine die gemeinsame native Hytale-Blockplatzierung mit der zur Zielblockposition gehörenden Chunk-Sektionsreferenz. Der Baufortschritt erfolgt nur bei erfolgreicher Platzierung; unpassende oder fehlende Zielsektionen gelten nicht als abgeschlossene Arbeit.


Aktuelle Minen-Balance: Pro aktivem Miner dauert der Aushub eines Tunnel- oder Raumblocks `30/128` Sekunden (doppelte Abbaugeschwindigkeit). Der Hauptstollen bietet drei gleichzeitige Abbauplätze, Seitenstollen zwei. Normale Hauptstollen-Beleuchtung verwendet explizit `Deco_Lantern`. Dekorations-Truhen (`Furniture_Crude_Chest_Small`) sind auf 5 % der Hauptstollen-Dekowürfe und 10 % der Seitenstollen-Dekowürfe reduziert; andere dekorative Objekte behalten ihre regulären Platzierungsregeln. Bereits platzierte Objekte werden nicht entfernt.

Für Testwelten mit einem alten, wegen verfrühtem `BUILD_STEP` aufgegebenen Hauptstollen existiert `/civdev mine-retry-stair <mine-id>`: Der Befehl kann ausschließlich die passende `ABANDONED`-Hauptfront an einem noch offenen Treppenübergang gezielt wieder freigeben. Andere gefährliche oder gesperrte Fronten bleiben unberührt.


Minenarbeiter bauen optionale Stützen, Lampen und Dekoration erst, wenn im Umkreis von einem Block um jede Zielposition keine noch festen Blöcke einer geplanten, nicht abgeschlossenen Abbaufront liegen. Bei Konflikt bleibt die Aufgabe erhalten und wird erst nach Fortschritt der tatsächlich störenden Front erneut geprüft. Treppen und Brücken behalten ihre verpflichtende Passierbarkeitssteuerung.


**Temporär deaktiviert:** Minentreppen und -stufen durch `ENABLE_MINE_STEPS=false`. Haupt- und Seitenstollen behalten Höhenunterschiede, aber es gibt keine `BUILD_STEP`-Arbeit, Ghost-Arbeitsmarker oder Stufen-Pflichtsperren. Bestehende Treppen und `ABANDONED`-Fronten bleiben unverändert. Natürliche Kanten können NPCs weiterhin behindern.
