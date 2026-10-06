# NPC-Combat

Diese Seite hält verifizierte Integrationsregeln für native Hytale-NPC-Kämpfe in Civ fest.

## Grundregel

Civ soll für NPC-Kämpfe Hytales natives Combat-System verwenden, statt einen parallelen Civ-Schadens- oder HP-Stack aufzubauen. Zielauswahl und Gameplay-Priorität können aus Civ kommen; Bewegung, Interaction-Ausführung, Trefferprüfung, Schaden, Knockback, Entity-Stats und Tod bleiben soweit möglich bei Hytale.

Vor unbekannten Hytale-Integrationen gilt außerdem der projektweite Research-first-Ablauf aus `docs/research-first.md`: vorhandene Nutzung im Projekt prüfen, offizielle Dokumentation und öffentliche Beispiele recherchieren, gepinnte JAR/Assets inspizieren und erst danach einen eigenen Mechanismus entwerfen.

## Zwei native NPC-Combat-Pfade

Für den gepinnten Hytale-Stand sind mindestens zwei native NPC-Melee-Pfade relevant:

1. **Leichtgewichtiger instruction-/interaction-basierter Melee-Pfad** über `Type: "Attack"` und eine NPC-kompatible Root-Interaction wie `Root_NPC_Attack_Melee`.
2. **Combat Action Evaluator (CAE)** für intelligentere Combatants mit mehreren Abilities, Utility-Auswahl, WeaponSlot und Combat-Konfiguration.

Für den aktuellen Soldier-Vertical-Slice reicht für den Soldier selbst der leichte native Melee-Pfad. Civ braucht dafür keine eigene Trefferprüfung und keine nachgebaute Spieler-Schwert-Interaction.

## `Root_NPC_Attack_Melee`

Die öffentliche Hytale-Dokumentation und gegen 0.6.8 verifizierte Community-Dokumentation beschreiben `Root_NPC_Attack_Melee` als den vorgesehenen einfachen NPC-Melee-Root. Seine Kette verwendet benannte `InteractionVars`:

- `Melee_Start` – Start/Animation/Timing,
- `Melee_Selector` – native Entity-Treffergeometrie,
- `Melee_Damage` – nativer `DamageEntity`-Pfad inklusive DamageEffects/Knockback.

Der Standardpfad endet bei `NPC_Attack_Melee_Damage` und verwendet native Hytale-Schadensverarbeitung. Dadurch bleiben HP, Damage-Events, Knockback und Tod Engine-Verhalten.

Ein `Generic`-Role kann diese Variablen über top-level `InteractionVars` überschreiben. In einem `Variant` unter `Modify` heißt derselbe Block `_InteractionVars`. Diese Unterscheidung ist wichtig; die falsche Schreibweise kann still wirkungslos bleiben oder ein Role-Asset ungültig machen.

## Warum nicht die Spieler-Schwert-Primary verwenden?

`Root_Weapon_Sword_Primary` ist eine Spieler-/Item-Interaction und nicht als direkter NPC-Angriff gedacht. In Runtime-Tests wurde bestätigt:

- die normale Spieler-Primary ist nicht automatisch als NPC-`Attack` freigegeben,
- die vollständige Spieler-Schwertkette enthält zusätzlich Blocktreffer-/Blockabbau-Logik,
- `BreakBlockInteraction` innerhalb einer NPC-`Attack`-Kette wird vom NPC-Role-Loader abgelehnt.

Der erste Civ-Versuch, eine eigene Root-/Swing-/Selector-Kette aus der Spieler-Schwertkette abzuleiten, war deshalb der falsche Integrationsansatz und wurde entfernt. Der Soldier verwendet stattdessen direkt `Root_NPC_Attack_Melee`.

## Waffenwerte und `InteractionVars`

Hytale-Items besitzen eigene `InteractionVars`. Für `Weapon_Sword_Iron` definiert der öffentliche 0.6.x-Asset-Stand unter anderem `Swing_Left_Damage` mit `Physical: 9`; weitere Swing-/Thrust-Varianten besitzen andere Werte.

Das bedeutet jedoch **nicht automatisch**, dass der leichte `Root_NPC_Attack_Melee`-Pfad die `Swing_Left_Damage`-Variable des gehaltenen Spieler-Schwerts übernimmt. Sein nativer Damage-Hook heißt `Melee_Damage`. Solange ein Runtime-Test oder die gepinnten Assets/API nicht belegen, dass der Item-Kontext automatisch in diesen Pfad übernommen wird, darf Civ diese Kopplung nicht voraussetzen.

Der aktuelle Soldier verwendet deshalb zunächst den nativen NPC-Melee-Pfad ohne eigene Civ-Schadensberechnung. Der verifizierte Runtime-Pfad verursacht dabei aktuell 5 native Schadenspunkte pro Treffer. Falls die ausgerüstete Waffe später die tatsächliche Damage-Variante bestimmen soll, soll das über Hytales vorgesehenes `InteractionVars`-/WeaponSlot-/CAE-System gelöst und separat verifiziert werden – nicht durch einen parallelen Java-Schadensstack.

## Native Zielübergabe und Gegenwehr

Ein wichtiges Ergebnis der Soldier-Runtime-Tests ist, dass Hytale nicht für alle NPC-Rollen denselben aktiven Combat-Target-Zustand verwendet.

- Leichtgewichtige Role-/Predator-Templates verwenden typischerweise einen markierten Zielslot wie `LockedTarget` und wechseln in einen nativen `Combat`-State.
- CAE-Rollen verwenden `TargetMemory`; Hytales eigener Combat-Target-Collector pflegt dort sowohl `knownHostiles` als auch `closestHostile`.

