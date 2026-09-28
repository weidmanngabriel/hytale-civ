# Domäne

Diese Datei ist der maßgebliche Ort für verifizierte Begriffe, Regeln, Invarianten, Wertebereiche und Zustandsübergänge der Hytale-Civ-Domäne.

Eine Regel gehört nur dann hierher, wenn sie bewusst Teil des Spielmodells ist und durch eine aktuelle Produktentscheidung, eine verlässliche Beobachtung oder eine ausdrückliche Benutzeranweisung gestützt wird. Bestehender Code allein reicht nicht als Beleg dafür aus, dass ein Verhalten eine Domänenregel ist.

Unbekanntes Verhalten bleibt unbekannt, bis es entschieden oder verifiziert wurde. Implementierungsdetails, vorläufiges Debug-Verhalten oder Einschränkungen der Hytale-Engine dürfen nicht ohne ausdrücklichen Grund zu dauerhaften Domänenregeln werden.

## Aktueller Domänenstatus

Das Projekt befindet sich noch in einem Engine-Validierungs-Meilenstein. Viele geplante Simulationsbereiche wie dauerhafte Bewohner, Bedürfnisse, allgemeine Inventare, Logistik, Familien und Wirtschaft besitzen noch keine vollständig umgesetzten Domänenregeln. Farm und Holzfäller sind die ersten umgesetzten Berufsausschnitte.

Der aktuelle Anspruchs- und Bewegungszustand von NPCs ist bewusst nur ein vorläufiger Integrationstest und noch kein dauerhaftes Civ-Besitzsystem oder vollständiger Bewohner-Lebenszyklus. Berufsdaten sind der erste Bewohnerzustand, der direkt an einer einzelnen Einheit dauerhaft gespeichert wird.

## Bewohner

- Ein Civ-Bewohner trägt seine dauerhaften Bewohnerdaten direkt an seiner Hytale-Entität. Die native persistente Hytale-UUID ist seine technische Entity-Identität; Civ führt dafür keine zweite UUID ein.
- Aktuell existiert genau eine Civ-Fraktion: Wikinger. Solange keine zweite Fraktion existiert, wird keine zusätzliche Fraktions-ID pro Bewohner gespeichert.
- Jeder neu initialisierte Bewohner erhält genau ein Geschlecht (männlich oder weiblich) sowie genau einen Vor-, Mittel- und Nachnamen aus dem dazugehörigen Wikinger-Namenspool. Diese konkreten Namen werden gespeichert und bei späteren Claims nicht neu ausgewürfelt.
- Ein Bewohner besitzt immer einen aktiven Berufszustand. Der initiale Zustand ist ARBEITSLOS (<code>UNEMPLOYED</code>).
- ARBEITSLOS besitzt keine Berufserfahrung. Bauer und Holzfäller behalten ihre jeweilige eigene, nichtnegative Berufserfahrung auch nach einem Berufswechsel.
- Bewohneridentität und Berufsdaten sind unabhängig davon, ob ein Spieler First Person oder RTS verwendet.
- Ein bewusstes Freigeben entfernt die Civ-Bewohnerzugehörigkeit wieder; der NPC kehrt in seinen nativen NPC-Zustand zurück.
- Ein manueller RTS-Bewegungsbefehl hat Vorrang vor automatischer Berufsbewegung. Nach Erreichen des manuellen Ziels darf die Berufsautomatik wieder übernehmen.
- Eine dauerhafte Arbeitsplatzidentität ist noch keine Domänenregel, weil platzierte Civ-Gebäude noch keine stabile dauerhafte Identität besitzen.

## Geplante Domänenbereiche

Sobald konkrete Features umgesetzt werden, werden ihre verifizierten Regeln hier in eigenen Abschnitten festgehalten. Erwartete Bereiche sind:

- Bewohner und Identität
- Berufe, Qualifikation und Erfahrung
- Bedürfnisse und autonomes Verhalten
- Haushalte und Familien
- Gebäude und Bau
- lokale Inventare und physische Waren
- Produktion und Rezepte
- Logistik und Transport
- Technologie und Freischaltungen
- Diplomatie und Kampf
- Missionen und Szenariozustand

Die Detailregeln dieser Bereiche werden nicht vorab festgelegt, bevor das zugehörige Produktverhalten entschieden ist.

## Holzfäller

- Holzfäller ist ein Beruf, der aktuell einem ausgewählten und beanspruchten Civ-NPC zugewiesen werden kann.
- Nach der Zuweisung sucht der Holzfäller selbstständig nach einem Baum in der Nähe, statt auf eine Gebäudezuweisung zu warten.
- Der Holzfäller läuft neben den Baum, bevor er mit der Arbeit beginnt.
- Das Fällen muss Hytales normales Ernte- und Physikverhalten verwenden, statt den Baum nur in Civ-eigenem Simulationszustand zu löschen.
- Nach dem Fällen sucht der Holzfäller einen weiteren Baum in der Nähe.
- Arbeitsbereiche, Holztransport, Lagerlieferung, Werkzeuge, Erfahrung und dauerhafte Arbeitsplatzspeicherung sind noch keine Domänenregeln.

