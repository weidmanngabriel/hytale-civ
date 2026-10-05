# Runner- und Pipeline-Debugging

Diese Anleitung gilt für fehlschlagende oder hängende GitHub-Actions-Pipelines, Self-Hosted-Runner und Hytale-Local-Workflows.

## Log-first-Diagnose

Bevor eine Root-Cause-Hypothese verfolgt, ein Commit-Bisect gestartet oder der Test-/Runtime-Harness geändert wird, muss zuerst der vollständige rohe beziehungsweise dekodierte GitHub-Actions-Job-Log des betroffenen Jobs geprüft werden.

Dabei den Log vollständig beziehungsweise mindestens bis einschließlich des fehlschlagenden Schritts lesen und insbesondere auf folgende Punkte achten:

- Setup und tatsächlich verwendete Umgebung
- Java-/Tool-Versionen und relevante Umgebungsvariablen
- Prozessstart und Prozessausgabe
- Exceptions und Stacktraces
- Zeitstempel und auffällige Wartezeiten
- Shutdown-Verhalten
- finalen Runner-/Workflow-Fehler

Anschließend die beobachteten Fakten kurz zusammenfassen und erst darauf Hypothesen aufbauen.

Marker-Suchen, Step-Summaries, Commit-Vergleiche, Workflow-Reruns und Bisects sind Folgewerkzeuge. Sie ersetzen nicht die Prüfung des vollständigen Job-Logs.

## Hytale-Local: Runtime-Artifact zuerst auswerten

Für Hytale-Local-Läufe speichert der Workflow die Runtime-Ausgabe zusätzlich als GitHub-Actions-Artifact mit dem Namen `hytale-runtime-logs-<run-id>-<attempt>`. Dieses Artifact wird durch `if: always()` auch nach einem fehlgeschlagenen Runtime-Schritt hochgeladen, sofern die Dateien bereits angelegt wurden.

Bei einem fehlgeschlagenen oder auffälligen Hytale-Local-Lauf gilt deshalb folgende Reihenfolge:

1. Betroffenen Workflow-Run und den Job `Verify local Hytale gameplay runtime` ermitteln.
2. Das Runtime-Artifact `hytale-runtime-logs-<run-id>-<attempt>` des Runs abrufen und herunterladen.
3. Die enthaltene `*.stdout.log`, `*.stderr.log` und `*.status` vollständig beziehungsweise mindestens bis zum relevanten Fehler prüfen. `stderr` kann PowerShell-CLIXML enthalten; das ist Transportformat und darf nicht mit der eigentlichen Root Cause verwechselt werden.
4. Beobachtete Runtime-Fakten zusammenfassen und mit den normalen Job-Logs beziehungsweise Step-Summaries abgleichen.
5. Erst wenn diese Evidenz nicht ausreicht, weitere Diagnose wie zusätzliche Marker, Harness-Observability, Workflow-Reruns, Commit-Vergleiche oder Bisects einsetzen.

Das Artifact ist für Runtime-Prozessausgabe die bevorzugte Diagnosequelle, weil es die getrennten stdout-/stderr-Dateien des gestarteten Hytale-Prozesses erhält. Ein fehlender oder nicht lesbarer Artifact-Upload ist selbst ein Diagnosebefund und muss vor Änderungen am Gameplay-Code oder Harness berücksichtigt werden.

Wenn der vollständige Log und das Runtime-Artifact keine ausreichenden Informationen enthalten, soll zuerst die Observability des Harness verbessert werden, bevor aufgrund fehlender Evidenz umfangreiche Code- oder Infrastrukturänderungen vorgenommen werden.
