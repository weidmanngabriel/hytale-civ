# NPC-Combat

Diese Seite hält verifizierte Integrationsregeln für native Hytale-NPC-Kämpfe in Civ fest.

## Grundregel

Civ soll für NPC-Kämpfe Hytales natives Combat-System verwenden, statt einen parallelen Civ-Schadens- oder HP-Stack aufzubauen. Zielauswahl und Gameplay-Priorität können aus Civ kommen; Bewegung, Interaction-Ausführung, Trefferprüfung, Schaden, Knockback, Entity-Stats und Tod bleiben soweit möglich bei Hytale.

Vor unbekannten Hytale-Integrationen gilt außerdem der projektweite Research-first-Ablauf aus `docs/research-first.md`: vorhandene Nutzung im Projekt prüfen, öffentliche Beispiele und Assets recherchieren, die gepinnte JAR/Assets inspizieren und erst danach einen eigenen Mechanismus entwerfen.

## Zwei native NPC-Combat-Pfade

Für den gepinnten Hytale-Stand sind mindestens zwei native NPC-Melee-Pfade relevant:

1. **Leichtgewichtiger instruction-/interaction-basierter Melee-Pfad** über `Type: "Attack"` und eine NPC-kompatible Root-Interaction wie `Root_NPC_Attack_Melee`.
2. **Combat Action Evaluator (CAE)** für intelligentere Combatants mit TargetMemory, Combat-Substates, WeaponSlot und nativen Basic-Attack-Interactions.

Der Civ-Soldier verwendet den CAE-Pfad. Der frühere leichte `Root_NPC_Attack_Melee`-Pfad bleibt als allgemeine Hytale-Integrationsreferenz dokumentiert, ist aber nicht mehr der Soldier-Angriffspfad.

## `Root_NPC_Attack_Melee`

Der einfache native NPC-Melee-Root verwendet benannte `InteractionVars`:

- `Melee_Start` – Start/Animation/Timing,
- `Melee_Selector` – native Entity-Treffergeometrie,
- `Melee_Damage` – nativer `DamageEntity`-Pfad inklusive DamageEffects/Knockback.

Ein `Generic`-Role kann diese Variablen über top-level `InteractionVars` überschreiben. In einem `Variant` unter `Modify` heißt derselbe Block `_InteractionVars`.

Für den Civ-Soldier ist dieser Pfad ungeeignet, wenn die tatsächlich gehaltene Waffe den Angriff bestimmen soll: Aus dem generischen `Melee_Damage`-Hook folgt nicht, dass die Item-InteractionVars eines gehaltenen Schwerts automatisch übernommen werden.

## Warum nicht die Spieler-Schwert-Primary direkt verwenden?

`Root_Weapon_Sword_Primary` ist eine Spieler-/Item-Interaction und nicht als direkter NPC-`Attack`-Root gedacht. Frühere Runtime-Versuche zeigten unter anderem, dass die vollständige Spieler-Schwertkette Blockinteraktionen enthält, die innerhalb der NPC-`Attack`-Kette nicht akzeptiert werden.

Daraus folgt nicht, dass NPCs keine Schwerter verwenden können. Der native Weg ist stattdessen Hytales Combat Action Evaluator mit einer für NPCs vorgesehenen Basic-Attack-Interaction wie `Sword_Attack`.

## CAE und echte Waffen-Slots

Für den gepinnten `HytaleServer.jar`-Stand wurde direkt in der Bytecode-Struktur verifiziert:

- `BasicAttackTargetCombatAction` besitzt `WeaponSlot` und `OffhandSlot`.
- Beim Ausführen wird über `InventoryHelper.setHotbarSlot(...)` auf den konfigurierten WeaponSlot gewechselt.
- Anschließend verwendet der Evaluator das zum aktuellen Combat-Substate gehörende `BasicAttacks`-Set.
- `ActionCombatAbility` führt die vom Evaluator ausgewählte native Root-Interaction über Hytales Interaction-System aus.

Damit ist `WeaponSlot` kein rein kosmetischer Wert: Der CAE schaltet vor dem Basic Attack tatsächlich auf diesen Hotbar-Slot.

Öffentliche Hytale-Assets bestätigen denselben Aufbau. Das Entwicklungs-Role `Test_Combat_Knight` ist ein `Generic`-NPC mit `CombatConfig`, `Weapon_Sword_Iron` im Hotbar-Inventar, einem nativen `Combat`-State und `CombatActionEvaluator`-/`CombatAbility`-Instructions. Der Goblin-Sword-CAE verwendet `SelectBasicAttackTarget` mit `WeaponSlot: 0` und `Sword_Attack` als Basic Attack.

