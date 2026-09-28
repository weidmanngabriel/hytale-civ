# Entwicklungsablauf

## Voraussetzungen

- JDK 25
- Git
- Keine globale Gradle-Installation; den Wrapper verwenden.

## Lokaler Ablauf

~~~text
Code/Assets ändern
    ↓
./gradlew test
    ↓
./gradlew build
    ↓
optional: ./gradlew deployToHytale
    ↓
in Hytale testen
~~~

Unter Windows <code>gradlew.bat</code> verwenden.

## Hytale-Abhängigkeit

Release-Repository: <code>https://maven.hytale.com/release</code>

Abhängigkeit: <code>com.hypixel.hytale:Server</code>

<code>hytaleServerVersion</code> wird zentral in <code>gradle.properties</code> verwaltet. Vor Änderungen an der Hytale-API-Nutzung oder am Dependency-Selektor muss die aktuelle offizielle Dokumentation geprüft werden.


## Hytale-API-Snapshot (Proof of Concept)

Für die laufende Erprobung kann die tatsächlich von Gradle aufgelöste Hytale-Server-JAR analysiert werden:

~~~bash
./gradlew snapshotHytaleApi
~~~

Der Task erzeugt unter <code>build/hytale-api-snapshot/</code> einen Klassenindex, Metadaten zur aufgelösten Hytale-Abhängigkeit und per <code>javap</code> lesbare Signaturen für einige bekannte Problemklassen. Der Snapshot ist ausdrücklich noch kein verpflichtender Entwicklungsworkflow und ersetzt keine Laufzeittests in Hytale.

Die CI lädt denselben Ordner vorläufig als separates Artefakt <code>hytale-api-snapshot-poc</code> hoch. Damit kann geprüft werden, ob die tatsächliche Hytale-API in späteren Entwicklungsruns zuverlässig ausgewertet werden kann, ohne dekompilierten Hytale-Quellcode ins Repository zu übernehmen.

## Asset Pack

Vom Ersteller bearbeitbare Hytale-Assets liegen unter:

~~~text
asset-pack/
~~~

Das Verzeichnis ist ein eigenständiges Hytale Asset Pack und enthält deshalb eine eigene <code>manifest.json</code>. Java-/Plugin-Ressourcen bleiben in <code>src/main/resources</code>; das Plugin-Manifest darf nicht aus dem JAR verschoben werden.

<code>./gradlew build</code> erzeugt ein Distributions-ZIP unter:

~~~text
build/distributions/hytale-civ-<version>-bundle.zip
~~~

Das ZIP enthält:

~~~text
hytale-civ.jar
hytale-civ-assets/
~~~

Das äußere ZIP dient nur als Release- beziehungsweise Download-Container. Das Asset-Verzeichnis bleibt vom JAR getrennt, damit installierte Assets bearbeitet oder ersetzt werden können, ohne Java neu zu kompilieren.

Gameplay-Daten sollen nur dann ins Asset Pack verschoben werden, wenn dafür ein konkreter Hytale-Asset-Typ benötigt wird. Core-Simulationsregeln und Domänenzustand bleiben in der bestehenden Java-Architektur, solange ein späteres Feature keine andere Grenze begründet.

## Prefabs im Spiel bearbeiten

Hytales Prefab-Editor kann Prefabs aus Asset Packs laden und wieder in ein ausgewähltes Asset Pack speichern. Während der Entwicklung soll deshalb das Repository-Verzeichnis <code>asset-pack/</code> die maßgebliche bearbeitbare Quelle sein und nicht eine separat deployte Kopie.

Empfohlenes lokales Setup:

~~~text
Git-Repository
└── asset-pack/                    ← maßgebliche bearbeitbare Dateien

Hytale-Mods-Verzeichnis
└── hytale-civ-assets              ← Verzeichnislink/Junction auf repo asset-pack/
~~~

Damit sieht der Bearbeitungsablauf so aus:

~~~text
Hytale-Prefab-Editor öffnen
    ↓
Civilizations/Farm/Farm_01 laden
    ↓
Blöcke und Creator-Marker bearbeiten
    ↓
in HytaleCivAssets speichern
    ↓
asset-pack/ im Git-Working-Tree ändert sich direkt
    ↓
Diff prüfen, testen, committen
~~~

<code>deployToHytale</code> darf beim Bearbeiten von Prefabs im Spiel nicht als maßgebliche Quelle verwendet werden. Dieser Task kopiert das Asset Pack absichtlich in das Mods-Verzeichnis. Spätere Änderungen im Spiel würden dann die installierte Kopie statt das Repository verändern. Deployment-Kopien sind für Laufzeittests gedacht; für Round-Trip-Prefab-Bearbeitung soll ein Verzeichnislink beziehungsweise eine Junction verwendet werden.

Beispielhafte Entwicklungslinks; die Pfade sind Platzhalter und dürfen nicht committed werden.

Windows Command Prompt mit einer Directory Junction:

~~~bat
mklink /J "%APPDATA%\Hytale\UserData\Mods\hytale-civ-assets" "C:\path\to\hytale-civ\asset-pack"
~~~

macOS/Linux:

