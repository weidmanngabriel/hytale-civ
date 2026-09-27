# Domain

This file is the authoritative location for verified Hytale Civ domain terms, rules, invariants, value ranges and state transitions.

A rule belongs here only when it is intentionally part of the game model and supported by a current product decision, reliable observation or explicit user instruction. Existing code alone is not sufficient evidence that a behavior is a domain rule.

Unknown behavior stays unknown until it is decided or verified. Do not turn implementation accidents, temporary debug behavior or Hytale engine constraints into permanent domain rules without an explicit reason.

## Current domain status

The project is still in an engine-validation milestone. Most planned simulation domains such as persistent inhabitants, needs, general inventories, logistics, families and economy do not yet have implemented domain rules. Farm and Woodcutter are the first implemented job slices.

The current NPC claim and movement state is deliberately temporary integration-test state, not persistent Civ ownership or an inhabitant lifecycle.

## Planned domain areas

As concrete features are implemented, keep their verified rules here under focused sections. Expected areas include:

- inhabitants and identity;
- professions, qualification and experience;
- needs and autonomous behavior;
- households and families;
- buildings and construction;
- local inventories and physical goods;
- production and recipes;
- logistics and transport;
- technology and unlock progression;
- diplomacy and combat;
- missions and scenario state.

Do not predefine their detailed rules before the corresponding product behavior is decided.

## Woodcutter

- Woodcutter is a profession that can currently be assigned to one selected claimed Civ NPC.
- After assignment, the Woodcutter autonomously seeks a nearby tree rather than waiting for a building assignment.
- The Woodcutter walks beside the tree before working on it.
- Felling must use Hytale's native block harvesting and physics behavior rather than deleting a tree through Civ-only simulation state.
- After a tree is felled, the Woodcutter searches for another nearby tree.
- Work areas, carrying wood, storage delivery, tools, experience and persistence are not domain rules yet.

## Building placement

- RTS building placement state belongs to the individual player; one player's preview or cancellation must not change another player's placement state.
- A building placement preview is advisory. Shared-world placement is validated again when the player confirms it.
- A building floor is embedded one block into the pointed terrain so its finished floor surface does not sit one full block above the surrounding ground.
- The current placement rules require supported ground across the floor footprint, no liquid or holes in the replaced floor layer, clear required building volume, clear entrances and no overlap with an existing Civ building footprint.
- Every placed building instance must retain the original world blocks replaced by its embedded floor so demolition can restore the prior terrain.
- The retained terrain snapshot follows the lifetime of the placed building. It is runtime-only until building persistence is implemented.

## Farm

The first implemented building domain is intentionally specific rather than a speculative generic building framework.

- A building prefab must define at least one entrance marker; a Farm may define multiple entrances.
- A Farm has one Farmer slot.
- Assigning a claimed Civ NPC to a Farm marks that NPC with the FARMER profession for the current runtime.
- The current Farm assignment selects the entrance nearest to the assigned Farmer by straight-line world distance. Reaching that entrance transitions the Farmer into the logical WORKING_INSIDE state.
- One wheat is produced after five seconds of active work inside the Farm.
- After every wheat production, the Farmer must leave the building before another production step can begin.
- The exterior exit target is two blocks south of the selected entrance for the current fixed-orientation Farm prefab.
- A Farm stops production at exactly 10 local wheat.
- Travel time is not part of the five-second work timer.
- Farm placement, NPC assignment, profession marking and wheat inventory are runtime-only and are not persisted across a server/plugin restart yet.
