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

## Farm

Die erste umgesetzte Gebäudedomäne ist bewusst konkret und noch kein spekulatives allgemeines Gebäudesystem.

- Ein Gebäude-Prefab muss mindestens einen Eingang beziehungsweise Arbeitszugang definieren. Eine Farm darf mehrere besitzen.
- Eine Farm hat genau einen Bauernplatz.
- Wird ein beanspruchter Civ-NPC einer Farm zugewiesen, erhält dieser Bewohner den Beruf Bauer.
- Die aktuelle Farm-Zuweisung verwendet den Zugang mit der geringsten Luftlinienentfernung zum zugewiesenen Bewohner. Erreicht der Bewohner diesen Zugang, wechselt er in den logischen Zustand ARBEITET_INNEN.
- Nach fünf Sekunden aktiver Arbeit in der Farm wird eine Einheit Weizen produziert.
- Nach jeder produzierten Einheit Weizen muss der Bauer das Gebäude verlassen, bevor der nächste Produktionsschritt beginnen kann.
- Das aktuelle Außenziel liegt bei der fest ausgerichteten Farm zwei Blöcke südlich des gewählten Zugangs.
- Die Produktion stoppt exakt bei zehn Einheiten lokalem Weizen.
- Laufzeit zählt nicht zu den fünf Sekunden Arbeitszeit.
- Farm-Platzierung, Farm-Zuweisung und Weizenbestand bleiben aktuell nur zur Laufzeit erhalten und werden nach einem Server- oder Plugin-Neustart nicht wiederhergestellt. Der Beruf des NPCs wird dauerhaft gespeichert, aber noch keine dauerhafte Farm-Referenz.
