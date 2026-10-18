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

Die reine `MineBridgeTerrainPolicyTest` prüft, dass ebene Höhenprofile für eine Brücke zugelassen und ansteigende, abfallende oder in der Landung steigende Tunnelprofile ausgeschlossen werden. Die Hytale-seitige geladene Weltblockprüfung auf seichten Untergrund bleibt ein Adapter-/Runtime-Vertrag und ist durch diese Höhenprofiltests allein nicht verifiziert.


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

- Der normale GitHub-hosted Build startet die echte gepinnte Server-JAR als Bare-Probe. Für den zuvor geprüften Stand 0.6.8 galt: Auch mit <code>--bare</code> wird das Asset-Modul geladen; ohne zugehörige Assets endete der Test lizenzierte <code>Assets.zip</code> erwartungsgemäß an der Missing-Assets-Grenze. Er beweist nur die frühe Server-/Plugin-Manager-Kompatibilität.
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

## Minenraum-Abdeckung

`MineRoomPlannerTest` prüft deterministische Raumplanung, die kanonischen Wahrscheinlichkeitsbereiche sowie die V1-Verteilung der drei authored Typen. `MineRoomGeometryTest` prüft, dass der Raum-Aushub vollständig durch kleine semantische Work-Units abgedeckt wird. `MineNormalTaskSelectorTest` schützt aktive-first-Auswahl, gemeinsame Prioritäten und das Layer-8-Aging bis maximal 9. `CivMinePersistenceServiceTest` prüft N5 einschließlich Aging sowie das rückwärtskompatible Lesen von N4/N3/N2. `MineRoomPrefabAssetValidationTest` hält die drei ersten Test-Prefabs klein und auf bekannte einfache Blocktypen begrenzt.

Die tatsächliche Hytale-Prefab-Ladung, sichtbare Raumrotation und NPC-Navigation in einen gebauten Raum sind Engine-/Client-Verhalten. Ein Hytale-Local-Lauf ist für diesen Change kein Merge-Gate und wurde ohne ausdrückliche Freigabe in diesem Chat nicht ausgeführt.

## Minenhöhlen-/Gefahren-Abdeckung

`MineCavePolicyTest` prüft kleine, große und unvollständige Höhlenbeobachtungen sowie die Deduplizierung natürlicher Kammern. `MineNaturalChamberTest` schützt den terminalen `NATURAL_INTEGRATED`-Zustand. `MineCaveScannerContractTest` prüft die Adaptergrenze: nur `getChunkIfLoaded`, Ausschluss geplanter Minengeometrie, native Fluid-/Lava-Auswertung und keine neue Navigation.

Die bereits vorhandenen Hindernis-/Infrastrukturtests schützen weiterhin `BLOCKED` versus `ABANDONED`, 16-/10-Slice-Brückengrenzen, Wasser/Lava und Pflicht-Infrastruktur. Layer 7 ersetzt diese Logik nicht, sondern ergänzt die Höhlenklassifikation und die strengere Gegenseitenprüfung.

Ein echter Runtime-Check ist nur für Engine-Fragen sinnvoll, etwa wie natürliche Worldgen-Hohlräume in geladenen Chunks erscheinen. Zufälliges Worldgen soll nicht als deterministische Merge-Assertion verwendet werden.

## Minenatmosphäre-/Aging-Abdeckung

`MineInfrastructurePlannerTest` prüft deterministische Atmosphäre, dichtere Main- als Branch-Dekoration, Branch-Ausschluss von Ketten/Hängelaternen, einfachere Branch-Supports und Abstand zu bereits geplanter Infrastruktur. `MineAtmosphereExecutionContractTest` schützt den produktiven Adaptervertrag: ein gemeinsamer normaler Aging-Scheduler, nur Priority 10 separat, native Assetauflösung sowie Schutz von Navigation und zentralem Rail-Korridor.

Der fokussierte Hytale-Local-Test `mineatmosphere` baut eine künstliche Hauptstollen-Fixture, löst alle sechs Main-Dekorationsarten über Produktionscode auf, platziert sie nativ und lässt anschließend einen echten Civ-NPC durch den Mittelgang navigieren. Das ist Runtime-Evidenz für Asset-/Hitbox-/Navigationsverhalten, kein Merge-Gate.

## Browser-Replay-Vertrag

`SimulationRecordingExporterTest` prüft die Minengeometrie weiterhin in allen vier Ausrichtungen, veröffentlicht im Browser aber nur eine feste `NORTH`-Referenz. Für die Mine existieren drei bewusst getrennte Browser-Szenarien: `Mine · Geometry` isoliert Layer 2/3 ohne Infrastruktur, `Mine · Straight Full` zeigt einen einzelnen Hauptstollen mit allen vom produktiven `MineInfrastructurePlanner` geplanten Infrastrukturaufgaben, und `Mine · Full Scenario` kombiniert mehrere Miner, Branches, vollständige Core-Infrastrukturplanung sowie kontrollierte Layer-6-Hindernisse. Die normalen Java-Tests bleiben eigenständige Assertions; ein exportierter Lauf mit Status `completed` bedeutet allein, dass der aufgezeichnete Ablauf beendet wurde.

`MineTunnelVoxelizerTest` enthält zusätzlich visuelle Qualitätsinvarianten über viele deterministische Seeds: benachbarte Slice-Mittelpunkte dürfen nicht springen, Breite und Höhe ändern sich nur schrittweise, die gesamte Ausgrabungsmenge bleibt sechsfach zusammenhängend und organische Ausbuchtungen bleiben innerhalb einer begrenzten lokalen Hülle. Diese Checks sollen grobe optische Fehlformen bereits ohne Hytale erkennen; sie ersetzen keine Engine-/Asset-Verifikation.

