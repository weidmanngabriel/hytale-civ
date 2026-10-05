# Prefabs und Bauen

## Aktueller Placement-Lifecycle

`PrefabPlacementService` besitzt den Civ-Baustellen-Placement-Lifecycle.

Während der Mausbewegung erzeugt oder verschiebt Civ eine native `PersistentPrefabPreview`-Entität am validierten Civ-Anker. Linksklick übernimmt diese Preview als Baustelle; Rechtsklick entfernt sie.

Der Civ-Commit-Pfad verwendet dafür nicht mehr `BlockSelection.place`. Auch `PrefabPasteEvent` ist nicht Teil dieses Commit-Pfads.

## Prefab-Rotation

Die gepinnte `HytaleServer.jar` enthält `com.hypixel.hytale.server.core.prefab.PrefabRotation` mit den vier diskreten Werten `ROTATION_0`, `ROTATION_90`, `ROTATION_180` und `ROTATION_270`. Verifiziert sind außerdem `getX(x,z)`, `getZ(x,z)`, `getYaw()` und die Rotation von Block-Rotationswerten. `BlockSelection.rotate(Axis.Y, degrees, pivot)` rotiert Blöcke, Entities und Fluids um einen Pivot; `PersistentPrefabPreview.spawn(...)` akzeptiert separat eine `Rotation3f` für die Preview-Entity. `TransformComponent` erlaubt zusätzlich `setRotation(...)`, sodass eine bereits existierende Preview beim Verschieben dieselbe Orientation beibehalten kann.

Civ hält diese Hytale-Klasse aus dem Core heraus. `BuildingOrientation` ist die Hytale-unabhängige Orientierung, `HytalePrefabOrientation` bildet sie am Adapterrand auf `PrefabRotation` ab. Weil Hytales `ROTATION_270` lokale `(x,z)`-Offsets als `(-z,x)` transformiert, entspricht dieser Wert Civs Orientierung `EAST`; entsprechend gilt `NORTH -> ROTATION_0`, `EAST -> ROTATION_270`, `SOUTH -> ROTATION_180`, `WEST -> ROTATION_90`.

`PlacementCandidate` trägt die Orientation als Teil des Placement-Transforms. Preview, Baustellen-Layer und finales Prefab verwenden dieselbe Orientation. Die vorbereitete `BlockSelection` wird explizit um ihren effektiven Prefab-Anker rotiert, damit Blöcke und authored Entities/Trigger gemeinsam transformiert werden.

Die Orientation wird in der Civ-Gebäudepersistenz gespeichert. Ältere Datensätze ohne Orientation werden rückwärtskompatibel als `NORTH` interpretiert; dadurch ändern bestehende Welten ihre Gebäudeausrichtung nicht beim Laden.

Der aktuelle Spieler-Placement-Flow bietet noch keine Rotationsbedienung an. Neue Placements entstehen deshalb weiterhin als `NORTH`, bis eine sichtbare Rotate-UI separat eingeführt und in Hytale getestet wird.

## Prefabs aus Asset Packs laden

Die in Hytale 0.6.8 gepinnte `PrefabStore`-API unterscheidet zwischen dem normalen Asset-Prefab-Pfad und einer Suche über alle geladenen Asset Packs. `getAssetPrefab(key)` löst den Key nur gegen `getAssetPrefabsPath()` auf. `getAssetPrefabFromAnyPack(key)` verwendet dagegen `findAssetPrefabPath(key)`, iteriert dabei über `AssetModule.get().getAssetPacks()` und lädt den ersten gefundenen Pfad.

Für JSON-Prefabs ist dabei wichtig: `findAssetPrefabPath(key)` ergänzt **nicht** automatisch `.prefab.json`. Es prüft zuerst den übergebenen relativen Pfad exakt und danach nur die daraus abgeleitete `.lpf`-Variante. Ein JSON-Prefab unter `Server/Prefabs/Civilizations/Mine/Mine_Support_01.prefab.json` muss deshalb mit dem vollständigen pack-relativen Key `Civilizations/Mine/Mine_Support_01.prefab.json` an `getAssetPrefabFromAnyPack(...)` übergeben werden. Die browsable-Suche ist davon getrennt und ergänzt bekannte Prefab-Suffixe selbst.

Prefabs, die Civ zur Laufzeit aus dem separaten `hytale-civ-assets`-Pack benötigt, werden deshalb über `getAssetPrefabFromAnyPack(key)` mit einem zur tatsächlichen Datei passenden pack-relativen Key geladen. Wird der Key in keinem geladenen Pack gefunden, gibt diese Methode `null` zurück. Der aufrufende Adapter muss diesen Fall kontrolliert behandeln; ein fehlendes optionales Runtime-Prefab darf keinen World-Thread beenden.

Bei der späteren Weltabfrage darf Civ außerdem nicht davon ausgehen, dass die Schreibweise einer Block-ID exakt der Schreibweise im Prefab-JSON entspricht. Im Minen-Runtime-Test wurde der Querbalken aus `Wood_Fir_Trunk` als `wood_fir_trunk` beobachtet. Vergleiche zwischen erwarteten Prefab-Blocktypen und `BlockType.getId()` müssen deshalb für denselben Asset-Namen gegenüber Groß-/Kleinschreibung tolerant sein. Das ist nur eine ID-Normalisierung; unterschiedliche Blocktypen dürfen dadurch nicht zusammengefasst werden.

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

## Gebäudephasen und Upgrade-Transform

