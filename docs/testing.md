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

Lokale Hytale-Runtime-Szenarien auf dem Self-Hosted-Runner sind aktuell **optionale Entwicklungs- und Diagnosehilfen**. Sie liefern bei Bedarf zusätzliche Evidenz für Engine-Verhalten, sind aber keine verpflichtenden Merge- oder Release-Checks und müssen nicht für jedes neue oder geänderte Hytale-Verhalten ergänzt oder ausgeführt werden. Die normale automatisierte Abdeckung durch Unit-, Simulations-, Adapter- und GitHub-CI-Checks bleibt die Standard-Abnahme.

## Unit-Tests

Schnelle JUnit-5-Tests für reine Java-Domänenregeln und Hilfsfunktionen.

## Simulations-/Szenario-Tests

Deterministische mehrstufige Tests sind die bevorzugte Abdeckung für Bewohner, Bedürfnisse, Berufe, Inventare, Produktion, Logistik, Wirtschaft und andere gekoppelte Simulationssysteme.

Wenn diese Systeme eingeführt werden, sollen kleine Golden-Szenarien mit klar definiertem Ausgangszustand, Befehlen und erwartetem Ergebnis verwendet werden. Wiederverwendbare Tick-0-Zustände liegen als <code>SimulationScenario</code> im gemeinsamen Szenariokatalog und können sowohl vom Viewer als auch von Tests gestartet werden. Erwartete Ergebnisse und Assertions gehören weiterhin in den jeweiligen Test, nicht in die Szenariodefinition. Wichtige Invarianten werden direkt geprüft, zum Beispiel dass Inputs exakt einmal verbraucht werden, Inventare nie negativ werden und dieselbe Befehlsfolge dasselbe Ergebnis erzeugt.

Ausgewählte fachliche Ausgangslagen dürfen zusätzlich als reine, Hytale-unabhängige Scenario-Fixture von Simulator und echtem Hytale-Runtime-Test gemeinsam verwendet werden. Der erste solche Vertrag ist <code>WoodcutterBasicScenario</code>: Startposition und Baum-Anker stammen aus derselben Definition. Der Simulator übersetzt die Anker in abstrakte Bäume; der Hytale-Runner platziert dort reale Vanilla-Prefabs. Engine-Details wie Prefab-Origin, Chunk-Preload, EntityStore und NPC-Spawn gehören nicht in die gemeinsame Fixture.

Engine-Schritte dürfen in solchen Tests als kontrollierte Ergebnisse zurückgespielt werden. Wenn der Core beispielsweise einen Bewegungs-Intent erzeugt, kann der Test „angekommen“ melden, ohne Hytales Navigation zu starten. Damit werden Ablauf, Unterbrechung und Wiederaufnahme headless geprüft; nur die tatsächliche Umsetzung des Intents durch Hytale bleibt ein Adapter-/Runtime-Test. Hytales Pathfinding wird ausdrücklich nicht im Simulator nachgebaut.

Szenario-Tests bleiben Hytale-unabhängig, außer das geprüfte Verhalten ist tatsächlich ein Engine-Vertrag. UI-Klicks sind kein Ersatz für einen Core-Test einer Regel, die auch ohne UI formulierbar ist.

<code>SimulationRuntime</code> stellt dafür einen festen 50-ms-Simulationsschritt, eine minimale Fake-Welt und deterministische <code>SimulationMetrics</code> bereit. Fake-Bewegung läuft geradlinig und beweist ausdrücklich nicht Hytales Wegfindung. Performance-Budgets in Szenario-Tests sollen primär fachliche Operationen begrenzen, zum Beispiel Suchentscheidungen, Weltabfragen und Bewegungsanforderungen, statt von der Geschwindigkeit eines CI-Rechners abzuhängen.

Der erste Cadence-Vertrag lautet: teure autonome Arbeitssuche erfolgt über <code>WorkDecisionSchedule</code> entweder unmittelbar nach einem relevanten Ereignis oder über einen begrenzten Retry. Ein wartender Bewohner darf deshalb nicht nur deshalb auf jedem Simulations- oder Engine-Tick dieselbe teure Suche wiederholen.

## Manueller Simulation-Viewer-Check

