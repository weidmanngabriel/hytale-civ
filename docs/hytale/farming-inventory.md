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


## Native Försterei (Hytale 0.7.2)

Im Gegensatz zum unveränderten Weizen-Farmer verwendet die Försterei echte registrierte `Plant_Sapling_*`-Blocktypen. `NativeForesterSaplings` liest über den nativen `ItemContainerBlock` der semantischen `output_storage`-Gebäudekiste alle gültigen Setzlings-Block-IDs, entfernt Dubletten und sortiert sie. Die Stückzahl je Stack beeinflusst die Auswahl nicht. Es wird **keine** Inventar-Entnahme vorgenommen; das Item schaltet die Baumart dauerhaft frei, solange es in der Truhe liegt. Eine leere Kiste stoppt weitere Pflanzungen.

Die Hytale-Engine wurde am 18.10.2026 mit Szenario `foresterassets` gegen `8ec0f56a203429434ba1c1c1860ac472ca0052e7` überprüft: Es gibt 34 registrierte Sapling-Blocktypen mit Farming-Konfiguration. Die drei echten ItemStacks `Plant_Sapling_Oak`, `Plant_Sapling_Birch` und `Plant_Sapling_Spruce` liefern genau ihre Block-IDs über `getBlockKey()`. Eine native Inventarprobe mit den Mengen 1,5,20 hat diese unverändert vorgefunden.

Das isolierte Szenario `foresterplant` für denselben SHA setzte diese drei nativen Sapling-Blöcke mit `HytaleBlockMutations.setBlock` (intern `BlockOperations.setBlock`) auf `Soil_Grass`; jeder Block besaß anschließend eine echte `FarmingBlock`-Komponente mit `lastTickGameTime`. Damit ist die **native Initialisierung**, nicht aber das vollständige Auswachsen oder das NPC-Verhalten bewiesen. Ein eigenständiges `foresternpc`-Szenario prüft echte NPC-Abläufe, Gebäude-Prefab, Kisteninhalt und unveränderte Stack-Mengen. Der Farmer-Weizenpfad bleibt weiterhin `UNSUPPORTED`; wir leiten daraus keinen allgemeingültigen NPC-Pflanzpfad für andere Feldfrüchte ab.
