# Teststrategie

Ziel ist, möglichst viel Spielverhalten testen zu können, ohne Hytale starten zu müssen.

~~~text
Unit-Tests
    ↓
Simulations-/Szenario-Tests
    ↓
Hytale-Adapter-Tests
    ↓
Hytale-Server-Integrationstests
    ↓
Manuelle Client-/UX-Tests
~~~

## Allgemeine Regressionsregel

Verhaltensänderungen benötigen Abdeckung auf der niedrigsten Ebene, die das Verhalten zuverlässig nachweisen kann, ohne unnötig von Hytale abzuhängen.

Ein erfolgreicher Compile- oder Build-Lauf reicht als Abdeckung für eine neue Domänenregel nicht aus. Wenn ein Feature einen relevanten mehrstufigen Ablauf einführt, muss ein Regressionstest für den vollständigen betroffenen Ablauf vorhanden sein, statt nur einzelne Hilfsmethoden zu testen.

Domänenregeln und Invarianten sollten normalerweise durch deterministische Core-Tests abgebildet werden. Hytale-Adapter-Tests sollen Übersetzungs- und Engine-Grenzverhalten prüfen, nicht dieselben Core-Regeln mit Mocks duplizieren.

## Unit-Tests

Schnelle JUnit-5-Tests für reine Java-Domänenregeln und Hilfsfunktionen.

## Simulations-/Szenario-Tests

Deterministische mehrstufige Tests sind die bevorzugte Abdeckung für Bewohner, Bedürfnisse, Berufe, Inventare, Produktion, Logistik, Wirtschaft und andere gekoppelte Simulationssysteme.

Wenn diese Systeme eingeführt werden, sollen kleine Golden-Szenarien mit klar definiertem Ausgangszustand, Befehlen und erwartetem Ergebnis verwendet werden. Wichtige Invarianten werden direkt geprüft, zum Beispiel dass Inputs exakt einmal verbraucht werden, Inventare nie negativ werden und dieselbe Befehlsfolge dasselbe Ergebnis erzeugt.

Szenario-Tests bleiben Hytale-unabhängig, außer das geprüfte Verhalten ist tatsächlich ein Engine-Vertrag.

## Hytale-Adapter-Tests

Tests für Übersetzung und Adapterverhalten, soweit dies ohne laufenden Server sinnvoll möglich ist.

Der aktuelle RTS-Prototyp betrifft vor allem Kamera, Cursor-Zielerfassung, interaktive Custom Pages, Platzierungsvorschau, natives Baumfällen und Hytale-NPC-Bewegung. First-Person-Personenaktionen hängen zusätzlich vom Runtime-Dispatch von <code>UseEntityEvent.Pre</code> auf dem handelnden Spieler ab. Diese Engine-Verträge werden deshalb nicht künstlich durch gemockte Unit-Tests vorgetäuscht.

## Hytale-Server-Integrationstests

Zukünftige kontrollierte Server-Tests für Lifecycle, Registrierung und Engine-Interaktion. Noch nicht umgesetzt.

## Hytale-API-Inspektion

Die Hytale-API wird bei Bedarf direkt aus der im Projekt bereitgestellten `HytaleServer.jar` untersucht. Ein CI-Snapshot oder Inspector-Smoke-Test ist dafür nicht mehr Teil der Teststrategie. Klassenauflistung, Signaturen und Bytecode sind Entwicklungswerkzeuge und keine Laufzeittests.

Ein erfolgreicher Binär- oder Signaturbefund beweist insbesondere nicht Event-Dispatch, Lifecycle, Client-Reaktionen oder andere Runtime-Semantik. Solche Verträge bleiben Aufgabe gezielter Hytale-Server-/Client-Tests beziehungsweise offizieller Dokumentation.

## Manuelle Client-/UX-Tests

Der steuerbare-NPC-Prototyp wird so geprüft:

