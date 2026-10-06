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

Layer 4 currently uses the following initial branch-opportunity tuning. These values are balance parameters, not persistence contracts:

- main-tunnel opportunity: 15%;
- side-tunnel opportunity at branch depth 1: 30%;
- for every additional branch depth, multiply that branch-opportunity chance by 0.65;
- there is no hard maximum branch depth.

This intentionally allows branches of branches while making very deep trees progressively rarer.

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

Layer 4 currently starts a branch at 16 blocks. Further growth is sampled in 8-block units. Continuation starts at 95%, falls by 4 percentage points after every accepted 8-block continuation and never falls below 20%. The initial 8-percentage-point decrease was rejected by long-run tests because it made 100+ block branches too rare to match the intended visible long tail. These are tuning values and may be rebalanced later without changing the continuation model.

A branch can also end because:

- no valid next geometry can be generated after reasonable retries;
- it reaches depth or footprint constraints;
- nearby mine geometry leaves insufficient space;
- a dangerous cave/fluid situation has no safe continuation;
- its continuation roll eventually fails.

## 10. Main-vs-branch work priority

Branching should be frequent, but the main tunnel must continue to grow over the lifetime of the mine.

Use weighted work-front selection rather than a fixed sequence. Side branches can have high probability, while the main tunnel periodically gains increased priority if it has not advanced for a while.

Layer 4 currently gives the main growth front a 40% share when both main and branch growth fronts are available. Independently of the random roll, after three consecutive branch-growth selections an available main front is selected next. This is mine-generation fairness only; it does not replace or alter the miner task scheduler defined in `docs/miner-npc-design.md`.

The data model must support multiple open work fronts. Miner coordination is local to one assigned mine: miners of other mines or factions do not participate in that mine's task selection. There are no persistent miner teams in V1. Multiple miners may temporarily cooperate on the same active task up to that task's capacity, and otherwise distribute themselves across the mine's other available work.

## 11. Spacing and tunnel collisions

Keep several blocks of rock between unrelated tunnel sections and rooms. Larger tunnels and rooms should use larger safety margins.

Random collisions should normally be avoided. However, when two existing passages approach in a geometrically sensible way, an intentional connection/crossing may occasionally be created. This allows the mine network to contain loops rather than being a strict tree.

Layer 4 currently requires branch start points to be at least 18 blocks apart. For unrelated tunnel centerlines, collision checking reserves the two local tunnel half-widths plus 5 blocks of rock. The short connection throat where a child leaves its direct parent is exempt from that parent-spacing rule; otherwise a branch could not physically connect to its parent. When an unrelated collision remains, the first implementation accepts it as an intentional crossing with a 5% chance; special crossing-room geometry is deliberately deferred to later layers.

These are initial safety/balance values and should still be tuned with larger generated networks.

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

Initial bridge span target is roughly 12-16 blocks maximum. Layer-5 V1 uses at most 16 planned floor slices and reduces that to 10 when fluid is detected below the gap. This is deliberately conservative and is not yet a complete cave/lava classifier.

A Layer-5 bridge is real miner work (`BUILD_BRIDGE`, priority 10), not an instant prefab. The V1 structure has a three-block-wide deck, Fir longitudinal side beams and periodic cross-girders below the deck. It does not require supports all the way to a deep cave floor, and railings remain optional/deferred where they could complicate NPC navigation. The route beyond an unfinished required bridge is not executable.

Water is less severe than lava. Lava should be treated as a dangerous obstacle; miners should not blindly tunnel or navigate into it.

## 14. Navigation anchors

Navigation anchors are evidence of world space that an NPC has actually traversed successfully. They are not generated merely because the mine planner or excavation geometry predicts that a point should be safe.

For V1, create a regular anchor when all of the following are true:

- a miner has actually walked through the candidate block;
- the candidate block is exactly Hytale `BlockType.EMPTY`;
- it is approximately 10 blocks from surrounding existing anchors. Exactly 10 blocks is acceptable; closer candidates are skipped.

Semantic anchors such as junctions, room accesses, bridge starts and bridge ends follow the same physical rule: the point becomes trusted only after successful traversal of an `EMPTY` block. The semantic type adds routing meaning after that validation. Strong turns and height transitions do not require their own synthetic anchors merely because the planner predicts them; normal traversed anchors already cover those areas when spacing allows.

