# Produktkonzept

Hytale Civ ist als Strategie- und Simulations-Plugin für Hytale geplant. Einzelne Bewohner, lokale Warenbestände, Produktion und Logistik bilden den Kern des Spielerlebnisses.

## Zukünftige Richtung

Spätere Meilensteine können dauerhafte Civ-Bewohner, vollständige Routenplanung, Gebäudebau, Bewohner mit Berufen und Bedürfnissen, physische Waren, lokale Lager, Produktionsketten und Logistik umfassen.

## Aktueller Umfang

Der aktuelle Produkt-Meilenstein ist ein RTS-Prototyp mit steuerbaren NPCs zusätzlich zum ursprünglichen Plugin-Smoke-Test.

<code>/civtest</code> zeigt, dass das Plugin geladen ist.

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

Der erste Arbeitsablauf konzentriert sich bewusst auf die sichtbare Interaktion in der Welt:

1. Der Holzfäller sucht in der Nähe nach einem geeigneten Baum.
2. Er läuft zu einer freien Position neben dem Stamm.
3. Er führt für kurze Zeit eine Fällarbeit aus.
4. Der unterste Stammblock wird gefällt.
5. Normale Hytale-Drops sowie das übliche Verhalten des Baums bleiben erhalten.
6. Danach sucht der Holzfäller den nächsten Baum und wiederholt den Ablauf.

Arbeitsbereiche, das Tragen von Holz, Lagerlieferungen und eine vollständige dauerhafte Arbeitsplatzzuweisung sind noch nicht Teil dieses Umfangs.

## Farm-Vertical-Slice

> **Status:** Der derzeit spielbare Ablauf mit fünf Sekunden Innenarbeit, abstraktem lokalem Weizen und Stopp bei zehn Einheiten ist ein Engine-Validierungsprototyp, nicht die festgelegte Zielmechanik der Farm. Die nächste Produktiteration soll sichtbare Feldarbeit und Hytales native Trigger-, NPC- und Containermechanismen bevorzugen. Lokale Waren sollen nach Möglichkeit in echten Hytale-Containern liegen statt in parallelen Civ-Zählern.

Im RTS-Modus öffnet <code>/civbuild</code> den Gebäudekatalog. Wird **Farm** ausgewählt, schließt sich der Katalog und die Platzierung beginnt. Nach der Auswahl wird das Prefab an Hytales natives Paste Tool übergeben. Damit soll dieselbe Ghost-Vorschau und Cursor-Platzierung verwendet werden, die der normale Hytale-Prefab-Browser beim Verwenden eines Prefabs zeigt. Die konkrete Runtime-UX dieses Pfads wird mit Farm und Feld im Client validiert. <code>/civfarm</code> bleibt als Debug-Abkürzung für denselben Platzierungsmodus erhalten.

Die Creator-Prefabs werden ohne zusätzlichen Civ-Höhenoffset an das native Paste Tool übergeben; dessen Anchor-Position ist für die sichtbare Platzierung maßgeblich. Eigene Civ-Platzierungsregeln dürfen erst wieder vor den Commit geschaltet werden, wenn der native Paste-Commit zuverlässig abgefangen beziehungsweise validiert werden kann.

Nach dem Platzieren kann ein beanspruchter Civ-NPC ausgewählt und über den Arbeitsbereich der Farm als Bauer zugewiesen werden. Gibt es mehrere mögliche Zugänge, wird aktuell der zum Bewohner nächstgelegene verwendet.

Der erste zusammenhängende Produktionsablauf verwendet eine fertig gebaute Farm und ein separat fertig gebautes Weizenfeld:

1. Der Spieler baut Farm und Weizenfeld über `/civbuild`.
2. Ein Bauer wird über den Arbeitsbereich der Farm zugewiesen. Dafür muss mindestens ein fertiges Weizenfeld vorhanden sein.
3. Der Bauer läuft zuerst zur Farm und danach zum nächstgelegenen fertigen Weizenfeld.
4. Auf dem Feld arbeitet er fünf Sekunden.
5. Danach erhält die Farm eine Einheit Weizen im vorläufigen lokalen Bestand und der Bauer kehrt zur Farm zurück.
6. Unter zehn Weizen beginnt von dort der nächste Gang zum Feld.
7. Bei zehn Weizen endet der aktuelle Produktionslauf.

Die Laufwege kommen zusätzlich zu den fünf Sekunden Feldarbeit hinzu. Weizen existiert in diesem ersten Build weiterhin nur als lokaler Bestand der Farm. Physische Weizengegenstände, Eingangswaren, Träger und Lagerlieferungen sind noch nicht umgesetzt.

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
