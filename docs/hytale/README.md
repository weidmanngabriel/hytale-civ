# Hytale-Integrationswiki

Dieses Verzeichnis sammelt wiederverwendbares, projektspezifisches Wissen darüber, wie Hytale in `hytale-civ` tatsächlich integriert wird.

## Zweck

`docs/architecture.md` beschreibt die Architektur von Civ. Dieses Wiki beschreibt dagegen Hytale als Integrationsplattform: verifizierte APIs, Engine-Verhalten, verwendete Asset-/ECS-Konzepte, bekannte Grenzen und offene Runtime-Fragen.

Das Wiki ist **kein Ersatz** für `docs/architecture.md`, `docs/concept.md`, `docs/development.md`, `docs/testing.md` oder ADRs.

## Evidenzregeln

Dokumentiere nur Erkenntnisse, für die es projektspezifische Evidenz gibt. Bevorzugte Quellen sind, in dieser Reihenfolge:

1. die im Projekt gepinnte `HytaleServer.jar` für konkrete Klassen, Methoden, Felder und Bytecode,
2. offizielle Hytale-Dokumentation für Runtime-Semantik und Lifecycle,
3. fokussierte In-Game-/Server-Diagnostik für Verhalten, das sich weder aus Signaturen noch aus offizieller Dokumentation sicher ableiten lässt,
4. bereits getesteter Projektcode als zusätzliche Evidenz für die konkrete Integration.

Eine gefundene Klasse oder Methodensignatur beweist nicht automatisch, wann oder wie Hytale sie zur Laufzeit aufruft.

## Pflege-Regel für zukünftige Arbeiten

Wenn bei Hytale-spezifischer Entwicklung eine neue wiederverwendbare Erkenntnis entsteht, muss die passende Seite in `docs/hytale/` im selben Change aktualisiert werden.

Das Wiki ist eine **Current-Truth-Dokumentation**. Ziel ist nicht, jede frühere Annahme oder jeden historischen Widerspruch aufzubewahren, sondern den aktuell verifizierten Wissensstand möglichst klar darzustellen.

Dabei gilt:

- neue verifizierte Erkenntnisse ergänzen statt nur im Chat, Issue oder Codekommentar stehen lassen,
- veraltete, falsche oder durch neuere Evidenz ersetzte Aussagen korrigieren, überschreiben oder entfernen,
- Widersprüche nur solange sichtbar lassen, wie noch nicht geklärt ist, welche Aussage richtig ist,
- sobald ein Widerspruch zuverlässig geklärt ist, nur die aktuelle korrekte Aussage in der laufenden Referenzdokumentation behalten,
- offene oder nur teilweise verifizierte Punkte ausdrücklich als **offen** kennzeichnen,
- keine hypothetischen APIs, Lifecycle-Annahmen oder Client-Verhalten als Fakten dokumentieren,
- Implementierungsdetails von Civ nur aufnehmen, wenn sie für das Verständnis der Hytale-Integration nötig sind,
- historische Begründungen oder bewusst verworfene Alternativen nur dann aufbewahren, wenn sie als Architekturentscheidung relevant sind; dafür ADRs unter `docs/decisions/` verwenden.

Vor neuer Hytale-spezifischer Arbeit zuerst diese Übersicht und die relevante Themenseite lesen. Wenn eine passende Themenseite fehlt und die Erkenntnis wiederverwendbar ist, eine neue Seite anlegen und hier verlinken.

## Themen

- [Verifikation und API-Recherche](verification.md)
- [Headless-Server und Runtime-Probe](server-headless.md)
- [Threading und World-Ausführung](threading.md)
- [Lifecycle und Runtime-Zustand](lifecycle-runtime-state.md)
- [Performance und Worker-Scheduling](performance-scheduling.md)
- [UI und Interaktion](ui-interaction.md)
- [NPCs und Navigation](npc-navigation.md)
- [NPC-Combat](npc-combat.md)
- [Worker-Item-Animationen](worker-item-animations.md)
- [ECS und Persistenz](ecs-persistence.md)
- [Prefabs und Bauen](prefabs-building.md)
- [Farming und Inventar](farming-inventory.md)

## Bekannte offene Bereiche

Noch nicht als eigene stabile Wissensseiten dokumentiert sind unter anderem Events allgemein, Audio, Client-spezifische Rendering-Hooks, Networking/Multiplayer-Verträge und weitere Assettypen. Solche Bereiche sollen erst ergänzt werden, sobald konkrete, verifizierte Projekterkenntnisse vorliegen.