The current moving excavation position is not represented by a moving `WORK_FRONT` anchor in V1. The miner routes through the known-safe anchor network and the existing dynamic work target handles the final local approach to the current face.

Anchors do not replace Hytale's native pathfinding. Civ stores only the small semantic graph of known-safe points and chooses the shortest currently valid semantic route when more than one anchor route exists. Hytale determines the physical local path between movement targets. Connections are normally bidirectional when physically safe. An unfinished bridge does not create an active graph connection; a bridge route becomes trusted only after the completed bridge has been traversed successfully.

### 14.1 Long-distance mine travel

The long-distance threshold is **more than 50 blocks measured as Euclidean air-line distance**. It is deliberately not based on semantic route length.

A miner returning from above ground always walks normally through:

`workplace_access -> mine_tunnel_connector`

No long-distance teleport may happen before the miner reaches the tunnel connector. Once the miner is at the connector or already underground, Civ compares the relevant safe reference point with the next work target. At more than 50 blocks air-line distance, Civ may teleport the miner to an already known, reachable safe anchor on the valid target route. Prefer the reachable safe anchor on that route that is closest to the target. Never teleport directly to the work position; Hytale-native navigation handles the remaining local distance.

The same principle may be used for long underground travel back toward the surface. The underground portion may use a safe anchor teleport, but the actual mine exit remains logically routed through the tunnel connector.

### 14.2 Native failure recovery

Hytale's native navigation state is the primary failure signal. On `BLOCKED` or `ABORTED`, Civ first requests one native path recomputation. If the native navigation still reports terminal failure after that retry:

- main corridor and work directly in it: return to the last known-safe anchor and retry;
- side tunnels, rooms and bridge areas: release the affected task and select another available work item once the multi-task miner scheduler owns that task lifecycle.

`DEFER` is not treated as terminal failure without runtime evidence. A Civ-owned elapsed-time stall detector is not the primary failure mechanism.

The existing surface-recovery watchdog remains a separate emergency safety net for miners that accidentally escape to the surface; it is not the normal 50-block travel mechanism.

This is intentionally simple for V1. Persistence may later be optimized if storing every regular anchor proves unnecessary.

## 15. Floor steps

When the planned usable floor changes elevation, miners build a connected stone staircase rather than leaving raw one-block ledges. Layer 3 exposes one-block `StepTransition`s; Layer 5 turns each due transition into priority-10 `BUILD_STEP` work so several consecutive transitions visually form one continuous staircase.

Use native stone stair/step blocks, correctly rotated toward the rise direction. The initial implementation covers the three-block guaranteed corridor where world geometry allows it. Every placed stair block takes 0.5 seconds of miner build time.

Exact native stair asset selection and real NPC traversal remain an integration contract to verify in Hytale runtime; Civ must not replace native navigation with its own stair pathfinder.

## 16. Supports

Layer-5 supports are dynamically built block-by-block and do not use the old fixed 4x4 `Mine_Support_01` prefab.

Initial support rules:

- target spacing is 6-10 blocks/slices along a tunnel;
- the selected frame may move up to roughly 3 blocks before or after that target to find a cleaner local cross-section, especially around curves;
- the opening inside the frame must remain at least 4 blocks wide and 3 blocks high;
- otherwise the support is shifted or skipped rather than narrowing the guaranteed corridor;
- the frame should be as high and wide as the actual open tunnel reasonably permits;
- `Wood_Fir_Branch_Long` forms the two upright side posts;
- `Wood_Fir_Trunk` forms the top crossbeam and is rotated along the local cross-axis;
- left and right posts independently extend down to one block above the first solid floor/stone beneath that side, so uneven ground may produce asymmetric post lengths;
- the solid floor block itself is never replaced.

The miner builds the left post bottom-to-top, then the right post bottom-to-top, then the top beam from its outside ends toward the centre. Every block takes 0.5 seconds.

Built Fir support wood is placed with Hytale's player-like block-operation/Deco metadata so it does not inherit natural-tree cascade physics. The Civ woodcutter independently excludes Deco wood from natural-tree discovery.

Recurring visual supports use ordinary infrastructure priority in the current V1. A support that is explicitly required for safety/passability belongs to priority 10 under the general miner rules.

## 17. Lighting

Lighting differs by tunnel type and is normal priority-5 infrastructure. V1 uses spacing plus valid geometry rather than measuring actual ambient light. Every placed light-construction block takes 0.5 seconds.

### Main tunnel

Primary recurring light source:

