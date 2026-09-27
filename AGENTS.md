# Coding agent rules

## Documentation

1. Read this file before implementing changes.
2. For technical changes, read `docs/architecture.md` first and update it when the current technical structure changes. Historical rationale belongs in ADRs.
3. For product-feature changes, also read `docs/concept.md` and keep it consistent with externally observable behavior.
4. For domain changes, read `docs/domain.md` and update it only when reliable domain information changes. Code alone is not proof of a domain rule.
5. Update `docs/development.md` when the development workflow, build, deployment, release or CI process changes.
6. Update `docs/testing.md` when test strategy, test infrastructure or the mapping from behavior to test types changes.
7. Use `docs/decisions/` for the rationale behind significant architectural decisions.
8. Update documentation together with the related change and only where affected. Do not duplicate the same rule across several documents without a clear reason.
9. Do not add speculative documentation. Leave unknown behavior explicitly unknown until reliable project-specific information exists.
10. Do not silently resolve contradictions between code, documentation or a current user instruction. Current user instructions and this file take precedence; surface the contradiction so the maintained documentation can be corrected deliberately.

## Architecture boundaries

11. Keep Hytale-specific code out of `src/main/java/dev/civilizations/core`.
12. Do not add Hytale-specific exceptions to the core when an adapter in `hytale` or wiring in `plugin` is sufficient.
13. Do not invent Hytale APIs, dependencies, manifest fields or lifecycle behavior. Before changing Hytale-specific code, first inspect the existing project code for established usage, then verify any remaining uncertainty against current official Hytale documentation. Only do broader external research when those two sources are insufficient.
14. Before implementing a new feature or subsystem, explicitly check whether Hytale already provides the required behavior, a closely related native API, asset type, interaction, game mode, UI primitive or engine system. Prefer composing or adapting native Hytale capabilities over recreating equivalent behavior, unless the native mechanism cannot satisfy the product requirement.
15. Prefer root-cause fixes over accumulating special cases or compatibility branches.
16. Add the smallest abstraction needed for the current feature; do not create speculative interfaces for planned RTS, NPC, economy, building or logistics systems.

## Engineering discipline

17. If the same problem requires a second implementation iteration, explicitly reassess whether the underlying issue belongs in a more general abstraction, invariant, state transition or scheduling boundary instead of adding another local patch.
18. Avoid profession-, building-, entity- or feature-specific branches when the behavior belongs to a shared system concern. Prefer one authoritative mechanism that specialized subsystems can call into.
19. Treat growing chains of special-case conditions, duplicated retry/routing/state logic and feature-specific bypasses as architecture smells. Do not add another exception without first checking whether the common mechanism should be improved.
20. When a general solution is practical, prefer it even if a local patch would be shorter. Keep the codebase coherent and testable rather than optimizing for the smallest immediate diff.

## Validation and repository hygiene

21. Run `./gradlew test` and `./gradlew build` before considering a change complete whenever the local environment permits it.
22. Never commit machine-specific paths, local Hytale installations, credentials or generated server/game files.
23. Implement changes on a temporary branch. Complete code, tests and documentation there before final integration.
24. At the end of a completed change, squash-merge it into `main` so one meaningful commit remains for that adjustment. Verify the resulting main build/release status.
