# Mine Design

Status: canonical design specification for the planned mine overhaul.

This document captures the product and gameplay decisions made in the original mine-design chat. Later implementation chats should treat this file as the source of truth unless a decision is explicitly changed and this document is updated together with the code.

## 1. Design goal

The mine should feel like an autonomously growing underground world structure rather than a single rigid tunnel. NPC miners should create a large, persistent three-dimensional network consisting of a main tunnel, many side tunnels, rooms, natural cave integrations, bridges, supports, lighting, decoration and a rail line through the main tunnel.

The player should have little direct influence on the generated mine beyond issuing NPC-related instructions. Player modifications to generated mine geometry are not part of the mine generator's decision model and should generally be ignored by the mine logic.

Prefer Hytale-native systems wherever they are suitable. Civ should own the gameplay decisions and planning; the Hytale adapter should use native world, navigation, prefab, block, fluid, persistence and rail capabilities where possible. Do not reimplement Hytale systems without a demonstrated need.

## 2. Horizontal footprint and depth

The mine should develop inside an area of roughly 500 x 500 blocks around the mine building.

Treat the boundary as a soft steering constraint, not a hard wall. As a tunnel approaches the outer area, the probability of continuing outward should decrease and the probability of gradually curving back inward should increase. The tunnel should not make artificial hard U-turns merely because it reaches the boundary.

Maximum depth must not be hard-coded as an absolute Y value. Derive it from the current world's lower boundary and keep a safety margin above that boundary. This keeps the design compatible with different world presets and future Hytale world-height changes.

## 3. Main tunnel

The main tunnel is the persistent backbone of the mine.

Target characteristics:

- Width: approximately 6-8 blocks.
- Height: approximately 6-8 blocks.
- Width and height vary gradually through form phases.
- Walls and ceiling receive natural irregular excavation.
- The floor remains controlled enough for safe navigation and infrastructure.
- The main tunnel follows broad, soft curves rather than frequent hard turns.
- Use approximately eight horizontal heading directions as the initial planning model so diagonal and curved paths are possible.
- The centerline may drift slightly sideways inside its general heading.
- The main tunnel trends downward over long distances.

Initial vertical weighting:

- 65-70% downward tendency.
- 25-30% approximately level.
- 5-10% slight upward tendency.

A vertical change is gradual: a form phase may change elevation by roughly one block across several blocks of forward distance rather than becoming a staircase every block.

## 4. Side tunnels and nested branches

Side tunnels are smaller and more irregular than the main tunnel.

Target characteristics:

- Width: approximately 3-5 blocks.
- Height: approximately 3-5 blocks.
- A normal planning segment is approximately 4-12 blocks long.
- Side tunnels may be long overall; there is no small hard maximum total branch length.
- Side tunnels may themselves create further side tunnels.
- Branching should be common, but the probability should decrease at deeper branch levels so the network remains controllable.

Initial horizontal direction weighting for side-tunnel planning segments:

- 50% straight.
- 25% left.
- 25% right.

Initial vertical weighting:

- about 35% upward.
- about 30% level.
- about 35% downward.

The exact weighting is tunable; 40/20/40 is also a plausible later variant if more vertical spread is desirable.

## 5. Form phases

Tunnel geometry should not reroll width, height and offset independently for every block. Instead, tunnels use form phases that last for several blocks and transition gradually.

Example small-tunnel sequence:

```text
3x4
4x4
4x5
5x5
4x5
4x4
```

Example main-tunnel sequence:

```text
6x6
6x7
7x7
7x8
8x8
7x8
7x7
```

Form phases may vary:

- width;
- height;
- lateral offset;
- vertical offset;
- wall/ceiling irregularity profile.

Changes should feel slow and coherent. As an initial rule of thumb, avoid changing width or height by more than roughly one block over a short transition span.

## 6. Lateral and vertical drift

A tunnel that is logically travelling straight does not need to stay on one exact block axis.

Allow slow lateral drift, typically zero or one block of sideways change over several blocks. Avoid per-block left-right jitter.

Vertical drift follows the same principle. A form phase can end one block higher, lower or at the same height as it started. This creates slopes and gentle changes instead of abrupt repeated block steps.

Large-scale direction changes and small-scale drift are separate concepts:

