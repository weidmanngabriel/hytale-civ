# Hytale Live – agent access to the running game

Read this page when a task needs diagnostics or controlled developer actions in the user's **normal running Hytale world**. This is not a remote MCP connection exposed directly to ChatGPT; access runs on demand through GitHub Actions.

## Preconditions and permission

Ask the user once per chat before accessing or changing the live game during ordinary feature/debugging work; name the expected action. Permission is implicit if the current request explicitly concerns building or testing this bridge. **Before any write command** ensure the requested action is authorized by the user, and consider whether it could affect their normal world. Do not infer permission from older conversations.

The normal Hytale game with the current Civ plugin must be running, its localhost command bridge must answer on `127.0.0.1:5523`, and the registered GitHub self-hosted runner with the `hytale-local` label must be online. The local Node MCP need not be started for the GitHub bridge.

## Request and result

1. Use the connected GitHub tools to add a comment to **locked Issue [#266](https://github.com/weidmanngabriel/hytale-civ/issues/266)** in `weidmanngabriel/hytale-civ`, formatted exactly as one command below.
2. The trusted `.github/workflows/hytale-live.yml` workflow verifies issue number, locked state, event actor, comment author, sender and a strict allowlist in a GitHub-hosted job. Only authorized commands reach `[self-hosted, Windows, X64, hytale-local]`.
3. Call `fetch_issue_comments` on Issue #266 and find the workflow-generated `HCIV_LIVE_RUN request_comment_id=<original-comment-id> run_id=<run-id> url=<run-url>` response. Match `request_comment_id` to the ID returned when posting the command. Do not use an unrelated or older result. The GitHub-hosted `Publish live run reference` job posts this response after authorization; it can appear before the self-hosted game query starts. If the announcement is missing, inspect [Hytale Live Diagnostics](https://github.com/weidmanngabriel/hytale-civ/actions/workflows/hytale-live.yml) for an authorization or comment-posting error. Never treat a missing announcement as confirmation of game availability.
4. With the correlated run ID, call the GitHub connector's `fetch_workflow_run_jobs` and select the job named `Query running Hytale instance`. Use its numeric job ID with `fetch_workflow_job_logs` (not the run ID) to retrieve the raw job log. Parse the `HCIV_LIVE_RESULT` JSON line (`success`, `command`, `output`); verify the command matches the request, the command succeeded, and the job conclusion is successful. `output` is the response from the running game.
5. If queued, check runner availability. If the health request fails, check that the **running game plugin** is exposing port 5523. This channel never installs, builds, launches or stops the game.

Do not mistake an old run for current game state. The Windows job independently validates all commands and never checks out submitted code. Keep arbitrary native console commands out of the GitHub bridge.

## Allowlisted commands

Read-only:
- `/hytale-live status` – native `version` command.
- `/hytale-live npcs` – list currently loaded NPCs and Civ metadata.
- `/hytale-live npc <uuid>` – inspect one loaded NPC.
- `/hytale-live events <uuid>` – recent events for one NPC.
- `/hytale-live mines` – list registered mines.
- `/hytale-live mine-info <uuid>` – inspect a mine.

Authorized development writes (affect the **normal live game**):
- `/hytale-live spawn Civ_Inhabitant <x> <y> <z>` – spawn a Civ inhabitant.
- `/hytale-live move <npc-uuid> <x> <y> <z>` – issue a manual movement command.
- `/hytale-live profession <npc-uuid> <profession>` – profession is one of `MINER`, `CONSTRUCTION_WORKER`, `SOLDIER`, `WOODCUTTER`, `FARMER`.
- `/hytale-live assign-mine <npc-uuid> <mine-uuid>` – assign miner through existing Civ command.
- `/hytale-live reset` – remove entities spawned by `civdev` in the current plugin session only. **Confirm the effect with the user before a reset.**

UUIDs must use canonical hyphenated hexadecimal syntax. Coordinates are signed decimal numbers with up to five digits before the decimal and up to two after it, separated by single spaces. Other roles, new commands and arbitrary command strings are not exposed through GitHub. Existing Civ console commands are documented in [local-mcp.md](local-mcp.md); enabling the above commands required **no plugin update** because `civdev` already implements them. Adding a new `civdev` command would require a plugin change.

## Hytale Live versus Hytale Local

- **Live Issue #266**: on-demand actions in the currently running game; no build/restart; results in an Actions job log.
- **Local Issue #126**: isolated, commit-pinned Hytale runtime scenario tests with build/artifact reuse and separate logs; never assume they target the user's normal game.

See [development.md](development.md) for the overall development workflow and [runner-debugging.md](runner-debugging.md) for log-first failure investigation.

## Verified behavior (2026-10-08)

- [Correlated run 37752886758](https://github.com/weidmanngabriel/hytale-civ/actions/runs/37752886758): GitHub-hosted announcement job successfully posted `HCIV_LIVE_RUN` for request comment `6056316411`. This verifies run discovery through Issue #266 independently of the local runner's availability.

- [Status run 37742025770](https://github.com/weidmanngabriel/hytale-civ/actions/runs/37742025770): `version` returned `HytaleServer v0.6.8 (release)`.
- [NPC listing run 37742779964](https://github.com/weidmanngabriel/hytale-civ/actions/runs/37742779964): six loaded NPCs, including five miners and one construction worker.

These runs prove read-only transport at that time, **not** that the newly allowlisted write commands have been runtime-tested. Avoid executing world-changing commands just to validate infrastructure without current user authorization.
