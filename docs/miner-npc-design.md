# Miner NPC Design

Status: canonical planning specification for Plan Phase 1 miner NPC behaviour.

This document defines the intended miner behaviour from the player's point of view before implementation. It complements `docs/mine-design.md` and should be treated as the source of truth for miner task selection, work-front behaviour, navigation hand-off, room work and infrastructure priorities until explicitly changed.

The design goal is an autonomous miner that looks purposeful and local rather than globally scripted. Civ decides what the miner should do, why and where. Hytale should handle actual movement and pathfinding wherever possible.

## 1. Scope

Plan Phase 1 supports at most three miners assigned to one mine.

The design covers:

- work-front behaviour;
- team distribution across active fronts;
- local creation of new work fronts;
- room excavation behaviour;
- interruption and reprioritization rules;
- cave, gap, water and lava encounters;
- infrastructure work;
- navigation anchors;
- long-distance task changes and teleport hand-off;
- manual player movement commands;
- high-level miner states visible from gameplay.

The design does not yet define exact placement formulas for supports, lights, rails or decoration. It only defines when miners should perform those tasks and the broad geometry constraints already agreed.

## 2. Core principles

Miner behaviour should follow these principles:

1. Prefer local, visible work over global invisible assignment.
2. New work fronts should emerge from actual excavation, not appear arbitrarily elsewhere in the mine.
3. Miners normally finish their current small work unit before reprioritizing.
4. Safety may interrupt immediately.
5. Avoid long, uninteresting walks when a known-safe navigation anchor can provide a better transition.
6. Hytale pathfinding is the default. Civ-provided anchors are support points, not a replacement pathfinder.
7. The main tunnel must continue to make progress over the lifetime of the mine, but miners should not constantly abandon nearby useful work just because the main tunnel exists.

## 3. Work rhythm

Plan Phase 1 does not introduce artificial work shifts.

An assigned miner treats the mine as a persistent workplace and works autonomously whenever no higher-priority activity is active.

There is no required periodic check-in at the mine building and no forced commute back to the surface between ordinary tasks.

Future needs such as sleep, food or shifts may interrupt work later, but they are not miner-specific behaviour in Plan Phase 1.

## 4. Work units and reprioritization

The normal work unit for excavation is one tunnel face / excavation slice.

A miner normally finishes the currently started face before selecting a different task.

This is intentionally larger than a single block so behaviour does not look nervous, but smaller than a full 4-12 block planning segment so miners remain responsive.

Safety events may interrupt immediately, even in the middle of a face.

## 5. Work fronts

A work front is a real, already-started point of mine expansion or construction.

A purely theoretical future branch position is not yet a work front.

The mine may remember planning opportunities, but they do not become real open work fronts until a miner reaches the location during normal work and actually begins that branch, room or other expansion.

The work-front list therefore contains only real started work fronts and is used for:

- state tracking;
- priority;
- blocked / abandoned / complete status;
- miner task selection among already existing fronts.

## 6. Local creation of new fronts

New fronts emerge locally from the work miners are already doing.

Example:

- Three miners are advancing one tunnel.
- A miner reaches a location where a side branch is allowed and currently desirable.
- That miner begins the branch.
- The new branch now becomes a real work front.
- The remaining miners continue their current tunnel work unless another priority causes them to change later.

A new branch must not spontaneously appear elsewhere in the network and summon a miner to it.

The mine planner may decide whether a branch is allowed or desirable at the miner's current location, but from the player's perspective the miner discovers and starts the opportunity locally.

## 7. Team distribution with three miners

Plan Phase 1 supports exactly these three-miner distribution patterns:

- `3`
- `2 + 1`
- `1 + 1 + 1`

These are not fixed squad types. They are the natural result of how many real useful work fronts currently exist and which miners locally encounter them.

Default behaviour should favour miners staying together unless real useful additional work fronts have emerged.

A miner is not pulled away from an active branch merely because another task exists elsewhere.

Team distribution changes at natural task boundaries, especially:

- after a face is completed;
- when a front completes;
- when a front becomes blocked or abandoned;
- when a local branch or room becomes a real new task;
- when a clearly higher-priority task should be taken next.

## 8. Task selection after a work unit

After completing a face or equivalent work unit, a miner chooses the next task in a local-first order.

Preferred behaviour:

1. resolve a safety condition at the current location;
2. take a higher-priority local task such as a due room or required infrastructure;
3. continue the current real work front;
4. start a newly allowed local branch or expansion if current mine priorities favour it;
5. only then consider another already-existing work front elsewhere.

Priority can override pure distance, but unnecessary movement should be avoided.

If a front completes or is abandoned, the miner chooses the nearest sensible existing task that still matches current mine priorities. If no special task is appropriate, the miner may join another active front.

## 9. Main-tunnel priority

The main tunnel must continue to grow over the lifetime of the mine.

