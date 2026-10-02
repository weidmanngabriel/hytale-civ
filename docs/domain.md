# Domäne

Diese Datei ist der maßgebliche Ort für verifizierte Begriffe, Regeln, Invarianten, Wertebereiche und Zustandsübergänge der Hytale-Civ-Domäne.

Eine Regel gehört nur dann hierher, wenn sie bewusst Teil des Spielmodells ist und durch eine aktuelle Produktentscheidung, eine verlässliche Beobachtung oder eine ausdrückliche Benutzeranweisung gestützt wird. Bestehender Code allein reicht nicht als Beleg dafür aus, dass ein Verhalten eine Domänenregel ist.

Unbekanntes Verhalten bleibt unbekannt, bis es entschieden oder verifiziert wurde. Implementierungsdetails, vorläufiges Debug-Verhalten oder Einschränkungen der Hytale-Engine dürfen nicht ohne ausdrücklichen Grund zu dauerhaften Domänenregeln werden.

## Aktueller Domänenstatus

Das Projekt befindet sich noch in einem Engine-Validierungs-Meilenstein. Viele geplante Simulationsbereiche wie dauerhafte Bewohner, Bedürfnisse, allgemeine Inventare, Logistik, Familien und Wirtschaft besitzen noch keine vollständig umgesetzten Domänenregeln. Farm und Holzfäller sind die ersten umgesetzten Berufsausschnitte.

Der aktuelle Anspruchs- und Bewegungszustand von NPCs ist bewusst nur ein vorläufiger Integrationstest und noch kein dauerhaftes Civ-Besitzsystem oder vollständiger Bewohner-Lebenszyklus. Berufsdaten und eine optionale Arbeitsplatzzuweisung sind Bewohnerzustände, die direkt an einer einzelnen Einheit dauerhaft gespeichert werden.

## Bewohner

- Ein Civ-Bewohner trägt seine dauerhaften Bewohnerdaten direkt an seiner Hytale-Entität. Die native persistente Hytale-UUID ist seine technische Entity-Identität; Civ führt dafür keine zweite UUID ein.
- Aktuell existiert genau eine Civ-Fraktion: Wikinger. Solange keine zweite Fraktion existiert, wird keine zusätzliche Fraktions-ID pro Bewohner gespeichert.
- Jeder neu initialisierte Bewohner erhält genau ein Geschlecht (männlich oder weiblich) sowie genau einen Vor-, Mittel- und Nachnamen aus dem dazugehörigen Wikinger-Namenspool. Diese konkreten Namen werden gespeichert und bei späteren Claims nicht neu ausgewürfelt.
- Ein Bewohner besitzt immer einen aktiven Berufszustand. Der initiale Zustand ist ARBEITSLOS (`UNEMPLOYED`).
- ARBEITSLOS besitzt keine Berufserfahrung. Bauer und Holzfäller behalten ihre jeweilige eigene, nichtnegative Berufserfahrung auch nach einem Berufswechsel.
- Bewohneridentität und Berufsdaten sind unabhängig davon, ob ein Spieler First Person oder RTS verwendet.
- Ein bewusstes Freigeben entfernt die Civ-Bewohnerzugehörigkeit wieder; der NPC kehrt in seinen nativen NPC-Zustand zurück.
- Ein manueller RTS-Bewegungsbefehl hat Vorrang vor automatischer Berufsbewegung. Nach Erreichen des manuellen Ziels darf die Berufsautomatik wieder übernehmen.
- Ein Bewohner kann optional die stabile ID genau eines Civ-Gebäudes als Arbeitsplatz referenzieren. Diese Arbeitsplatz-ID ist persistent und dient unter anderem dazu, den Bewohner im Gebäude-Interface als dort angestellten Arbeiter aufzulisten.
- Das Auswählen eines Arbeiters im Gebäude-Interface und ein anschließender manueller Bewegungsbefehl lösen die Arbeitsplatzzuweisung nicht automatisch. Der Bewohner bleibt dem Gebäude zugeordnet; der manuelle Befehl unterbricht nur vorübergehend die autonome Arbeit.

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
- Arbeitsbereiche, Holztransport, Lagerlieferung, Werkzeuge und Erfahrung sind noch keine vollständigen Domänenregeln.

