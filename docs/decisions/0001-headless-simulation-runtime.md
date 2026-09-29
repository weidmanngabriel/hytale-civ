# 0001: Headless-Simulationsruntime als zweiter Laufzeitpfad

Status: Proposed

## Kontext

Gameplay-Regeln sollen ohne gestarteten Hytale-Server reproduzierbar getestet werden. Besonders autonome Bewohner können teure Arbeit verursachen, wenn sie Weltzustand, Arbeitsaufträge oder Ziele zu häufig neu abfragen. Reine Wall-Clock-Benchmarks unterscheiden dabei schlecht zwischen Engine-Kosten und unnötiger Civ-Planung.

Der vorhandene Core enthält bereits Hytale-unabhängige Zustandsautomaten für Holzfäller, Bauarbeiter, Produktion, Farmarbeit und manuelle Unterbrechungen. Gleichzeitig sollen Hytales Navigation, Physik und Weltmodell nicht als zweite Engine nachgebaut werden.

## Entscheidung

Ein zusätzlicher Java-Laufzeitpfad unter <code>dev.civilizations.simulation</code> verwendet dieselben Core-Klassen wie der Hytale-Adapter.

Die Headless-Simulation bildet nur die Engine-Verträge ab, die für einen konkreten Core-Ablauf benötigt werden. Für den ersten Slice sind das eine kleine In-Memory-Welt, geradlinige Fake-Bewegung, Bäume, Baustellen, Felder und kontrollierte Abschlussereignisse.

Autonome teure Entscheidungen verwenden <code>WorkDecisionSchedule</code>. Eine Planung läuft entweder unmittelbar nach einem relevanten Ereignis oder nach Ablauf eines begrenzten Retry-Intervalls. Hytale-Adapter und Headless-Simulation können dieselbe Cadence-Semantik verwenden.

<code>SimulationMetrics</code> zählt deterministische fachliche Operationen wie Suchentscheidungen, Weltabfragen und Bewegungsanforderungen. Szenario-Tests dürfen dafür feste Operationsbudgets definieren. Die Browser- oder Desktop-Oberfläche ist optional und bleibt eine Präsentationsschicht auf derselben Simulation.

## Konsequenzen

Gameplay-Abläufe können schneller und reproduzierbar ohne Hytale getestet werden. Performance-Regressionen in der Auftragsverteilung lassen sich anhand stabiler Operationszähler erkennen, ohne von der Geschwindigkeit eines Testrechners abhängig zu sein.

Die Headless-Simulation darf keine eigene Hytale-ähnliche Wegfindung, Physik oder Blocksimulation entwickeln. Engine-spezifische Kosten und Laufzeitverhalten bleiben weiterhin Hytale-Tests und Runtime-Profiling vorbehalten.

Neue Gameplay-Systeme sollten ihre teuren Entscheidungen so strukturieren, dass deren Cadence und Weltabfragen im Headless-Pfad beobachtbar sind. Das kann bestehende Adaptergrenzen sichtbar machen, die später weiter in den Core verschoben werden sollten.

## Betrachtete Alternativen

**Nur Hytale als Testlaufzeit:** vermeidet einen zweiten Runtime-Pfad, macht Gameplay-Regressionstests aber langsam und erschwert die Trennung zwischen Engine-Kosten und Civ-Planung.

**Eigenständige TypeScript-Browsersimulation:** wäre leicht auf GitHub Pages betreibbar, würde aber Gameplay-Logik duplizieren und langfristig vom Java-Core abweichen können.

**Java-Core direkt im Browser über WebAssembly:** bleibt eine mögliche spätere Präsentationsoption. Für den ersten Schritt erhöht sie jedoch Build- und Tooling-Komplexität, ohne den Headless-Testnutzen zu verbessern.