- heading/curve changes alter where the tunnel is going;
- drift adds local natural variation inside that general direction.

## 7. Natural excavation

After defining a safe structural tunnel shape, add natural irregularity to walls and ceiling.

Desired effects include:

- small extra cutouts;
- connected clusters of missing wall/ceiling blocks;
- occasional pockets and recesses;
- locally higher ceilings;
- locally wider sides;
- larger irregularities in some stretches.

Avoid independent random noise on every block. Irregularities should usually be spatially connected so the tunnel looks excavated or cave-like rather than moth-eaten.

The naturalization layer must never compromise the guaranteed navigable corridor.

## 8. Guaranteed navigable corridor

Every generated tunnel phase must preserve a connected, walkable core suitable for Hytale NPC navigation.

This is especially important when combining:

- rising/falling floors;
- lower ceiling variation;
- side drift;
- organic wall/ceiling cutouts;
- supports and decoration.

Generation should validate that consecutive phases remain connected and navigable. If a candidate phase blocks the corridor, reject/regenerate it or terminate that work front.

If a work front later becomes unusable because of an environmental edge case, miners should abandon that front and choose another available work front rather than repeatedly trying an impossible path.

## 9. Branch continuation and endings

Do not use one fixed maximum branch length as the primary ending rule.

Instead, continuation probability should gradually decrease as a branch becomes longer. This should allow a mixture of short branches, ordinary branches and occasional very long branches of 100+ blocks.

A branch can also end because:

- no valid next geometry can be generated after reasonable retries;
- it reaches depth or footprint constraints;
- nearby mine geometry leaves insufficient space;
- a dangerous cave/fluid situation has no safe continuation;
- its continuation roll eventually fails.

## 10. Main-vs-branch work priority

Branching should be frequent, but the main tunnel must continue to grow over the lifetime of the mine.

Use weighted work-front selection rather than a fixed sequence. Side branches can have high probability, while the main tunnel periodically gains increased priority if it has not advanced for a while. Exact weights are tuning parameters and should be adjusted after long-run tests.

The data model should support multiple open work fronts even if the first implementation still keeps miners together at one active work point.

## 11. Spacing and tunnel collisions

Keep several blocks of rock between unrelated tunnel sections and rooms. Larger tunnels and rooms should use larger safety margins.

Random collisions should normally be avoided. However, when two existing passages approach in a geometrically sensible way, an intentional connection/crossing may occasionally be created. This allows the mine network to contain loops rather than being a strict tree.

Exact minimum distances are tuning parameters and should be established in implementation tests.

## 12. Rooms

Rooms are selected by suitable location, minimum spacing and weighting. Do not simply roll every room type independently on every block.

Rooms should primarily be implemented as Hytale prefabs when native prefab placement is suitable. Define rough size envelopes in code, while prefab variants provide the visual design.

Current room rules:

| Room type | Frequency / spacing intent |
| --- | --- |
| Rest / accommodation room | approximately every 80-120 blocks, primarily on the main tunnel |
| Material storage | 25-35% at eligible room opportunities, with about 60-80 blocks spacing |
| Tool / workshop room | 10-15% |
| Ore collection room | 15-25% |
| Large natural chamber | 5-10% |
| Small niche | 20-30% |
| Support / supply station | 10-20% |
| Water / drainage room | 5% |
| Large work hall | 3-6% |

General placement preference:

- Main tunnel: accommodation, material storage, workshop, supply station, large work hall.
- Side tunnels: ore collection rooms, smaller chambers, niches and rarer special rooms.
- Large rooms should not cluster immediately next to each other.

Room sizes are intentionally ranges, not fixed dimensions. Exact prefab sizes will be decided when the prefab set is created.

## 13. Natural caves, water, lava and bridges

Natural caves must be treated as a first-class case rather than an error.

Classify encounters roughly as:

1. Small opening: integrate naturally and continue.
2. Bridgeable cave: find a safe opposite side and bridge across.
3. Large useful cave: integrate it as a natural chamber/node in the mine network.
4. Dangerous/unusable cave: abandon or redirect the work front.

Before creating a bridge, verify:

- a stable opposite landing area exists;
- the span is within the configured bridge range;
- the tunnel can continue beyond the landing;
- the resulting route is navigable;
- fluids do not make the route unsafe.