`npm test --prefix web-viewer` prüft weiterhin das historische Replay-Modell, die gerichteten Voxel-Grenzflächen, Spectator-Picking und die fein abgestufte Fluggeschwindigkeit. Der lokale Browser-Build ist Teil der normalen CI. Es gibt keinen Pages-Publisher und keinen automatischen Replay-Katalog mehr; das alte Recording bleibt ein optionaler Offline-Testpfad.

Manueller Viewer-Check:

1. Site öffnen, Branch und Commit prüfen und zuerst `Mine · Geometry` wählen. Der Tunnel muss ohne Infrastruktur sauber und zusammenhängend entstehen.
2. `Mine · Straight Full` öffnen. Beim Vorspulen müssen wiederholt Stützen und Lichter zwischen normalen Ausgrabungsschritten erscheinen; geplante Stufen erscheinen an Höhenwechseln.
3. `Mine · Full Scenario` öffnen. Mehrere Miner, Branches, wiederholte Infrastruktur, Bridge/Water, Lava-Abbruch sowie Leave/Re-entry müssen im Ablauf erkennbar sein.
4. `Zum Arbeiter` drücken. Mit Rechtsziehen umsehen; WASD fliegt, Q/E ändert die Höhe, Shift beschleunigt. Im Fels müssen gegenüberliegende Tunnelwände sichtbar bleiben, ohne dass die äußere Felsquader-Hülle gerendert wird.
5. Einen Arbeiter oder sichtbaren Block anklicken und Inspector prüfen. `Marker` zeigt die semantischen Zonen, ohne den Ablauf zu verändern.
6. Auf einem Touch-Gerät links bewegen, rechts umsehen und Höhe mit ↑/↓ ändern. Hoch- und Querformat sowie Start/Pause/Zeitleiste prüfen.

Mobilgeräte-FPS und tatsächliche Hytale-Navigation sind durch die headless Checks nicht belegt.

### Mine-Szenarien

`Mine · Geometry` ist der reine Geometrie-Test. Er verwendet die produktiven Layer-2/3-Planer, zeigt aber bewusst keine Supports, Lichter, Treppen, Bridges oder Hindernisse.

`Mine · Straight Full` ist der verständliche Miner-Ablauf für einen einzelnen Hauptstollen. Alle vom produktiven `MineInfrastructurePlanner` gelieferten Supports, Lichter, Steps und Dekorationsaufgaben werden nach dem gleichen semantischen Fortschrittsrhythmus abgearbeitet, den der Hytale-Adapter verwendet. Die sichtbare Blockform dieser Infrastruktur ist eine richtungsbewusste Headless-Darstellung und behauptet nicht Hytales konkrete Assetauflösung.

`Mine · Full Scenario` verwendet das aktuelle `MineNetwork`, drei Miner und Haupt-/Seitenstollen. Die Miner starten auf einer kleinen Headless-Repräsentation des Mine-Prefabs, passieren `workplace_access` und `mine_tunnel_connector` und werden anschließend nach der produktiven normalen Task-Auswahl verteilt. Bereits aktive ausführbare Fronten mit freier Kapazität werden zuerst aufgefüllt; erst wenn keine aktive Front mehr Kapazität hat, wird eine weitere freie Front geöffnet. Für jeden Tunnel werden sämtliche Core-geplanten Infrastrukturaufgaben sichtbar abgearbeitet. Das kontrollierte Lava-Ereignis liegt spät auf einem Leaf-Branch, damit keine abhängigen Unterstollen künstlich unerschlossen bleiben. Der Replay enthält getrennte Marker für geplante und tatsächlich erschlossene Tunnelbereiche; der Viewer stellt diese nach dem Merge unterschiedlich dar. Ein kontrollierter Bridge-Task läuft vor der blockierten Front; Wasser-/Lava-Marker, Abbruch sowie Rausgehen/Wiedereinstieg testen zusätzliche Ablaufzustände. Hytale-Navigation, Fluidphysik und konkrete Assetplatzierung bleiben außerhalb des Headless-Systems.

## Live-MCP-Grenzen

`DevEntityTrackerTest` schützt Reset gegen Löschen ausgewählter bestehender Bewohner und behält nicht entfernte eigene NPCs für einen späteren Versuch. Mine- und Front-Diagnose sowie Miner-Zuweisung über `civdev mines`/`civdev mine-info`/`civdev assign-mine` nutzen die vorhandene Command-Bridge und den gemeinsam genutzten Zuweisungspfad; Hytale-Command-Signaturen werden durch den normalen Build abgesichert. Die Node-Live-Tests verbinden einen externen HTTP-Testserver und prüfen Zugangsdaten, Session-Mismatch, Attach/Detach sowie gesperrtes Start/Stop/Deployment ohne Prozessbesitz. Ein echter Client-/Einzelspieler-Durchlauf bleibt offen; die manuelle Schrittfolge steht in [local-mcp.md](local-mcp.md).


## Civdev/MCP-Diagnostik

Die lokale `civdev`-Diagnostik ist kein Ersatz für Gameplay-Tests. Reine Zustandscontainer wie die begrenzte Event-Historie und das explizite Tracking von Dev-Spawns werden als normale JUnit-Tests geprüft. Hytale-spezifische Command-, Spawn- und Entity-Removal-Signaturen werden durch den normalen Java-Build gegen die gepinnte Server-Abhängigkeit abgesichert. Ein echter In-Game-Test des localhost-MCP bleibt eine optionale Runtime-Verifikation und kein Merge-Gate.

