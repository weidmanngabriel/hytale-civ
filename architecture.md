# Architektur

## Überblick

Die Anwendung ist eine rein clientseitige React-App mit TypeScript und Vite. Der Einstieg liegt in `src/main.tsx`, die neutrale Beispieloberfläche in `src/App.tsx` und das globale Styling in `src/styles.css`.

Das Repository ist als Ausgangspunkt für neue kleine Progressive Web Apps gedacht. Produktspezifische Funktionen sollen auf dieser Basis ergänzt werden, ohne unnötig Teile des Templates mitzuschleppen.

## PWA

`vite-plugin-pwa` erzeugt beim Produktionsbuild das Web-App-Manifest und den Service Worker. Die App verwendet feste App-Icons aus `public/` und ein `apple-touch-icon` für iOS.

`src/PullToRefresh.tsx` ergänzt auf Touch-Geräten ein eigenes Pull-to-Refresh. Die Geste startet nur am oberen Seitenrand und löst nach Überschreiten des Schwellwerts einen vollständigen Reload aus.

Der Vite-Basispfad ist relativ (`./`). Dadurch funktioniert derselbe Build sowohl lokal als auch als GitHub-Project-Page in einem Repository-Unterpfad, ohne dass der Repository-Name in der Konfiguration fest codiert werden muss.

## Google Login

Die Google-Anmeldung verwendet Google Identity Services direkt im Browser. Das Script wird in `index.html` geladen. Die öffentliche Client-ID kommt aus `VITE_GOOGLE_CLIENT_ID`.

`src/auth/google.ts` kapselt das Lesen und lokale Speichern der Google-Profildaten. Mehrere erfolgreich angemeldete Konten können im `localStorage` gespeichert und über die Oberfläche gewechselt werden. Gespeichert wird kein Google-Passwort und kein Client-Secret.

Die gespeicherten Profildaten sind nur Komfortzustand für die Oberfläche. Sie stellen keine belastbare Autorisierung für sensible Daten oder Backend-Aktionen dar. Sobald eine App geschützte Ressourcen verwendet, muss deren tatsächliche Zugriffsprüfung passend zur gewählten Backend-Architektur erfolgen.

Das Template fordert keine Google-Sheets-, Drive- oder sonstigen Google-API-Berechtigungen an.

## Deployment

`.github/workflows/deploy.yml` baut die App bei Änderungen auf `main` und veröffentlicht ausschließlich `dist` über GitHub Pages.

Die GitHub-Actions-Variable `GOOGLE_CLIENT_ID` wird beim Build als `VITE_GOOGLE_CLIENT_ID` bereitgestellt. Fehlt die Variable, baut die App trotzdem erfolgreich; die Oberfläche zeigt dann lediglich, dass der Google Login noch nicht konfiguriert ist.

GitHub Pages muss im Repository als Veröffentlichungsquelle **GitHub Actions** verwenden.

## Konfiguration

Lokale Entwicklung:

- `.env.example` nach `.env.local` kopieren,
- bei Bedarf `VITE_GOOGLE_CLIENT_ID` setzen,
- `npm install`,
- `npm run dev`.

Für Google Login wird außerhalb des Repositories ein Google OAuth Client vom Typ **Web application** benötigt. Der lokale Ursprung und die spätere GitHub-Pages-Origin müssen als autorisierte JavaScript-Ursprünge eingetragen werden.

## Erweiterung

Neue fachliche Bereiche sollten zunächst direkt und verständlich in `src/` ergänzt werden. Erst wenn Umfang oder Wiederverwendung es rechtfertigen, sollten zusätzliche Module, Datenzugriffsschichten oder Libraries eingeführt werden.

Bei neuen Produktfunktionen ist `concept.md` zu aktualisieren. Bei relevanten technischen Entscheidungen ist diese Datei anzupassen.