Nur einen dieser Pfade zu setzen reicht deshalb nicht zuverlässig für beliebige Vanilla-Monster. Der Civ-Soldier-Adapter spiegelt beim Engagement beide **vorhandenen nativen Mechanismen**:

1. `LockedTarget` auf den Soldier setzen; unbekannte Slots werden von Rollen ignoriert,
2. einen vorhandenen `Combat`-State beim neuen Engagement einmal aktivieren, statt ihn jeden Tick neu zu starten,
3. bei vorhandenem `TargetMemory` den Soldier in `knownHostiles` halten und als `closestHostile` setzen.

Danach trifft Hytale selbst die Bewegungs- und Angriffsentscheidungen. Civ erzeugt weder Angriffe noch Schaden. Dieses Muster wurde im fokussierten Soldier-Local-Szenario gegen einen nativen Predator erfolgreich mit gegenseitiger HP-Änderung verifiziert.

## `SensorTarget.AutoUnlockTarget`

Für den gepinnten Server 0.6.8 wurde direkt in `HytaleServer.jar` verifiziert: Ein `SensorTarget` prüft zuerst seine Anforderungen, darunter die konfigurierte Reichweite. Schlägt diese Prüfung fehl und `AutoUnlockTarget` ist `true`, löscht Hytale den markierten Zielslot.

Das ist bei gestaffelten Combat-Instructions relevant. Wenn zuerst ein kurzer Nahkampf-Sensor und danach ein weiter reichender Chase-Sensor denselben Zielslot lesen, darf der Nahkampf-Sensor das Ziel bei einem bloßen Reichweitenfehler nicht freigeben. Andernfalls sieht die nachfolgende Chase-Instruction das Ziel nicht mehr.

Für `CivCombatTarget` gilt deshalb:

- kurzer Attack-Sensor: `AutoUnlockTarget: false`,
- nachfolgender Chase-Sensor: darf außerhalb seiner eigenen zulässigen Gesamtreichweite weiter aufräumen.

Damit bleibt ein gültiges, aber noch entferntes Ziel erhalten und Hytales `Seek` kann die Verfolgung übernehmen.

## Timing und Treffergeometrie

Der native NPC-Melee-Selector ist eine gerichtete Sweep-Geometrie vor dem NPC. Ein Angriff ist daher kein automatisch treffender Homing-Hit. Der NPC muss beim Schlag ausreichend auf das Ziel ausgerichtet und in Reichweite sein.

Vanilla-Rollen kombinieren dafür typischerweise:

1. Ziel-/Range-Sensor,
2. Body-/Head-Ausrichtung bzw. Seek,
3. `ActionsBlocking`,
4. kurze Vorlaufzeit,
5. `Attack`,
6. kurze Nachlauf-/Cooldown-Zeit.

Falls der Soldier mit dem nativen Root zwar lädt, aber regelmäßig daneben schlägt oder den Attack zu früh startet, ist dieses Timing-/Range-Muster der nächste zu prüfende native Ansatz. Nicht vorschnell eigene Trefferlogik hinzufügen.

## Blockzerstörung bei kämpfenden NPCs

Aus der Ablehnung von `BreakBlockInteraction` innerhalb einer NPC-`Attack`-Kette folgt **nicht**, dass ein NPC grundsätzlich keine Blöcke während eines Angriffs verändern kann. Verifiziert ist nur: Blockabbau darf nicht einfach in dieselbe NPC-`Attack`-Interaction-Chain eingebettet werden.

Für spätere Einheiten wie einen Ogre mit zerstörerischem Heavy-Attack sollten Combat gegen Entities und eine mögliche Blockzerstörung deshalb zunächst als getrennte Engine-/Gameplay-Schritte modelliert und separat zur Laufzeit verifiziert werden. Keine Blockzerstörungssemantik als gesichert annehmen, bevor ein fokussierter Runtime-Test existiert.

## Zielauswahl

Für den Soldier werden normale Monster derzeit über Hytales NPC-Rollen-Metadaten als Ziele klassifiziert. Ein für Spieler feindlicher nativer NPC (`DefaultPlayerAttitude = HOSTILE`) gilt als angreifbares Monster. Die frühere strengere Annahme, ein Ziel müsse zusätzlich standardmäßig allen NPCs gegenüber feindlich sein, war für normale Goblins zu restriktiv.

## Headless-Runtime-Fixtures

Eine geladene Test-Entity ist nicht automatisch dauerhaft gepinnt. Im Soldier-Probe wurde verifiziert, dass die temporäre Headless-Testwelt nach einigen Sekunden Chunks entladen konnte, obwohl die Entities nicht gestorben waren. Für dieses fokussierte Fixture wird deshalb `WorldConfig.setCanUnloadChunks(false)` verwendet. Ein ungültiger Entity-`Ref` darf in Runtime-Probes nicht ohne weitere Evidenz als Tod interpretiert werden.

## Runtime-Verifikation

Interaction-Asset-Kompatibilität ist runtime-abhängig. Ein erfolgreicher Java-/Gradle-Build beweist nicht, dass Hytale eine NPC-Role mit der gewählten Attack-Chain akzeptiert oder die Interaction tatsächlich ausführt.

Bei Änderungen an NPC-Combat daher, wenn im aktuellen Chat ausdrücklich erlaubt und sinnvoll:

1. normalen CI-Build ausführen,
2. fokussiertes Hytale-Local-Szenario verwenden,
3. bei Fehlern zuerst Runtime-Artifact/Server-Logs lesen,
4. erst dann Interaction- oder Harness-Code ändern.

Das Soldier-Szenario soll mindestens Zielerfassung, Verfolgung, manuelle Unterbrechung/Resume sowie tatsächlich gemessene native HP-Änderungen in beide Richtungen überprüfen.