## ActionSets entsprechen Combat-Substates

Direkte JAR-Inspektion von `CombatActionEvaluator` zeigt: Die Schlüssel unter `ActionSets` werden über `StateMappingHelper.getSubStateIndex(combatState, actionSetName)` auf Substates des nativen `Combat`-States aufgelöst. Existiert der benannte Substate nicht, wirft der Evaluator beim Aufbau einen Fehler.

Für den normalen Combat-Substate ist deshalb `ActionSets.Default` der passende Vertrag. Zusätzliche ActionSets wie `Melee` oder `Ranged` benötigen entsprechende Combat-Substates in der Role.

## Civ-Soldier-Integration

Der Civ-Soldier ist als normaler `Civ_Inhabitant` weiterhin ein `Generic`-Role, besitzt aber `CombatConfig: "CAE_Civ_Soldier"`.

Der Ablauf ist:

1. `SoldierWorkSystem` entscheidet nach Civ-Regeln, welches feindliche Monster das Ziel ist.
2. Civ behält `CivCombatTarget` als eigenen Gameplay-Zielvertrag.
3. Beim Engagement wird das Ziel zusätzlich in Hytales natives `TargetMemory` des Soldiers eingetragen und als `closestHostile` gesetzt.
4. Der Soldier wechselt in den nativen `Combat`-State.
5. Die Role prüft `HasHostileTargetMemory`.
6. `CombatActionEvaluator` entscheidet, ob das Ziel bereits in Angriffsreichweite ist.
7. Außerhalb der Reichweite übernimmt Hytales `Seek` die Verfolgung.
8. Innerhalb der Reichweite führt `CombatAbility` den vom CAE gewählten `Sword_Attack` aus.
9. Bei manuellem Civ-Befehl oder Zielverlust werden natives TargetMemory und Combat-Zustand aufgeräumt und der Bewohner kehrt nach `Idle` zurück.

Civ implementiert dabei weder Trefferprüfung noch eigene Schadenswerte.

`CAE_Civ_Soldier` verwendet aktuell `WeaponSlot: 0`. Der Soldier-Bootstrap rüstet `Weapon_Sword_Iron` über Hytales `InventoryHelper.useItem(...)` aus. Der reale Runtime-Test muss zusätzlich absichern, dass dieser Vertrag im tatsächlichen Soldier-Fixture zusammenpasst; ein erfolgreicher JSON-/Java-Build allein beweist das nicht.

## Native Zielübergabe und Gegenwehr

Hytale verwendet nicht für alle NPC-Rollen denselben aktiven Combat-Target-Zustand:

- Leichtgewichtige Predator-/Role-Templates verwenden häufig einen markierten Zielslot wie `LockedTarget` und einen nativen `Combat`-State.
- CAE-Rollen verwenden `TargetMemory`; der native Combat-Target-Collector pflegt dort `knownHostiles` und `closestHostile`.

Für den Civ-Soldier selbst ist `TargetMemory` der entscheidende CAE-Vertrag. Für das angegriffene Vanilla-Monster spiegelt Civ die Gegenwehr weiterhin in beide vorhandenen nativen Mechanismen, soweit die jeweilige Role sie unterstützt: `LockedTarget`/`Combat` sowie ein vorhandenes `TargetMemory`.

Danach trifft Hytale selbst die Bewegungs- und Angriffsentscheidungen des Gegners. Civ erzeugt weder gegnerische Angriffe noch Schaden.

## `SensorTarget.AutoUnlockTarget`

Für den gepinnten Server wurde direkt in `HytaleServer.jar` verifiziert: Ein `SensorTarget` prüft zuerst seine Anforderungen, darunter die konfigurierte Reichweite. Schlägt diese Prüfung fehl und `AutoUnlockTarget` ist `true`, löscht Hytale den markierten Zielslot.

Das war beim früheren Soldier-Role relevant: Ein kurzer Attack-Sensor vor einem weiter reichenden Chase-Sensor löschte dasselbe `CivCombatTarget`, bevor die Chase-Instruction es lesen konnte. Der daraus entstandene Gruppenfehler wurde behoben, indem der kurze Sensor das Ziel nicht mehr automatisch freigab.

Mit dem CAE-Soldier liegt Attack-vs-Chase nun im nativen Combat-Evaluator; der alte gestaffelte Soldier-`SensorTarget`-Pfad wird nicht mehr verwendet. Die `AutoUnlockTarget`-Regel bleibt für andere gestaffelte Role-Instructions gültig.

## Timing und Treffergeometrie