## Layer-10 persistence/performance regression

`MineNetworkTest.batchAnchorUpdatesReplaceExistingIdWithoutReordering` covers batched replacement, duplicate incoming IDs and stable existing order. Existing `CivMinePersistenceServiceTest` exercises `N5` round trips and `N2`–`N4` decoding. Normal `./gradlew test` and `./gradlew build` are required for the change. Large-network profiling, memory usage and save/load latency are not verified by this small regression and should not be reported as proven improvements without measurements.


Minen-Regressionstests: `MineInfrastructureAvailabilityTest` prüft, dass ein `BUILD_STEP` erst nach Aushub des tieferen Übergangsabschnitts freigegeben wird (auf- und abwärts), ohne den früheren Brücken-Vorlauf zu verändern. `MineFrontCoordinatorTest` prüft drei gleichzeitig beanspruchte MAIN-Arbeiter mit unterschiedlichen Block-Claims und weiterhin nur zwei BRANCH-Arbeitern. `MineTuningTest` validiert den verdoppelten Ausgrabungsdurchsatz; `MineObstaclePolicyTest` lässt die explizite Altfall-Wiederaufnahme nur für `ABANDONED` mit passender offener Treppe zu. Native Treppenplatzierung, Block-Asset-Darstellung und die Wiederaufnahme einer gespeicherten Welt erfordern weiterhin einen fokussierten In-Game-Test.


`MinePlacementExcavationGuardTest` verifies optional infrastructure clearance at adjacent still-solid planned excavation positions, including diagonal/vertical neighbors; completed excavation and positions beyond the one-block buffer are allowed. An in-game check with overlapping tunnel fronts is still needed to verify that the worker releases the conflicting placement task, continues excavation, and later resumes the deferred support/light/decor without obstructing the new front.


`MineInfrastructurePlannerTest.leavesHeightTransitionsIntactButDoesNotScheduleDisabledStairs` checks preserved StepTransitions without BUILD_STEP jobs. In-game validation should use a fresh mine to avoid pre-existing ABANDONED state.


Hytale Live runner-selection validation: check strict allowlist parsing for `/hytale-live runner gabe status` and `thommy`, accept omitted runner as shared-label (`auto`) scheduling and reject unknown explicit runners including legacy `pc1`/`pc2`; ensure resolved `runs-on` labels include the selected exclusive label only for explicit selection, and verify announcement ties the runner and run ID to the triggering Issue #266 comment. Actual execution requires the target runner's matching label and a running game; an explicitly selected offline runner must not fall back to another machine; unqualified calls retain shared-label scheduling.

Für Hytale-Live-Batches sind insbesondere syntaktisch ungültiges JSON, unzulässige Spielbefehle, doppelte Schritt-IDs, überschrittene Wartebudgets, fehlgeschlagene Assertions und Abbruch nach Fehlern zu validieren. Eine lesende Batch-Probe am laufenden Spiel prüft erst nach Integration auf `main` den GitHub-Event-Workflow und die reale Bridge; reine CI-Builds beweisen keinen Live-Erfolg.

Die Hytale Agent API wird durch den regulären Java-Compile-/Build-Pfad mit der gepinnten Server-JAR geprüft. Die GitHub-Live-Node-Tests validieren erlaubte `agent`-Befehle und verwerfen unbekannte Befehle sowie Shell-Metazeichen. Diese Tests garantieren weder die Runtime-Verfügbarkeit des Prefab-Placement-Pfades noch die tatsächliche Fertigstellung eines Gebäudes. Ein Live-Nachweis benötigt die installierte neue Mod und einen verbundenen Spieler für `create-site`; eine sichere Read-only-Prüfung nutzt `agent capabilities`, `agent players`, `agent sites` und `agent block` an einer bekannten geladenen Position.

## Performance-Recorder

`CivPerformanceRecorderTest` verifiziert den inaktiven Fast-Path, eine einzige gleichzeitige Aufnahme, das 15-Minuten-Limit, Stopp/Neustart, Report-/Aufrufaggregate, Sample-Pagination und die getrennte Buchhaltungszeit. Ein nicht-gatender Test vergleicht Basis- und instrumentierte Schleifenlaufzeit; die Ergebnisse schwanken durch JIT, CPU und CI und sind keine akzeptierte absolute Overhead-Grenze. Belastbare Overhead-Ziele müssen zusätzlich mit reproduzierbaren Hytale-Szenarien und mehreren Messläufen überprüft werden. Die UI-/HUD-Laufzeitdarstellung bleibt ein fokussierter manueller Client-Test.

## Performance-Timeline

`CivPerformanceRecorderTest` prüft Intervallkosten, kumulierte Aufrufe, Ereignis-Zeitstempel und die Verfügbarkeit der Marker nach Aufzeichnungsende. Der Laufzeitvergleich ohne/mit Profiling bleibt diagnostisch und besitzt keine anfälligen Zeit-Grenzwerte. Reale FPS-Korrelation benötigt erst eine nachgewiesene clientseitige Messquelle.

## Ingame-Profilerbefehle

Die vier Player-Commands (`/civdebug perf start|stop|status|report`) delegieren an den bereits über JUnit getesteten `CivPerformanceRecorder`. Der normale Gradle-Test und Build deckt Kompilierung und Profiler-Regressionen ab; die konkrete Darstellung von Chatnachrichten wird ohne gesonderten Hytale-Local-Lauf nicht als zur Laufzeit bestätigt ausgegeben.

