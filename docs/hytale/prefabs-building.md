# Prefabs und Bauen

## Aktueller Placement-Lifecycle

`PrefabPlacementService` besitzt den Civ-Baustellen-Placement-Lifecycle.

Während der Mausbewegung erzeugt oder verschiebt Civ eine native `PersistentPrefabPreview`-Entität am validierten Civ-Anker. Linksklick übernimmt diese Preview als Baustelle; Rechtsklick entfernt sie.

Die sichtbaren Baustellen-Layer werden ohne Prefab-Entities materialisiert. Erst nach dem letzten Bauschritt wird die vollständige vorbereitete `BlockSelection` nativ platziert, damit authored Entities und TriggerVolumes entstehen.

Wichtig für Hytale 0.6.8: `BlockSelection.place(...)` fügt normale Prefab-Entities nicht synchron in den `EntityStore` ein. `placeEntity(...)` erzeugt zunächst einen `Ref`, hängt die eigentliche Store-Insertion anschließend per `World.execute(...)` an die World-Queue und gibt den Ref schon vorher an den Entity-Consumer zurück. Ein `isValid()`-Check im unmittelbaren Consumer ist daher zu früh. Civ sammelt diese Refs zunächst nur und liest UUIDs erst in einem eigenen Finalize-Schritt hinter Hytales Insertions in derselben World-Queue. Prefab-TriggerVolumes folgen dagegen einem anderen nativen Pfad: Hytales `TriggerVolumePasteHandler` registriert sie synchron während `BlockSelection.place(...)` im `TriggerVolumeManager` und cancelt ihre normale ECS-Insertion. Civ erfasst deshalb die exakt in diesem Paste entstandenen TriggerVolume-IDs über einen unmittelbaren Manager-Diff direkt um den nativen `place(...)`-Aufruf; sie müssen nicht auf die spätere Entity-Queue warten.

Das fertige Civ-Gebäude wird erst nach dieser nativen Finalisierung registriert. Bei einem Upgrade bleibt die alte Phase bis dahin registriert; erst nachdem die neue Phase gültige authored Bounds geliefert und unter derselben Building-ID registriert wurde, entfernt Civ die alten TriggerVolumes und Prefab-Entities.

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

## Minenraum-Prefabs

Layer 6 verwendet die bestehende native Prefab-Grenze auch für unterirdische Räume, behandelt einen Minenraum aber nicht als eigenständiges Civ-Gebäude. Es gibt deshalb keine zusätzliche Building-ID, Spielerplatzierung, Baustellen-Preview oder Gebäude-Trigger-Lifecycle pro Raum.

Die drei V1-Testprefabs werden über `PrefabStore.getAssetPrefabFromAnyPack(...)` geladen. Civ rotiert die geladene `BlockSelection` am authored Anchor mit der bereits verifizierten 90-Grad-Orientierungsabbildung und platziert einzelne belegte Y-Layer als kleine `BUILD_ROOM`-Abschnitte über `BlockSelection.placeNoReturn(...)`. Diese Nutzung wurde erneut gegen die gepinnte `HytaleServer.jar` verifiziert. Die Prefabs enthalten bewusst nur einfache Blöcke; spätere visuelle Varianten können ausgetauscht werden, ohne den Raumplaner zu ändern.

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

Die Mine besitzt im Asset Pack drei authored Phasen: `Civilizations/Mine/Mine_01`, `Mine_02` und `Mine_03`. Im aktuellen Debug-Baumenü sind sie direkt als `Mine 1 – Kupfer`, `Mine 2 – Eisen` und `Mine 3 – Gold` auswählbar. Das ist ausdrücklich Entwicklungszugriff; ein späteres Progressionssystem soll direkte höhere Phasen aus dem normalen Spieler-Baufluss entfernen.

Ein regulärer Ausbau lädt die nächste Phase als normale Civ-Baustelle und übernimmt Anker und `BuildingOrientation` der bestehenden Mine statt eine neue Spielerplatzierung zu starten.