1. <code>./gradlew simulationViewer</code> starten und bestätigen, dass <strong>Demo Settlement</strong> ohne Hytale-Fenster erscheint.
2. Oben ein anderes Szenario auswählen und bestätigen, dass dessen definierter Startzustand sofort geladen wird.
3. <code>Reset</code> drücken und bestätigen, dass das aktuell ausgewählte Szenario wieder exakt bei Tick 0 startet.
4. <code>Step</code> drücken und bestätigen, dass Tick und Simulationszeit genau einen Schritt weiterlaufen.
5. Einen Bewohner mit Linksklick auswählen und prüfen, dass Beruf, State, Position und Bewegungsziel rechts erscheinen.
6. Für den ausgewählten Bewohner per Rechtsklick ein Ziel auf der Karte setzen und bestätigen, dass <code>MANUAL_MOVE</code> aktiv wird und die autonome Tätigkeit danach fortgesetzt wird.
7. <code>x100</code> oder <code>Max</code> wählen, starten und bestätigen, dass Weltzustand sowie Metrics schnell fortschreiten, ohne dass Hytale gestartet wird.

## Hytale-Adapter-Tests

Der lokale MCP-Zugriff wird zusätzlich ohne Gameplay-Engine geprüft: `node --test tools/hytale-mcp/test/*.test.mjs` sichert Lifecycle/stdio, Argumentgrenzen, Tool-Fehler, Log-Cursor und Runtime-Dateibesitz. `CivDevBridgeTest` prüft den tatsächlichen HTTP-Endpunkt auf Token-/Session-/Origin-Prüfung und Request-Größe. Der Build prüft die Bridge gegen die gepinnte API. Live-Startup/Shutdown, NPC-/Combat-Verhalten und Clientdarstellung dieses neuen direkten Ablaufs bleiben lokal zu verifizieren. Anleitung: [local-mcp.md](local-mcp.md).

Tests für Übersetzung und Adapterverhalten, soweit dies ohne laufenden Server sinnvoll möglich ist.

Der aktuelle RTS-Prototyp betrifft vor allem Kamera, Cursor-Zielerfassung, interaktive Custom Pages, Platzierungsvorschau, natives Baumfällen und Hytale-NPC-Bewegung. First-Person-Personenaktionen hängen zusätzlich vom Runtime-Dispatch von <code>UseEntityEvent.Pre</code> auf dem handelnden Spieler ab. Diese Engine-Verträge werden deshalb nicht künstlich durch gemockte Unit-Tests vorgetäuscht.

## Hytale-Server-Integrationstests

Es existieren zwei unterschiedliche Server-Proben:

- Der normale GitHub-hosted Build startet die echte gepinnte Server-JAR als Bare-Probe. Weil Hytale 0.6.8 auch mit <code>--bare</code> das Asset-Modul lädt, endet dieser Test ohne lizenzierte <code>Assets.zip</code> erwartungsgemäß an der Missing-Assets-Grenze. Er beweist nur die frühe Server-/Plugin-Manager-Kompatibilität.
- Echte Gameplay-Verträge können über <code>.github/workflows/hytale-local.yml</code> auf einem vertrauenswürdigen Windows-Self-Hosted-Runner mit lokaler Hytale-Installation und den lizenzierten Basisassets ausgeführt werden. Diese Läufe sind optional und primär für Entwicklung, Diagnose und gezielte Runtime-Verifikation gedacht.

Ein Runtime-Lauf wird über einen Kommentar in der festen GitHub-Issue <code>#126 Hytale Runtime Test Requests</code> angefordert. Das Format lautet <code>/hytale-test &lt;szenarien&gt; &lt;commit-sha&gt;</code>. Der Commit darf jeder Commit des eigenen Repositories sein, also auch ein Spike ohne Pull Request. Mehrere Szenarien werden mit <code>-</code> getrennt; <code>all</code> steht allein und expandiert auf alle aktuell registrierten Runtime-Szenarien. Die Registry <code>scripts/hytale-runtime-scenarios.json</code> ist die gemeinsame Allowlist für Autorisierung und Harness. Aktuell sind <code>woodcutter</code>, <code>persistence</code>, <code>minesupport</code>, <code>warmruntime</code> und <code>soldier</code> registriert.

