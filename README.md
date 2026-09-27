# PWA Template

Schlanke Ausgangsbasis für eine neue Progressive Web App mit React, TypeScript, Vite, Google Login und Deployment über GitHub Pages.

Das Repository enthält bewusst keine produktspezifische Fachlogik. Es soll kopiert und anschließend über `concept.md` und `architecture.md` auf die neue App zugeschnitten werden.

## Enthalten

- React + TypeScript + Vite
- installierbare PWA mit Service Worker und App-Icons
- Pull-to-Refresh für installierte Apps auf Touch-Geräten
- neutraler responsiver Startscreen
- Google Login
- mehrere lokal gespeicherte Google-Konten mit Accountwechsel
- GitHub-Pages-Deployment über GitHub Actions
- `agents.md`, `concept.md` und `architecture.md` als Startpunkt für agentengestützte Entwicklung

Nicht enthalten sind Google Sheets/Drive, Datenbanklogik, Rollen, fachliche Workflows oder andere Funktionen einer konkreten App.

## Neue App aus dem Template erstellen

1. Auf GitHub **Use this template** wählen und ein neues Repository erstellen.
2. `concept.md` mit der Grundidee der neuen App füllen.
3. Name, Texte, Farben und Icons der neutralen Beispieloberfläche ersetzen.
4. Falls Google Login gebraucht wird, die Google-Konfiguration unten durchführen.
5. In **Settings → Pages** als Quelle **GitHub Actions** auswählen.
6. Die unten stehenden ChatGPT-Projektinstruktionen kopieren und Repository-Name sowie Live-URL anpassen.

## Lokal starten

```bash
npm install
cp .env.example .env.local
npm run dev
```

Für einen Produktionsbuild:

```bash
npm run build
```

## Google Login einrichten

Der Login ist optional. Ohne Client-ID funktioniert die restliche App weiterhin.

1. In Google Cloud einen OAuth Client vom Typ **Web application** anlegen.
2. Für lokale Entwicklung `http://localhost:5173` als autorisierten JavaScript-Ursprung eintragen.
3. Die spätere GitHub-Pages-Origin ebenfalls eintragen, zum Beispiel `https://DEIN-USER.github.io`.
4. Die Client-ID lokal in `.env.local` setzen:

```env
VITE_GOOGLE_CLIENT_ID=deine-client-id.apps.googleusercontent.com
```

5. In GitHub unter **Settings → Secrets and variables → Actions → Variables** eine Repository-Variable `GOOGLE_CLIENT_ID` mit derselben Client-ID anlegen.

Die Client-ID ist öffentlich und darf im Browser verwendet werden. Ein Client-Secret darf dagegen niemals in das Frontend oder Repository eingebaut werden.

Der Template-Login speichert nur die Profildaten bereits angemeldeter Konten lokal. Er ist keine serverseitige Autorisierung für sensible Daten.

## GitHub Pages

Der Workflow `.github/workflows/deploy.yml` wird bei Änderungen auf `main` ausgeführt, baut `dist` und veröffentlicht dieses Verzeichnis über GitHub Pages.

Der Vite-Basispfad ist relativ gehalten. Deshalb muss der Repository-Name nach dem Erstellen aus dem Template nicht in `vite.config.ts` eingetragen werden.

## ChatGPT-Projekt einrichten

Für ein neues ChatGPT-Projekt kann der folgende Text als projektspezifische Instruktion verwendet werden. Er ist absichtlich direkt kopierbar. Nur die Werte in `<...>` müssen für die neue App angepasst werden.

```text
Du bist ein Coding Agent für das GitHub Projekt "<OWNER>/<REPOSITORY>".

Bitte gehe von einem geringen technischen Verständnis aus; vermeide schwierige technische Details und Entwickler-Sprech, es sei denn, ich frage explizit danach.

Bitte berate mich positiv, aber challenge auch meine Annahmen. Ziel ist es, einen funktionierenden Prototyp auf die Beine zu stellen, mit dem man ein echtes Geschäftsmodell validieren kann.

Du musst in jedem neuen Chat als erstes per GitHub Connector auf die dortige agents.md Datei zugreifen.

Nach dem Lesen von agents.md lies vor jeder Implementierung auch architecture.md. Bei Änderungen an Produktfunktionen lies zusätzlich concept.md.

Halte agents.md, architecture.md und concept.md bei relevanten Änderungen aktuell. Bei widersprüchlichen Angaben haben agents.md und die aktuelle Nutzeranweisung Vorrang. Weise mich auf echte Widersprüche hin, damit wir die Dokumentation bereinigen können.

Committe während eines Runs auf einen temporären Branch und merge am Ende alles per Squash direkt auf main. Pro Anpassung soll auf main nur ein aussagekräftiger Commit übrig bleiben. Das triggert dann einmalig den Rebuild und das Deployment per GitHub Action.

Die App ist hier live gehostet: <LIVE-URL>
```

### Beispielwerte

Für ein Repository `maxmustermann/meine-app` wären die wichtigsten Ersetzungen:

```text
<OWNER>/<REPOSITORY> = maxmustermann/meine-app
<LIVE-URL> = https://maxmustermann.github.io/meine-app/
```

Wenn eine neue App andere technische Grundregeln braucht, sollten zuerst `agents.md` und `architecture.md` angepasst werden. Der ChatGPT-Projektprompt kann danach meist unverändert bleiben.

## Wichtige Dateien

- `agents.md` – Arbeitsregeln für Coding Agents
- `concept.md` – fachliche Produktidee und Kernabläufe
- `architecture.md` – technische Architektur und wichtige Entscheidungen
- `src/App.tsx` – neutrale Beispieloberfläche
- `src/auth/google.ts` – lokale Google-Account-Verwaltung
- `src/PullToRefresh.tsx` – Pull-to-Refresh für Touch-Geräte
- `.github/workflows/deploy.yml` – GitHub-Pages-Deployment

## Nach dem Erstellen aus dem Template

Die erste sinnvolle Änderung ist normalerweise nicht eine neue technische Library, sondern das Ausfüllen von `concept.md`: Problem, Zielgruppe, Kernnutzen und der kleinste validierbare Ablauf. Danach kann die neutrale Beispieloberfläche gezielt durch die echte Produktoberfläche ersetzt werden.