Main-tunnel priority influences task selection; it is not a separate task type.

If the main tunnel has not advanced for long enough, its priority may increase until one or more miners prefer it at their next natural reprioritization point.

This should not normally cause miners to abandon a face mid-work or instantly leave useful side-branch work.

## 10. Priority order

Plan Phase 1 uses the following gameplay priority order:

1. **Immediate safety / passability**
   - fall risk;
   - unsafe cave opening;
   - dangerous water or lava situation;
   - missing safe continuation;
   - required bridge before further progress.

2. **Manual player command**
   - temporarily overrides autonomous work.

3. **Due room**
   - high priority when a valid room opportunity is reached and enough space exists.

4. **Required infrastructure**
   - due support;
   - due light;
   - step / stair treatment when needed for passability;
   - bridge when needed for continuation.

5. **Continue current real work front**

6. **Start a new local real work front**
   - side branch or similar opportunity reached during current work.

7. **Take another existing work front**

8. **Rails**

9. **Decoration**

A higher-priority task does not automatically pull all miners from other fronts. Busy miners normally finish their current work unit first unless the situation is an immediate safety issue.

## 11. Rooms

Rooms have high priority once a valid room location is reached and enough space exists to build the intended room.

A room does not globally summon miners from distant side branches.

Behaviour:

- a free miner near the room can take the task;
- miners already working elsewhere finish their current work unit first;
- after that they may choose the room if its priority is still higher than their alternatives.

In Plan Phase 1, accommodation, storage, workshop, ore rooms and similar spaces are structure and atmosphere only.

They do not yet provide real NPC behaviours such as sleeping, fetching materials, tool use or logistics.

### 11.1 Room shape

Rooms should not be excavated as perfect boxes.

The default room shape is rounded / oval-like:

- corner blocks commonly remain in place;
- larger rooms may keep stepped corner areas rather than a single untouched block;
- mild asymmetry is desirable;
- usable space, entrances and safe navigation take priority over visual rounding.

The miner excavates the actual room geometry over time; the room should not simply appear fully completed at once.

## 12. Natural caves, gaps, water and lava

A miner must never blindly continue an excavation sequence into unsafe empty space.

If the miner is advancing an eight-block planning stretch and, for example, discovers a deep opening after four blocks, normal forward excavation stops immediately before entering the unsafe area.

The encounter is then classified by mine logic.

Typical outcomes:

- small opening: integrate safely and continue;
- bridgeable gap: create bridge work before further advance;
- large useful cave: accept it as a natural chamber / mine node;
- dangerous or unusable cave: redirect or abandon the front;
- unsafe water / lava: stop and treat as a special obstacle.

A natural area becomes part of normal mine movement only after mine logic has accepted it as safe and usable.

A miner should not step into an unclassified gap merely because excavation exposed it.

### 12.1 Breaking out to the outside world

If a tunnel breaks through a mountainside or otherwise reaches the outside world, this does not automatically create a second official mine entrance.

Plan Phase 1 keeps one official surface transition through the authored mine entrance / workplace access.

A breakout front should normally be stopped, redirected or abandoned instead of being treated as an unrestricted new exit.

## 13. What counts as being inside the mine

Mine membership is semantic, not based on absolute Y height.

A side tunnel may rise above the Y level of the mine building and still be fully inside the mine.

Known mine-space includes accepted tunnel sections, junctions, rooms, bridges and safe natural cave integrations.

The relevant distinction is:

- **inside the known mine network**;
- **outside the mine network / surface world**.

Absolute Y height must not be used as the general definition.

## 14. Bridges

A bridge has immediate high priority when further safe progress requires it.

Normal excavation should not continue across an unsafe gap until a valid bridge route is available.

The exact bridge span and prefab rules remain defined by mine design and later implementation tuning.

## 15. Steps and stairs

Do not automatically place a staircase for every vertical change.

Prefer simple steps for ordinary single-block or gentle elevation changes.

Use a more explicit stair treatment only when the terrain becomes genuinely staircase-like, for example repeated stepped elevation changes.

The goal is safe movement without making every minor height change look artificially engineered.

## 16. Supports

When infrastructure planning marks a support as due, a miner builds it before ordinary excavation continues past that point.

Supports must follow the actual tunnel direction and actual excavated cross-section rather than assuming a perfect rectangular tunnel.

Broad geometry rule:

- orient the support with the tunnel;
- place the top beam at an appropriate high point with enough usable span;
- extend vertical support posts from the beam down to the actual floor;
- adapt to asymmetric, diagonal or irregular tunnel geometry;
- never block the guaranteed navigation corridor.

The exact spacing formula and support-shape algorithm are intentionally deferred.

## 17. Lighting

Lighting is required infrastructure but does not normally interrupt a face immediately.

When lighting becomes due, the miner normally finishes the current face and then places the light before continuing ordinary excavation.

