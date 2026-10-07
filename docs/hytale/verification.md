# Verifikation und API-Recherche

## Ziel

Hytale-spezifische Implementierungen dürfen nicht auf geratenen APIs oder vermuteter Runtime-Semantik beruhen.

## Standardvorgehen

1. Bestehenden Projektcode auf bereits etablierte Nutzung prüfen.
2. Wenn die konkrete Umsetzung eines neuen Hytale-Features noch nicht klar ist, **vor dem Entwurf einer eigenen Lösung online recherchieren**: zuerst die aktuelle offizielle Hytale-Dokumentation, danach passende öffentliche GitHub-Repositories, Beispielmods oder andere belastbare Implementierungsbeispiele nach vergleichbaren Mechaniken durchsuchen.
3. Die im Projekt bereitgestellte `HytaleServer.jar` untersuchen.
4. Klassen und Packages mit `jar tf` oder einem gleichwertigen ZIP/JAR-Listing finden.
5. Relevante Klassen mit `javap` untersuchen.
6. Wenn Signaturen nicht reichen, `javap -c -p` oder ein gleichwertiges Classfile-/Bytecode-Werkzeug verwenden.
7. Die Ergebnisse aus Online-Recherche, JAR und bestehendem Projektcode gegeneinander prüfen. Fremde Repositories sind Beispiele, keine API-Garantie; maßgeblich bleibt die im Projekt gepinnte Hytale-Version.
8. Verhalten, das davon weiter offen bleibt, mit einem kleinen gezielten Runtime-Test verifizieren.
9. Wiederverwendbare Erkenntnisse in der passenden Seite unter `docs/hytale/` dokumentieren.

Die Online-Recherche ist insbesondere dann Pflicht, wenn noch unklar ist, welchen nativen Hytale-Mechanismus ein Feature verwenden soll. Erst wenn offizielle Dokumentation, bestehende Beispiele und die gepinnte Server-JAR keine passende Lösung zeigen, soll Civ eine eigene Mechanik entwerfen. Dadurch vermeiden wir, Engine-Funktionen unnötig nachzubauen oder durch Versuch und Irrtum eine bereits dokumentierte Lösung erneut zu entdecken.

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

Die Hytale-Dokumentation ist eine **Current-Truth-Dokumentation**. Wenn neue Evidenz eine frühere Annahme widerlegt oder präzisiert, muss die betroffene Aussage im selben Change korrigiert oder entfernt werden. Widerlegte Ansätze dürfen nicht als weiterhin gültige Anleitung stehen bleiben. Historische Fehlversuche werden nur dann aufbewahrt, wenn sie als Architekturentscheidung oder Warnung weiterhin relevant sind; dafür eignen sich ADRs oder ausdrücklich als historisch markierte Abschnitte.

## Manifest-Versionen

**Verifiziert:** In der gepinnten Hytale-Server-JAR verwendet <code>PluginManifest.Version</code> den Typ <code>com.hypixel.hytale.common.semver.Semver</code>. <code>Semver</code> besitzt explizite Pre-Release-Bestandteile und kann Versionen wie <code>0.2.1-dev.42</code> darstellen. Hytale Civ darf deshalb Development-Builds mit normaler SemVer-Pre-Release-Syntax in ausgelieferten Manifesten versionieren.

## Verifikation reiner Dokumentationsänderungen

Änderungen, die ausschließlich Dokumentation betreffen und weder Code, Assets, Build-Konfiguration noch Runtime-Verhalten verändern, benötigen **keinen eigenen Test-, Build- oder Hytale-Local-Lauf**. Inhalt, Links und betroffene Querverweise sollen direkt geprüft werden; ein automatisch durch GitHub ausgelöster Workflow muss für eine reine Doku-Änderung nicht zusätzlich als fachlicher Testschritt abgewartet oder manuell erneut gestartet werden.

Sobald eine Änderung neben Dokumentation auch Code, Assets, Konfiguration oder ausführbares Verhalten verändert, gelten wieder die normalen Verifikationsregeln für diese Änderung.

## Keine Ersatzimplementierung ohne Prüfung

Bevor Civ eigene Navigation, UI-Primitive, Interaktionslogik, Assettypen, Game-Mode-Verhalten, Farming-, Container- oder Weltmechanik baut, zuerst prüfen, ob Hytale eine passende native Funktion bereitstellt. Native Engine-Fähigkeiten werden bevorzugt adaptiert, solange sie die Produktanforderung erfüllen.
