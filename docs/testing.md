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

Engine-Schritte dürfen in solchen Tests als kontrollierte Ergebnisse zurückgespielt werden. Wenn der Core beispielsweise einen Bewegungs-Intent erzeugt, kann der Test „angekommen“ melden, ohne Hytales Navigation zu starten. Damit werden Ablauf, Unterbrechung und Wiederaufnahme headless geprüft; nur die tatsächliche Umsetzung des Intents durch Hytale bleibt ein Adapter-/Runtime-Test.

Szenario-Tests bleiben Hytale-unabhängig, außer das geprüfte Verhalten ist tatsächlich ein Engine-Vertrag. UI-Klicks sind kein Ersatz für einen Core-Test einer Regel, die auch ohne UI formulierbar ist.

## Hytale-Adapter-Tests

Tests für Übersetzung und Adapterverhalten, soweit dies ohne laufenden Server sinnvoll möglich ist.

Der aktuelle RTS-Prototyp betrifft vor allem Kamera, Cursor-Zielerfassung, interaktive Custom Pages, Platzierungsvorschau, natives Baumfällen und Hytale-NPC-Bewegung. First-Person-Personenaktionen hängen zusätzlich vom Runtime-Dispatch von <code>UseEntityEvent.Pre</code> auf dem handelnden Spieler ab. Diese Engine-Verträge werden deshalb nicht künstlich durch gemockte Unit-Tests vorgetäuscht.

## Hytale-Server-Integrationstests

Zukünftige kontrollierte Server-Tests für Lifecycle, Registrierung und Engine-Interaktion. Noch nicht umgesetzt.

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

- <code>FarmBuildingTest</code>: prüft einen Bauernplatz, fünf Sekunden aktive Arbeit pro Weizen, verpflichtendes Verlassen nach jedem Produktionsschritt und den harten Stopp bei zehn Weizen.
- <code>FarmPrefabValidationTest</code>: prüft die beiden eingecheckten Creator-Prefabs. Die Farm besitzt eine leere native 18-Slot-Truhe sowie <code>workplace_access</code>- und <code>output_storage</code>-Marker; das separate 6×6-Weizenfeld besteht aus nativem Tilled Soil und besitzt den <code>field</code>-Marker.

Manuelle Abnahme:

1. Sowohl <code>hytale-civ.jar</code> als auch <code>hytale-civ-assets</code> installieren beziehungsweise deployen.
2. <code>/civrtstest</code> ausführen.
3. <code>/civbuild</code> ausführen und bestätigen, dass ein modaler Katalog **Gebäude** geöffnet wird und normale RTS-Weltinteraktion blockiert.
4. Bestätigen, dass der aktuelle Katalog **Farm** und **Weizenfeld** enthält und geschlossen werden kann, ohne die Platzierung zu starten.
5. <code>/civbuild</code> erneut ausführen, **Farm** auswählen und den Mauszeiger über flaches Gras bewegen. Prüfen, dass die Validierung trotz des Y−1-Ankers einen gültigen Candidate erzeugt und Civ eine native <code>PersistentPrefabPreview</code> als Ghost erzeugt. Das Editor-Paste-Tool darf dabei nicht aktiviert werden.
6. Rechtsklick ausführen und bestätigen, dass die Platzierung abgebrochen wird, ohne die Welt zu verändern.
7. **Farm** erneut wählen und auf flachem Boden mit Linksklick bestätigen. Es dürfen **keine echten Prefab-Blöcke** entstehen. Der bewegte <code>PersistentPrefabPreview</code>-Ghost bleibt als Baustelle stehen. Sein Civ-Anker liegt exakt einen Block unter dem anvisierten Oberflächenblock.
8. Dasselbe über einem Loch, einer Flüssigkeit, tatsächlich blockiertem Bauvolumen oberhalb des eingelassenen Baugrunds und einer bestehenden Farmfläche versuchen. Bestätigen, dass die Platzierung mit einem Grund abgelehnt wird und der Platzierungsmodus aktiv bleibt.
9. **Weizenfeld** auswählen und denselben Ablauf prüfen. Auch hier darf beim Bestätigen nicht sofort das echte Feld entstehen; es muss eine getrennte Baustellen-Vorschau an der abgesenkten Civ-Bauposition entstehen.
10. Bestätigen, dass <code>/civfarm</code> weiterhin denselben Farm-Platzierungsablauf startet wie das Menü.
11. Mit <code>/civclaim</code> einen NPC beanspruchen, ihn auswählen, im Arbeitsbereich der Farm rechts klicken und bestätigen, dass die Zuweisung als Bauer weiterhin funktioniert.
12. Bestätigen, dass der NPC zum Eingang läuft, ungefähr fünf Sekunden innen arbeitet, das Gebäude verlässt, für jeden Produktionsschritt erneut hineingeht und nach dem zehnten Weizen draußen bleibt.
13. Während der Bauer zu einem Farmziel läuft, ein manuelles Bodenziel geben. Bestätigen, dass er zuerst dorthin läuft und anschließend seinen Farmablauf fortsetzt.
14. Den Server nach einer Berufszuteilung sauber neu starten und bestätigen, dass der Bewohner weiterhin als Civ-Bewohner mit demselben Namen und Beruf erkannt wird. Die konkrete Farm-Arbeitsplatzzuweisung bleibt derzeit laufzeitgebunden.

Mehrspieler-Abnahme:

1. Zwei Spieler verbinden und mit beiden in den RTS-Modus wechseln.
2. Farm-Platzierung unabhängig voneinander starten und bestätigen, dass Bewegung oder Abbruch einer Vorschau die Vorschau beziehungsweise den Zustand des anderen Spielers nicht verändert.
3. Beide Vorschauen auf überlappende, zunächst gültige Flächen richten.
4. Spieler A zuerst platzieren lassen, danach Spieler B bestätigen lassen, ohne den Mauszeiger zu bewegen.
5. Bestätigen, dass Spieler B durch die erneute serverseitige Prüfung abgelehnt wird und keine überlappende Farm entsteht.

Der ursprüngliche Schnappschuss der Bodenblöcke bleibt interner Laufzeitzustand, bis eine Abrissoberfläche existiert. Sobald Abriss umgesetzt wird, muss der Abnahmetest die exakte Wiederherstellung dieser gespeicherten Blöcke prüfen. Rotation bleibt ein separates zukünftiges Feature.
