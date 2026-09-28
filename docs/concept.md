# Produktkonzept

Hytale Civ ist als Strategie- und Simulations-Plugin für Hytale geplant. Einzelne Bewohner, lokale Warenbestände, Produktion und Logistik bilden den Kern des Spielerlebnisses.

## Zukünftige Richtung

Spätere Meilensteine können dauerhafte Civ-Bewohner, vollständige Routenplanung, Gebäudebau, Bewohner mit Berufen und Bedürfnissen, physische Waren, lokale Lager, Produktionsketten und Logistik umfassen.

## Aktueller Umfang

Der aktuelle Produkt-Meilenstein ist ein RTS-Prototyp mit steuerbaren NPCs zusätzlich zum ursprünglichen Plugin-Smoke-Test.

<code>/civtest</code> zeigt, dass das Plugin geladen ist.

<code>/civrtstest</code> schaltet eine feste, schräge RTS-Kamera mit sichtbarem Mauszeiger ein oder aus. Der Spieler wechselt dabei nicht in den Spectator-Modus.

<code>/civclaim</code> aktiviert den nächsten Linksklick unabhängig vom Kameramodus. Wird anschließend in First Person oder RTS ein vorhandener NPC angeklickt, wird er als Civ-Bewohner initialisiert beziehungsweise für die aktuelle Steuerung beansprucht oder wieder freigegeben. Beim ersten Initialisieren erhält er dauerhaft ein Geschlecht, einen dreiteiligen Wikinger-Namen und den Zustand arbeitslos. Ein erneuter Claim würfelt diese Identität nicht neu aus. Der vollständige Bewohnername wird als sichtbare Namensplakette des NPCs verwendet.

In First Person öffnet Rechtsklick/Benutzen auf einen beanspruchten Civ-Bewohner dessen Aktionsmenü. Darüber kann aktuell wie im RTS der Beruf Holzfäller zugewiesen werden.

Während der RTS-Modus aktiv ist:

- Ein Linksklick auf eine beanspruchte Civ-Einheit wählt genau diese Person aus.
- Ein Linksklick auf freien Boden hebt die Auswahl auf.
- Nicht beanspruchte Einheiten können nicht ausgewählt werden.
- Ein Rechtsklick auf den aktuell ausgewählten Civ-Bewohner öffnet dessen Aktionsmenü.
- Die erste verfügbare Aktion weist den Beruf Holzfäller zu.
- Ein Rechtsklick auf einen Bodenblock gibt dem ausgewählten Bewohner weiterhin ein direktes Bewegungsziel.
- <code>/civbuild</code> öffnet den Gebäudekatalog. Solange dieser geöffnet ist, sind normale RTS-Interaktionen mit der Welt pausiert.
- <code>/civwiki</code> öffnet das Ingame-Wiki.
- Gebäude im Katalog sind alphabetisch nach ihrem Anzeigenamen sortiert.

RTS ist eine Bedienungsart und keine Voraussetzung für die Civ-Simulation. Bewohneridentität und Berufsdaten sind unabhängig von einer RTS-Session persistent; Auswahl, aktuelle Steuerungsansprüche und Arbeitsausführung bleiben vorläufige Laufzeitzustände.

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

Im RTS-Modus öffnet <code>/civbuild</code> den Gebäudekatalog. Wird **Farm** ausgewählt, schließt sich der Katalog und die Platzierung beginnt. Eine Vorschau der Farm folgt der Position unter dem Mauszeiger. Linksklick versucht die Farm zu platzieren, Rechtsklick bricht die Platzierung ab. <code>/civfarm</code> bleibt als Debug-Abkürzung für denselben Platzierungsmodus erhalten.

Der sichtbare Boden der Farm wird in das Gelände eingelassen und nicht einfach oben darauf gesetzt. Eine Platzierung ist nur möglich, wenn die Fläche ausreichend gestützt ist, keine Löcher oder Flüssigkeiten enthält, der benötigte Raum frei ist, Zugänge nicht blockiert sind und sich die Fläche nicht mit einem anderen Civ-Gebäude überschneidet.

Nach dem Platzieren kann ein beanspruchter Civ-NPC ausgewählt und über den Arbeitsbereich der Farm als Bauer zugewiesen werden. Gibt es mehrere mögliche Zugänge, wird aktuell der zum Bewohner nächstgelegene verwendet.

Der derzeitige Produktionsablauf ist bewusst klein gehalten:

1. Der Bauer läuft zum Eingang der Farm.
2. Sobald er den Eingang erreicht, gilt er spielerisch als im Gebäude.
3. Er arbeitet fünf Sekunden in der Farm.
4. Die Farm erhält eine Einheit Weizen in ihrem lokalen Bestand.
5. Der Bauer verlässt das Gebäude.
6. Hat die Farm weniger als zehn Weizen, kehrt er zurück und wiederholt den Ablauf.
7. Bei zehn Weizen bleibt der Bauer draußen und die Produktion stoppt.

Die Laufzeit kommt zusätzlich zu den fünf Sekunden aktiver Produktionszeit hinzu. Weizen existiert aktuell nur als lokaler Bestand der Farm. Physische Weizengegenstände, Eingangswaren, Träger und Lagerlieferungen sind noch nicht umgesetzt.

## Ingame-Wiki

<code>/civwiki</code> öffnet ein modales Ingame-Wiki mit vier Bereichen: **Berufe**, **Ressourcen**, **Gebäude** und **Tiere**.

Das Wiki beschreibt nur bereits umgesetztes Civ-Verhalten und verknüpft verwandte Einträge miteinander. Aktuell gibt es Einträge zu Holzfäller, Bauer, Holz, Weizen und Farm. Der Bereich Tiere weist ausdrücklich darauf hin, dass Tiere derzeit noch keine Civ-spezifische Gameplay-Rolle besitzen.

Wird das Wiki während einer aktiven Farm-Platzierung geöffnet, wird die Platzierung vorher abgebrochen.
