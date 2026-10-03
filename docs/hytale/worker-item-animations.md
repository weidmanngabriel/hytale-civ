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

Ein Worker-System darf `AnimationSlot.Action` nur stoppen, wenn sein eigener Runtime-State zuvor die zugehörige Arbeitsanimation gestartet hat. Insbesondere darf ein Miner-System nicht pauschal die Action-Animation von Nicht-Minern stoppen.

Das aktuelle Pattern ist daher:

1. Arbeitsposition erreichen.
2. Falls `runtime.animationStarted == false`, Item-Animation einmal starten und den Flag setzen.
3. Während der Arbeitsphase keinen erneuten Start senden.
4. Beim Verlassen/Abbruch nur bei gesetztem Flag `stopAnimation(Action)` senden und den Flag zurücksetzen.

## Aktuelle Werkzeugzuordnung

Die Entwicklungs-Bootstrap-Items sind derzeit:

- Holzfäller: `Weapon_Axe_Iron`
- Minenabbauer: `Tool_Pickaxe_Iron`
- Bauarbeiter: `Tool_Hammer_Crude`

Diese Bootstrap-Zuordnung ist kein dauerhaftes Ressourcen-/Logistiksystem. Sie sorgt nur dafür, dass die Berufe bis zur späteren Werkzeugbeschaffung sichtbar mit dem passenden nativen Item arbeiten können.
