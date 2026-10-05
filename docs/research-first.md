# Research-first bei unbekannten Hytale-Features

Diese Regel gilt für neue Hytale-spezifische Features und Subsysteme, bei denen die konkrete technische Umsetzung noch nicht verifiziert ist.

## Grundsatz

Bevor ein eigener Mechanismus, Adapter, Asset-Typ oder Workaround entworfen wird, muss zuerst geprüft werden, ob Hytale selbst bereits eine passende oder eng verwandte Lösung bereitstellt. Ziel ist, native Engine-Fähigkeiten zu verwenden oder zu adaptieren, statt ein paralleles System zu bauen, das später mit Hytale kollidiert oder unnötig gewartet werden muss.

## Reihenfolge der Recherche

1. **Bestehenden Projektcode prüfen.** Nach bereits verwendeten Hytale-APIs, Assets, Interactions, UI-Primitiven oder Adaptermustern im Repository suchen.
2. **Offizielle Hytale-Dokumentation online prüfen.** Nach dem betreffenden Feature, dem Asset-Typ, der API oder einem vergleichbaren Vanilla-Anwendungsfall suchen. Bei versionsabhängigen Informationen auf die im Projekt gepinnte Hytale-Version achten.
3. **Öffentliche Beispiel-Repositories prüfen.** Geeignete Hytale-Mod-Repositories, dokumentierte Vanilla-Beispiele oder seriöse Community-Repositories nach real verwendeten Konfigurationen und Integrationsmustern durchsuchen.
4. **Gepinnte Hytale-Binärdateien und Assets prüfen.** `HytaleServer.jar` mit `jar tf`/`javap` und verfügbare Vanilla-Assets verwenden, um konkrete API- und Asset-Strukturen der gepinnten Version zu verifizieren.
5. **Unklarheiten benennen.** Dokumentation, Code und Runtime-Beobachtung können sich unterscheiden. Nicht aus einzelnen Fundstellen stillschweigend Runtime-Semantik ableiten.
6. **Erst danach implementieren.** Wenn Hytale eine geeignete native Funktion besitzt, diese bevorzugt komponieren oder adaptieren. Nur wenn die Produktanforderung damit nicht erfüllt werden kann, einen eigenen Mechanismus entwerfen.
7. **Runtime nur für offene Engine-Verträge einsetzen.** Verhalten wie Lifecycle, Dispatch, Navigation, Interaction-Ausführung oder Client-/Server-Semantik bei Bedarf mit einem kleinen fokussierten Hytale-Local-Szenario verifizieren.
8. **Erkenntnisse festhalten.** Wiederverwendbare, verifizierte Hytale-Erkenntnisse anschließend in `docs/hytale/` dokumentieren. Workflow-Erkenntnisse gehören in die Entwicklungs- beziehungsweise Debugging-Dokumentation.

## Was nicht als ausreichende Recherche gilt

- Eine API oder ein JSON-Schema aus dem Namen heraus zu erraten.
- Nach dem ersten fehlgeschlagenen Versuch sofort einen eigenen Ersatzmechanismus zu bauen.
- Nur die Java-Signatur zu prüfen und daraus Runtime-Verhalten abzuleiten.
- Nur einen einzelnen Community-Codeausschnitt zu übernehmen, ohne Version, Kontext und native Alternative zu prüfen.

## Entscheidungsregel

Für unbekannte Hytale-Features gilt daher standardmäßig:

> **Research first, native first, custom second.**

Der eigene Code soll möglichst die Civ-spezifische Produktlogik enthalten; Hytale-spezifische Engine-Fähigkeiten sollen dort genutzt werden, wo Hytale sie bereits bereitstellt.
