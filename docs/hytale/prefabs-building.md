# Prefabs und Bauen

## Aktueller Placement-Lifecycle

`PrefabPlacementService` besitzt den Civ-Baustellen-Placement-Lifecycle.

Während der Mausbewegung erzeugt oder verschiebt Civ eine native `PersistentPrefabPreview`-Entität am validierten Civ-Anker. Linksklick übernimmt diese Preview als Baustelle; Rechtsklick entfernt sie.

Der Civ-Commit-Pfad verwendet dafür nicht mehr `BlockSelection.place`. Auch `PrefabPasteEvent` ist nicht Teil dieses Commit-Pfads.

## Prefabs aus Asset Packs laden

Die in Hytale 0.6.8 gepinnte `PrefabStore`-API unterscheidet zwischen dem normalen Asset-Prefab-Pfad und einer Suche über alle geladenen Asset Packs. `getAssetPrefab(key)` löst den Key nur gegen `getAssetPrefabsPath()` auf. `getAssetPrefabFromAnyPack(key)` verwendet dagegen `findAssetPrefabPath(key)`, iteriert dabei über `AssetModule.get().getAssetPacks()` und lädt den ersten gefundenen Pfad.

Für JSON-Prefabs ist dabei wichtig: `findAssetPrefabPath(key)` ergänzt **nicht** automatisch `.prefab.json`. Es prüft zuerst den übergebenen relativen Pfad exakt und danach nur die daraus abgeleitete `.lpf`-Variante. Ein JSON-Prefab unter `Server/Prefabs/Civilizations/Mine/Mine_Support_01.prefab.json` muss deshalb mit dem vollständigen pack-relativen Key `Civilizations/Mine/Mine_Support_01.prefab.json` an `getAssetPrefabFromAnyPack(...)` übergeben werden. Die browsable-Suche ist davon getrennt und ergänzt bekannte Prefab-Suffixe selbst.

Prefabs, die Civ zur Laufzeit aus dem separaten `hytale-civ-assets`-Pack benötigt, werden deshalb über `getAssetPrefabFromAnyPack(key)` mit einem zur tatsächlichen Datei passenden pack-relativen Key geladen. Wird der Key in keinem geladenen Pack gefunden, gibt diese Methode `null` zurück. Der aufrufende Adapter muss diesen Fall kontrolliert behandeln; ein fehlendes optionales Runtime-Prefab darf keinen World-Thread beenden.

## Anker und Höhe

Für einfache Creator-Prefabs liegt der logische Civ-Bauanker weiterhin einen Block unter dem anvisierten Oberflächenblock. `groundSinkBlocks` wird bei der Erzeugung dieses terrain-relativen Civ-Ankers angewendet.

Unterirdische oder anderweitig vertikal versetzte Gebäude dürfen ihre Oberflächenhöhe zusätzlich über genau ein TriggerVolume mit `civ.type=construction_ground_level` authoren. `PrefabPlacementService` übernimmt dessen lokale Y-Ebene als effektiven Prefab-Anker. Dadurch hängt der Code weder von einer festen Prefab-Höhe noch von einer festen Schachttiefe ab.

Die native `PersistentPrefabPreview` lädt weiterhin den ursprünglichen Prefab-Key. Civ kompensiert deshalb beim Preview-Transform die Differenz zwischen dem im Prefab gespeicherten Anker und dem semantischen `construction_ground_level`. Baustellen-Layer und finales Prefab verwenden dagegen die semantisch vorbereitete `BlockSelection`.

## Semantische Bau-Reihenfolge

Prefabs ohne `construction_ground_level` behalten die bisherige Reihenfolge: belegte Y-Layer werden von unten nach oben gebaut.

Ist `construction_ground_level` vorhanden, wird zweiphasig gebaut:

1. die Ground-Level-Schicht und alle belegten Schichten darüber, aufsteigend;
2. anschließend alle belegten Schichten unterhalb des Ground-Levels, absteigend.

Damit kann beispielsweise eine Mine zuerst ihr sichtbares Obergebäude fertigstellen und danach den Schacht nach unten materialisieren. Leere Y-Schichten erzeugen keinen zusätzlichen Bauschritt.

## Reservierungsfläche und `building_bounds`

Wenn ein Prefab mindestens ein TriggerVolume mit `civ.type=building_bounds` enthält, leitet Civ den horizontalen Placement-Footprint aus der Vereinigung dieser Bounds ab. Die Reservierung hängt dadurch nicht von den aktuell enthaltenen Blockkoordinaten ab. Mehrere `building_bounds` sind zulässig.

Für upgradebare Gebäude gilt als Authoring-Regel: Bereits das erste Level muss den maximal vorgesehenen horizontalen Ausbau-Footprint reservieren, damit spätere Upgrades nicht mit zwischenzeitlich daneben gebauten Civ-Gebäuden kollidieren. Die sichtbare Struktur darf innerhalb dieser Reservierung anfangs kleiner sein.

Semantische Trigger sind grundsätzlich gegenüber festen Prefab-Maßen zu bevorzugen. Bestehende externe Anschlussstellen sollten bei Upgrades stabil bleiben, sofern sie weiter benutzt werden. Ein bewusst verschobener aktiver Anschluss, etwa ein tiefer gesetzter Minen-Tunnel-Connector, wird dagegen als neue aktive Arbeitsfront behandelt; alte Tunnel können physisch bestehen bleiben, ohne weiter produktiv genutzt zu werden.

## Aktuelle Validierungsgrenze

Im derzeitigen Preview-Spike wird die frühere blockweise Kollisions-/Terrainprüfung bewusst nicht vor dem Preview-Spawn ausgeführt. Diese Prüfung stammte aus dem Sofort-Paste-Pfad und störte die isolierte Verifikation von `PersistentPrefabPreview` bei eingesenkten Baustellenankern.

Die Kollisionsregeln müssen für den Baustellen-Lifecycle erneut passend eingeführt und zur Laufzeit verifiziert werden.

## Fertige Gebäude

`BuildingPlacementRegistry` verwaltet weiterhin bereits fertig platzierte Civ-Bauflächen. Eine neue Baustellen-Preview ist noch kein fertiges Gebäude und darf deshalb nicht vorzeitig als fertige `FarmBuilding`-Instanz oder andere spezialisierte Gebäudeinstanz registriert werden.
