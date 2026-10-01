# Woodcutter work position visibility

## Context

Runtime testing showed that a woodcutter could begin chopping several blocks away from the visible trunk because valid work positions were primarily ranked by distance from the worker. That made it hard for the player to tell which tree the NPC was currently working on.

## Decision

For each detected tree, Civ still searches only safe stand positions within the existing three-block work radius. Candidate positions are now ranked first by horizontal distance to the tree's lower trunk blocks (base through three blocks above it), then by distance from the worker as a tie-breaker. This keeps the visible worker close to the trunk without changing Hytale navigation or the tree-felling rules.

Only lower trunk blocks near the base are used for this score, rather than every collected wood block, so large canopies do not pull the work position away from the trunk and the scoring cost stays bounded.

The woodcutter presentation also uses the native horizontal `Axe` / `SwingLeft` animation in the existing client-side loop. The server still sends one animation start at work begin and one stop at work end or interruption.

## Consequences

- Work remains delegated to Hytale navigation once Civ selects the target position.
- The worker's visual location more clearly communicates which trunk is being worked.
- Large or irregular trunks can still choose a safe position farther out when adjacent cells are blocked.
- No per-swing server timer is introduced.