Für einen Ausbau misst `PrefabPlacementService` den Footprint der Zielphase am vorhandenen Transform neu. Der Terrain-Snapshot wird dabei kumulativ erweitert: Alle bereits gespeicherten Positionen behalten unverändert ihren Weltzustand von vor Phase 1; nur Positionen, die eine spätere Phase erstmals berührt, werden zusätzlich mit ihrem unmittelbar vorherigen Weltzustand aufgenommen. Ein Abriss von Phase 2 oder 3 kann dadurch den gesamten von allen Phasen veränderten Bereich auf den Zustand vor der jeweils ersten Veränderung zurücksetzen.

Der neue Terrain-Snapshot speichert neben der Block-ID auch Rotation, Filler, Support-Wert sowie Fluid-ID und Fluid-Level. Ältere persistierte Gebäude ohne diese Daten werden rückwärtskompatibel mit den früheren Block-IDs und neutralen Zusatzwerten geladen. Block-Component-Holder werden derzeit nicht eigenständig serialisiert; komplexe mod-/container-spezifische Blockkomponenten bleiben deshalb eine bekannte Grenze.

Die fachliche Building-ID wird bei einem Upgrade nicht ersetzt. Die Construction-Pipeline materialisiert die Zielphase wie eine normale Baustelle; bei der Fertigstellung ersetzt `BuildingPlacementRegistry` die bestehende Building-Instanz unter derselben ID durch die nächste Phase. Persistente Arbeitsplatzreferenzen bleiben dadurch stabil. Miner lösen zuerst diese Building-ID auf und verwenden anschließend die aktuell an dieser Building-Instanz registrierten semantischen Volumes; neue Volume-IDs einer höheren Phase sind deshalb zulässig, solange die erforderlichen Tags weiter vorhanden sind.

Beim erfolgreichen Phasenwechsel werden die semantischen TriggerVolumes der alten Phase über ihre gespeicherten IDs gezielt deregistriert. Die neue Phase liefert anschließend die aktive Menge an `workplace_access`, `mine_tunnel_connector`, `building_bounds` und weiteren semantischen Markern. Es werden nicht pauschal fremde TriggerVolumes im räumlichen Bereich gelöscht.

## Reservierungsfläche und `building_bounds`

Wenn ein Prefab mindestens ein TriggerVolume mit `civ.type=building_bounds` enthält, leitet Civ den horizontalen Placement-Footprint aus der Vereinigung dieser Bounds ab. Die Reservierung hängt dadurch nicht von den aktuell enthaltenen Blockkoordinaten ab. Mehrere `building_bounds` sind zulässig.

Aktive Baustellen besitzen bereits vor Fertigstellung einen reservierten `PlacementFootprint`. Diese Reservation gilt als temporärer Civ-Schutzbereich: Holzfäller dürfen dort keine Bäume als Ziel wählen oder fällen, und direkte Spieler-Blockänderungen werden ebenfalls blockiert. Dafür werden keine vorzeitig als „fertig“ registrierten Arbeitsplatz-TriggerVolumes benötigt.

Für upgradebare Gebäude gilt als Authoring-Regel: Bereits das erste Level soll den maximal vorgesehenen horizontalen Ausbau-Footprint reservieren, wenn spätere Nachbarbebauung dauerhaft ausgeschlossen werden soll. Beim aktiven Upgrade wird zusätzlich der tatsächlich gemessene Footprint der Zielphase als Baustelle reserviert.

Semantische Trigger sind grundsätzlich gegenüber festen Prefab-Maßen zu bevorzugen. Bestehende externe Anschlussstellen sollten bei Upgrades stabil bleiben, sofern sie weiter benutzt werden. Ein bewusst verschobener aktiver Anschluss, etwa ein tiefer gesetzter Minen-Tunnel-Connector, wird dagegen als neue aktive Arbeitsfront behandelt; alte Tunnel können physisch bestehen bleiben, ohne weiter produktiv genutzt zu werden.

## Upgrade-Sicherheit für Bewohner