- an upright `Stone Brick Pillar - Base`, tip pointing upward;
- one lantern placed on top.

Target spacing is roughly 8-14 tunnel blocks/slices. The pillar should preferably sit about one block inward from the wall rather than directly touching it, but never at the cost of the guaranteed navigation corridor or the central lane reserved for later main-tunnel infrastructure such as rails.

Additional occasional decoration may later include hanging chains and hanging lanterns.

### Side tunnels

Side tunnels and nested side branches use wall torches only.

Target spacing is roughly 7-12 tunnel blocks/slices. The torch requires a solid wall mounting surface and must remain outside the guaranteed navigation corridor.

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

Open work state, task priority and unfinished work progress belong to the mine/task state rather than to a particular miner. Temporary miner-to-task assignments and capacity reservations do not need to survive a server restart: after load, miners assigned to the mine select again from the persisted open work. Completed tasks are removed from the task system once their durable result is represented by the mine metadata and/or Hytale world state, so finished work cannot be selected again merely because historical task records remain.

`MineNetwork` is the semantic persistent mine state. It stores logical tunnels, their parent hierarchy, rooms, work fronts, navigation anchors and the IDs of completed deterministic infrastructure tasks. It does not persist excavated blocks, pathfinding routes or a second copy of placed infrastructure geometry already represented by Hytale. Completion IDs exist only so already-finished deterministic work is not scheduled again after restart; if a player later removes finished infrastructure, Civ does not automatically rebuild it.

Layer 4 supplies deterministic Core planning for a nested logical tunnel network, including branch probability, branch continuation, spacing/collision checks and growth-front fairness. The live miner consumes this Layer-2/3/4 plan directly through persistent work fronts. Concrete tunnel geometry is regenerated deterministically from the stable mine identity after restart, while Hytale world blocks remain authoritative for excavation progress.

Mine persistence uses the current network-only format. Development saves from earlier mine formats are intentionally not migrated; incompatible records are ignored and the mine initializes fresh runtime state on next use.

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

Layer 1 is implemented with the following current model:

- `MineNetwork` is the aggregate for one mine building and is keyed by the stable mine building ID.
- `MineTunnel` represents either the one `MAIN` tunnel or a nested `BRANCH`; branch depth follows the parent hierarchy.
- `MineRoom`, `MineWorkFront` and `MineNavigationAnchor` reference logical tunnel IDs rather than concrete block geometry.
- `MineNavigationAnchor` may store explicit neighbouring anchor IDs, but these are semantic safe-point connections only; Hytale remains responsible for pathfinding.
- `MineTunnel` contains only semantic tunnel identity, kind, parentage, branch depth and origin; concrete excavation geometry is planned separately.
- `MineRoom`, `MineWorkFront` and `MineNavigationAnchor` reference logical tunnel IDs and do not depend on excavation-segment IDs.
- Form phases, concrete geometry, branching and work-front scheduling are implemented by the later layers below rather than duplicated in the Layer-1 model.

### Layer 2 - tunnel path and form phases

Layer 2 is implemented as Hytale-independent planning in Core. It does not excavate world blocks itself; Layer 3 converts the planned path into concrete tunnel volumes, and the live Hytale miner consumes those Layer-3 slices.

Current Layer-2 model:

- `MineHeading` provides eight horizontal planning headings in 45-degree steps.
- `MinePathPlanner` produces a deterministic continuous centerline from a seed, origin, tunnel kind and initial heading.
- `MineTunnelPath` contains sampled centerline points plus the `MineFormPhase` sequence that produced them.
- Heading decisions may only remain straight or move one 45-degree step left/right per phase. The actual centerline tangent interpolates continuously across the phase, so a heading change becomes a broad curve rather than a hard corner.
- Main-tunnel initial direction weighting is currently 78% straight, 11% left and 11% right. Branches use the established 50/25/25 weighting. These are tuning values, not persistence contracts.
- Form-phase target width and height change by at most one block from the previous phase. Main-tunnel values remain in the 6-8 range; branch values remain in the 3-5 range. Centerline samples carry interpolated continuous width/height values for later voxelization.
- Current form-phase length tuning is 10-18 forward blocks for the main tunnel and 6-12 for branches. The final partial phase may be shorter when a caller requests an exact path length.
- Main-tunnel vertical phase weighting is 68% down, 27% level and 5% up. Branch weighting is 35% down, 30% level and 35% up. A selected vertical change is spread continuously across the full phase and is never more than one block per phase.
- Lateral drift is also phase-based rather than per-block noise. A phase changes its lateral offset by at most one block; the running offset is currently clamped to two blocks either side of the underlying path.
- The 500x500 footprint remains soft. Boundary pressure starts once the square-distance from the mine center exceeds roughly 70% of the half-extent and progressively reduces outward candidate weights while increasing inward weights. No candidate is reduced to zero merely for pointing outward.
- Layer 2 performs no Hytale world queries, collision checks, cave checks, navigation checks or block placement. Those remain later-layer responsibilities.
- Deterministic Core tests cover the eight headings, reproducibility, size ranges, gradual dimension changes, soft boundary behaviour, downward main-tunnel tendency, approximately balanced branch vertical tendency and bounded lateral drift.