Der <code>issue_comment</code>-Workflow liegt auf dem vertrauenswürdigen Default-Branch. Sein GitHub-hosted Autorisierungsjob prüft vor jeder lokalen Codeausführung, dass der Kommentar aus Issue <code>#126</code> stammt, Event-Aktor, Kommentarautor und Sender ausdrücklich erlaubt sind, die Befehlssyntax gültig ist, der angegebene 7- bis 40-stellige SHA auf einen passenden exakten Repository-Commit auflösbar ist und jedes angeforderte Szenario in der Registry genau dieses Commits steht. Unbekannte oder doppelte Szenarien sowie mehr als acht explizite Szenarien werden vor dem Self-Hosted-Runner abgelehnt. Aktuell ist <code>weidmanngabriel</code> der einzige erlaubte Anforderer.

Der normale CI-Build veröffentlicht das getestete Plugin-JAR zusätzlich als SHA-adressiertes Artefakt. Der Autorisierungsjob sucht für den angeforderten Commit nach einem vollständig erfolgreichen CI-Lauf und akzeptiert nur das nicht abgelaufene Artefakt mit exakt passendem SHA-Namen. Der Self-Hosted-Job erhält <code>contents: read</code> und <code>actions: read</code>, checkt ausschließlich den bereits autorisierten exakten Commit-SHA mit <code>persist-credentials: false</code> aus und verifiziert den Checkout erneut. Ist das getestete CI-Artefakt vorhanden, wird dessen JAR direkt wiederverwendet und der lokale Gradle-Test-/Build-Schritt entfällt. Fehlt es, führt der Runner weiterhin <code>test + jar</code> als sicheren Fallback aus. <code>scripts/hytale-runtime-tests.ps1</code> führt danach nur die autorisierte Szenarioliste aus und erzeugt für jedes Szenario ein eigenes isoliertes Hytale-Runtime-Verzeichnis. Damit können mehrere Engine-Verträge in einem Lauf geprüft werden, ohne denselben Weltzustand zu teilen. Der lokale Runner besitzt keine Repository-Schreibrechte.

Der lokale Runtime-Vertrag <code>woodcutter</code> führt <code>WoodcutterBasicScenario</code> real aus: drei Vanilla-Oaks werden als Prefabs platziert, ein echter <code>Civ_Inhabitant</code> wird gespawnt, geclaimt und zum Holzfäller gemacht. Danach muss der produktive <code>WoodcutterWorkSystem</code> autonom einen Baum auswählen, Hytales echte NPC-Navigation nutzen, die Arbeitsphase durch echte World-/ECS-Ticks ausführen, einen Oak über den nativen Fällpfad aus der Welt entfernen und anschließend einen weiteren Baum als Ziel aufnehmen. Die Assertion beobachtet dabei reale Weltzustände und Produktionslogs statt den Core-Job direkt fernzusteuern. Der erfolgreiche Referenzlauf reduzierte 54 reale <code>Woods</code>-Blöcke auf 36, also genau einen 18-Block-Oak, und wurde ohne Codeänderung erneut erfolgreich ausgeführt.

Der lokale Runtime-Vertrag <code>persistence</code> startet zwei getrennte Hytale-Serverprozesse im selben isolierten Runtime-Verzeichnis. Der Prepare-Prozess spawnt und claimt einen echten <code>Civ_Inhabitant</code>, setzt deterministische persistente Daten, liest dessen native <code>UUIDComponent</code> und beendet den Server über den normalen Shutdownpfad. Der Restore-Prozess startet Hytale anschließend neu, lädt denselben Weltzustand und muss exakt dieselbe Entity-UUID wiederfinden. Zusätzlich werden Civ-Claimzustand, vollständiger Name, Beruf, Berufs-XP, Workplace-ID, Appearance sowie die rehydrierten Display-Komponenten geprüft. Der Vertrag gilt nur als bestanden, wenn beide Prozesse mit Exit-Code 0 sauber herunterfahren und die Restore-Assertions gegen die vorbereitete UUID erfolgreich sind.

Damit sind echte NPC-, Navigations-, Worldgen-, Prefab-, Tick-, Weltmutations- und saubere Persistenz-/Restart-Verträge headless automatisierbar. Sie werden bei Bedarf eingesetzt, sind aber aktuell kein allgemeiner Fertigstellungsnachweis. Nicht abgedeckt sind weiterhin Client-UI/Rendering sowie Gameplay, das noch nicht implementiert ist, insbesondere Holz-Drops einsammeln und in ein Lager liefern. Details und Grenzen stehen in <code>docs/hytale/server-headless.md</code>.

