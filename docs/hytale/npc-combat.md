# NPC-Combat

Diese Seite hält verifizierte Integrationsregeln für native Hytale-NPC-Kämpfe in Civ fest.

## Grundregel

Civ soll für NPC-Kämpfe Hytales natives Combat-System verwenden, statt einen parallelen Civ-Schadens- oder HP-Stack aufzubauen. Zielauswahl und Gameplay-Priorität können aus Civ kommen; Bewegung, Interaction-Ausführung, Trefferprüfung, Schaden, Knockback, Entity-Stats und Tod bleiben soweit möglich bei Hytale.

## NPC-`Attack` und Root-Interactions

Hytales NPC-`Attack`-Action akzeptiert nicht automatisch jede Root-Interaction, die für einen Spieler oder ein gehaltenes Item funktioniert.

Für eine Root-Interaction, die über NPC-`Attack` ausgeführt werden soll, gilt für den aktuell gepinnten Stand:

- die Root-Interaction muss als `Attack` getaggt sein, zum Beispiel `"Attack": ["Melee"]`,
- eine normale Spieler-Primary-Interaction eines Items ist deshalb nicht automatisch NPC-kompatibel,
- eine als NPC-Angriff verwendete Interaction-Chain darf keine für NPC-Attacks verbotenen Interaction-Typen enthalten.

### Verifizierter Sword-Fall

`Root_Weapon_Sword_Primary` kann nicht unverändert als NPC-Attack verwendet werden. Die normale Spieler-Schwertkette enthält über ihren Selector auch einen Blocktreffer-Zweig, der `Block_Break_Adventure` und damit eine `BreakBlockInteraction` ausführt. Der NPC-Role-Loader lehnt diese Kette als NPC-Angriff ab.

Die Civ-Soldier-Integration verwendet deshalb eine NPC-sichere Schwertkette:

1. eine eigene als `Attack=Melee` getaggte Root-Interaction,
2. die native Sword-Swing-Animation und ihre Laufzeiten,
3. einen eigenen Selector mit derselben Entity-Treffergeometrie,
4. **keinen `HitBlock`-/Blockabbau-Zweig**,
5. für Entity-Treffer weiterhin den nativen `Swing_Left_Damage`-Replace-Var.

Dadurch bleibt die Schadensdefinition beim gehaltenen Item. Für `Weapon_Sword_Iron` überschreibt Hytale `Swing_Left_Damage` aktuell auf `Physical: 9`. Civ dupliziert diesen Wert nicht.

## Warum nicht einfach eigenen Schaden berechnen?

Das wäre eine konkurrierende Combat-Implementierung und würde unter anderem Waffenbalance, DamageEffects, Knockback, Item-Overrides und spätere Hytale-Änderungen von Civ entkoppeln. Solange die native Interaction-Kette passend zusammengesetzt werden kann, soll Civ nur die NPC-taugliche Ausführungsschicht bereitstellen und die eigentliche Schadenslogik bei Hytale lassen.

## Blockzerstörung bei kämpfenden NPCs

Aus der Ablehnung von `BreakBlockInteraction` innerhalb einer NPC-`Attack`-Kette folgt **nicht**, dass ein NPC grundsätzlich keine Blöcke während eines Angriffs verändern kann. Verifiziert ist nur: Blockabbau darf nicht einfach in dieselbe NPC-`Attack`-Interaction-Chain eingebettet werden.

Für spätere Einheiten wie einen Ogre mit zerstörerischem Heavy-Attack sollte Combat gegen Entities und eine mögliche Blockzerstörung deshalb zunächst als getrennte Engine-/Gameplay-Schritte modelliert und separat zur Laufzeit verifiziert werden. Keine Blockzerstörungssemantik als gesichert annehmen, bevor ein fokussierter Runtime-Test existiert.

## Zielauswahl

Für den Soldier werden normale Monster derzeit über Hytales NPC-Rollen-Metadaten als Ziele klassifiziert. Ein für Spieler feindlicher nativer NPC (`DefaultPlayerAttitude = HOSTILE`) gilt als angreifbares Monster. Die frühere strengere Annahme, ein Ziel müsse zusätzlich standardmäßig allen NPCs gegenüber feindlich sein, war für normale Goblins zu restriktiv.

## Runtime-Verifikation

Interaction-Asset-Kompatibilität ist runtime-abhängig. Ein erfolgreicher Java-/Gradle-Build beweist nicht, dass Hytale eine NPC-Role mit der gewählten Attack-Chain akzeptiert oder die Interaction tatsächlich ausführt.

Bei Änderungen an NPC-Combat daher, wenn im aktuellen Chat ausdrücklich erlaubt und sinnvoll:

1. normalen CI-Build ausführen,
2. fokussiertes Hytale-Local-Szenario verwenden,
3. bei Fehlern zuerst Runtime-Artifact/Server-Logs lesen,
4. erst dann Interaction- oder Harness-Code ändern.

Das Soldier-Szenario soll mindestens Zielerfassung, Verfolgung, manuelle Unterbrechung/Resume sowie tatsächlich gemessene native HP-Änderungen überprüfen.
