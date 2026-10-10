# Civ Simulation Sandbox – Implementierungs- und Abnahmecheckliste

Dieses Dokument trennt **im Branch implementiert** von **abschließend abgenommen**. Ein Punkt wird erst abgeschlossen, wenn die technische Abnahme vorliegt. Der zugehörige PR ist [#323](https://github.com/weidmanngabriel/hytale-civ/pull/323). PR #323 wurde am 10. Oktober 2026 in `main` integriert; die Main-CI war erfolgreich (Run 38029485309). Eine Implementierung zählt erst als abgenommen, wenn ihr passender automatischer oder manueller Test bestätigt wurde.

## Phase 1 – Lokaler 3D-Viewer

- [x] Vorhandenen Three.js-Viewer und Spectator-Oberflächenregel wiederverwenden (bereits im Repository).
- [x] WASD, Mausblick und fein abgestuftes Mausrad mit 5-%-Tempoänderung (PR #322, bereits in main).
- [x] Java-HTTP-Live-Service und separate Live-Ansicht implementiert (Branch #323).
- [x] End-to-End-Start und 3D-Browserinteraktion am 10. Oktober 2026 durch Nutzer getestet (9/9 Live-Viewer-Checks bestanden).

## Phase 2 – Dynamische Szenarien

- [x] Live-Start/Pause/Step/Reset und Tick-Zähler implementiert.
- [x] Vorhandene Core-Szenarien und parametrisierte zusätzliche NPC-Startzustände eingebunden.
- [x] Interaktive Miner können auf einer ausreichend großen importierten Region Core-geplante Tunnel autonom ausheben (Headless-Diagnose, keine native Hytale-Engine).
- [x] Java-, Browser- und MCP-CI inklusive parametrisierter Szenarien auf Feature-Commit `b14fe7c` grün (Workflow 38029238656).

## Phase 3 – Echte Hytale-Welten

- [x] Versionsgebundenes, komprimiertes Roh-Weltarchiv inklusive Luft, Block-ID, Flüssigkeiten und Region erstellt.
- [x] Import der Rohregion und abgeleitete Voxelklassifikation für Viewer und Navigation implementiert.
- [x] Hytale-Exportbefehl für bereits geladene Chunks umgesetzt.
- [ ] Export in echter Hytale-Laufzeit anhand eines Spielstands geprüft.
- [ ] Direkten Import bestehender Save-Dateien untersuchen; nur bei belegtem Format hinzufügen.

## Phase 4 – Navigation und Weltänderungen

- [x] Vereinfachtes orthogonales A* mit maximal einem Block Höhenunterschied implementiert.
- [x] Headless-Bewegung kann das importierte Voxelmodell nutzen.
- [x] Änderung des Voxelkartenzustands invalidiert vorhandene Wege.
- [x] Automatischer Headless-Minenabbau ändert das Voxelmodell, dessen Revision die 3D-Darstellung neu lädt.

## Phase 5 – Tests und Diagnose

- [x] World-Archive-Roundtrip, Voxel-A*, Live-Steuerung und mehrere NPC-Routen als Java-Tests implementiert.
- [x] Automatisierte Mine-Fixture mit drei Minern sowie manuellem Unterbrechen und Rückkehrtest implementiert.
- [ ] CI bestätigt den kompletten Ablauf und zusätzliche reale Höhlen-/Wasser-/Lavafälle.
- [x] Diagnostische, begrenzte Ereignislisten und zusätzliche Assertions implementiert.
- [x] Browser-Event-Anzeige, NPC-Arbeit und 3D-Kamerasteuerung manuell bestätigt (9/9 Nutzerchecks).
- [ ] Höhlen-/Voxeldarstellung mit real exportierter Welt manuell geprüft.
- [x] Alle aktuell im CI ausgeführten 281 Java-Tests und Browser-/MCP-Prüfungen erfolgreich (Feature-Commit `b14fe7c`).
- [x] Automatisierte Core-Paritäts- und Integrationsprüfungen aus Phase 5B ergänzen (native Engine-Grenzen bleiben separat).

## Phase 6 – Aufräumen und Abschluss

- [x] Actions-Workflows für Replay-Vorberechnung und GitHub-Pages-Veröffentlichung entfernt (Feature-Branch).
- [x] Entwicklungs-, Architektur-, Test- und Produktdokumentation auf lokale Live-Nutzung aktualisiert.
- [x] PR #323 per Squash in `main` integriert (Commit `a877215`); Main-CI erfolgreich (Workflow 38029485309).
- [x] Manuelle Schritt-für-Schritt-Testanleitung verfasst: [simulation-sandbox-manual-test.md](simulation-sandbox-manual-test.md).
- [x] Lokale Standard-Browser-Abnahme (ohne importierte Hytale-Welt) vom Nutzer bestätigt: alle neun Bedienungsschritte erfolgreich.
- [ ] Ingame-Weltexport und anschließender Import mit echten Voxel-/Höhlendaten manuell geprüft.

## Phase 5B – Gemeinsame Miner-Core-Logik (Option B, ausdrücklich beauftragt)

- [x] Bestandsaufnahme: Produktions-Miner verwendet bereits `MineNormalTaskSelector` und `MineFrontCoordinator` aus dem Core.
- [x] Headless-Miner verwendet nun dieselbe Core-Auswahlregel für Tunnel-Task-Kapazität statt einer rein eigenen Auswahl.
- [x] Gemeinsam verwendeter Core-Entscheider für Front-Beitritt und Blockclaim in Hytale-Miner und Headless-Lab verdrahtet; separater Regressionstest.
- [x] Gemeinsame Miner-Ablaufmaschine für Auswahl, Navigation-Intent, Engine-Rückmeldung, Blockclaim, Unterbrechung und Wiederaufnahme extrahieren.
- [x] Bestehenden Hytale-`MinerWorkSystem` auf diese Ablaufmaschine als Engine-Adapter umstellen (nicht nur parallele Simulation bauen).
- [x] Headless-Simulator über dieselbe Ablaufmaschine ausführen und Engine-Ergebnisse künstlich melden.
- [x] Paritätstests über identische Befehls-/Ergebnisfolgen, einschließlich Mehrfach-Miner, Rückweg, Hindernis, Abbruch und Retry.
- [ ] Integrations- und CI-Abnahme grün; erst dann diesen Teil als abgeschlossen markieren.

## Lab A–C – ergänzender Stand

- [x] Shared Core `MineWorkerRouteDecision` für Zugang → Anschluss → Arbeitsfront in produktivem und Headless-Miner referenziert.
- [x] Authored Mine_01-Prefab mit Block-/Empty-Daten und semantischen Markern für importierte Welt visualisierbar und platzierbar.
- [x] Lokales Debug-API, Weg-/Arbeitsfront-/Marker-Overlays und begrenzte Simulator-Konsole.
- [x] Vollständige produktive Miner-Task-State-Machine für alle Arbeitsarten als Core-Laufzeit geteilt und Golden-Paritätstests implementiert.
- [ ] End-to-End auf echtem großen Weltarchiv mit Platzierung, drei Minern und deren vollständigem Rückweg vom Nutzer abgenommen.

### Phase-5B-Abnahmeprotokoll

- Gemeinsamer Core: `MinerWorkController`, einschließlich Tunnel-, Raum- und Infrastrukturarbeit; beide Live-Adapter delegieren an diesen Controller.
- Engine-Golden-Historien vergleichen identische Beobachtungen/Ergebnisse mit produktiver Coordinator- und Headless-Verdrahtung. Sie sind kein echter Hytale-Lauf.
- Voxel-Runtime testet Mehrfach-Miner, manuellen Abbruch/Wiederaufnahme, Rückweg, Wasser/Lava und explizite Recovery.
- Native Block-/Prefab-APIs bleiben im Hytale-Adapter. Hytale Local wurde für diese Core-Umstellung nicht ausgeführt.
- Integration: PR-/Main-CI-Nachweis wird nach erfolgreicher Validierung ergänzt.
- Unabhängig offen: realer Weltexport, native Navigation/Placementphysik und Nutzerabnahme auf großem Echtweltarchiv. Diese Punkte gehören nicht zum Core-Abschluss von Phase 5B.