## Isolierter Plugin-Reload-Test

Das opt-in Hytale-Local-Szenario `reload` verwendet den Runner mit Label `hytale-pc-gabe`. Es startet einen separaten Serverprozess in `RUNNER_TEMP` und ein Test-Plugin mit dem Marker `CIV_RELOAD_MARKER_A`. Vor dem Boot wird eine zweite Plugin-JAR erzeugt, in der ausschließlich die gleich lange Marker-Konstante durch `CIV_RELOAD_MARKER_B` ersetzt wird. Nach dem Boot entlädt das Skript das Plugin über `plugin unload`, tauscht die isolierte JAR aus und lädt mit `plugin load` neu. Der Markerbefehl muss anschließend den B-Code ausgeben. Zwei weitere Reloads dienen als erster Stabilitätstest. Das Szenario berührt keine regulären Mods oder Spielwelten. NPC-Zustand, registrierte ECS-Komponenten und clientseitige Darstellung sind **durch diesen technischen Marker-Test noch nicht verifiziert**; diese benötigen eigene Folgeprüfungen.

**Sicherheitsgrenze (Hytale 0.6.8):** Ein ergänzender isolierter Versuch mit einem geclaimten Miner scheiterte bereits beim `plugin unload` an einer `NullPointerException` in der Hytale-ECS-Komponentenabmeldung (Actions-Lauf `37906037185`). Das Marker-Szenario bleibt daher bewusst ein NPC-freier API-Test. Ein grüner `reload`-Lauf ist **keine Freigabe** für Hot Reload in aktiven Civ-Welten. Für Deployments mit Bewohnern gilt: kontrollierter Stopp und Neustart.

## Phase 2: isolierter Deployment-/Restart-Vertrag

Das opt-in Hytale-Local-Szenario `deployment` verwendet ausschließlich eine frisch erzeugte Runtime unter `RUNNER_TEMP` auf dem Runner `gabe`. Es startet einen echten Hytale-Server mit Civ-NPC, speichert dessen persistente Daten durch normalen Shutdown, sichert dann Plugin-JAR und Civ-Asset-Pack, stellt ein unterscheidbares JAR-Update bereit, installiert dieses nach Prozessende und startet den Server erneut. Es verlangt, dass die NPC-UUID mit Claim, Beruf, XP und Arbeitsplatz wiederhergestellt wird. SHA-256-Hashes prüfen die installierte JAR. Bei Installations- oder Verifikationsfehlern stellt das Skript die vorherigen Sandbox-Plugin- und Asset-Dateien wieder her. Ein simulierter Fehler und ein regulärer Client/Produktivserver-Deploy sind **nicht** Teil dieser Prüfung. Der Test führt keinen Neustart der normalen Hytale-Instanz aus.

**Phase 3/4:** `deploymentrollback` prüft per absichtlichem Fehler nach JAR-/Asset-Installation die SHA-256-Wiederherstellung und dass der gespeicherte NPC mit der vorherigen Version nach einem neuen Start geladen werden kann. `deploymentreal` prüft dagegen einen Neustart zwischen zwei commitgebundenen echten GitHub-CI-JAR-Artefakten und persistierte Civ-Daten. Beide Tests laufen nur in einer isolierten Hytale-Local-Instanz, ersetzen keine Rollback-Validierung für echte Spielerwelten und garantieren keine automatisierte Produktionsfreigabe.

**Phase 5 – vereinfachte Bedienung:** Der GitHub-Issue-Befehl `/hytale-deploy <commit-sha>` ist ein autorisierter Alias für das vorhandene Szenario `deploymentreal` und unterliegt denselben CI-Artefakt-, Runner- und Isolationsgrenzen. Der Alias verändert keine Installationslogik und benötigt keine zusätzliche Gameplay-Probe; seine Autorisierung und die Befehlsauswertung sind Teil des Hytale-Local-Workflows.

## Lokaler Live-Server

`LocalSimulationServerTest` startet die API auf einem temporären Loopback-Port, verifiziert Szenarioauswahl, Tick-Schritt, Start/Pause, Reset und Live-Zustand ohne vorberechnete Replay-Dateien. Die Browseroberfläche wird weiterhin über den `web-viewer`-Build und Node-Tests validiert; echtes Rendering und Pointer-Lock erfordern ergänzend einen manuellen Browsercheck.

## Manuelle Abnahme der lokalen Simulations-Sandbox (10. Oktober 2026)

Der Nutzer hat im lokalen Browser folgende neun Tests erfolgreich bestätigt: Viewer-Start; laufender Tick-Zähler bei Start; Stillstand bei Pause; exakt ein Tick bei Einzelschritt; Reset auf Tick 0; freie WASD-/Maus-Kamera; kleine Geschwindigkeitsänderungen nach drei Mausrad-Rastungen; konfigurierte NPC-Startanzahl; sichtbare arbeitende NPCs und Ereignisprotokoll. Diese Rückmeldung belegt die grundlegende Bedienbarkeit des lokalen Live-Viewers, **nicht** den Export einer realen Hytale-Welt, Korrektheit nativer Hytale-Navigation oder vollständige Parität der beiden Miner-Adapter. Diese weiteren Abnahmen bleiben in `docs/simulation-sandbox-roadmap.md` offen.

`web-viewer/test/terrain-chunks.test.mjs` prüft selektives Mesh-Invalidieren auch über Chunkgrenzen. `web-viewer/test/performance-recorder.test.mjs` prüft das begrenzte JSON-Recording. Messwerte werden im Browser aufgezeichnet; Performanceverbesserung auf unterschiedlichen PCs bleibt durch reale Exporttests zu prüfen.

