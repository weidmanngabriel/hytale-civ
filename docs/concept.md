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

Claims and movement targets are runtime test state only and reset with the plugin/server. There is not yet a persistent inhabitant identity, custom Civ NPC role, visual selection marker, drag-box selection or general economy. A minimal Farm building/production slice now exists as the first concrete building feature.

Do not introduce speculative interfaces until a concrete feature needs them.


## Farm vertical slice

/civfarm arms placement of the first Farm prefab while RTS test mode is active. The next right click places the Farm at that prefab anchor. Doorways are defined independently by one or more creator-visible entrance markers stored inside the prefab.

A player can then select exactly one claimed Civ NPC and right click any marked Farm doorway. The NPC is marked as a Farmer and assigned to that Farm. If the prefab contains several entrances, the current prototype uses the entrance nearest to the NPC.

The prototype loop is intentionally narrow:

1. Farmer walks to the Farm entrance.
2. Reaching the entrance means the Farmer is logically inside.
3. The Farmer works inside for five seconds.
4. The Farm gains one local wheat.
5. The Farmer walks two blocks outside.
6. If the Farm has fewer than ten wheat, the Farmer returns and repeats.
7. At ten wheat the Farmer remains outside and production stops.

Travel time is additional to the five seconds of active production time. Wheat is currently a local integer inventory on the Farm only; there are no physical wheat items, input crops, carriers or warehouse delivery yet.
