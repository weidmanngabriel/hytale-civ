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

## Försterei: native API-Prüfung vor der Implementierung (18.10.2026)

Die bereitgestellte HytaleServer.jar enthält `BlockType.getFarming()`, `FarmingData.getStages()` und `FarmingData.getStartingStageSet()`. `FarmingBlock` besitzt eine registrierte ChunkStore-Komponente und Felder für GrowthProgress, CurrentStageSet und LastTickGameTime. In `FarmingSystems.OnFarmBlockAdded` wurde per Bytecode nachvollzogen, dass beim Hinzufügen einer **bereits existierenden FarmingBlock-Komponente** das StageSet und der GameTime-Zeitstempel gesetzt werden. Das **beweist nicht**, dass das Civ-`MineBlockPlacement.placeDetailed` für einen Setzling die benötigte native FarmingBlock-Komponente erzeugt.

Die allgemeine `BlockOperations.setBlock`-Signatur und der bereits verwendete Spieler-ähnliche Platzierungsadapter `MineBlockPlacement` existieren. Vor produktiver Aktivierung eines Försters sind ein exakt registrierter Sapling-Block inklusive passendem Item, native Soil-/Fluid-/Freiraumvalidierung, ein bestätigter FarmingBlock-Lifecycle und eine Native-Inventory-Transaktion mit gezielter Hytale-Local-Probe zu verifizieren. Bis dahin bleibt native Setzlingsplatzierung **offen**, statt eine nur visuelle Blockplatzierung fälschlich als gewachsenen Baum zu bestätigen. <Link-Referenz nicht im Repo gespeichert: https://hytale.com/news/2026/8/update-6-patch-notes> beschreibt Hytales Prefab-Sapling-Growth-Obstruction-Regeln. 