Der Regressionstest `minersStartInSameReachableCavernInsteadOfIsolatedPockets` prüft, dass Miner in importierten Welten nicht über getrennte Höhlenteile verteilt werden. Eine erreichbare Anfangsposition allein beweist noch keine erfolgreiche spätere Front-Navigation.

`terrainDeltaTransmitsOnlyChangedBlocksAndResetCanRecoverFullSnapshot` verifiziert den lokalen Terrain-Deltavertrag einschließlich Reset-Fallback. Die Browser-Performanceaufzeichnung enthält nun pro Sekunde auch begrenzte NPC-Zustände und letzte Ereignisse; damit lassen sich `BLOCKED`-Verläufe zusammen mit FPS und Mesh-Kosten auswerten.

Ein importiertes Archiv liefert beim Start sofort einen leeren NPC-Zustand; `LocalSimulationServerTest` prüft danach die ausdrücklich angeforderte Miner-Konfiguration. Das verhindert die bisherige synchrone Spawnsuche beim initialen Viewer-Start. Das erstmalige Einlesen einer sehr großen gzip-JSON-Datei ist dabei noch nicht unabhängig asynchronisiert.

Bei Fehlersuche am lokalen Live-Start sind die `[CIV STARTUP]`-Zeilen und die Meldung `READY http://localhost:8765/api/state` maßgeblich. Sie unterscheiden teures gzip-JSON-Decoding von der Voxelindex-Erstellung und Listener-Problemen; die Gradle-Anzeige `75% EXECUTING` ist kein Server-Readiness-Signal.

`denseVoxelIndexPreservesAbsoluteBoundsAndFluidCategories` verifiziert die kompakte Voxel-Speicherung für negative absolute X-Koordinaten, Flüssigkeit, Standfähigkeit, Änderungen und Out-of-Bounds-Abfragen. Die tatsächliche Laufzeit und Heap-Spitze bei 1,87 Millionen Blöcken bleibt durch `[CIV STARTUP]` und den Nutzer-PC zu prüfen.

Die UI-/API-Tests für das Mine Simulation Lab prüfen den begrenzten `/sim`-Parser und die Ablehnung nativer Fremdcommands. `MineWorkerRouteDecisionTest` prüft den von Hytale und Headless genutzten semantischen Zugangspfad. `MineSandboxPrefabTest` validiert die authored Phase-1-Marker und das Platzierungs-Bounds-Handling; tatsächliche komplexe prefab-interne Wegfindung bleibt eine explizite Headless-Einschränkung. Die CI führt keine zusätzlichen echten Hytale-Läufe aus.

Der API-Regressionstest für Terrain-Deltas kontrolliert nun zusätzlich den monotonen `terrainEpoch`-Wechsel nach Reset. Ein vollständiger Browser-Snapshot wird beim Austausch des Weltobjekts auch bei identischem Revisionszähler angefordert.

`web-viewer/test/console-commands.test.mjs` testet den read-only Befehl `/sim worker <id>` und lehnt ungültige und pfadähnliche IDs ab. Die angezeigten Diagnosewerte stammen aus `/api/debug` und `/api/state` des lokalen Simulators.

## Phase 5B: gemeinsamer Miner-Lifecycle

`MinerWorkControllerTest` prüft die gemeinsame Engine-Grenze: Zugang → Connector → Arbeit → Connector → Zugang, Kapazität und exklusive Claims für drei Miner, Unterbrechung mit Freigabe, Wiederaufnahme, veraltete Claims/Arbeitsrevisionen, terminale Navigation, begrenzte Retries, Priority-10-Kontinuität, Platzierung und verschiedene Raum-Bauabschnitte. Golden-Historien vergleichen die produktive Coordinator-Verdrahtung mit der unabhängigen Headless-Verdrahtung bei identischen Beobachtungen/Ergebnissen. Ein weiterer Test vergleicht Arbeitsbudgets bei 500-ms- und 50-ms-Ticks.

`MineRoomProgressTest` prüft gemeinsamen Raumfortschritt und wiederholte Bauabschnitt-Ergebnisse. `AutonomousMineWorldTest` führt den echten `SimulationRuntime.MineLab` mit drei Minern, manueller Unterbrechung, Voxelabbau und Rückkehr aus; kontrollierte Wasser-/Lavafälle stoppen Arbeit und erlauben nach expliziter Recovery einen neuen Versuch ohne Verlust abgeschlossener Arbeit. Die Hytale-Quellvertragsprüfungen folgen der verschobenen Core-Grenze und schützen weiterhin native Block-/Prefab-Operationen, Gebäudegrenzen, Fluidprüfungen und Diagnostik.

Diese Tests beweisen die gemeinsame Ablaufsteuerung. Sie beweisen keine nativen Hytale-Wege, Blockmodelle oder Teleport-Semantik. Das Lab quittiert Infrastruktur und drei Raum-Bauabschnitte synthetisch; es kopiert weder native Asset-Auflösung noch Placementphysik. Der historische Replay-Exporter liefert geskriptete Bildfolgen und zählt nicht als Paritätstest.

## Reale Geländefixture für Miner-Simulationen

