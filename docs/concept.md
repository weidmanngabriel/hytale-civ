# Product concept

Hytale Civ is planned as a Hytale strategy/simulation plugin where individual inhabitants, local inventories, production and logistics form the simulation core while Hytale supplies the world, entities, input and presentation.

## Future direction

Later milestones may include full movement commands, building placement, inhabitants with jobs and needs, physical goods, local inventories, production chains, logistics and deterministic golden-scenario tests.

## Current scope

The current product milestone is an RTS interaction validation spike in addition to the original plugin smoke test.

`/civtest` still proves that the plugin is loaded.

`/civrtstest` toggles an experimental angled RTS-style camera with a visible cursor. While that mode is active:

- left-clicking an entity toggles it in or out of the current logical multi-selection;
- left-clicking empty world space clears the selection;
- right-clicking a world block records that block as the requested movement target and reports the target plus selection count.

The spike deliberately stops before actual entity movement. Hytale NPC spawning requires a valid NPC role asset, and locomotion must be validated against the chosen role/navigation API rather than guessed. The first milestone therefore validates camera, cursor targeting, entity targeting, multi-selection semantics and ground targeting independently.

There is not yet a visual selection marker, drag-box selection, NPC population, building system, economy or simulation.

Do not introduce speculative interfaces until a concrete feature needs them.