~~~bash
ln -s /path/to/hytale-civ/asset-pack /path/to/Hytale/UserData/Mods/hytale-civ-assets
~~~

Falls bereits ein normales deploytes Verzeichnis <code>hytale-civ-assets</code> existiert, muss diese Kopie vor dem Erstellen des Links entfernt oder umbenannt werden. Maschinenspezifische Hytale-Pfade oder Links dürfen niemals committed werden.

## Lokales Deployment

<code>HYTALE_MODS_DIR</code> setzen und ausführen:

~~~bash
./gradlew deployToHytale
~~~

Alternativ:

~~~bash
./gradlew deployToHytale -PhytaleModsDir=/path/to/mods
~~~

Der Task kopiert sowohl das Plugin-JAR als auch <code>hytale-civ-assets/</code> in das konfigurierte Mods-Verzeichnis. Normale Tests und Builds benötigen diese Einstellung nicht.

## Integration von Änderungen

Jede Änderung wird auf einem temporären Branch umgesetzt. Zwischen-Commits sind während der Arbeit erlaubt.

Vor der Integration:

1. Vorgesehenen Code, Tests und betroffene Dokumentation auf dem temporären Branch fertigstellen.
2. <code>./gradlew test</code> und <code>./gradlew build</code> ausführen, sofern die lokale Umgebung dies erlaubt.
3. Einen Pull Request gegen <code>main</code> öffnen, damit GitHub Actions die finale Version prüft.
4. Wenn die Validierung Änderungen verlangt, diese auf denselben temporären Branch pushen und erneut validieren.
5. Nur den final validierten Branch per Squash-Merge integrieren, damit genau ein sinnvoller Commit für die Änderung auf <code>main</code> verbleibt.
6. Danach den Workflow auf <code>main</code> sowie das erzeugte Pre-Release prüfen.

Nach dem final validierten Zustand dürfen keine zusätzlichen Änderungen auf denselben Branch gepusht und anschließend ungeprüft gemerged werden. Ein Fix nach dem Merge beginnt auf einem neuen temporären Branch und wird ein eigener Squash-Commit.

## GitHub-Workflow

Pull Requests führen Tests und einen vollständigen Java-25-Gradle-Build für Feature-Branches aus. Normale Pushes auf Nicht-Main-Branches starten keinen zweiten, doppelten Workflow. Pushes auf <code>main</code> und Tags mit Präfix <code>v</code> führen weiterhin CI aus, weil sie Development- und Stable-Releases erzeugen. Wird derselbe Pull Request beziehungsweise Branch mit einem neueren Commit aktualisiert, bricht GitHub Actions den älteren laufenden Workflow ab, sodass nur die neueste Revision weiterläuft. Das Release-Bundle-ZIP wird als Actions-Artefakt hochgeladen. Fehlgeschlagene Testberichte werden zur Analyse ebenfalls hochgeladen.

Jeder erfolgreiche Push auf <code>main</code> erzeugt ein SemVer-kompatibles Development-Pre-Release. Die Basisversion stammt aus <code>projectVersion</code> in <code>gradle.properties</code>. Der Workflow entfernt <code>-SNAPSHOT</code> und hängt die GitHub-Actions-Run-Nummer an:

~~~text
projectVersion=0.1.0-SNAPSHOT
→ v0.1.0-dev.42
~~~

Dieselbe aufgelöste Version wird an Gradle übergeben, damit Build-Metadaten von JAR/Distribution und GitHub Release dieselbe Projektversion verwenden. Das herunterladbare Release-Asset heißt dann:

~~~text
hytale-civ-0.1.0-dev.42.zip
~~~

Der Commit-SHA bleibt zur Nachverfolgbarkeit in den Release Notes, wird aber nicht mehr als Release-Version verwendet. Dadurch bleiben Development-Releases natürlich sortierbar und stabile Versionen wie <code>v0.1.0</code> bilden das Ende der jeweiligen SemVer-Linie.

Vor Beginn der Entwicklung für die nächste stabile Versionslinie muss <code>projectVersion</code> entsprechend erhöht werden, zum Beispiel von <code>0.1.0-SNAPSHOT</code> auf <code>0.2.0-SNAPSHOT</code>.

Die GitHub-Release-Beschreibung enthält den Betreff des veröffentlichten Commits. Da abgeschlossene Projektänderungen per Squash-Merge integriert werden, erhält jedes Main-Release damit eine einzeilige Zusammenfassung der jeweiligen Änderung.

Für stabile Versionen ein SemVer-Tag pushen, zum Beispiel:

~~~bash
git tag v0.1.0
git push origin v0.1.0
~~~

Ein erfolgreicher Tag-Build erzeugt ein normales GitHub Release mit demselben ZIP-Bundle als Anhang. Tags mit Präfix <code>v</code> gelten als stabile Releases; normale Builds auf <code>main</code> bleiben Pre-Releases.

Ein erneuter Lauf eines Release-Jobs ist idempotent: Existiert das Release bereits, aktualisiert der Workflow die kurze Release-Notiz und ersetzt das ZIP-Asset, statt ein Duplikat anzulegen.