`RealRegionMineFixture` verwendet die **vollständige Materialklassifikation** aus `region.civworld.gz` in `src/test/resources/fixtures/full-region-materials.b64`: alle 1.872.500 Zellen der Originalregion werden an ihren ursprünglichen Weltkoordinaten in `VoxelWorld` geladen. Die gzip-komprimierten Materialdaten werden per SHA-256 auf Unverändertheit geprüft. Die Materialkarte bewahrt die Klassifikation für Luft, feste Blöcke, Wasser, Lava und sonstige Flüssigkeiten, aber **keine** Block-IDs, Drehungen oder Dekorationen. Der `Mine_01`-Test platziert deshalb das echte Prefab weiterhin zusätzlich in die Voxelwelt. Gezielt synthetische Fixtures bleiben für isolierte Sonderfälle sinnvoll. Der Originalexport wird nur für Tests benötigt, die blockgenaue Asset-Daten erfordern.

### Regression: fehlender Laufboden im echten Mine_01-Szenario

Die reale Weltprobe platziert das unveränderte `Mine_01`-Prefab am aus dem Originalexport abgeleiteten Oberflächenanker (118,102,12), erzeugt drei durch Wegsuche erreichbare Miner und testet den Core-Miner-Lifecycle über neun Tunnelabschnitte. Im Original ist am anschließenden Tunnelboden (118,102,22) Luft. Ohne Brückenauftrag stoppen alle Miner nach 111 abgebauten Blöcken und zwei vollständigen Abschnitten; im Headless-Simulator bleibt die Front danach gesperrt. Die ursprüngliche Headless-Sonderregel baute auch bei flachen Bodenlücken eine Brücke. Das wich von der bestehenden Hytale-Regel ab: native Miner tolerieren beim Erreichen eines Arbeitsziels 1,6 Blöcke horizontal und 1 Block vertikal; die Brückenprüfung behandelt flachen Boden mit naher tieferer Auflage nicht als tiefe Brückenlücke. MineLab sucht deshalb für nicht standfähige Anker eine tatsächlich erreichbare Standposition innerhalb derselben Ankunftstoleranz und verzichtet bei flachen Lücken auf Pflichtbrücken. Beim simulierten Überqueren einer Stufenkante wird eine in der Luft unterbrochene Bewegung beim **Zielwechsel** auf den darunterliegenden tragfähigen Standblock gesetzt; laufende Aufwärtsbewegungen werden nicht durch erneute Schwerkraftkorrekturen zurückgesetzt. Der Echtwelt-Test war ohne diese Korrektur zunächst bei Abschnitt 4 im Wegfindungszustand festgefahren. Die konservative Erkennung tiefer, begehbarkeitssichernder Brücken ist nur eine Headless-Abbildung der bestehenden nativen Bedingungen; Original-Hytale-Code bleibt unverändert. Der Regressionstest prüft, dass der reale flache Luftblock nicht zugeschüttet wird und die Miner trotzdem weiterarbeiten. Native Seek-/Prefab-Platzierung ist dadurch nicht runtime-verifiziert.

### Speicheroptimierter Material-Loader

`VoxelWorld.fromMaterialCodes(bounds, byte[])` erstellt die Voxel-Simulation direkt aus einem x-/y-/z-sortierten Materialbytearray. Der Loader prüft Länge und Materialcodes und erstellt eine defensive Kopie; er erzeugt keine `WorldArchive.Cell`-Objekte und keine Koordinaten-HashSets. `RealRegionMineFixture` nutzt denselben Pfad für die versionierte Weltprobe. `CompactVoxelWorldTest` prüft zusätzlich die vollständige Regiongröße von 1.872.500 Zellen (synthetisches Volumen für diesen Test) sowie Eingabevalidierung. Ein separater lokaler Java-21-Belastungstest hat den vollständigen Originalmaterialexport mit 32, 64 und 128 MB Heap korrekt entpackt und den SHA-256-Inhalt bestätigt. Das ist nicht mit einem vollständigen Java-25-Miner-Szenario auf der gesamten Originalregion gleichzusetzen: Die vollständige Materialkarte ist jetzt ins Repository übertragen; Original-Blocktypen bleiben außerhalb dieser Klassifikations-Fixture.

### Vollständige reale Testregion

Die standardmäßig genutzte `RealRegionMineFixture.load()` lädt die **gesamte** Materialklassifikation aus dem hochgeladenen `region.civworld.gz`, versioniert als `src/test/resources/fixtures/full-region-materials.b64`. Originalgrenzen: x=[56,181), y=[8,148), z=[-41,66), also 1.872.500 Zellen. Das GZIP-komprimierte Bytefeld wird erst nach einer exakten SHA-256-Integritätsprüfung (`34cc1622257266142e946dcd86583e4347928fd99a9e01d8d6c16867722aae35`) direkt über `VoxelWorld.fromMaterialCodes` geladen; dabei werden keine 1,87 Millionen `WorldArchive.Cell`-Objekte erstellt. Die Datei erhält die Kategorien AIR, SOLID, WATER, LAVA und OTHER_FLUID, nicht aber die vollständigen Hytale-Block-IDs. Die Mine-Tests verwenden weiterhin die tatsächliche `Mine_01`-Prefab-Platzierung und reproduzieren den Bodenlücken-Fix.

### Forester flat-world headless checks

`ForesterPlantingPlannerTest` checks deterministic seeded candidate selection, bounded work radius, exclusion and minimum spacing. `ForesterFlatWorldTest` runs two foresters on a 64 × 64 flat voxel world for 1200 simulation ticks, checks reproducible snapshots and tree-site uniqueness. This covers headless Core/adapter behavior, not Hytale engine terrain/block placement.

## Standalone Java-21-Simulator (CI nach Main-Push)