1. <code>/civrtstest</code> ausführen und bestätigen, dass die Ansicht zu einer festen schrägen Cursor-Kamera wechselt, ohne den Spectator-Modus zu aktivieren.
2. Einen nicht beanspruchten NPC mit Linksklick anklicken und bestätigen, dass er nicht zur Civ-Auswahl wird.
3. <code>/civclaim</code> ausführen, denselben NPC anklicken und bestätigen, dass er beansprucht wird. Danach RTS deaktivieren, mit leerer Hand in First Person auf den Bewohner zielen und per Rechtsklick bestätigen, dass <code>PersonActionsPage</code> öffnet. Rechtsklick auf einen nicht beanspruchten NPC darf das Civ-Menü nicht öffnen.
4. RTS wieder aktivieren und den beanspruchten NPC mit Linksklick anklicken und bestätigen, dass er zur ausgewählten Civ-Einheit wird.
5. Einen zweiten NPC beanspruchen und auswählen. Bestätigen, dass er die vorherige Auswahl ersetzt und keine Mehrfachauswahl entsteht.
6. Den ausgewählten NPC mit Rechtsklick anklicken und bestätigen, dass die Seite für Personenaktionen geöffnet wird.
7. Einen <code>Civ_Inhabitant</code> verwenden, auf offenen und ausreichend flachen Boden rechtsklicken und bestätigen, dass er selbstständig zum Ziel läuft.
8. Hinter ein Hindernis rechtsklicken und bestätigen, dass die normale Hytale-Wegfindung das Routenverhalten bestimmt.
9. <code>/civclaim</code> ausführen und einen bereits beanspruchten NPC erneut anklicken. Bestätigen, dass er freigegeben wird und danach nicht mehr ausgewählt oder befehligt werden kann.
10. <code>/civrtstest</code> erneut ausführen und bestätigen, dass die normale Kamerasteuerung zurückkehrt und der Client stabil bleibt.
11. Den RTS-Modus zweimal weiter ein- und ausschalten und bestätigen, dass wiederholte Wechsel stabil bleiben.
12. Bestätigen, dass Ansprüche nur zur Laufzeit bestehen und einen Plugin- oder Server-Neustart nicht überleben.
13. Einem beanspruchten NPC einen Beruf zuweisen, Server oder Plugin neu starten, dieselbe persistierte NPC-Entität erneut beanspruchen und bestätigen, dass der Beruf noch vorhanden ist. Die Farm-Zuweisung muss derzeit noch nicht erhalten bleiben.

Der Test für direkte Bewegung verwendet die eingecheckte Rolle <code>Civ_Inhabitant</code>. Deren einzelner Positionsslot <code>CivMoveTarget</code> ist ein ausdrücklicher Vertrag zwischen Java-Code und Asset. <code>CivInhabitantRoleValidationTest</code> schützt diese Annahme. Civ selbst übt keine eigene Steuerkraft pro Tick aus.

## Aktuelle automatisierte Tests

- <code>CoreSmokeTest</code> zeigt, dass JUnit funktioniert.
- <code>CoreIndependenceTest</code> verhindert direkte Hytale-Imports im Core.
- <code>WoodcutterJobTest</code> prüft den Ablauf Ziel → Ankunft → Fällen → bereit zum tatsächlichen Baumfällen.
- <code>ManifestValidationTest</code> prüft die verpackten Plugin-Metadaten ohne Hytale zu starten.
- <code>CivInhabitantRoleValidationTest</code> prüft die eingecheckte Civ-NPC-Rolle und den einzelnen Positionsslot, auf den die Java-Bewegungsanbindung angewiesen ist.

## Abdeckung des Holzfäller-Vertical-Slice

Manuelle Abnahme:

1. Sowohl <code>hytale-civ.jar</code> als auch <code>hytale-civ-assets</code> installieren beziehungsweise deployen.
2. <code>/civrtstest</code> ausführen.
3. Mit <code>/civclaim</code> einen NPC beanspruchen und ihn anschließend mit normalem Linksklick auswählen.
4. Den ausgewählten NPC mit Rechtsklick anklicken und bestätigen, dass das Menü Personenaktionen geöffnet wird.
5. <code>Holzfäller</code> auswählen und bestätigen, dass die Seite geschlossen wird und der NPC selbstständig mit der Arbeit beginnt.
6. Den NPC in der Nähe eines normalen Hytale-Baums halten und bestätigen, dass er neben den Stamm läuft statt in den Stamm hinein.
7. Nach der Arbeitsphase bestätigen, dass der unterste Stammblock über Hytales normalen Ernteweg gebrochen wird.
8. Bestätigen, dass normale Drops erscheinen und der restliche Baum entsprechend seiner nativen Support-/Physik-Konfiguration reagiert.
9. Bestätigen, dass der Holzfäller danach einen weiteren Baum in der Nähe sucht.