## Manueller Check: nativer NPC-Pfad-Debug

1. Einen NPC mit <code>/civclaim</code> als Civ-Bewohner beanspruchen.
2. <code>/civdebug path</code> ausführen. Die Rückmeldung muss mindestens einen geladenen Civ-Bewohner melden und den Debug-Modus als aktiviert anzeigen.
3. Dem Bewohner per RTS-Rechtsklick ein Ziel auf freiem Boden geben und bestätigen, dass Hytales Pfadvisualisierung erscheint.
4. Ein Ziel hinter einem deutlichen Hindernis setzen und prüfen, dass die visualisierten Wegpunkte beziehungsweise Linien dem von Hytale gewählten Umweg folgen und nicht nur eine gerade Civ-Linie zum Endziel bilden.
5. Den Test während Holzfäller-, Bauer- und Bauarbeiterbewegung wiederholen und bestätigen, dass dieselbe native Pfaddarstellung verwendet wird.
6. <code>/civdebug path</code> erneut ausführen und bestätigen, dass die von Civ aktivierte Pfadvisualisierung verschwindet.
7. Falls ein NPC bereits vor dem Civ-Toggle einen eigenen <code>VisPath</code>-Flag hatte, bestätigen, dass dieser beim Ausschalten nicht von Civ entfernt wird.

Dieser Check ist ausdrücklich ein Runtime-Test des Hytale-Verhaltens. Die CI deckt hier Kompilierung und API-Vertrag gegen die festgesetzte Hytale-Abhängigkeit ab; die tatsächliche Darstellung des Engine-Pfads kann nur im laufenden Server/Client verifiziert werden.

## Hytale-API-Inspektionscheck

Die CI führt zusätzlich zur normalen Kompilierung <code>snapshotHytaleApi</code> und einen repräsentativen Aufruf von <code>inspectHytaleClass</code> für <code>CommandBuffer</code> aus. Dieser Check beweist nur, dass die festgesetzte Hytale-Abhängigkeit auflösbar und ihre API maschinell inspizierbar ist; er ist kein Ersatz für Hytale-Server- oder Client-Laufzeittests.

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
9. <code>/civclaim</code> auf einen bereits initialisierten Civ-Bewohner erneut anwenden. Bestätigen, dass er aus der Civ freigegeben wird, nicht mehr auswählbar ist und wieder einen nativen NPC-Namen verwendet. Anschließend erneut claimen und bestätigen, dass er wieder als neuer Civ-Bewohner initialisiert wird.
10. <code>/civrtstest</code> erneut ausführen und bestätigen, dass die normale Kamerasteuerung zurückkehrt und der Client stabil bleibt.
11. Den RTS-Modus zweimal weiter ein- und ausschalten und bestätigen, dass wiederholte Wechsel stabil bleiben.
12. Einen Civ-Bewohner initialisieren, seinen vollständigen Namen notieren, die Welt normal verlassen beziehungsweise den Server sauber neu starten und dieselbe NPC-Entität erneut laden. Bestätigen, dass sie ohne erneuten Claim als Civ-Bewohner erkannt wird und denselben Namen trägt.
13. Dem Bewohner einen Beruf zuweisen, sauber neu starten und bestätigen, dass derselbe Beruf erhalten bleibt. Die Farm-Zuweisung muss derzeit noch nicht erhalten bleiben.
14. Den Persistenztest zusätzlich nach einem normalen Autosave wiederholen. Ein harter Prozessabbruch ist kein verlässlicher Ersatz für den sauberen Save-/Reload-Test.

Der Test für direkte Bewegung verwendet die eingecheckte Rolle <code>Civ_Inhabitant</code>. Deren einzelner Positionsslot <code>CivMoveTarget</code> ist ein ausdrücklicher Vertrag zwischen Java-Code und Asset. <code>CivInhabitantRoleValidationTest</code> schützt diese Annahme. Civ selbst übt keine eigene Steuerkraft pro Tick aus.

## Aktuelle automatisierte Tests