Die Mine besitzt im Asset Pack drei authored Phasen: `Civilizations/Mine/Mine_01`, `Mine_02` und `Mine_03`. Ein Neubau verwendet immer `Mine_01`. Ein Ausbau lädt die nächste Phase als normale Civ-Baustelle, übernimmt aber Anker und `BuildingOrientation` der bestehenden Mine statt eine neue Spielerplatzierung zu starten.

Für einen Ausbau misst `PrefabPlacementService` den Footprint der Zielphase am vorhandenen Transform neu. Der Terrain-Snapshot bleibt dagegen bewusst der Snapshot der Erstplatzierung aus Phase 1. Dadurch kann ein späterer Abriss auch nach mehreren Upgrades weiterhin den Weltzustand vor dem ursprünglichen Minenbau wiederherstellen.

Die fachliche Building-ID wird bei einem Upgrade nicht ersetzt. Die Construction-Pipeline materialisiert die Zielphase wie eine normale Baustelle; bei der Fertigstellung ersetzt `BuildingPlacementRegistry` die bestehende Building-Instanz unter derselben ID durch die nächste Phase. Persistente Arbeitsplatzreferenzen bleiben dadurch stabil.

## Reservierungsfläche und `building_bounds`

Wenn ein Prefab mindestens ein TriggerVolume mit `civ.type=building_bounds` enthält, leitet Civ den horizontalen Placement-Footprint aus der Vereinigung dieser Bounds ab. Die Reservierung hängt dadurch nicht von den aktuell enthaltenen Blockkoordinaten ab. Mehrere `building_bounds` sind zulässig.

Für upgradebare Gebäude gilt als Authoring-Regel: Bereits das erste Level muss den maximal vorgesehenen horizontalen Ausbau-Footprint reservieren, damit spätere Upgrades nicht mit zwischenzeitlich daneben gebauten Civ-Gebäuden kollidieren. Die sichtbare Struktur darf innerhalb dieser Reservierung anfangs kleiner sein.

Semantische Trigger sind grundsätzlich gegenüber festen Prefab-Maßen zu bevorzugen. Bestehende externe Anschlussstellen sollten bei Upgrades stabil bleiben, sofern sie weiter benutzt werden. Ein bewusst verschobener aktiver Anschluss, etwa ein tiefer gesetzter Minen-Tunnel-Connector, wird dagegen als neue aktive Arbeitsfront behandelt; alte Tunnel können physisch bestehen bleiben, ohne weiter produktiv genutzt zu werden.

## Upgrade-Sicherheit für Bewohner

Beim Start eines Minen-Upgrades werden die aktuell geladenen Bewohner mit passender persistenter Arbeitsplatz-ID über Hytales native `Teleport`-ECS-Komponente an einen Punkt außerhalb des Minenzugangs gesetzt. Vorher entfernt Civ ihre aktuellen manuellen und nativen Bewegungsziele. Der sichere Punkt wird bevorzugt aus dem authored `workplace_access` der Mine nach außen abgeleitet; fehlt ein nutzbarer Marker, gibt es einen Bounds-basierten Fallback.

Während der Ausbau läuft, bleibt die vorhandene Building-Instanz für Picking und Gebäude-UI sichtbar. Normale Gameplay-Abfragen nach dieser Building-ID behandeln sie jedoch als nicht verfügbar. Berufsadapter wie der Minenarbeiter erhalten dadurch kein benutzbares Arbeitsplatzgebäude und erzeugen keine autonomen Bewegungsziele zurück in den Baukörper. Das ist eine Civ-Gameplay-Sperre auf der bestehenden Hytale-Navigation, keine zweite Wegfindung.

Eine separate native Hytale-API, mit der Civ einen beliebigen fertigen Gebäudeinnenraum temporär als allgemeine physische No-Go-Zone für alle Entitäten oder Spieler markieren könnte, ist für die gepinnte Runtime nicht verifiziert. Deshalb wird eine solche Engine-Barriere derzeit nicht behauptet oder künstlich nachgebaut.

## Aktuelle Validierungsgrenze

Im derzeitigen Preview-Spike wird die frühere blockweise Kollisions-/Terrainprüfung bewusst nicht vor dem Preview-Spawn ausgeführt. Diese Prüfung stammte aus dem Sofort-Paste-Pfad und störte die isolierte Verifikation von `PersistentPrefabPreview` bei eingesenkten Baustellenankern.

Die Kollisionsregeln müssen für den Baustellen-Lifecycle erneut passend eingeführt und zur Laufzeit verifiziert werden. Die vier Orientation-Transformationen sind automatisiert gegen Core/Simulation geprüft; die sichtbare Player-Rotation selbst ist noch nicht als In-Game-UX aktiviert und daher noch nicht runtime-verifiziert.

Die sichtbare Ersetzung `Mine_01 -> Mine_02 -> Mine_03`, der genaue Evakuierungspunkt und die Upgrade-UI sind noch fokussiert im echten Client zu prüfen. Ebenso ist zu beobachten, ob beim finalen nativen Prefab-Placement alte Trigger-Volume-Instanzen einer vorherigen Mine-Phase dauerhaft in der Welt verbleiben; dafür liegt noch kein fokussierter Runtime-Befund vor.

## Fertige Gebäude

`BuildingPlacementRegistry` verwaltet weiterhin bereits fertig platzierte Civ-Bauflächen. Eine neue Baustellen-Preview ist noch kein fertiges Gebäude und darf deshalb nicht vorzeitig als fertige `FarmBuilding`-Instanz oder andere spezialisierte Gebäudeinstanz registriert werden.
