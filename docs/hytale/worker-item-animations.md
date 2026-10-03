# Worker-Item-Animationen

## Verifizierte native Animationssets

Hytales ItemPlayerAnimations verwenden Asset-Keys als Parent-Namen. Für die aktuell verwendeten Civ-Werkzeuge sind verifiziert:

- Axt: `Axe`
- Spitzhacke/Hammer: `Pickaxe`

Die native Pickaxe-Arbeitsanimation heißt `Mine` und liegt unter `Characters/Animations/Items/Main_Handed/Pickaxe/Attacks/Mine/...`.

Ein Parent-Name wie `Pickaxe_Animations` ist ungültig. Hytale entfernt in diesem Fall das Child-Asset beim Laden; spätere `AnimationUtils.playAnimation(...)`-Aufrufe können dieses Set dann nicht mehr verwenden.

## Civ-Pattern für lange Arbeitsphasen

Die nativen Schlag-/Mine-Animationen sind nicht generell als Loop definiert. Civ verwendet deshalb dünne Child-Assets, die ausschließlich die native Animation wiederverwenden und `Looping: true` ergänzen:

- `Civ_Woodcutter_Axe` -> Parent `Axe`
- `Civ_Miner_Pickaxe` -> Parent `Pickaxe`
- `Civ_Construction_Hammer` -> Parent `Pickaxe`

Die Animation wird einmal beim Eintritt in die Arbeitsphase gestartet und läuft clientseitig weiter. Sie wird nicht pro Tick neu ausgelöst.

## Ownership-Regel

Ein Worker-System darf `AnimationSlot.Action` niemals bei einer Entity stoppen, die nicht zu seiner eigenen Profession gehört. Sonst kann ein System die Arbeitsanimation eines anderen Berufs direkt nach deren Start wieder löschen.

Für den eigenen Worker gilt zusätzlich: Arbeitsanimationen werden nur beim Eintritt in die Arbeitsphase gestartet und beim Verlassen oder Abbruch dieser Phase beendet. Es gibt kein per-Tick-Retriggering.

Das aktuelle Pattern ist daher:

1. Nicht-eigene Profession: Runtime aufräumen, aber keinen Animationsbefehl senden.
2. Arbeitsposition erreichen.
3. Falls die eigene Arbeitsanimation noch nicht läuft, Item-Animation einmal starten.
4. Während der Arbeitsphase keinen erneuten Start senden.
5. Beim Verlassen/Abbruch die eigene Arbeitsanimation stoppen und den Runtime-State zurücksetzen.

## Aktuelle Werkzeugzuordnung

Die Entwicklungs-Bootstrap-Items sind derzeit:

- Holzfäller: `Weapon_Axe_Iron`
- Minenabbauer: `Tool_Pickaxe_Iron`
- Bauarbeiter: `Tool_Hammer_Iron`

`Tool_Hammer_Iron` erbt nativ von `Tool_Hammer_Crude`; Civ rüstet bewusst den sichtbaren Eisenhammer aus.

Diese Bootstrap-Zuordnung ist kein dauerhaftes Ressourcen-/Logistiksystem. Sie sorgt nur dafür, dass die Berufe bis zur späteren Werkzeugbeschaffung sichtbar mit dem passenden nativen Item arbeiten können.
