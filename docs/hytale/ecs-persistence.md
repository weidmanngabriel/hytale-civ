# ECS und Persistenz

## Civ-Bewohnerdaten

`CivInhabitantData` ist eine serialisierbare Hytale-ECS-Komponente auf Civ-Bewohner-Entitäten. Sie speichert unter anderem Geschlecht, den vergebenen Namen, aktiven Beruf, getrennte Berufserfahrung und eine optionale zukünftige Arbeitsplatz-ID.

Die native `UUIDComponent`-UUID bleibt die technische Entity-Identität.

## Sichtbarer Name

`CivInhabitantService` setzt den sichtbaren Namen über Hytales `PersistentDisplayName`, `DisplayNameComponent` und `Nameplate`.

`CivNameplateStatusSystem` ergänzt nur Laufzeitstatus für die Darstellung. Persistenter Name, `DisplayNameComponent` und `CivInhabitantData` werden dadurch nicht ersetzt.

## Persistenz

Persistente Änderungen an Civ-Bewohnerdaten markieren Hytales native `Dirty`-Komponente, damit die Entity-Saving-Pipeline die Änderung berücksichtigt.

Bewohnerzugehörigkeit, Identität, aktiver Beruf und Berufserfahrung hängen damit nicht von einer RTS-Session ab.

Auswahl, aktuelle Bewegungsziele und derzeitige Arbeitsplatzzuweisung sind aktuell laufzeitgebunden.

## Laufzeitidentität

`CivUnitRegistry` identifiziert eine Laufzeitentität über ihren `Store` plus Entitätsindex und behält die ursprüngliche `Ref` zur Validierung. Damit wird nicht auf Java-Objektidentität mehrfach erzeugter `Ref`-Instanzen vertraut und ein veralteter Entity-Slot nicht ohne Prüfung als gültige Civ-Einheit behandelt.

## Projektgrenze

Persistente Gameplay-Daten werden nicht in UI- oder Session-Zuständen dupliziert. Hytale-ECS-Komponenten dienen als persistente Engine-Anbindung; die eigentlichen Civ-Regeln bleiben im Core, soweit sie nicht inhärent ein Engine-Vertrag sind.
