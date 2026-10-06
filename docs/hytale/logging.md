# Logging

## Verifizierte API

Für die aktuell gepinnte `HytaleServer.jar` stellt `PluginBase` über `getLogger()` einen `com.hypixel.hytale.logger.HytaleLogger` bereit. `JavaPlugin` erbt diesen Logger über `PluginBase`.

`HytaleLogger` basiert auf Flogger und unterstützt unter anderem `atInfo()`, `atWarning()` und `at(Level)`. Civ sollte für normale Plugin-Diagnostik diesen Logger verwenden statt zusätzliche Datei-Logger oder direkte `System.out`-Ausgaben einzuführen.

Die Signatur wurde direkt gegen die gepinnte Server-JAR geprüft. Daraus ist die Klassen- und Methodenexistenz belegt; konkrete externe Log-Routing-Konfiguration bleibt Laufzeit-/Serverkonfiguration.

## Civ-Mine-Diagnostik

`CivMineDecisionDiagnostics` ist der Hytale-Adapter für strukturierte Mine-Entscheidungsereignisse. Die Entscheidung selbst wird dort beobachtet, wo sie entsteht; der Adapter filtert nur Kategorien und schreibt aktivierte Events über den Plugin-Logger. Der Core importiert dafür keine Hytale-Klassen.

Logging ist standardmäßig deaktiviert. Es darf keine zusätzlichen Zufallsentscheidungen, Weltabfragen oder Gameplay-Zustandsänderungen auslösen.