Der unabhängige Workflow `.github/workflows/simulator-build.yml` baut nach jedem Push auf `main` den vorhandenen `core` und `simulation` mit Java 21 als eigene Application-Distribution und führt als Smoke-Test das bestehende Szenario `woodcutter-basic` über 1000 Ticks aus. Dies prüft Laden, Kompilieren und Headless-Ausführung der gemeinsam verwendeten Java-Quellen, jedoch keine Hytale-Engine-Semantik und keine grafische Oberfläche. Der bestehende Java-25-Root-Test-/Buildpfad bleibt unabhängig. Ein lokaler identischer Test lautet: `cd simulator && ../gradlew --project-dir . clean installDist && ./build/install/civ-simulator/bin/civ-simulator woodcutter-basic 1000`.

### Forester and woodcutter regeneration scenario

`forester-woodcutter-flat` extends the existing 64 × 64 flat-world fixture with exactly one forester and one woodcutter. No trees are mature at startup. The forester plants and saplings mature; the woodcutter subsequently finds and fells those trees via the existing Core woodcutter behavior. `ForesterWoodcutterFlatWorldTest` verifies both occupations, deterministic seeded replay, actual tree felling and continuing tree supply over 4000 ticks. It does not test wood inventories or warehouse delivery, which are not implemented in this scenario.


### Lokale Simulatorprüfung vor Integration (nur bei betroffenen Änderungen)

Wenn Simulator, Szenario oder simulierte Core-Regeln geändert werden, vor dem PR-Merge Java-21-Quellen mithilfe des aktuellen `main`-Simulator-ZIPs lokal kompilieren und das relevante Szenario sowie passende Regressionen ausführen. Keine allgemeine Pflicht für sonstige Mod-, UI-, Hytale- oder Dokumentationsänderungen. Der CLI-Override und `forester-woodcutter-flat` mit 4000 Ticks wurden am 18.10.2026 nachweislich lokal ausgeführt; Details in `docs/development.md`. Die reguläre PR-CI ersetzt das nicht und bleibt weiterhin verpflichtend.

### Holzlager-Simulation

`woodcutter-storage` prüft isoliert einen Holzfäller, drei bereits ausgewachsene Bäume, zwei simulierte Slots mit je 100 Einheiten und ein Lager. Baum-Erträge sind reproduzierbar zwischen 60 und 140 Holz (Erwartungswert 100); nach vollständigem Fällen wird Holz aufgenommen, Überschuss bleibt als logischer Holzstapel zurück, und der Holzfäller liefert im Lager ab. Die konservierte Menge umfasst Lager, Traglast und Holzstapel; Tests prüfen Teilaufnahme, Abholung nach Transport, Nachlieferung und deterministischen Ablauf. Dieses Szenario benutzt weder Förster noch natives Hytale-Inventar. Lokaler Test vor GitHub-Upload ist Pflicht; siehe `docs/development.md`.

### Hytale Local: Woodcutter-Spawn und Asset-Kompatibilität

Das `woodcutter`-Szenario muss die gesamte Spawn-Voraussetzung prüfen: eine aktive Chunk-Spalte, aktive Y-Sektion, ein erfolgreicher nativer Spawn-Test, gültiger Entity-Verweis und erfolgreiches Claimen. In einer isolierten Welt ohne Spieler werden Chunks während des Szenarios aktiv gehalten, damit Hytales normales Unloading den Test nicht vorzeitig beendet. Ein separater Fehlerpfad betrifft fehlende Kosmetik-IDs im Wikinger-Aussehenskatalog; der Generator gleicht Pflichtslots gegen das native Register ab. Der Hytale-Local-Test ergänzt die grüne CI, weil weder Tick-/Chunk-Lebenszyklus noch native Skin-Validierung allein durch Java-Kompilierung bestätigt werden.

