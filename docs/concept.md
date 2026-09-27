# Product concept

Hytale Civ is planned as a Hytale strategy/simulation plugin where individual inhabitants, local inventories, production and logistics form the simulation core while Hytale supplies the world, entities, input and presentation.

## Future direction

Later milestones may include persistent Civ inhabitants, full route planning, building placement, inhabitants with jobs and needs, physical goods, local inventories, production chains, logistics and deterministic golden-scenario tests.

## Current scope

The current product milestone is a controllable-NPC RTS validation spike in addition to the original plugin smoke test.

`/civtest` proves that the plugin is loaded.

`/civrtstest` toggles a fixed angled RTS-style Custom camera with a visible cursor. It is not Spectator mode.

`/civclaim` arms the next left click. Clicking an existing `NPCEntity` then toggles whether that NPC is a temporary Civ test unit.

While RTS mode is active:

- left-clicking a claimed Civ unit makes it the only selected person;
- left-clicking empty world space clears the selection;
- unclaimed entities cannot become the Civ selection;
- pressing Hytale's standard Use action (default F) opens the action menu for the selected person;
- the first implemented action is assigning the Woodcutter profession;
- right-clicking a world block still gives the selected Civ unit a direct movement target.

The current Custom camera does not switch the player into Spectator. Hiding only the local player model is not part of this slice because no verified native self-hide mechanism has been established yet.

Movement uses the claimed NPC's existing Hytale role and motion controller. Claims, selection, profession and work state reset with the plugin/server.

## Woodcutter vertical slice

A selected claimed Civ NPC can be assigned the Woodcutter profession from the F action menu.

The first loop is intentionally focused on the visible world interaction:

1. The Woodcutter searches nearby for the closest Hytale tree base.
2. It walks to an open block beside the trunk.
3. It performs a short chopping work phase.
4. The trunk base is broken through Hytale's native block-harvest path.
5. Hytale remains responsible for normal drops, break events, support changes and falling-block behavior.
6. The Woodcutter searches for the next nearby tree and repeats.

There is no work-area selection, carrying, warehouse delivery or persistent job assignment yet.

## Farm vertical slice

/civfarm arms placement of the first Farm prefab while RTS test mode is active. The next right click places the Farm at that prefab anchor. Doorways are defined independently by one or more creator-visible entrance markers stored inside the prefab.

A player can then select one claimed Civ NPC and right click any marked Farm doorway. The NPC is marked as a Farmer and assigned to that Farm. If the prefab contains several entrances, the current prototype uses the entrance nearest to the NPC.

The prototype loop is intentionally narrow:

1. Farmer walks to the Farm entrance.
2. Reaching the entrance means the Farmer is logically inside.
3. The Farmer works inside for five seconds.
4. The Farm gains one local wheat.
5. The Farmer walks two blocks outside.
6. If the Farm has fewer than ten wheat, the Farmer returns and repeats.
7. At ten wheat the Farmer remains outside and production stops.

Travel time is additional to the five seconds of active production time. Wheat is currently a local integer inventory on the Farm only; there are no physical wheat items, input crops, carriers or warehouse delivery yet.
