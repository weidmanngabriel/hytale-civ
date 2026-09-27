# Domain

This file is the authoritative location for verified Hytale Civ domain terms, rules, invariants, value ranges and state transitions.

A rule belongs here only when it is intentionally part of the game model and supported by a current product decision, reliable observation or explicit user instruction. Existing code alone is not sufficient evidence that a behavior is a domain rule.

Unknown behavior stays unknown until it is decided or verified. Do not turn implementation accidents, temporary debug behavior or Hytale engine constraints into permanent domain rules without an explicit reason.

## Current domain status

The project is still in an engine-validation milestone. Most planned simulation domains such as persistent inhabitants, jobs, needs, inventories, goods, production, logistics, buildings, families and economy do not yet have implemented domain rules.

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
