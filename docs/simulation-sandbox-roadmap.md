# Civ Simulation Sandbox – Implementierungs- und Abnahmecheckliste

Dieses Dokument trennt **im Branch implementiert** von **abschließend abgenommen**. Ein Punkt wird erst abgeschlossen, wenn die technische Abnahme vorliegt. Der zugehörige PR ist [#323](https://github.com/weidmanngabriel/hytale-civ/pull/323). Ein nicht gemergter Branch bedeutet nicht „fertig“.

## Phase 1 – Lokaler 3D-Viewer

- [x] Vorhandenen Three.js-Viewer und Spectator-Oberflächenregel wiederverwenden (bereits im Repository).
- [x] WASD, Mausblick und fein abgestuftes Mausrad mit 5-%-Tempoänderung (PR #322, bereits in main).
- [x] Java-HTTP-Live-Service und separate Live-Ansicht implementiert (Branch #323).
- [ ] End-to-End-Start und 3D-Browserinteraktion lokal geprüft.

## Phase 2 – Dynamische Szenarien

- [x] Live-Start/Pause/Step/Reset und Tick-Zähler implementiert.
- [x] Vorhandene Core-Szenarien und parametrisierte zusätzliche NPC-Startzustände eingebunden.
- [x] Interaktive Miner können auf einer ausreichend großen importierten Region Core-geplante Tunnel autonom ausheben (Headless-Diagnose, keine native Hytale-Engine).
- [ ] CI und Wiederholbarkeit der parametrisierten Szenarien abschließend grün.

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
- [ ] Browser-Event- und Renderingverhalten manuell geprüft.
- [ ] Alle relevanten Tests erfolgreich abgeschlossen.

## Phase 6 – Aufräumen und Abschluss

- [x] Actions-Workflows für Replay-Vorberechnung und GitHub-Pages-Veröffentlichung entfernt (Feature-Branch).
- [x] Entwicklungs-, Architektur-, Test- und Produktdokumentation auf lokale Live-Nutzung aktualisiert.
- [ ] GitHub CI vollständig grün, PR per Squash nach main integriert und main-CI geprüft.
- [x] Manuelle Schritt-für-Schritt-Testanleitung verfasst: [simulation-sandbox-manual-test.md](simulation-sandbox-manual-test.md).
- [ ] Lokale Browser-/Ingame-Abnahme anhand dieser Anleitung durchgeführt.