## Gebäudeplatzierung

- Der Zustand einer RTS-Gebäudeplatzierung gehört immer zum einzelnen Spieler. Vorschau oder Abbruch eines Spielers dürfen den Platzierungszustand eines anderen Spielers nicht verändern.
- Eine Platzierungsvorschau ist nur eine visuelle Hilfe. Die tatsächliche Veränderung der gemeinsamen Welt wird beim Bestätigen erneut geprüft.
- Der Gebäudeboden wird einen Block in das anvisierte Gelände eingelassen, damit seine fertige Oberfläche nicht einen vollen Block über dem umliegenden Gelände liegt.
- Die aktuellen Platzierungsregeln verlangen durchgehend gestützten Boden, keine Flüssigkeiten oder Löcher in der ersetzten Bodenschicht, freien benötigten Gebäuderaum, freie Zugänge und keine Überschneidung mit einem vorhandenen Civ-Gebäude.
- Jede platzierte Gebäudeinstanz muss die ursprünglichen Weltblöcke behalten, die durch ihren eingelassenen Boden ersetzt wurden, damit ein späterer Abriss das vorherige Gelände wiederherstellen kann.
- Dieser Geländeschnappschuss besitzt dieselbe Lebensdauer wie das platzierte Gebäude. Solange Gebäude nicht dauerhaft gespeichert werden, ist auch der Schnappschuss nur laufzeitgebunden.

## Gebäude und lokale Waren

- Für physische Warenbestände eines Gebäudes soll Hytales natives Container-/Inventarsystem verwendet werden, sofern der jeweilige Gebäudetyp einen geeigneten Containerblock besitzt. Ein paralleler Civ-Zähler oder eine eigene Text-/JSON-Datei ist dafür nicht das bevorzugte Modell.
- Die aktuelle Serverversion besitzt serialisierbare Block-Container im ChunkStore. Damit ist ein echter Hytale-Container der bevorzugte Kandidat für beispielsweise lokal bei einer Farm gelagerten Weizen.
- Räumliche Gebäudefunktionen sollen nach Möglichkeit im Prefab mit nativen Hytale-Mechanismen beschrieben werden. Trigger Volumes dürfen dafür Civ-Tags tragen, können aber zusätzlich native Volume-Logik nutzen.
- Eine dauerhafte allgemeine Gebäudeidentität und eine persistente Bewohner-zu-Gebäude-Zuweisung sind noch nicht als Domänenmodell entschieden. Hytale stellt persistente Referenz-, Meta- und ECS-Infrastruktur bereit; der konkrete Lifecycle für Civ-Gebäude muss jedoch noch praktisch validiert werden.

## Farm

Die erste umgesetzte Gebäudedomäne bleibt ein Engine-Validierungs-Slice. Die bestehende Implementierung ist nicht automatisch die Produktdefinition der späteren Farm.

Verifiziert bzw. aktuell gewollt:

- Das Farm-Prefab definiert mindestens einen räumlichen Arbeitszugang über ein natives Hytale Trigger Volume. Mehrere Zugänge sollen möglich bleiben.
- Ein zugewiesener Bewohner erhält den Beruf Bauer.
- Farmarbeit soll möglichst sichtbar und physisch in der Hytale-Welt stattfinden und native Hytale-Systeme für Bewegung, Trigger, Interaktionen und Waren verwenden, bevor Civ äquivalente eigene Mechanismen einführt.
- Lokaler Weizen soll, sobald die Farmproduktion entsprechend umgebaut wird, bevorzugt als physischer Bestand in einem geeigneten nativen Hytale-Container der Farm liegen statt nur als Integer im Java-`FarmBuilding`.

Nur aktueller Prototyp, **keine dauerhafte Domänenregel**:

- genau ein Bauernplatz;
- Auswahl des Zugangs nur nach Luftlinienentfernung;
- logischer Zustand ARBEITET_INNEN als eigentlicher Produktionsort;
- fünf Sekunden Arbeitszeit pro Weizen;
- eine Einheit Weizen pro Zyklus;
- Verlassen des Gebäudes nach jeder Einheit;
- festes Außenziel zwei Blöcke südlich des Zugangs;
- Produktionsstopp bei zehn Einheiten.

Diese Punkte bleiben als Beschreibung des derzeit laufenden Codes relevant, dürfen aber ohne erneute Produktentscheidung nicht als Zielverhalten für die nächste Farm-Iteration verwendet werden. Das in `civilizations-poc` vorhandene Modell mit sichtbarer Feldarbeit, Feldentwicklung, Ernte und Rücktransport ist eine Referenz für die weitere Produktentscheidung, nicht automatisch eine Regel dieses Projekts.