Native Waffen-Interactions bleiben echte Hytale-Interactions. Ein ausgewählter Basic Attack ist daher nicht automatisch ein garantierter Homing-Hit. Ausrichtung, Reichweite, Animation, Selector, Cooldown und Trefferverarbeitung bleiben Engine-Verhalten.

Der Civ-Soldier kombiniert deshalb im `Combat`-State:

- `CombatActionEvaluator` für native Range-/Action-Auswahl,
- `Aim` für Kopfausrichtung,
- `MaintainDistance` im Angriffsbereich,
- `Seek` außerhalb des Angriffsbereichs,
- `CombatAbility` für den tatsächlichen nativen Basic Attack.

Bei Problemen mit Treffern oder Timing soll zuerst der Hytale-Runtime-Log und das Verhalten der nativen CAE-Konfiguration untersucht werden. Keine parallele Civ-Trefferlogik hinzufügen.

## Blockzerstörung bei kämpfenden NPCs

Aus der Ablehnung von `BreakBlockInteraction` innerhalb einer einfachen NPC-`Attack`-Kette folgt nicht, dass ein NPC grundsätzlich keine Blöcke während eines Angriffs verändern kann. Verifiziert ist nur, dass Blockabbau nicht einfach in dieselbe frühere NPC-`Attack`-Interaction-Chain eingebettet werden sollte.

Für spätere Einheiten mit zerstörerischen Heavy-Attacks sollen Combat gegen Entities und mögliche Blockzerstörung separat zur Laufzeit verifiziert werden.

## Zielauswahl

Für den Soldier werden normale Monster derzeit über Hytales NPC-Rollen-Metadaten als Ziele klassifiziert. Ein für Spieler feindlicher nativer NPC (`DefaultPlayerAttitude = HOSTILE`) gilt als angreifbares Monster. Die frühere strengere Annahme, ein Ziel müsse zusätzlich standardmäßig allen NPCs gegenüber feindlich sein, war für normale Monster zu restriktiv.

## Grizzly-Referenz

Im öffentlichen Asset-Stand existiert die native Role `Bear_Grizzly` als `Template_Predator`-Variante. Sie verwendet ihren eigenen nativen Bear-Attack-Root und besitzt deutlich höhere HP als das bisherige leichte Runtime-Fixture. Das macht sie für einen manuellen Drei-Soldaten-Gameplay-Test besonders passend.

Für automatisierte Headless-Probes darf trotzdem ein kleineres stabiles Hostile-Fixture verwendet werden, wenn der Test gezielt Targeting, Chase, CAE-Ausführung und gegenseitige native HP-Änderungen absichern soll. Ein stärkerer Gegner darf nicht allein deshalb gewählt werden, wenn dadurch der Test durch Threat-/Backoff-Timing unnötig flakey wird.

## Headless-Runtime-Fixtures

Eine geladene Test-Entity ist nicht automatisch dauerhaft gepinnt. Im Soldier-Probe wurde verifiziert, dass die temporäre Headless-Testwelt nach einigen Sekunden Chunks entladen konnte, obwohl die Entities nicht gestorben waren. Für dieses fokussierte Fixture wird deshalb `WorldConfig.setCanUnloadChunks(false)` verwendet. Ein ungültiger Entity-`Ref` darf in Runtime-Probes nicht ohne weitere Evidenz als Tod interpretiert werden.

Der Soldier-Probe verwendet drei Civ-Soldaten gegen ein stabiles natives Hostile-Fixture. Er soll für alle drei Zielerfassung und echte Hytale-Verfolgung beobachten, beim primären Soldier die Civ-Priorität durch manuelle Unterbrechung/Resume prüfen und außerdem native HP-Änderungen in beide Richtungen sehen.

## Runtime-Verifikation

Interaction-Asset-Kompatibilität ist runtime-abhängig. Ein erfolgreicher Java-/Gradle-Build beweist nicht, dass Hytale eine Role mit der gewählten CAE-Konfiguration akzeptiert oder `Sword_Attack` tatsächlich ausführt.

Bei Änderungen an NPC-Combat daher, wenn im aktuellen Chat ausdrücklich erlaubt und sinnvoll:

1. normalen CI-Build ausführen,
2. fokussiertes Hytale-Local-Szenario verwenden,
3. bei Fehlern zuerst Runtime-Artifact/Server-Logs lesen,
4. erst dann Role-, CAE- oder Harness-Code ändern.

Für die neue `CAE_Civ_Soldier`-Integration ist die abschließende Hytale-Local-Verifikation erforderlich, bevor die Umstellung als runtime-bestätigt dokumentiert wird.
