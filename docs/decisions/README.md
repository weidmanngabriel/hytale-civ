# Architecture Decision Records

Architecture Decision Records (ADRs) dokumentieren die Begründung bedeutender Architekturentscheidungen, deren Entstehungsgeschichte sonst aus <code>docs/architecture.md</code> verloren gehen würde.

Dateinamen verwenden dieses Schema:

~~~text
NNNN-kurzer-titel.md
~~~

Erlaubte Statuswerte sind <code>Proposed</code>, <code>Accepted</code>, <code>Superseded</code>, <code>Deprecated</code> und <code>Rejected</code>.

Coding Agents dürfen neue ADRs nur mit dem Status <code>Proposed</code> anlegen. <code>Accepted</code> und alle späteren Statusänderungen erfordern eine ausdrückliche menschliche Entscheidung.

Ein ADR ist sinnvoll, wenn eine Entscheidung bedeutende langfristige Auswirkungen auf die Architektur besitzt, echte Alternativen abgewogen wurden oder spätere Maintainer voraussichtlich fragen werden, warum die aktuelle Struktur existiert. Normale Implementierungsdetails benötigen kein ADR.
