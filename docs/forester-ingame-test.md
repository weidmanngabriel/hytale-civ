# Försterei – Ingame-Abnahme (Phase 1)

Das ist die Bedienungs- und Abnahmeanleitung der echten Hytale-Mod. Die vereinfachten Headless-Szenarien sind separat unter [forester-snapshot-probe.md](forester-snapshot-probe.md) beschrieben.

## Voraussetzungen

- Die Civ-Mod und das passende Asset Pack derselben Version sind installiert.
- Ein freier Bauplatz ist verfügbar; nahegelegene Gras-/Erdoberflächen sind begehbar.
- Mindestens ein beanspruchter Einwohner/NPC ist in der Nähe.

## Schritte

1. Das Civ-Baumenü öffnen und **Försterei** bauen. Die Baureihenfolge abschließen; nur die fertig gebaute Hütte ist nutzbar.
2. In die Gebäudetruhe drei native Hytale-Setzlinge legen, z. B. **Eiche** (1× `Plant_Sapling_Oak`), **Birke** (5× `Plant_Sapling_Birch`) und **Fichte** (20× `Plant_Sapling_Spruce`).
3. Einen zuvor beanspruchten Einwohner in der Nähe der Försterei auswählen. In **Personenaktionen** auf **Förster** klicken. Er sucht automatisch eine freie Försterei innerhalb von 32 Blöcken; es ist keine weitere Rechtsklick-Zuweisung erforderlich.
4. Beobachten, wie er einen geeigneten Platz innerhalb der räumlichen **24-Block-Kugel** um das Gebäude auswählt, zum Pflanzort läuft und dort einen echten Hytale-Setzling platziert. Er kehrt nicht nach jeder Pflanzung zur Hütte zurück.
5. In der echten Gebäudetruhe prüfen, dass die ursprünglichen Mengen unverändert bleiben (1/5/20). Stapelmengen haben keinen Einfluss auf die Wahrscheinlichkeit der Arten: Jede **vorhandene Art** kann unabhängig von ihrer Anzahl ausgewählt werden.
6. Mehrere Pflanzungen beobachten. Der Abstand zu anderen Stämmen/Setzlingen soll mindestens vier horizontale Blöcke betragen. Aufgrund der zufälligen Auswahl muss nicht in jeder kurzen Stichprobe jede Art auftauchen.
7. **Birke** aus der Truhe entfernen und weitere Pflanzungen beobachten. Neue Setzlinge dürfen dann nur noch aus den verbliebenen Arten ausgewählt werden. Die vorhandenen Setzlinge in der Welt bleiben bestehen.
8. Alle gültigen Setzlinge herausnehmen. Der Förster soll mit dem Pflanzen aufhören. Eine Art wieder hineinlegen und prüfen, ob er die Arbeit ohne NPC-Neustart fortsetzt.
9. Den NPC manuell wegbewegen bzw. seine Arbeit unterbrechen und später wieder freigeben. Er soll bestehende Zielreservierungen freigeben und danach geeignete Pflanzziele neu wählen.
10. Das Wachsen der gesetzten Bäume erfolgt **nativ durch Hytale**. Die Mod erzwingt keine eigenen Timings. Spätere Wachstumsstufen und das Fällen durch den Holzfäller separat über längere Beobachtung prüfen; der vollständige Waldkreislauf ist kein Test mit garantiertem Endzustand zu einer festen Tickzahl.

## Diagnose

Bei Problemen die Serverlogs auf `[CivForester] planted=` prüfen: Dort werden die native Block-ID und Pflanzkoordinaten gemeldet. Ist kein Setzling vorhanden, die **richtige Gebäudetruhe** prüfen; das System liest keinen direkten NPC-Itemtransfer.

Die getrennten Hytale-Local-Szenarien heißen `foresterassets` (registrierte Setzlinge und 1/5/20 Inventar), `foresterplant` (native Eiche/Birke/Fichte-Farming-Komponenten) und `foresternpc` (echter NPC, Originalprefab, echte Truhe, Weg und Setzling ohne Verbrauch). Keine dieser Prüfungen garantiert, dass ein Setzling zu einer festgelegten Ticknummer ausgewachsen ist.

## Abgrenzung

Nicht implementiert: direkter Itemtransfer an NPCs, eigenständiges Baumwachstum durch Civ, mehrfache Baumarten im abstrakten Simulator, Baumkronenkollisionen im Headless-Lab. Das Originalweltarchiv `region.civworld.gz` bleibt unverändert; die bisherigen Miner- und Flat-Szenarien werden nicht migriert.