- <code>CoreSmokeTest</code> zeigt, dass JUnit funktioniert.
- <code>CoreIndependenceTest</code> verhindert direkte Hytale-Imports im Core.
- <code>WoodcutterJobTest</code> prüft den headless Ablauf Such-Intent → Bewegungs-Intent → Ankunft → Arbeit → Fäll-Intent → neuer Zyklus.
- <code>InhabitantActivityTest</code> prüft, dass ein manueller Bewegungsauftrag autonome Arbeit verdrängt, nach Abschluss wieder freigibt und den pausierten Holzfällerzustand nicht verändert.
- <code>WorkDecisionScheduleTest</code> prüft unmittelbare Entscheidungen, begrenzte Retries und das Vorziehen eines relevanten Ereignisses gegenüber einem noch nicht fälligen Retry.
- <code>SimulationRuntimeTest</code> deckt die Core-Abläufe für Bauarbeiter, Holzfäller, Farmer und manuelle Unterbrechungen ab. Dazu gehören feste Operationsbudgets: 100 wartende Bauarbeiter führen in 60 Simulationssekunden 6.000 Baustellensuchen aus, und ein Farmer ohne Feld führt in derselben Zeit 60 Feldsuchen statt einer Suche pro Tick aus.
- <code>SimulationScenariosTest</code> prüft den gemeinsamen Szenariokatalog. Für <code>WoodcutterBasicScenario</code> wird zusätzlich geprüft, dass dieselbe gemeinsame Fixture mit drei Baum-Ankern startet, mindestens ein Baum fällt und der Holzfäller anschließend erneut nach Arbeit sucht.
- <code>SimulationIndependenceTest</code> verhindert direkte Hytale-Imports im wiederverwendbaren Headless-Runtime-Paket.
- <code>SimulationViewerAppTest</code> prüft das Demo-Szenario und die Snapshot-Grenze, ohne ein Swing-Fenster zu öffnen. Die grafische Darstellung selbst bleibt ein manueller Entwickler-Check.
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
9. Während der Holzfäller zu einem Baum läuft, per Rechtsklick ein freies Bodenziel geben. Bestätigen, dass er zuerst vollständig zum manuellen Ziel läuft und erst danach wieder einen Baum sucht.
10. Bestätigen, dass der Holzfäller danach einen weiteren Baum in der Nähe sucht.

Der Prototyp verlässt sich nach dem Brechen des Stammblocks bewusst auf das native Verhalten des jeweiligen Hytale-Baum-Assets. Falls ein bestimmter Baum nicht zusammenfällt, muss zuerst dessen Support-/Physik-Konfiguration geprüft werden, bevor eigene Civ-Sonderlogik ergänzt wird.

## Abdeckung des Farm-Vertical-Slice

Automatisierte Abdeckung umfasst derzeit:

- <code>FarmBuildingTest</code>: prüft den einzelnen Bauernplatz sowie den fünfsekündigen Arbeitszyklus vom Farmzugang über das Feld bis zur physischen Ablage des Outputs.
- <code>FarmPrefabValidationTest</code>: prüft die beiden eingecheckten Creator-Prefabs. Die Farm besitzt eine leere native 18-Slot-Truhe sowie <code>workplace_access</code>- und <code>output_storage</code>-Marker; das separate 6×6-Weizenfeld besteht aus nativem Tilled Soil und besitzt den <code>field</code>-Marker.

Manuelle Abnahme:

