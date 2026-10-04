# ADR 0006: Warmer Hytale-Runtime-Harness

Status: Proposed

## Kontext

Lokale Hytale-Runtime-Probes sind wertvoll, weil sie reale Engine-Verträge wie NPC-Navigation, Prefab-Platzierung, Blockphysik und native Weltmutation prüfen. Der bisherige Harness startete für jedes normale Gameplay-Szenario einen eigenen Hytale-Prozess. Ein kompletter Serverboot lädt Assets, NPC-Konfigurationen und Plugins und kostet auf dem Self-Hosted-Runner deutlich mehr Zeit als das Erzeugen einer zusätzlichen Flat-World.

Der Simulator soll gleichzeitig keine zweite Hytale-Engine werden. Hytale-spezifische Navigation, Physik und Prefab-Runtime-Semantik sollen weiterhin in der echten Engine geprüft werden.

## Entscheidung

Normale Gameplay-Probes dürfen in einem gemeinsamen warmen Hytale-Prozess gebündelt werden, sofern sie ihre Testzustände in getrennten nativen Welten isolieren.

Für den aktuellen Harness gilt:

- `woodcutter` und `minesupport` werden automatisch gemeinsam als Warm-Suite ausgeführt, wenn beide angefordert sind.
- Beide Probes behalten ihre bestehenden fachlichen Assertions und realen Produktionspfade.
- Jede Probe erzeugt eine eigene native Flat-World.
- Im Warm-Suite-Modus melden die Probes PASS/FAIL an `CivRuntimeProbeSuite`, statt den kompletten Server selbst zu beenden.
- Die Testwelten verwenden `World.setTimeDilation(4.0f, ...)` für beschleunigte reale Engine-Zeit.
- Standalone-Ausführung einzelner Szenarien bleibt für Diagnose möglich.
- `persistence` bleibt absichtlich ein Cold-Test mit zwei getrennten Hytale-Prozessen, weil Prozess-Neustart Teil des zu prüfenden Vertrags ist.

## Verifizierte Evidenz

Referenzcommit für den realen Benchmark:

`ca712dbefdbf9f0c23663c35b49df75506e48be8`

Warm-Request:

`/hytale-test woodcutter-minesupport ca712dbefdbf9f0c23663c35b49df75506e48be8`

Ergebnis:

- Woodcutter und MineSupport bestanden parallel in einem Hytale-Prozess.
- `CIV_WARM_SUITE_RESULT scenarios=2 elapsedMs=3206 dilation=4.0`
- gesamte Self-Hosted-Runtime-Stufe inklusive Runtime-Vorbereitung, Hytale-Boot, beider Szenarien, Shutdown und Cleanup: ungefähr `24.1 s`.

Cold-Vergleich auf demselben Commit:

- `woodcutter` standalone: ungefähr `28.0 s` Runtime-Stufe.
- `minesupport` standalone: ungefähr `19.9 s` Runtime-Stufe.
- seriell zusammen: ungefähr `47.9 s`.
- warm zusammen: ungefähr `24.1 s`.

Damit spart die Warm-Suite für diese beiden realen Gameplay-Verträge ungefähr `23.8 s` beziehungsweise rund `50 %` der Runtime-Stufe.

Separat wurde die Hytale-Time-Dilation verifiziert: vergleichbare native Civ-NPC-Bewegung benötigte bei 1× ungefähr `4144 ms` und bei 4× ungefähr `1007 ms`, entsprechend etwa `4.12×` realer Beschleunigung. Drei zusätzliche Flat-Worlds konnten nach Serverstart in ungefähr `29 ms` erzeugt werden.

## Konsequenzen

- Der große Hytale-Boot-Overhead wird für gebündelte Gameplay-Tests nur einmal bezahlt.
- Mehrere Hytale-Welten können parallel ticken, sodass Szenarien nicht nur seriell im selben Prozess laufen müssen.
- Der Simulator bleibt klein und Hytale-unabhängig; Engine-nahe Verträge werden direkt in Hytale getestet.
- Runtime-Probes müssen ihren Lifecycle vom globalen Server-Lifecycle trennen, damit sie sowohl standalone als auch als Teil einer Suite funktionieren.
- Gemeinsame Plugin-Registries und andere globale Zustände bleiben eine mögliche Isolationsgrenze. Neue parallelisierte Probes müssen deshalb weiterhin nachweisen, dass ihre Welt-/Szenariozustände nicht kollidieren.
- Cold-Tests bleiben für Verträge erforderlich, bei denen Restart, Persistenz oder Prozessinitialisierung selbst Teil des Verhaltens sind.

## Nicht entschieden

- Es wird kein Hytale-Pathfinding, keine Hytale-Physik und keine vollständige 3D-Engine im Simulator nachgebaut.
- Mehrere vollständige Hytale-Prozesse parallel auf verschiedenen `--bind`-Ports sind technisch möglich, werden aber nicht als Standard gewählt, solange Multi-World im warmen Prozess schneller und ressourcenschonender ist.
- Weitere Gameplay-Szenarien werden erst dann in die Warm-Suite aufgenommen, wenn ihre Isolation in einer eigenen Testwelt sauber nachgewiesen ist.
