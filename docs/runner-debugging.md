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

Wenn der vollständige Log keine ausreichenden Informationen enthält, soll zuerst die Observability des Harness verbessert werden, bevor aufgrund fehlender Evidenz umfangreiche Code- oder Infrastrukturänderungen vorgenommen werden.