## Gebäudeplatzierung

- Der Zustand einer RTS-Gebäudeplatzierung gehört immer zum einzelnen Spieler. Vorschau oder Abbruch eines Spielers dürfen den Platzierungszustand eines anderen Spielers nicht verändern.
- Eine Platzierungsvorschau ist nur eine visuelle Hilfe. Die tatsächliche Veränderung der gemeinsamen Welt wird beim Bestätigen erneut geprüft.
- Die Civ-Bauposition liegt für die aktuellen Creator-Prefabs einen Block unter der vom nativen Paste Tool gemeldeten Cursorposition, damit die fertige Bodenebene bündig in das Gelände eingelassen werden kann.
- Die aktuellen Platzierungsregeln verlangen durchgehend gestützten Boden, keine Flüssigkeiten oder Löcher in der ersetzten Bodenschicht, freien benötigten Gebäuderaum, freie Zugänge und keine Überschneidung mit einem vorhandenen Civ-Gebäude.
- Jede platzierte Gebäudeinstanz muss die ursprünglichen Weltblöcke behalten, die durch ihren eingelassenen Boden ersetzt wurden, damit ein späterer Abriss das vorherige Gelände wiederherstellen kann.
- Dieser Geländeschnappschuss besitzt dieselbe Lebensdauer wie das platzierte Gebäude und wird zusammen mit der Civ-Gebäudeinstanz gespeichert.

## Gebäude, Phasen und Arbeiterplätze

- Jedes fertiggestellte Civ-Gebäude besitzt eine stabile Civ-Gebäude-ID, einen Gebäudetyp und eine aktuelle Phase. Diese Daten werden persistent gespeichert.
- Neue Gebäude starten aktuell in Phase 1. Das eigentliche Upgrade-Gameplay ist noch nicht umgesetzt.
- Eigenschaften, die vom Gebäudetyp und der Phase abhängen, werden aus einer gemeinsamen Gebäudetyp-Definition gelesen und nicht als UI-Sonderregeln pro Gebäudeart verdoppelt.
- `workerCapacity` ist eine solche Typ-/Phasen-Eigenschaft. In diesem Slice ist sie ausschließlich Metadaten: sie wird gespeichert beziehungsweise abgeleitet und im Interface angezeigt, aber noch nicht zur Begrenzung oder Validierung von Arbeitsplatzzuweisungen verwendet.
- Für die Mine gilt als Produktregel: Phase 1 besitzt 1 Abbauerplatz, Phase 2 besitzt 2 Abbauerplätze, Phase 3 besitzt 3 Abbauerplätze.
- Für bereits vorhandene Prototyp-Gebäude werden derzeit passende Metadaten mitgeführt (Farm: 1 Arbeiterplatz in Phase 1; Weizenfeld: 0). Diese Werte beschreiben den aktuellen Slice und sind nicht automatisch eine endgültige Ausbaukurve dieser Gebäudetypen.
- Das Gebäude-Interface erzeugt seine sichtbaren Arbeiterplätze aus `workerCapacity`. Zugeordnete Bewohner werden über ihre persistente Arbeitsplatz-ID ermittelt und können dort ausgewählt werden.
- Wird ein Gebäude abgerissen, dürfen Bewohner keine tote Arbeitsplatz-ID auf das entfernte Gebäude behalten.

## Gebäude und lokale Waren

