# Product concept

Hytale Civ is planned as a Hytale strategy/simulation plugin where individual inhabitants, local inventories, production and logistics form the simulation core while Hytale supplies the world, entities, input and presentation.

## Future direction

Later milestones may include persistent Civ inhabitants, full route planning, building placement, inhabitants with jobs and needs, physical goods, local inventories, production chains, logistics and deterministic golden-scenario tests.

## Current scope

The current product milestone is a controllable-NPC RTS validation spike in addition to the original plugin smoke test.

`/civtest` proves that the plugin is loaded.

`/civrtstest` toggles an experimental angled RTS-style camera with a visible cursor.

`/civclaim` arms the next left click. Clicking an existing `NPCEntity` then toggles whether that NPC is a temporary Civ test unit. This claim is explicit: animals, monsters and other NPCs are not Civ units just because they use Hytale's NPC system.

While RTS mode is active:

- left-clicking a claimed Civ unit toggles it in or out of the logical multi-selection;
- left-clicking empty world space clears the selection;
- unclaimed entities are not added to the Civ selection;
- right-clicking a world block gives every selected Civ unit a nearby movement target;
- multiple selected units receive slightly offset targets so they do not all converge on exactly the same point.

Movement uses the claimed NPC's existing Hytale role and motion controller. The spike validates locomotion and collision-aware steering without teleporting the NPC. It does not yet guarantee route finding around arbitrary obstacles.

Claims and movement targets are runtime test state only and reset with the plugin/server. There is not yet a persistent inhabitant identity, custom Civ NPC role, visual selection marker, drag-box selection, building system, economy or simulation.

Do not introduce speculative interfaces until a concrete feature needs them.
