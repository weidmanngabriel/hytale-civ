# Manuelle Simulationstests

Für fokussierte Java-Simulationsdiagnosen ist `.github/workflows/manual-simulation.yml` vorgesehen. Der Workflow wird ausschließlich durch neue Kommentare in [Issue #341](https://github.com/weidmanngabriel/hytale-civ/issues/341) ausgelöst. Die Workflow-Datei muss dafür auf `main` vorhanden sein.

Aktuell erlaubte Anfrage:

```text
/sim-test miner3 <7- bis 40-stelliger Commit-SHA>
```

Nur `weidmanngabriel` darf diese Anfrage auslösen. Der Autorisierungsjob prüft Kommentarautor, Actor, Sender, die erlaubte Test-ID und den im eigenen Repository auflösbaren Commit-SHA. Der Diagnosejob checkt genau diesen Commit mit deaktivierter Credential-Persistenz aus, installiert Java 25 und führt ausschließlich `./gradlew test --tests dev.civilizations.simulation.MineThreeStallDiagnosticTest --console=plain --info --stacktrace` aus. Testberichte werden auch bei Fehlschlag als Artefakt gespeichert.

Dies führt **keinen** Browser Viewer, Local MCP, vollständigen Build, Release oder Hytale-Server-Runtime-Test aus. Gradle muss für den einzelnen Test weiterhin Projekt- und Testcode kompilieren und Abhängigkeiten auflösen. Der existierende PR-CI-Workflow bleibt davon unabhängig und wird durch den Pull Request weiterhin gestartet.

Wenn eine neue Diagnose hinzugefügt wird, muss ihre Test-ID bewusst in der vertrauenswürdigen Workflow-Allowlist ergänzt werden. Keine freie Eingabe von Gradle-Aufrufen oder Testklassen über Issue-Kommentare zulassen.