### Layer 3 - excavation geometry

Convert planned tunnel paths into safe tunnel volumes, add organic wall/ceiling variation, enforce the navigation core and add floor stairs for one-block elevation transitions.

### Layer 4 - branching and work-front selection

Layer 4 is implemented as an Hytale-independent Core planning foundation:

- `MineNetworkGrowthPlanner` composes Layer 2 and Layer 3 into one deterministic logical network plan rather than duplicating path or voxel generation.
- The plan supports branches from the main tunnel and nested branches from branches, with no hard branch-depth cap.
- Branch opportunity decreases with depth; branch total length uses a decreasing continuation roll instead of a fixed gameplay maximum.
- Branch starts use an initial 18-block minimum spacing. Unrelated passages reserve their local half-widths plus 5 blocks of rock.
- Random collisions are normally rejected; a small 5% intentional-crossing opportunity exists, while special crossing geometry remains deferred.
- `MineWorkFrontGrowthSelector` gives branches ordinary priority while guaranteeing that an available main front is not starved beyond three consecutive branch selections.
- These values are current tuning values, not persistence contracts; final balancing remains Layer 11 work.
- The live Hytale miner is wired to this planned network. It creates or resumes one persistent work front per planned tunnel and executes Layer-3 slices for main and branch tunnels.

See `docs/mine-layer4-implementation.md` for the implementation handoff and explicit limitations.

### Layer 5 - NPC navigation

Layer 5 uses sparse known-safe anchors created from actual NPC traversal rather than planned geometry. A candidate regular anchor must be an actually traversed exact `BlockType.EMPTY` position and approximately 10 blocks from surrounding anchors. Civ owns the small semantic anchor graph and shortest safe graph-route choice; Hytale remains the local physical pathfinder. The existing dynamic work target remains responsible for the final approach to the excavation face rather than a moving `WORK_FRONT` anchor.

Long-distance travel uses the >50-block Euclidean air-line rule described in section 14. A miner returning from above ground must physically reach `mine_tunnel_connector` before any anchor teleport is considered. Native `NavState` is the primary failure signal: request a native recompute first, then recover to the last safe main-corridor anchor when needed. Side-task release/reselection binds to the multi-task scheduler once that lifecycle is implemented.

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

- final balancing of tunnel spacing, branch probabilities and main-vs-branch growth weights after larger long-run tests;
- final room prefab dimensions and visual variants;
- exact support spacing ranges;
- whether material costs will become active gameplay;
- whether ore encounters later influence specialised mining behaviour;
- whether all regular navigation anchors remain persisted after later optimization;
- exact runtime capability of Hytale prefab molding/scanners for dynamically generated mine spaces.

These should be decided only when their implementation layer needs them.

## Current implementation checkpoint - NPC layer 4 work fronts

Live miner excavation is connected to the Layer-2/3/4 mine plan. The active work unit is one `MineTunnelGeometry.Slice`; its variable width/height and voxel set come directly from the planned tunnel geometry. A persistent `MineWorkFront` tracks the current semantic front for every planned main or branch tunnel, while already excavated blocks remain world truth.

Up to two miners may share a normal tunnel front. Their per-block claims are transient execution coordination and are not stored as permanent worker slots. After each completed slice, miners select again from currently executable fronts; active fronts with free capacity are filled first, otherwise Branch priority 6 precedes Main priority 4, followed by distance and stable tie-breaking. Rooms, supports, steps, bridges, lighting, decoration and rails remain separate later integrations. Supports are not placed automatically by the excavation loop; they remain dedicated mine work according to `docs/miner-npc-design.md`.