**Bestätigter Runtime-Nachweis:** [Hytale Local #39106588274](https://github.com/weidmanngabriel/hytale-civ/actions/runs/39106588274), getesteter Commit `97c563eed3d0612c0d901632c94213632433074d`, Szenario `woodcutter`, `CIV_WOODCUTTER_RUNTIME_PASS` bei Tick 265. Chunk und Sektion waren aktiv; die native Spawn-Prüfung ergab `TEST_OK`, die Entity war gültig. Bei Tick 249 wurde ein Baum gefällt (54 → 36 Holzblöcke), bei Tick 265 war ein neues Ziel gesetzt; NPC-Bewegung 4,27 Blöcke. Das deckt den Holztransport zur Hütte noch nicht ab. Hintergrund: [Hytale-Laufzeitdiagnose](hytale/woodcutter-runtime-diagnostic.md).


### Förster-Core-3D-Teilschritt (noch keine Hytale-Abnahme)

`ForesterPlantingPlannerTest` prüft 3D-Kugel, Höhengrenze und deterministische Höhenauflösung. `ForesterWorkControllerTest` prüft konkurrierende Claims, manuelle Freigabe, Vorratsmangel, Navigationsfehler, Zielinvalidierung und identische Entscheidungen zweier Core-Instanzen bei identischen Engine-Rückmeldungen. `ForesterSurfaceTest` prüft obere freie Oberflächen, Höhlendecken und Wasser. Der Headless-Gemeinschaftslauf `forester-woodcutter-flat` wurde lokal mit 4.000 Ticks und Java 21 erfolgreich kompiliert/ausgeführt; zwei zusätzliche Core-/3D-Smoke-Checks bestanden. Dies sind keine echten Hytale-Runtime- oder nativen Pflanznachweise. Gradle-Komplettsuite und PR-CI sind gesondert zu prüfen.


Das reale `region.civworld.gz` wurde lokal mit `WorldArchive.read` geladen: 1.872.500 Zellen, 9.164 konservativ als freiliegend klassifizierte Gras-/Erdspalten, 1.057 beobachtete Stammblöcke. Das bestehende Local-Simulation-HTTP-API hat `forester-imported` im Szenariokatalog angeboten; Auswahl und 3.200 Ticks mit originalem Terrain liefen lokal, mit 43 simulierten Bäumen im WorldSnapshot. Der Test beweist **nicht**, dass Hytale diese Pflanzenarten nativ akzeptiert, dass die Försterei als Hütte gebaut wurde oder dass ein echter NPC Setzlinge gesetzt hat.

### Drei Waldszenarien auf demselben Original-Snapshot

`ImportedForestScenarioTest` verwendet das eingecheckte `src/test/resources/fixtures/region.civworld.gz`. Die Varianten `forester-imported`, `woodcutter-imported` und `forest-cycle-imported` erzeugen jeweils eine unabhängige Voxelwelt aus demselben Roharchiv; bestehende Miner-Fixtures bleiben unverändert. Für importierte Holzfällerbäume wird vor der Zuweisung ein tatsächlich begehbarer Nachbarstandpunkt mit dem bestehenden Voxel-A* gesucht; unerreichbare Stämme werden nicht als Aufgaben ausgegeben.

Lokaler Java-21-Test vor Upload der geänderten Quellen: 3.000 Ticks je Variante, 41 Bäume bei Förster-only, 4 gefällte Bäume bei Holzfäller-only, 6 gefällte Bäume und 40 verbleibende Bäume in der gemeinsamen Variante. Kompilation und HTTP-Szenarioauswahl für alle drei Varianten erfolgreich. Der Test ist ein Headless-Nachweis und kein Hytale-Local-Szenario. Die importierten Bäume werden derzeit als erreichbare Stammziele behandelt; ein vollständiges natives Entfernen aller Stamm-/Blattblöcke ist nicht implementiert.


### Försterei: native Engine-Verträge und echte NPCs

Der Java-25-PR-CI-Build für `8ec0f56a203429434ba1c1c1860ac472ca0052e7` war grün. Hytale-Local `foresterassets` und `foresterplant` liefen gegen exakt diesen Commit ([Run 39119560340](https://github.com/weidmanngabriel/hytale-civ/actions/runs/39119560340)). Die Runtime selbst meldete jeweils `CIV_FORESTER_ASSET_PROBE_PASS` bzw. `CIV_FORESTER_NATIVE_PASS`, einschließlich Inventar 1/5/20 unverändert und drei realer FarmingBlock-Komponenten. Der Gesamt-Harnesslauf wurde **nicht** grün, weil das Prüfszenario einen vorhandenen nicht-null Exit-Code trotz nativer PASS- und vollständiger Shutdown-Marker noch als Fehler behandelte. Die Auswertung wurde analog zum bewährten Holzfäller-Harness angepasst und muss erneut ausgeführt werden. Diese Nachweise beziehen sich nicht auf autonomes NPC-Pflanzen oder die spätere Wachstumsgeschwindigkeit.

Das zusätzliche isolierte `foresternpc`-Szenario startet eine echte Hytale-`Civ_Inhabitant`-Instanz, platziert das originale Försterei-Prefab, befüllt deren echte Kiste mit Oak/Birch/Spruce (Mengen 1/5/20), weist den Beruf zu und prüft, ob mindestens zwei echte Sapling-Blöcke gesetzt werden, sich der NPC bewegt und sämtliche Stackmengen unverändert sind. Ergebnis dieses Hytale-Local-Laufs ist gesondert nachzutragen; native Wachstumstests verwenden keine feste Tickzahl als Garantie.


### Försterei – geprüfter Runtime-Abschluss (18.10.2026)

Die zuvor im Abschnitt oben noch offenen Hytale-Local-Prüfungen wurden nachgeholt: [Run 39120832278](https://github.com/weidmanngabriel/hytale-civ/actions/runs/39120832278) ist für `b549ba3b7a3b3a045019ce9687bde2d4d9bc9a2b` **erfolgreich** und umfasst `foresterassets`, `foresterplant` und `foresternpc`. Dabei wurde nachgewiesen, dass echte Oak-, Birch- und Spruce-Setzlinge native FarmingBlock-Zustände erhalten, ein echter Civ-Förster autonom läuft und mindestens zwei Setzlinge platziert, während die ursprünglichen Truhenstacks (1/5/20) unverändert bleiben. Die zunächst fehlende native Containerkomponente des frisch platzierten Försterei-Prefabs wird über `NativeBuildingStorage.initializeNewPrefabChest` initialisiert, ohne bestehende bestückte Truhen zu ersetzen. Diese nachgereichte Abnahme ersetzt die älteren offenen Aussagen zum NPC-Nachweis weiter oben. Nicht nachgewiesen ist das vollständige native Auswachsen eines Baums und anschließendes Fällen in einer einzigen Hytale-Local-Session. Der zuletzt ergänzte automatische Arbeitsplatz-Claim und die Reservierungs-/Bewegungs-Korrekturen benötigen einen weiteren auf exakt den finalen Commit bezogenen Hytale-Local-Lauf.

## Browser-Live-Viewer

`npm test --prefix web-viewer` prüft unter anderem den DOM-Vertrag zwischen `web-viewer/live.html` und `web-viewer/src/live.js`: verwendete Element-IDs müssen vorhanden und im HTML eindeutig sein. Damit wird der Startfehler durch fehlende MineLab-, Konsolen- oder Performance-Bedienelemente verhindert.