Initial bridge span target: roughly 12-16 blocks maximum. Be more conservative over lava.

Use bridge prefab variants where practical.

Water is less severe than lava. Lava should be treated as a dangerous obstacle; miners should not blindly tunnel or navigate into it.

## 14. Navigation anchors

For the first version, persist a guaranteed-safe navigation anchor approximately every 10 blocks of constructed tunnel.

Also create anchors at important topology points such as:

- junctions;
- rooms;
- bridge starts and ends;
- strong turns;
- significant height transitions;
- open work fronts.

These anchors do not replace Hytale's native pathfinding. They provide known-safe intermediate targets. Civ chooses the next safe anchor; Hytale should determine the actual local route whenever native navigation supports it.

This is intentionally simple for V1. Persistence may later be optimized if storing every regular anchor proves unnecessary.

## 15. Floor steps

When a usable tunnel floor rises or falls by one block, place stairs across the usable width so NPCs do not face raw one-block ledges.

Stair placement should adapt to the actual navigable width and irregular tunnel geometry rather than assuming a perfect rectangular tunnel.

## 16. Supports

Place mine supports every few blocks with non-fixed spacing so the pattern does not look perfectly mechanical.

Supports must adapt to the actual tunnel/cave geometry, especially variable ceiling height and natural chambers. They must never block the guaranteed navigation corridor.

Use native block/prefab capabilities where suitable.

## 17. Lighting

Lighting differs by tunnel type.

### Main tunnel

Primary recurring light source:

- an upright stone pillar;
- a lantern placed on top.

Use slightly variable spacing.

Additional occasional decoration may include hanging chains and hanging lanterns.

### Side tunnels

Side tunnels and nested side branches use torches only as their light source.

Place torches at slightly variable intervals, preferably along walls and outside the guaranteed navigation corridor.

This visual distinction is intentional: the main tunnel should look developed and infrastructural, while side tunnels look simpler and rougher.

## 18. Decoration

Decoration is a separate generation pass after safe structure and infrastructure are established.

Potential decoration includes:

- hanging chains;
- occasional hanging lanterns in the main tunnel;
- barrels;
- crates;
- timber piles;
- tools;
- small material piles;
- other mine-appropriate objects.

Decoration must never block the navigable corridor. If a decoration candidate conflicts with navigation or infrastructure, skip it.

Prefer small weighted prefab variants over hard-coding every decorative object individually when Hytale's prefab system supports the intended runtime placement.

## 19. Main-tunnel rail line

The main tunnel should contain a largely continuous rail line running through it.

Desired behaviour:

- follows the main tunnel;
- remains visually recognizable as one coherent main route;
- adapts to curves and elevation changes;
- may occasionally contain small gaps for visual wear/variation;
- uses Hytale-native rail/minecart systems wherever available.

Do not build custom minecart movement if native Hytale functionality is suitable.

The rail route is infrastructure and should be derived from the planned main-tunnel path rather than generated as independent random decoration.

## 20. Ore interaction

NPCs should not actively search for ore or steer tunnels toward ore veins.

If a tunnel happens to intersect ore, later gameplay may react to that event, but ore is not part of the primary tunnel-direction planning algorithm.

## 21. Player modifications

The mine generator should generally ignore player-created changes to the mine. The design goal is autonomous world generation, not continuous reconciliation with player construction.

Do not automatically rebuild every removed support/decorative block or reinterpret arbitrary player tunnels as part of the Civ mine topology.

If native pathfinding can use a player-created shortcut, that is acceptable, but the Civ mine topology does not need to register that geometry automatically.

## 22. Material costs

Design code so future tunnel infrastructure actions can optionally consume resources, for example:

- supports;
- bridges;
- rooms;
- rails;
- decoration.

For the first implementation, these actions should not consume actual materials. The feature remains an extension point because the desired economic model is not yet decided.

## 23. Persistence strategy

Avoid persisting a second full copy of every excavated block. The Hytale world already stores the resulting world geometry.

Persist only the state required to continue Civ's mine behaviour, for example:

- mine identity and seed if useful;
- current/open work fronts;
- generator state needed for deterministic continuation;
- main-tunnel heading/state;
- branch/topology metadata that cannot be cheaply reconstructed;
- navigation anchors;
- important room/infrastructure metadata.

