# Verifikation und API-Recherche

## Ziel

Hytale-spezifische Implementierungen dürfen nicht auf geratenen APIs oder vermuteter Runtime-Semantik beruhen.

## Standardvorgehen

1. Bestehenden Projektcode auf bereits etablierte Nutzung prüfen.
2. Die im Projekt bereitgestellte `HytaleServer.jar` untersuchen.
3. Klassen und Packages mit `jar tf` oder einem gleichwertigen ZIP/JAR-Listing finden.
4. Relevante Klassen mit `javap` untersuchen.
5. Wenn Signaturen nicht reichen, `javap -c -p` oder ein gleichwertiges Classfile-/Bytecode-Werkzeug verwenden.
6. Runtime-Semantik zusätzlich mit offizieller Hytale-Dokumentation prüfen.
7. Verhalten, das davon weiter offen bleibt, mit einem kleinen gezielten Runtime-Test verifizieren.
8. Wiederverwendbare Erkenntnisse in der passenden Seite unter `docs/hytale/` dokumentieren.

## Evidenzgrenzen

Die JAR ist bevorzugte Evidenz für die im Projekt gepinnte Hytale-Version. Sie belegt Klassenstruktur und Implementierungsdetails, die tatsächlich in dieser Version enthalten sind.

Sie belegt allein jedoch nicht zuverlässig:

- Lifecycle-Reihenfolgen,
- Event-Dispatch-Verhalten,
- Client-/Server-Zusammenspiel,
- Seiteneffekte bestimmter Engine-Aufrufe,
- Verhalten bei entladenen Chunks oder getrennten Stores,
- visuelle Client-Reaktionen.

Solche Aussagen brauchen offizielle Dokumentation oder einen fokussierten Runtime-Test.

## Dokumentationsstatus

Verwende auf den Themenseiten möglichst diese Begriffe:

- **Verifiziert:** durch JAR, offizielle Dokumentation oder reproduzierbaren Runtime-Test ausreichend belegt.
- **Projektvertrag:** eine von Civ bewusst gewählte Konvention, die durch Tests oder Code abgesichert ist.
- **Offen:** für die aktuelle Hytale-Version nicht ausreichend belegt.

## Keine Ersatzimplementierung ohne Prüfung

Bevor Civ eigene Navigation, UI-Primitive, Interaktionslogik, Assettypen, Game-Mode-Verhalten, Farming-, Container- oder Weltmechanik baut, zuerst prüfen, ob Hytale eine passende native Funktion bereitstellt. Native Engine-Fähigkeiten werden bevorzugt adaptiert, solange sie die Produktanforderung erfüllen.