- Für physische Warenbestände eines Gebäudes soll Hytales natives Container-/Inventarsystem verwendet werden, sofern der jeweilige Gebäudetyp einen geeigneten Containerblock besitzt. Ein paralleler Civ-Zähler oder eine eigene Text-/JSON-Datei ist dafür nicht das bevorzugte Modell.
- Die aktuelle Serverversion besitzt serialisierbare Block-Container im ChunkStore. Damit ist ein echter Hytale-Container der bevorzugte Kandidat für beispielsweise lokal bei einer Farm gelagerten Weizen.
- Räumliche Gebäudefunktionen sollen nach Möglichkeit im Prefab mit nativen Hytale-Mechanismen beschrieben werden. Trigger Volumes dürfen dafür Civ-Tags tragen, können aber zusätzlich native Volume-Logik nutzen.
- Die allgemeine räumliche Grenze eines fertigen Civ-Gebäudes wird vom Creator als natives Trigger Volume mit `civ.type=building_bounds` und `civ.building=<Gebäudetyp>` im Prefab festgelegt. Diese Zone ist nach Fertigstellung die maßgebliche Runtime-Fläche für Gebäude-Picking und Schutz; semantische Volumes wie `workplace_access` bleiben davon getrennte Funktionsbereiche.
- Die stabile Civ-Gebäude-ID verbindet persistente Gebäudedaten mit Bewohner-Arbeitsplatzreferenzen. Hytale-eigene Trigger-Volumes, Container und Prefab-Geometrie bleiben weiterhin Engine-Integration und nicht die Domänenidentität des Gebäudes.

## Farm

Die erste umgesetzte Gebäudedomäne bleibt ein Engine-Validierungs-Slice. Die bestehende Implementierung ist nicht automatisch die Produktdefinition der späteren Farm.

Verifiziert bzw. aktuell gewollt:

- Das Farm-Prefab definiert mindestens einen räumlichen Arbeitszugang über ein natives Hytale Trigger Volume. Mehrere Zugänge sollen möglich bleiben.
- Ein zugewiesener Bewohner erhält den Beruf Bauer.
- Farmarbeit findet sichtbar und physisch in der Hytale-Welt statt: Der Bauer setzt echtes Saatgut auf dem Weizenfeld, die Pflanzen durchlaufen Hytales nativen Farming-Wachstumszyklus und reife Pflanzen werden über Hytales native Farming-Ernte geerntet.
- Civ besitzt keinen eigenen Pflanzen-Wachstumstimer und erzeugt bei der Ernte keinen künstlichen Weizen-Output.
- Der bei der nativen Ernte tatsächlich im NPC-Inventar ankommende `Plant_Crop_Wheat_Item` wird zum nativen Hytale-Container der Farm transportiert. Erst erfolgreiche Einlagerung schließt diesen Erntezyklus ab.

Nur aktueller Prototyp, **keine dauerhafte Domänenregel**:

- genau ein Bauernplatz;
- Auswahl des Zugangs nur nach Luftlinienentfernung;
- genau ein automatisch ausgewähltes, nächstgelegenes Feld pro Arbeitszyklus;
- Rücktransport nach jedem erfolgreich geernteten Weizen-Stack;
- die native Containerkapazität begrenzt den eingelagerten Bestand.

Diese Punkte bleiben als Beschreibung des derzeit laufenden Codes relevant, dürfen aber ohne erneute Produktentscheidung nicht als Zielverhalten für die nächste Farm-Iteration verwendet werden. Das in `civilizations-poc` vorhandene Modell mit sichtbarer Feldarbeit, Feldentwicklung, Ernte und Rücktransport ist eine Referenz für die weitere Produktentscheidung, nicht automatisch eine Regel dieses Projekts.

## Civ-Prefab-Platzierung

Farmgebäude und Felder sind getrennte, vom Spieler platzierte Civ-Bauobjekte. Ein Feld wird nicht automatisch zusammen mit einer Farm erzeugt. Die Geometrie einschließlich semantischer Trigger-Volumes gehört vollständig zum jeweiligen Hytale-Prefab. Civ verschiebt solche Marker nicht unabhängig vom Prefab.

Für die aktuellen Creator-Prefabs gilt die gemeinsame Geländekonvention: Der Prefab-Anker wird bei der Platzierung um einen Block gegenüber dem anvisierten Gelände abgesenkt, damit die im Prefab definierte Bodenebene im Spiel bündig mit der Geländeoberfläche abschließt. Diese Konvention gilt für Gebäude und Felder gleichermaßen und ist kein farmspezifischer Sonderfall.

