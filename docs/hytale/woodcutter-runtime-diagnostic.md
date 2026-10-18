# Hytale Local: Holzfäller-Spawn – Diagnose und Nachweis

Am 18. Oktober 2026 wurde der Fehler im isolierten Szenario `woodcutter` unter Hytale 0.7.2 untersucht. Der Kopflose-Test lud Chunk und Y-Sektion zuvor als `NonTicking`; die native Spawn-Prüfung ergab `FAIL_INVALID_POSITION`. Mit Hytales Ladeoption `4` werden beide aktiv geladen. Da kein Spieler die Testwelt aktiv hält, deaktiviert nur dieses Szenario das spätere Chunk-Unloading. Der native Wikinger-Kosmetikkatalog wird für die Pflichtfelder Gesicht, Ohren und Mund gegen die tatsächlich registrierten IDs abgeglichen.

**Bestätigte Engine-Verifikation:** [Hytale Local #39106588274](https://github.com/weidmanngabriel/hytale-civ/actions/runs/39106588274), Commit `97c563eed3d0612c0d901632c94213632433074d`, Szenario `woodcutter`, Ergebnis `CIV_WOODCUTTER_RUNTIME_PASS`, nach 265 Ticks. Im Log: `chunkTicking=true`, `sectionTicking=true`, `validation=TEST_OK`, `returnedRef=true`, `validRef=true`; Baumfällung bei Tick 249 (Woods-Blöcke 54 auf 36, Bewegung 4,21 Blöcke); erneute Arbeitsaufnahme bei Tick 265, insgesamt 4,27 Blöcke Bewegung. Keine Aussage über Holztransport zur Hütte, der weiterhin unimplementiert ist.

Technische Details: [NPCs und Navigation](npc-navigation.md), [Kosmetik und NPC-Aussehen](cosmetics.md). Der gewöhnliche CI-Build beweist keine Engine-Navigation oder Spawn-Lebenszyklen.