The exact spacing and placement formulas are deferred.

## 18. Rails

Rails exist only in the main tunnel in Plan Phase 1.

Rail work is lower priority than excavation, rooms and required safety infrastructure.

The rail line is built in sections between consecutive navigation anchors that lie on the main-tunnel route.

Not every anchor inside the mine belongs to the rail line. Anchors created in rooms, side branches or other off-route spaces must not make the rail path deviate from the main tunnel.

The intended unit is therefore:

`main-tunnel anchor A -> main-tunnel anchor B = candidate rail section`

## 19. Navigation anchors

Navigation anchors are shared safe points for the whole mine, not per-miner markers.

They are generated from actual miner movement through the known tunnel system.

While a miner is moving inside accepted mine space:

- if the miner is more than roughly 10 blocks from the next suitable existing safe anchor;
- and no other suitable anchor is already within roughly 10 blocks;
- and the miner's current position is safe and navigable;
- a new anchor may be created at that position.

This means the anchor network grows along paths miners actually use.

Anchors should not be created on temporary ledges, unsafe cave edges or unfinished locations that are not valid navigation positions.

## 20. Anchor responsibilities

Anchors have three purposes in Plan Phase 1:

1. known-safe mine locations;
2. optional navigation waypoints;
3. possible teleport entry points for long task changes.

Anchors do not replace Hytale pathfinding.

Default navigation policy:

1. give Hytale the real target and let native navigation try to reach it;
2. if direct navigation is unsuitable or unreliable, Civ may choose a useful anchor or anchor sequence as intermediate semantic targets;
3. Hytale still performs the actual local pathfinding between those targets.

Do not build a full custom Civ pathfinder unless later evidence shows native navigation cannot support the required behaviour.

## 21. Long-distance task changes inside the mine

When a miner changes to a task that is very far away inside the same mine, the system may skip the long commute.

Instead, the miner may be teleported to a suitable known-safe anchor near the new task and then continue with normal Hytale navigation.

This applies generally, for example:

- side branch -> main tunnel after main-tunnel priority increases;
- one distant branch -> a higher-priority room or task;
- after a manual player command when autonomous work resumes.

The selected anchor should not merely be geometrically closest. It should be a safe known anchor from which the target task is expected to be reachable.

The exact distance threshold for teleporting is a tuning value and is intentionally not fixed yet.

## 22. Surface transition

When a miner needs to move from an underground mine task to an outside-world target, the official mine exit is abstracted through the authored surface access point.

The miner may be teleported to `workplace_access` and then continue normal Hytale navigation from there.

This is the same broad transition model already used when a player manually calls an underground miner to an outside target.

Normal movement between two mine tasks should not use the surface transition.

## 23. Manual player commands

A manual movement command temporarily overrides autonomous mining work.

If the manual target is outside the mine while the miner is deep underground, the surface transition may be used so the miner does not need to walk the entire mine network back to the entrance.

When manual control ends, the miner does not have to return rigidly to the exact previous front.

Instead, autonomous task selection runs again using current priorities and real existing work fronts.

If the selected next task is far away, the long-distance anchor teleport rule may be used.

## 24. High-level miner states

The player-facing Plan Phase 1 behaviour can be represented with these high-level states:

- moving to mine / work location;
- working at an active front;
- excavating a room;
- building infrastructure;
- handling / waiting on a safety obstacle;
- choosing the next task;
- moving to another task;
- executing a manual player command;
- idle because no sensible work is currently available.

Do not treat animation timing, facing, block timers or similar implementation details as gameplay states in this design document.

## 25. Deferred features

The following are intentionally outside Plan Phase 1:

- actual sleeping / accommodation use;
- real storage logistics;
- workshop usage;
- material consumption for supports, bridges, rails or rooms;
- hunger, sleep and other needs;
- formal work shifts;
- specialist miner professions;
- more than three miners per mine;
- complex material transport;
- advanced rail economics;
- final support-spacing formula;
- final lighting-spacing formula;
- final rail-shape algorithm;
- final decoration rules.

## 26. Open tuning values

The following should remain configuration or tuning decisions until implementation tests provide evidence:

- exact distance threshold for long-distance teleport;
- exact anchor spacing tolerance around the 10-block target;
- exact main-vs-branch priority weights;
- exact duration / trigger before main-tunnel priority increases;
- exact support spacing;
- exact lighting spacing;
- exact rail construction timing;
- exact room-shape variation strength.

## 27. Implementation boundary

This document defines intended behaviour only.

Implementation should preserve the repository architecture rules:

- Civ owns gameplay priorities, semantic tasks and decisions;
- Hytale owns native movement and pathfinding wherever suitable;
- anchor points are semantic support targets, not a reason to recreate Hytale navigation;
- no speculative systems should be added beyond behaviour required by this specification.
