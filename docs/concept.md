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
- right-clicking the currently selected Civ NPC opens that person's action menu;
- the first implemented action is assigning the Woodcutter profession;
- right-clicking a world block still gives the selected Civ unit a direct movement target;
- a persistent menu bar appears on the left with a **Bauen** entry;
- clicking **Bauen** opens a modal building catalog to the right of the bar. The catalog must be closed or a building selected before normal RTS world interaction resumes;
- building entries are ordered alphabetically by display name.

The current Custom camera does not switch the player into Spectator. Hiding only the local player model is not part of this slice because no verified native self-hide mechanism has been established yet.

Direct movement is currently supported by the Civ-owned `Civ_Inhabitant` role. Civ supplies the destination, while that role's native Hytale `ReadPosition`/`Seek` behavior performs pathfinding and walking; Civ does not steer the NPC every tick. Claimed NPCs using unrelated Hytale roles are not given this movement contract. Claims, selection and work state reset with the plugin/server; profession data is stored on the inhabitant entity.

## Woodcutter vertical slice

A selected claimed Civ NPC can be assigned the Woodcutter profession by right-clicking that NPC and choosing the action from its context menu.

The first loop is intentionally focused on the visible world interaction:

1. The Woodcutter searches nearby for the closest Hytale tree base.
2. It walks to an open block beside the trunk.
3. It performs a short chopping work phase.
4. The trunk base is broken through Hytale's native block-harvest path.
5. Hytale remains responsible for normal drops, break events, support changes and falling-block behavior.
6. The Woodcutter searches for the next nearby tree and repeats.

There is no work-area selection, carrying, warehouse delivery or persistent job assignment yet.

## Farm vertical slice

In RTS mode, **Bauen → Farm** closes the building catalog and starts Farm placement. A Farm ghost follows the world position under the cursor. Left click attempts to place it; right click cancels the placement. `/civfarm` remains a debug shortcut into the same placement mode.

The visible floor of the Farm is embedded one block into the pointed terrain rather than being placed on top of it. Placement is accepted only when the footprint is supported, contains no holes or liquids, the building volume and entrances are clear, and the footprint does not overlap another Civ building. The server rechecks these conditions when left click confirms the build; the preview is not authoritative.

Each placed Farm retains the original blocks replaced by its embedded floor. A future demolition action can therefore restore the prior ground instead of leaving a building-shaped hole. This snapshot currently has the same runtime-only lifetime as the placed Farm.

Farm workplace access is defined inside the prefab by one or more native Hytale Trigger Volumes tagged `civ.type=workplace_access` and `civ.building=farm`.

A player can then select one claimed Civ NPC and right click the Farm workplace area. The NPC is marked as a Farmer and assigned to that Farm. If the prefab contains several workplace access volumes, the current prototype uses the one nearest to the NPC.

The prototype loop is intentionally narrow:

1. Farmer walks to the Farm entrance.
2. Reaching the entrance means the Farmer is logically inside.
3. The Farmer works inside for five seconds.
4. The Farm gains one local wheat.
5. The Farmer walks two blocks outside.
6. If the Farm has fewer than ten wheat, the Farmer returns and repeats.
7. At ten wheat the Farmer remains outside and production stops.

Travel time is additional to the five seconds of active production time. Wheat is currently a local integer inventory on the Farm only; there are no physical wheat items, input crops, carriers or warehouse delivery yet.


## In-game wiki

The persistent left RTS menu includes a **?** button below **Bauen**. It opens a modal in-game wiki with four sections: **Berufe**, **Ressourcen**, **Gebäude** and **Tiere**.

The wiki documents implemented Civ behavior only and cross-links related sections. The current entries cover Holzfäller, Bauer, Holz, Weizen and Farm. The animal section explicitly states that no animal has a Civ-specific gameplay role yet; animals are added only when a real Civ system uses them.

Opening the wiki cancels an active Farm placement preview before the page is shown, matching the building catalog's modal interaction model.