1. Sowohl <code>hytale-civ.jar</code> als auch <code>hytale-civ-assets</code> installieren beziehungsweise deployen.
2. <code>/civrtstest</code> ausführen.
3. <code>/civbuild</code> ausführen und bestätigen, dass ein modaler Katalog **Gebäude** geöffnet wird und normale RTS-Weltinteraktion blockiert.
4. Bestätigen, dass der aktuelle Katalog **Farm** und **Weizenfeld** enthält und geschlossen werden kann, ohne die Platzierung zu starten.
5. <code>/civbuild</code> erneut ausführen, **Farm** auswählen und den Mauszeiger über flaches Gras bewegen. Im aktuellen Preview-Spike darf die frühere Meldung „oberhalb des Baugrunds … blockiert“ nicht erscheinen. Prüfen, ob Civ jetzt tatsächlich eine native <code>PersistentPrefabPreview</code> als Ghost erzeugt und bewegt. Das Editor-Paste-Tool darf dabei nicht aktiviert werden.
6. Rechtsklick ausführen und bestätigen, dass die Platzierung abgebrochen wird, ohne die Welt zu verändern.
7. **Farm** erneut wählen und auf flachem Boden mit Linksklick bestätigen. Es dürfen **keine echten Prefab-Blöcke** entstehen. Der bewegte <code>PersistentPrefabPreview</code>-Ghost bleibt als Baustelle stehen. Sein Civ-Anker liegt exakt einen Block unter dem anvisierten Oberflächenblock.
8. Dasselbe über einem Loch, einer Flüssigkeit, tatsächlich blockiertem Bauvolumen oberhalb des eingelassenen Baugrunds und einer bestehenden Farmfläche versuchen. Bestätigen, dass die Platzierung mit einem Grund abgelehnt wird und der Platzierungsmodus aktiv bleibt.
9. **Weizenfeld** auswählen und denselben Ablauf prüfen. Auch hier darf beim Bestätigen nicht sofort das echte Feld entstehen; es muss eine getrennte Baustellen-Vorschau an der abgesenkten Civ-Bauposition entstehen.
10. Bestätigen, dass <code>/civfarm</code> weiterhin denselben Farm-Platzierungsablauf startet wie das Menü.
11. Mit <code>/civclaim</code> einen NPC beanspruchen, ihn auswählen, im Arbeitsbereich der Farm rechts klicken und bestätigen, dass die Zuweisung als Bauer weiterhin funktioniert.
12. Bestätigen, dass der NPC zum Eingang und anschließend zum Weizenfeld läuft. Wenn `Plant_Seeds_Wheat` verfügbar ist, muss der Pflanzschritt über `FarmPlantingService` laufen. In der aktuell unterstützten Runtime ist dieser Adapter bewusst `UNSUPPORTED`; der Bauer darf deshalb nicht weiter versuchen, eine Spieler-Interaction zu simulieren, sondern pausiert den Arbeitszyklus kontrolliert.
13. Während der Bauer zu einem Farmziel läuft, ein manuelles Bodenziel geben. Bestätigen, dass er zuerst dorthin läuft und anschließend seinen Farmablauf fortsetzt.
14. Den Server nach einer Berufszuteilung sauber neu starten und bestätigen, dass der Bewohner weiterhin als Civ-Bewohner mit demselben Namen und Beruf erkannt wird. Die konkrete Farm-Arbeitsplatzzuweisung bleibt derzeit laufzeitgebunden.

Mehrspieler-Abnahme:

1. Zwei Spieler verbinden und mit beiden in den RTS-Modus wechseln.
2. Farm-Platzierung unabhängig voneinander starten und bestätigen, dass Bewegung oder Abbruch einer Vorschau die Vorschau beziehungsweise den Zustand des anderen Spielers nicht verändert.
3. Beide Vorschauen auf überlappende, zunächst gültige Flächen richten.
4. Spieler A zuerst platzieren lassen, danach Spieler B bestätigen lassen, ohne den Mauszeiger zu bewegen.
5. Bestätigen, dass Spieler B durch die erneute serverseitige Prüfung abgelehnt wird und keine überlappende Farm entsteht.

Der ursprüngliche Schnappschuss der Bodenblöcke bleibt interner Laufzeitzustand, bis eine Abrissoberfläche existiert. Sobald Abriss umgesetzt wird, muss der Abnahmetest die exakte Wiederherstellung dieser gespeicherten Blöcke prüfen. Rotation bleibt ein separates zukünftiges Feature.

### Construction preview input diagnostic

For the current focused runtime diagnostic, select **Farm** and move the mouse across terrain before clicking. The chat should identify the first boundary reached: either `MouseMotion` with a null target, a concrete `target=x,y,z anchor=x,y,z`, a preview-spawn exception, or a successful preview update. This diagnostic is temporary evidence for the engine input/preview contract and is not intended as final player-facing UX.

### Native ghost click-cancel spike

Select **Farm** and verify that Hytale's native moving Paste ghost appears. It should render one block lower than the earlier native Paste ghost because the loaded selection anchor is offset. Left-click once on flat terrain. Verify separately whether (a) the real prefab is suppressed and only a stationary construction preview remains, or (b) Hytale still performs a real paste despite the cancelled `PlayerMouseButtonEvent`. Outcome (b) proves the Builder paste commit is independent of the cancellable normal mouse event.

### Construction blueprint lifecycle regression

