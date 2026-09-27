# Arbeitsregeln für Coding Agents

## Ziel

Baue aus diesem Repository eine kleine, installierbare Web-App als Progressive Web App. Die Anwendung soll standardmäßig vollständig clientseitig laufen und als statische Website über GitHub Pages veröffentlicht werden.

## Technischer Rahmen

Die Basis verwendet:

- React
- TypeScript
- Vite
- `vite-plugin-pwa`
- GitHub Pages über GitHub Actions
- optionalen Google Login über Google Identity Services

Halte zusätzliche Libraries und Architektur so klein wie sinnvoll. Führe kein Backend, Framework oder Zustandsmanagement ein, solange die Produktanforderungen es nicht rechtfertigen.

## Verbindliche Projektdokumentation

Lies vor jeder Implementierung `architecture.md`.

Bei Änderungen an Produktfunktionen oder fachlichen Abläufen lies zusätzlich `concept.md`.

Halte `architecture.md` und `concept.md` bei relevanten Änderungen aktuell. Ergänze neue Entscheidungen, aktualisiere geändertes Verhalten und entferne Aussagen, die nicht mehr dem tatsächlichen Stand entsprechen.

Die Dokumentation soll eine belastbare Highlevel-Übersicht bleiben. Sie muss so gut sein, dass ein fähiger Agent die App und ihre wesentlichen Entscheidungen schnell versteht und im Zweifel neu aufbauen könnte. Dokumentiere nicht jedes Implementierungsdetail.

Bei Widersprüchen haben diese Datei und die aktuelle Nutzeranweisung Vorrang. Weise auf echte inhaltliche Widersprüche hin, damit die Dokumentation bereinigt werden kann.

## Authentifizierung

Google Login ist als optionale Basis vorbereitet. Verwende dafür Google Identity Services im Browser.

Die öffentliche OAuth-Web-Client-ID wird über `VITE_GOOGLE_CLIENT_ID` bereitgestellt. Für GitHub Actions wird die Repository-Variable `GOOGLE_CLIENT_ID` verwendet.

Ein Client-Secret gehört niemals in dieses Repository oder in den ausgelieferten Browser-Code.

Der Template-Login speichert nur die Profildaten bereits erfolgreich angemeldeter Google-Konten lokal, damit mehrere Konten bequem gewechselt werden können. Das ist keine serverseitige Autorisierung und keine Sicherheitsgrenze für geschützte Daten. Falls eine spätere App ein Backend oder sensible Daten nutzt, muss dort eine passende serverseitige Prüfung ergänzt werden.

## PWA und Deployment

Die App muss weiterhin installierbar bleiben. Manifest, Service Worker und App-Icons werden über `vite-plugin-pwa` erzeugt bzw. eingebunden.

GitHub Pages verwendet GitHub Actions als Veröffentlichungsquelle. Die Action baut `dist` und veröffentlicht ausschließlich dieses Artefakt. Generierte Dateien wie `dist`, `sw.js`, `manifest.webmanifest`, `workbox-*` und gebündelte Assets dürfen nicht committed werden.

Prüfe nach Änderungen, ob der Produktionsbuild weiterhin erfolgreich ist. Nach Änderungen auf `main` soll auch der GitHub-Actions-Workflow erfolgreich abgeschlossen sein.

## Git-Arbeitsweise

Arbeite während eines Runs auf einem temporären Branch. Fasse die Anpassung am Ende per Squash in genau einen aussagekräftigen Commit auf `main` zusammen. Dadurch wird das Deployment nur einmal ausgelöst.

## Entwicklungsprinzipien

- Bevorzuge einfache, etablierte Lösungen.
- Entferne Template-Beispiele, sobald sie der echten Produktfunktion im Weg stehen.
- Erfinde keine umfangreiche Fachlogik, wenn sie noch nicht definiert ist.
- Behandle alles im Frontend als öffentlich einsehbar.
- Speichere keine Secrets oder privaten Zugangsdaten im Repository.
- Halte die App auf kleinen mobilen Displays ebenso nutzbar wie auf Desktop.
- Bewahre die PWA-Grundfunktionen und das GitHub-Pages-Deployment, sofern der Nutzer nicht bewusst etwas anderes entscheidet.
