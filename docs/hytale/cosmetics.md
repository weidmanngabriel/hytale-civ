# Native Hytale-Kosmetikregistrierung

Der `VikingAppearanceGenerator` liest die kuratierte Auswahl aus `appearance/viking_appearance_v5.json`, aber die tatsächlich gültigen Kosmetik-IDs kommen zur Laufzeit aus `CosmeticsModule.get().getRegistry()`.

Die gepinnte Hytale-0.7.2-JAR bietet `CosmeticRegistry.getFaces()`, `getEars()` und `getMouths()`. `CosmeticsModule.validateSkin` verlangt für diese drei Felder jeweils eine nicht-null ID, die im entsprechenden nativen Register existiert. Andere Anbauteile werden separat anhand der nativen Register/Varianten/Texturen validiert.

Ein Hytale-Local-Lauf auf `4e0a8e91` scheiterte beim Claim mit `Unknown face: Face_MakeUp_HeavyEyeliner`, da der ältere Civ-Katalog diese nicht mehr gültige ID enthielt. Nach dem Filtern gegen native Register führte eine andere Alters-/Geschlechtskombination zu keiner verfügbaren Katalog-Mund-ID. Der Generator filtert deshalb alle Katalogoptionen gegen die nativen Register und greift bei den drei verpflichtenden Raw-Slots zuerst auf andere noch verfügbare kuratierte Optionen und zuletzt auf eine deterministisch gewählte gültige native ID zurück. Eine reine Prüfung der ursprünglichen Katalogabdeckung reicht bei Server-Upgrades nicht aus.