The current implementation persists Civ-owned tunnel segments. The redesign should reassess this format so long-lived mines do not create unnecessarily large save data or progressively slower save/load behaviour.

Use a deterministic seed only where it materially reduces persistence or improves reproducibility without making regeneration expensive.

## 24. Performance

Never scan the entire 500 x 500 x depth mine volume during normal operation.

World inspection should remain local to the active planning/building operation, for example around:

- the current work front;
- the next candidate tunnel phase;
- a candidate room;
- a cave/bridge encounter.

Prefer native Hytale spatial/world queries when suitable. Long-run performance should be tested with mines containing hundreds or thousands of tunnel blocks and many branches.

## 25. Debugging requirement

The mine generator needs dedicated debugging support because many decisions are probabilistic and spatial.

Two complementary tools are planned:

1. Structured mine decision logging that can explain decisions such as branch continuation, turn selection, room rejection, boundary pressure, cave classification and abandoned work fronts.
2. In-game debug commands that can inspect or visualize the current mine state, especially work fronts, navigation anchors, tunnel classifications and important generator state.

Debug tooling must be optional and should not affect normal gameplay behaviour.

## 26. Implementation layers

Implement the overhaul in separate scopes so each later chat can start from repository state rather than relying on conversational memory.

### Layer 0 - canonical specification

Maintain this document as the source of truth.

### Layer 1 - domain model and mine network

Represent main tunnel, nested branches, work fronts, rooms and navigation anchors in the core model. Avoid visual/world generation work beyond what the model requires.

### Layer 2 - tunnel path and form phases

Implement headings, broad curves, footprint steering, vertical tendencies, width/height phases and lateral/vertical drift.

### Layer 3 - excavation geometry

Convert planned tunnel paths into safe tunnel volumes, add organic wall/ceiling variation, enforce the navigation core and add floor stairs for one-block elevation transitions.

### Layer 4 - branching and work-front selection

Implement nested branch creation, decreasing continuation probability, spacing/collision behaviour and main-vs-branch work prioritization.

### Layer 5 - NPC navigation

Implement approximately 10-block safe anchors and use Hytale-native pathfinding between appropriate intermediate targets. Handle work-front switching and safe return routes.

### Layer 6 - rooms and prefabs

Add room opportunity selection, spacing, weighted room types and prefab placement.

### Layer 7 - caves, fluids and bridges

Classify natural cave encounters, integrate useful caves, detect dangerous water/lava conditions and construct safe bridge transitions.

### Layer 8 - supports, lighting and decoration

Add adaptive supports, main-tunnel lantern pillars, side-tunnel torches, chains, barrels and other non-blocking detail.

### Layer 9 - main-tunnel rails

Add the largely continuous rail route using native Hytale rail/minecart behaviour wherever possible.

### Layer 10 - persistence and performance hardening

Minimize stored state, test save/load at scale and remove unnecessary long-term data growth.

### Layer 11 - integration and balancing

Run long-lived mines and tune probabilities, spacing, curves, room rates, navigation reliability and visual density.

## 27. Rules for future implementation chats

A new mine-related implementation chat should:

1. Read `AGENTS.md` first.
2. Read this document before changing mine behaviour.
3. Read the architecture/domain/Hytale documentation required by `AGENTS.md`.
4. Inspect the current repository state because earlier layers may already be implemented.
5. Treat decisions in this document as established unless there is a concrete reason to challenge them.
6. Prefer Hytale-native capabilities and verify actual APIs rather than inventing them.
7. Keep the scope limited to the requested implementation layer.
8. Update this document whenever implementation changes or clarifies a design decision.
9. Leave the repository understandable for the next implementation chat without requiring access to the previous chat.

## 28. Deferred / unresolved decisions

The following are deliberately not fully decided yet:

- exact minimum spacing values between tunnels and rooms;
- final work-front weighting numbers;
- final room prefab dimensions and visual variants;
- exact support spacing ranges;
- whether material costs will become active gameplay;
- how factions and multiplayer affect interactions between multiple mines;
- whether ore encounters later influence specialised mining behaviour;
- whether all regular navigation anchors remain persisted after later optimization;
- exact handling of legacy mines during the overhaul;
- exact runtime capability of Hytale prefab molding/scanners for dynamically generated mine spaces.

These should be decided only when their implementation layer needs them.