## Baustellen

- Das Bestätigen einer Civ-Gebäudeplatzierung soll nicht unmittelbar das fertige Gebäude erzeugen. Es entsteht zunächst eine Baustelle an der bestätigten Position.
- Die Baustelle darf Hytales native Prefab-Preview als visuelle Darstellung der geplanten Struktur verwenden; diese Vorschau ist noch kein gebautes Gebäude.
- Baufortschritt durch Bewohner, benötigte Materialien, Baugeschwindigkeit und Auswahl beziehungsweise Zuweisung von Bauarbeitern sind noch nicht als Domänenregeln festgelegt und werden im nächsten Vertical Slice entschieden.

## Bau / Construction v1

- Bauarbeiter ist ein aktiver Beruf eines Civ-Bewohners.
- Ein Bauarbeiter sucht selbstständig nach einer freien Civ-Baustelle in derselben Welt.
- Eine Baustelle wird in Construction v1 gleichzeitig höchstens von einem Bauarbeiter bearbeitet.
- Der Bauarbeiter läuft zu einem freien Arbeitspunkt außerhalb des Gebäudegrundrisses und bleibt dort während der eigentlichen Bauarbeit.
- Das Gebäude entsteht schrittweise von unten nach oben. Der aktuelle Prototyp verwendet eine belegte Prefab-Y-Ebene pro Bauschritt.
- Das Platzieren einer realen Prefab-Ebene darf vorhandene Weltblöcke an den vom Prefab belegten Positionen ersetzen; dadurch kann insbesondere der eingelassene Gebäudeboden die dortigen Bodenblöcke ersetzen.
- Prefab-Entities und Trigger Volumes gelten erst nach Abschluss aller Bauschritte als fertig und werden erst beim finalen vollständigen Prefab-Placement aktiviert.
- Die derzeitige Dauer von einer Sekunde pro Ebene und die generische Action-Animation sind Prototypwerte bzw. Platzhalter und keine dauerhaften Balancing-Regeln.
- Baumaterialien, mehrere Bauarbeiter pro Baustelle, Bauarbeiter-Erfahrung und persistente Baustellenzuweisungen sind noch keine Domänenregeln.

## Allgemeine Produktion und spätere Materialbeschaffung

- Produktionsrezepte beschreiben Inputs, Outputs und Grundarbeitszeit unabhängig vom konkreten Beruf.
- Berufe dürfen unterschiedliche Rezepte und Geschwindigkeiten verwenden, ohne eigene Kopien des gesamten Produktionsablaufs zu benötigen.
- Ein Produktionsarbeiter mit Inputs darf nicht voraussetzen, dass diese bereits am Arbeitsplatz liegen.
- Die spätere Warenlogistik entscheidet, aus welcher zulässigen Quelle benötigte Güter kommen (z. B. lokaler Arbeitsplatzcontainer, anderes Gebäude/Lager oder physische Weltware), reserviert sie und organisiert den Transport.
- Produktion entscheidet **was** benötigt und erzeugt wird; Logistik entscheidet **woher** die Güter kommen. Diese Trennung soll spätere Trägerlieferungen ermöglichen, ohne Müller, Steinmetz oder andere Produzenten neu zu modellieren.

### Temporärer Farmer-Bootstrap

- Für den aktuellen Entwicklungsslice erhält ein Bewohner beim Eintritt in den Beruf Bauer vier native Wheat Seed Bags (`Plant_Seeds_Wheat`).
- Beim Verlassen des Farmer-Berufs werden bis zu vier Wheat Seed Bags wieder aus seinem Inventar entfernt.
- Dieses Verhalten ist ausdrücklich keine dauerhafte Domänenregel. Es wird entfernt, sobald Farmer Saatgut über die allgemeine Waren-/Logistikbeschaffung beziehen.