Der Prototyp verlässt sich nach dem Brechen des Stammblocks bewusst auf das native Verhalten des jeweiligen Hytale-Baum-Assets. Falls ein bestimmter Baum nicht zusammenfällt, muss zuerst dessen Support-/Physik-Konfiguration geprüft werden, bevor eigene Civ-Sonderlogik ergänzt wird.

## Abdeckung des Farm-Vertical-Slice

Automatisierte Abdeckung umfasst derzeit:

- <code>FarmBuildingTest</code>: prüft einen Bauernplatz, fünf Sekunden aktive Arbeit pro Weizen, verpflichtendes Verlassen nach jedem Produktionsschritt und den harten Stopp bei zehn Weizen.
- <code>FarmPrefabValidationTest</code>: prüft die Metadaten des eingecheckten Asset-Pack-Prefabs, eindeutige Blockkoordinaten, Dach- und Feldmaterialien sowie das markierte <code>civ_farm_workplace</code>-Trigger-Volume ohne den alten Eingang-Markerblock.

Manuelle Abnahme:

1. Sowohl <code>hytale-civ.jar</code> als auch <code>hytale-civ-assets</code> installieren beziehungsweise deployen.
2. <code>/civrtstest</code> ausführen.
3. <code>/civbuild</code> ausführen und bestätigen, dass ein modaler Katalog **Gebäude** geöffnet wird und normale RTS-Weltinteraktion blockiert.
4. Bestätigen, dass der aktuelle Katalog **Farm** enthält und geschlossen werden kann, ohne die Platzierung zu starten.
5. <code>/civbuild</code> erneut ausführen, **Farm** auswählen, den Mauszeiger über das Gelände bewegen und bestätigen, dass eine Farm-Vorschau dem anvisierten Block folgt.
6. Rechtsklick ausführen und bestätigen, dass die Platzierung abgebrochen wird, ohne die Welt zu verändern.
7. **Farm** erneut wählen und auf gültigem, flachem und gestütztem Boden links klicken. Bestätigen, dass die Farm mit eingelassenem Boden platziert wird und nicht einen Block über dem Gelände steht.
8. Dasselbe über einem Loch, einer Flüssigkeit, blockiertem Gebäuderaum und einer bestehenden Farmfläche versuchen. Bestätigen, dass die Platzierung mit einem Grund abgelehnt wird und der Platzierungsmodus aktiv bleibt.
9. Bestätigen, dass <code>/civfarm</code> denselben Farm-Platzierungsablauf startet wie das Menü.
10. Mit <code>/civclaim</code> einen NPC beanspruchen, ihn auswählen, im Arbeitsbereich der Farm rechts klicken und bestätigen, dass die Zuweisung als Bauer weiterhin funktioniert.
11. Bestätigen, dass der NPC zum Eingang läuft, ungefähr fünf Sekunden innen arbeitet, das Gebäude verlässt, für jeden Produktionsschritt erneut hineingeht und nach dem zehnten Weizen draußen bleibt.
12. Den NPC während eines Zyklus mit <code>/civclaim</code> freigeben und bestätigen, dass die Farm-Zuweisung aufgehoben wird.

Mehrspieler-Abnahme:

1. Zwei Spieler verbinden und mit beiden in den RTS-Modus wechseln.
2. Farm-Platzierung unabhängig voneinander starten und bestätigen, dass Bewegung oder Abbruch einer Vorschau die Vorschau beziehungsweise den Zustand des anderen Spielers nicht verändert.
3. Beide Vorschauen auf überlappende, zunächst gültige Flächen richten.
4. Spieler A zuerst platzieren lassen, danach Spieler B bestätigen lassen, ohne den Mauszeiger zu bewegen.
5. Bestätigen, dass Spieler B durch die erneute serverseitige Prüfung abgelehnt wird und keine überlappende Farm entsteht.

Der ursprüngliche Schnappschuss der Bodenblöcke bleibt interner Laufzeitzustand, bis eine Abrissoberfläche existiert. Sobald Abriss umgesetzt wird, muss der Abnahmetest die exakte Wiederherstellung dieser gespeicherten Blöcke prüfen. Rotation bleibt ein separates zukünftiges Feature.
