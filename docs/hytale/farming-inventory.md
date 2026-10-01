# Farming und Inventar

## Farming

`FarmNpcWorkSystem` übersetzt Hytale-unabhängige Farmzustände in native Bewegungsziele und reale Hytale-Feldarbeit.

Reife Pflanzen werden über Hytales native `FarmingUtil.harvest`-Funktion geerntet. Civ erzeugt dafür keinen eigenen Wachstumstimer und keinen künstlichen Weizen-Output.

Für die aktuell gepinnte Hytale-Runtime ist im Projekt kein verifizierter nativer serverseitiger NPC-Pflanzpfad dokumentiert. `FarmPlantingService` meldet deshalb `UNSUPPORTED`, statt eine Spieler-Interaction zu simulieren oder Hytales Farming-Regeln nachzubauen.

Das ausgewählte Feld bleibt während eines laufenden Saat-/Erntezyklus gebunden und wird erst neu gesucht, wenn es nicht mehr registriert ist.

## Inventar

`ProfessionBootstrapInventory` führt vorläufige Startausstattung ausschließlich über von Hytale bestätigte Inventartransaktionen aus.

Beim aktuellen Farmer-Slice werden vier Seed-Bags als ein nativer Stack angefordert. Wenn ein vorhandener NPC-Inventarbereich die vollständige Einlagerung ablehnt, wird der native Rest nacheinander an weitere verfügbare Bereiche wie Storage, Hotbar und Backpack weitergereicht. Ein Fehlschlag im ersten Container darf die übrigen Bereiche nicht blockieren.

## Projektgrenze

Civ soll keine parallelen Farming-, Drop-, Wachstums- oder Containerregeln erfinden, wenn Hytale dafür native Mechanismen bereitstellt. Wo ein benötigter nativer Pfad nicht verifiziert ist, wird das offen behandelt statt durch eine scheinbar ähnliche Spieleraktion ersetzt.