Beim Start eines Minen-Upgrades werden die aktuell geladenen Bewohner mit passender persistenter Arbeitsplatz-ID über Hytales native `Teleport`-ECS-Komponente aus dem Ziel-Footprint evakuiert. Vorher entfernt Civ ihre aktuellen manuellen und nativen Bewegungsziele. Der sichere Punkt wird bevorzugt aus dem authored `workplace_access` der bestehenden Mine in Außenrichtung abgeleitet. Civ läuft entlang dieser Richtung weiter, bis der Punkt sicher außerhalb des gemessenen Footprints der Zielphase liegt, und fügt anschließend Sicherheitsabstand hinzu. Mehrere Arbeiter werden quer zur ermittelten Außenrichtung verteilt; jeder finale Zielpunkt wird nochmals aus dem Ziel-Footprint herausgeschoben. Fehlt ein nutzbarer Marker, gibt es einen Footprint-/Bounds-basierten Fallback.

Während der Ausbau läuft, bleibt die vorhandene Building-Instanz für Picking und Gebäude-UI sichtbar. Normale Gameplay-Abfragen nach dieser Building-ID behandeln sie jedoch als nicht verfügbar. Berufsadapter wie der Minenarbeiter erhalten dadurch kein benutzbares Arbeitsplatzgebäude und erzeugen keine autonomen Bewegungsziele zurück in den Baukörper. Das ist eine Civ-Gameplay-Sperre auf der bestehenden Hytale-Navigation, keine zweite Wegfindung.

Eine separate native Hytale-API, mit der Civ einen beliebigen fertigen Gebäudeinnenraum temporär als allgemeine physische No-Go-Zone für alle Entitäten oder Spieler markieren könnte, ist für die gepinnte Runtime nicht verifiziert. Deshalb wird eine solche Engine-Barriere derzeit nicht behauptet oder künstlich nachgebaut.

## Prefab-Entities und Abriss

Die gepinnte `BlockSelection.place(...)`-API liefert über ihren Entity-Consumer Referenzen auf die beim Prefab-Paste erzeugten normalen Entities. Diese Refs sind im unmittelbaren Callback noch nicht valide, weil Hytale die Store-Insertion erst per `World.execute(...)` queued. Civ wertet sie deshalb erst im nachgelagerten World-Queue-Finalizer aus. Dort wird die `UUIDComponent` gelesen und als Eigentum der jeweiligen Building-Instanz gespeichert. Die UUID-Liste wird zusammen mit den übrigen Gebäude-Metadaten persistiert. Prefab-TriggerVolumes werden nicht über diese UUID-Liste verwaltet: Hytales Paste-Handler registriert sie synchron im `TriggerVolumeManager`, und Civ persistiert ihre Volume-IDs als semantische Marker.

Beim Abriss werden nur diese exakt aufgezeichneten Prefab-Entities entfernt. Bereits vorher in der Welt vorhandene Entities werden weder gelöscht noch restauriert. TriggerVolumes werden separat über ihre gespeicherten Volume-IDs deregistriert. `TriggerVolumeManager.unregister(...)` ist für den von Hytales Paste-Handler registrierten Trigger-Lifecycle der relevante native Cleanup; die normale TriggerVolume-ECS-Insertion wurde beim Paste bereits gecancelt. Dadurch braucht der Abriss keine unsichere Regel wie „alle Entities innerhalb des Gebäude-Bereichs außer Civ-NPCs löschen“.

Terrain wird beim Abriss nicht mehr blockweise über `WorldChunk.setBlock(...)` zurückgeschrieben. Civ erzeugt aus dem gespeicherten Snapshot eine `BlockSelection` und verwendet `placeNoReturn(...)`, damit Hytales eigener Bulk-Placement-Lifecycle für Rotation, Fluids, Block-State-/Container-Cleanup, Heightmaps und Chunk-Updates greift.

