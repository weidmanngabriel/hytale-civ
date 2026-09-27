# Coding agent rules

1. Read this file before implementing changes.
2. For technical changes, read `docs/architecture.md` first and update it when architecture changes.
3. For product-feature changes, also read `docs/concept.md` and keep it consistent with implemented behavior.
4. Update `docs/development.md` when the development workflow, build, deployment or CI changes.
5. Update `docs/testing.md` when test strategy or test infrastructure changes.
6. Keep Hytale-specific code out of `src/main/java/dev/civilizations/core`.
7. Do not add Hytale-specific exceptions to the core when an adapter in `hytale` or wiring in `plugin` is sufficient.
8. Do not invent Hytale APIs, dependencies, manifest fields or lifecycle behavior. Check current official Hytale documentation before changing Hytale API usage.
9. Prefer root-cause fixes over accumulating special cases or compatibility branches.
10. Add the smallest abstraction needed for the current feature; do not create speculative interfaces for planned RTS, NPC, economy, building or logistics systems.
11. Run `./gradlew test` and `./gradlew build` before considering a change complete whenever the local environment permits it.
12. Never commit machine-specific paths, local Hytale installations, credentials or generated server/game files.
