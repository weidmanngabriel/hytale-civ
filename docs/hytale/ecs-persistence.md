# ECS und Persistenz

## Civ-Bewohnerdaten

`CivInhabitantData` ist eine serialisierbare Hytale-ECS-Komponente auf Civ-Bewohner-Entitäten. Sie speichert Geschlecht, den vergebenen Namen, Altersstufe, den fertig aufgelösten `PlayerSkin`, aktiven Beruf, getrennte Berufserfahrung und eine optionale Arbeitsplatz-ID.

Die native `UUIDComponent`-UUID bleibt die technische Entity-Identität.

Die Civ-Identität lebt exakt so lange wie die `CivInhabitantData`-Komponente. Ein `/civclaim` legt sie an; ein erneutes Claim/Release entfernt sie vollständig. Ein späteres Reclaim erzeugt eine neue Person.

## Sichtbarer Name und Appearance

`CivInhabitantService` setzt den sichtbaren Namen über Hytales `PersistentDisplayName`, `DisplayNameComponent` und `Nameplate`.

Für die Darstellung eines Civ-Bewohners wird Hytales natives Player-Modell über `NPCEntity.setAppearance(..., "Player", ...)` verwendet. Der persistierte `PlayerSkin` wird über `PlayerSkinComponent` angewendet. Beim Release wird die native Rollen-Appearance des NPCs wiederhergestellt und `PlayerSkinComponent` entfernt.

Name und Appearance teilen dasselbe persistierte `Gender`: erst wird die Identität erzeugt, danach wird der Appearance-Pool mit genau diesem Geschlecht gefiltert.

`CivNameplateStatusSystem` ergänzt nur Laufzeitstatus für die Darstellung. Persistenter Name, `DisplayNameComponent` und `CivInhabitantData` werden dadurch nicht ersetzt.

## Persistenz

Persistente Änderungen an Civ-Bewohnerdaten markieren Hytales native `Dirty`-Komponente, damit die Entity-Saving-Pipeline die Änderung berücksichtigt.

Bewohnerzugehörigkeit, Identität, Altersstufe, Appearance, aktiver Beruf und Berufserfahrung hängen damit nicht von einer RTS-Session ab.

Auswahl und aktuelle Bewegungs-/Worker-Zustände sind laufzeitgebunden.

## Nativer Entity-Lifecycle statt Polling

`CivInhabitantLifecycleSystem` ist ein Hytale-`RefSystem`, dessen Query nur Entities mit `CivInhabitantData` und `NPCEntity` beobachtet. Es gibt keinen zusätzlichen periodischen Cleanup-Scan.

Verifizierte Hytale-Lifecycle-Gründe aus der gepinnten `HytaleServer.jar`:

- `AddReason.SPAWN` und `AddReason.LOAD`
- `RemoveReason.REMOVE`
- `RemoveReason.UNLOAD`
- `RemoveReason.BUILDER_TOOLS_UNDO`

Beim Laden/Spawnen wird die persistierte Civ-Darstellung wieder angewendet.

Beim Entfernen aus dem aktiven ECS werden nur flüchtige Civ-Zustände und Reservierungen des betroffenen NPCs gelöscht: Activity-/Unit-Cache, Farm-Worker-State, Holzfäller-Baumreservierung und Bauarbeiter-Baustellenreservierung.

`UNLOAD` bedeutet ausdrücklich **nicht**, dass die Civ-Person gelöscht wurde. Persistente `CivInhabitantData` bleibt Hytale-owned und eine bestehende Farmzuweisung wird nicht wegen normalem Chunk-Streaming aufgelöst.

Bei echtem `REMOVE` beziehungsweise Builder-Undo wird zusätzlich die laufzeitgebundene Farmzuweisung freigegeben. Hytales `/entityclean` verwendet intern `CommandBuffer.tryRemoveEntity(..., RemoveReason.REMOVE)` und läuft damit durch denselben Cleanup-Pfad.

## Laufzeitidentität

`CivUnitRegistry` identifiziert eine Laufzeitentität über ihren `Store` plus Entitätsindex und behält die ursprüngliche `Ref` zur Validierung. Damit wird nicht auf Java-Objektidentität mehrfach erzeugter `Ref`-Instanzen vertraut und ein veralteter Entity-Slot nicht ohne Prüfung als gültige Civ-Einheit behandelt.

## Projektgrenze

Persistente Gameplay-Daten werden nicht in UI- oder Session-Zuständen dupliziert. Hytale-ECS-Komponenten dienen als persistente Engine-Anbindung; die eigentlichen Civ-Regeln bleiben im Core, soweit sie nicht inhärent ein Engine-Vertrag sind.

Grundregel: **Civ data must not outlive its owner.** Externe Maps sind nur Laufzeitcache oder Reservierungsindex, nie die persistente Quelle der Wahrheit.