After selecting a Farm through `/civbuild`, confirm it with left click. The stationary blueprint should align vertically with the correctly positioned moving native ghost. Run `/civbuildcancel`; the stationary blueprint must disappear immediately without rejoining the world. Also verify that disconnecting removes the player's runtime blueprint previews. Progressive NPC block replacement is not part of this regression yet because `PersistentPrefabPreview` cannot hide individual prefab blocks.

## Browser-Replay-Vertrag

`SimulationRecordingExporterTest` prüft alle vier Minenausrichtungen und sämtliche gemeinsamen Runtime-Szenarien: Die exportierten Deltas müssen den gleichen Endzustand und Arbeiterzustand wie eine direkte Java-Ausführung erzeugen. Fehler bei der Szenarioinitialisierung liefern einen lesbaren Fehler-Replay. Die normalen Java-Tests bleiben eigenständige Assertions; ein exportierter Lauf mit Status `completed` bedeutet allein, dass der aufgezeichnete Ablauf beendet wurde.

`npm test --prefix web-viewer` prüft Vor-/Rückwärtssprünge, inkompatible Daten, gerichtete Grenzflächen, den Blick von außen/im Fels/im Tunnel, einen realen Three.js-Raycast sowie Publisher-Provenienz und Kataloggrenzen. Der Pages-Publisher führt diese Tests vor jeder Veröffentlichung aus.

Manueller Viewer-Check:

1. Site öffnen, Branch und Commit prüfen, ein `Mine`-Szenario wählen.
2. `Start` drücken, dann pausieren. Einzelschritte und Zeitleiste müssen Arbeiter und Blockzustand ändern; Rückspringen muss entfernten Fels wiederherstellen.
3. `Zum Arbeiter` drücken. Mit Rechtsziehen umsehen; WASD fliegt, Q/E ändert die Höhe, Shift beschleunigt. Im Fels müssen gegenüberliegende Tunnelwände sichtbar bleiben, im Tunnel die nahen Wände.
4. Einen Arbeiter oder sichtbaren Block anklicken und Inspector prüfen. `Marker` zeigt die authored semantischen Zonen, ohne den Ablauf zu verändern.
5. Auf einem Touch-Gerät links bewegen, rechts umsehen und Höhe mit ↑/↓ ändern. Hoch- und Querformat sowie Start/Pause/Zeitleiste prüfen.
6. Ein anderes Szenario und danach einen anderen Lauf wählen. Zustand und Inspector müssen zum neuen Lauf gehören. Ein fehlgeschlagener Build darf keinen alten Replay als neuen Erfolg zeigen.

Mobilgeräte-FPS und tatsächliche Hytale-Navigation sind durch die headless Checks nicht belegt.

### Minen-Kombinationslauf mit Rückweg

`MineBranchingScenarioTest` prüft sieben verbundene Abschnitte mit Längen 8/12/4/5/8/9/4 in allen vier Gebäudeausrichtungen. Gerade Fortsetzungen, Links-/Rechtsäste und Stützen werden gemeinsam in einer Voxelwelt ausgeführt. Der Miner unterbricht den 12er-Abschnitt nach 73 Blöcken, läuft zellenweise zum `workplace_access` zurück, geht über den Connector wieder hinein, bearbeitet zwei andere Äste und setzt denselben gespeicherten Abschnitt fort. Nach allen Arbeiten läuft er erneut zum Ausgang. Tests prüfen offene Rückwege ohne Teleport-Sprünge, unveränderte Voxels während der Rückkehr, erhaltenen Fortschritt/Stützen, keine Segment-/Prefab-Überschneidungen, stützenfreie Junctions sowie den vollständigen erwarteten Weltzustand (1248 Abbaublöcke, 12 Stützen).

Der Browser bietet `Mine · Kombinationen · NORTH/EAST/SOUTH/WEST`. Der Inspector zeigt aktuelle Phase, Abschnitt, Länge und gespeicherten Fortschritt. `SimulationRecordingExporterTest` vergleicht diese Replays mit direkter Ausführung und prüft das 32-MB-Veröffentlichungsbudget. Arbeitswahl und Navigation sind geskriptete/geometrische Fixtures; echte NPC-Wegfindung, Player-Teleport-Verhalten und die geplante Mehrminer-Koordination sind damit nicht belegt.