Ältere persistierte Gebäude besitzen noch keine aufgezeichnete Prefab-Entity-Liste. Für sie bleibt die Liste leer; der neue Cleanup kann rückwirkend nicht beweisen, welche vorhandenen Entities ursprünglich von diesem Gebäude erzeugt wurden.

## Baustellen-Persistenz

Bestätigte `ConstructionSite`s sind Weltzustand und nicht an die Spielersession gebunden. Persistiert werden die Baustelle selbst, ihr Transform/Footprint, der Terrain-Snapshot, bereits abgeschlossene Bau-Layer sowie bei Upgrades die bestehende Building-ID und Zielphase. Ein Disconnect entfernt nur unbestätigte Placement-/Ghost-Sessiondaten.

Beim World-Start wird der Baustellenzustand für genau diese Welt neu aus der Hytale-Ressource aufgebaut. Reservierungen werden wiederhergestellt, Upgrade-Locks erneut gesetzt und Bauarbeiter können die Baustelle ab dem gespeicherten Layer wieder übernehmen. Der Prefab-Ghost wird dabei absichtlich nicht rekonstruiert; nach dem bestätigten Linksklick ist die persistente `ConstructionSite` die autoritative Wahrheit.

Der Baufortschritt gehört der Baustelle, nicht dem ausführenden NPC. Ein Bauarbeiterwechsel oder neu erzeugter Worker-Runtime-State darf deshalb nicht auf Layer 0 zurücksetzen. Persistenz-I/O wird an echte Zustandsänderungen gekoppelt: neue Baustelle, abgeschlossener Layer, Abschluss/Abbruch; es gibt keinen hochfrequenten Save-Tick.

## Aktuelle Validierungsgrenze

Während der Ghost bewegt wird, verwendet Civ einen leichten Footprint-Probe und prüft Kollisionen gegen fertige Gebäude sowie aktive Baustellen ereignisgesteuert bei relevanten Mauszieländerungen. Bei einer Kollision zeigt Civ clientlokal die geplante und die blockierende Boundary. Die vollständige `PrefabPlacementService.validatePlacement(...)`-Prüfung einschließlich Terrain-Snapshot läuft erst beim bestätigenden Linksklick; damit wird kein teurer vollständiger Welt-/Snapshot-Scan pro Mouse-/Engine-Tick erzeugt.

Die vier Orientation-Transformationen sind automatisiert gegen Core/Simulation geprüft; die sichtbare Player-Rotation selbst ist noch nicht als In-Game-UX aktiviert und daher noch nicht runtime-verifiziert.

Die queued Finalisierung normaler Prefab-Entities, die synchrone TriggerVolume-Erfassung, die sichtbare Ersetzung `Mine_01 -> Mine_02 -> Mine_03`, der genaue Evakuierungspunkt, der native Abriss-Restore und die persistente Wiederaufnahme einer aktiven ConstructionSite sind fokussiert im echten Client zu prüfen. CI prüft Compile, Tests, Build und den Bare-Hytale-Server-Probe, ersetzt aber keinen Client-Runtime-Test.

## Fertige Gebäude

`BuildingPlacementRegistry` verwaltet weiterhin bereits fertig platzierte Civ-Bauflächen. Eine neue Baustellen-Preview ist noch kein fertiges Gebäude und darf deshalb nicht vorzeitig als fertige `FarmBuilding`-Instanz oder andere spezialisierte Gebäudeinstanz registriert werden.

### Demolition cleanup invariant

A successful Civ demolition is terminal for that building instance. After native terrain/entity/semantic-volume removal succeeds, Civ must evacuate assigned workers that could still be inside (all miners, because their tunnel extends beyond `building_bounds`), clear workplace assignments and building-specific farm/field/mine runtime+persistence, remove the building registry/persistence entry, clear viewer selection/HUD state, remove any related stale upgrade ConstructionSite state, and release orphaned overlapping construction reservations. Live unrelated ConstructionSites must not be unlocked by this defensive cleanup. Upgrade completion itself must explicitly release the ConstructionSite reservation because the upgraded building keeps its stable building UUID.
