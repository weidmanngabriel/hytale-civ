# Hytale Live – agent access to the running game

Read this page when a task needs diagnostics or controlled developer actions in the user's **normal running Hytale world**. This is not a remote MCP connection exposed directly to ChatGPT; access runs on demand through GitHub Actions.

## Preconditions and permission

Ask the user once per chat before accessing or changing the live game during ordinary feature/debugging work; name the expected action. Permission is implicit if the current request explicitly concerns building or testing this bridge. **Before any write command** ensure the requested action is authorized by the user, and consider whether it could affect their normal world. Do not infer permission from older conversations.

The normal Hytale game with the current Civ plugin must be running, its localhost command bridge must answer on `127.0.0.1:5523`, and a GitHub self-hosted runner with the `hytale-local` label must be online. For explicit routing, the selected runner additionally needs its unique `hytale-pc-gabe` or `hytale-pc-thommy` label. Configure these labels on the corresponding runners in repository Settings → Actions → Runners; each unique label must belong only to its intended computer. The local Node MCP need not be started for the GitHub bridge.

## First use in each chat

Before the first Hytale Live request in a new chat, explicitly offer the user three choices: **Gabe** (`runner gabe`), **Thommy** (`runner thommy`), or **Egal / zufällig** (omit runner, `auto`). Ask once and retain that selection for later Live calls in the same chat unless the user changes it. Do not silently infer a target from previous chats. The selection question can accompany the required permission question. A new chat requires a new selection. The `auto` option preserves existing GitHub scheduling on shared `hytale-local` and may target either computer when multiple are online. For world-changing commands, ensure the affected normal world is authorized; consider a specifically named runner safer than `auto`.

## Request and result

1. Use the connected GitHub tools to add a comment to **locked Issue [#266](https://github.com/weidmanngabriel/hytale-civ/issues/266)** in `weidmanngabriel/hytale-civ`, formatted as `/hytale-live <command>` or (recommended when multiple runners are online) `/hytale-live runner gabe <command>` / `/hytale-live runner thommy <command>`. Without selection, GitHub assigns the job to any available runner with shared label `hytale-local`, as before. This is nondeterministic with multiple runners.
2. The trusted `.github/workflows/hytale-live.yml` workflow verifies issue number, locked state, event actor, comment author, sender and a strict allowlist in a GitHub-hosted job. Only authorized commands reach `[self-hosted, Windows, X64, hytale-local, hytale-pc-gabe]` or the corresponding `hytale-pc-thommy` label set.
3. Call `fetch_issue_comments` on Issue #266 and find the workflow-generated `HCIV_LIVE_RUN request_comment_id=<original-comment-id> run_id=<run-id> runner=<gabe-or-thommy-or-auto> url=<run-url>` response. Match `request_comment_id` to the ID returned when posting the command and verify the `runner` field (`auto` indicates shared-label scheduling, not a particular machine). Do not use an unrelated or older result. The GitHub-hosted `Publish live run reference` job posts this response after authorization; it can appear before the self-hosted game query starts. If the announcement is missing, inspect [Hytale Live Diagnostics](https://github.com/weidmanngabriel/hytale-civ/actions/workflows/hytale-live.yml) for an authorization or comment-posting error. Never treat a missing announcement as confirmation of game availability.
4. With the correlated run ID, call the GitHub connector's `fetch_workflow_run_jobs` and select the job named `Query running Hytale instance`. Use its numeric job ID with `fetch_workflow_job_logs` (not the run ID) to retrieve the raw job log. Parse the `HCIV_LIVE_RESULT` JSON line (`success`, `command`, `output`); verify the command matches the request, the command succeeded, and the job conclusion is successful. `output` is the response from the running game.
5. If queued, check runner availability. If the health request fails, check that the **running game plugin** is exposing port 5523. This channel never installs, builds, launches or stops the game.

Do not mistake an old run for current game state. The Windows job independently validates all commands and never checks out submitted code. Keep arbitrary native console commands out of the GitHub bridge.

## Allowlisted commands

Read-only (optional `runner gabe` or `runner thommy` prefix, e.g. `/hytale-live runner gabe status`):
- `/hytale-live status` – native `version` command.
- `/hytale-live npcs` – list currently loaded NPCs and Civ metadata.
- `/hytale-live npc <uuid>` – inspect one loaded NPC.
- `/hytale-live events <uuid>` – recent events for one NPC.
- `/hytale-live mines` – list registered mines.
- `/hytale-live mine-info <uuid>` – inspect a mine.

Authorized development writes (optional `runner gabe` or `runner thommy` prefix chooses which machine's **normal live game** is affected):
- `/hytale-live spawn Civ_Inhabitant <x> <y> <z>` – spawn a Civ inhabitant.
- `/hytale-live move <npc-uuid> <x> <y> <z>` – issue a manual movement command.
- `/hytale-live profession <npc-uuid> <profession>` – profession is one of `MINER`, `CONSTRUCTION_WORKER`, `SOLDIER`, `WOODCUTTER`, `FARMER`.
- `/hytale-live assign-mine <npc-uuid> <mine-uuid>` – assign miner through existing Civ command.
- `/hytale-live reset` – remove entities spawned by `civdev` in the current plugin session only. **Confirm the effect with the user before a reset.**

The examples above without a runner remain valid and use the original shared-runner selection. Explicit selection is recommended if more than one runner exists. When a specifically selected runner is offline, its job queues instead of falling back to another machine.

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

## Structured batch requests (version 1)

Send a single comment to locked Issue #266 with the exact prefix and a JSON body (runner is mandatory):

~~~text
/hytale-live-batch runner gabe
{"version":1,"steps":[{"id":"list","action":"command","command":"npcs"},{"id":"delay","action":"wait","seconds":2},{"id":"mine","action":"command","command":"mines"},{"id":"check","action":"assert","from":"mine","contains":"CIVDEV_MINES"}]}
~~~

Supported actions are \`command\` (existing read and write Hytale Live commands except \`reset\`), \`wait\` (integer 1–30 seconds) and \`assert\` (case-sensitive substring check against a preceding command's output). IDs must be unique alphanumeric/hyphen/underscore identifiers beginning with a letter, max 40 characters. Limits: maximum 30 steps, 16,000 comment characters and 120 seconds total waiting. Any failure stops the batch; there is no shell/PowerShell escape or arbitrary command execution. The existing single-command format and its explicit \`reset\` confirmation requirement remain unchanged. Do not include \`reset\` in batches.

A GitHub-hosted authorization job validates each action and produces a normalized, base64-encoded payload. The trusted Windows runner independently validates each game command. The execution job writes \`HCIV_LIVE_BATCH_RESULT\` to the logs and uploads \`hytale-live-batch-result.json\` as a seven-day workflow artifact. The result contains \`schemaVersion\`, overall \`status\`, selected \`runner\`, and per-step \`id\`, \`action\`, \`success\`, \`durationMs\`, game \`output\` or \`error\`. The normal \`HCIV_LIVE_RUN\` comment still identifies the exact run. An assertion checks previously returned text, not actual world-block changes; confirming excavation requires suitable gameplay telemetry.

The current batch runner composes already supported \`civdev\` commands; \`civdebug mine recover\` is a player-relative command and is **not** currently exposed through this bridge. Adding stable mine-ID-based recovery or block counters needs a separate game-side adapter.

## Agent-Commands in Hytale Live

`/hytale-live runner gabe agent capabilities` liest den zur installierten Mod-Version gehörenden Funktionskatalog. Im JSON-Batch gilt `{"id":"capabilities","action":"command","command":"agent capabilities"}`. Weitere Aktionen sind `agent players`, `agent buildings`, `agent sites`, `agent block <x> <y> <z>`, `agent set-block <x> <y> <z> <blockAssetId>`, `agent create-site <playerUuid> <mine|farm|wheat_field> <x> <y> <z>` und `agent mine-recover <mineUuid> <status|workers|fronts|all>`. Der `agent`-Präfix wird nur für erlaubte Kommandos nach `civagent` übersetzt; beliebige native Hytale-Kommandos bleiben gesperrt.

`CIVAGENT_RESULT`-Ausgaben enthalten jeweils `action` und `data` als JSON. Ein `CIVAGENT_ERROR`- oder `CIVDEV_ERROR`-Antworttext gilt nun auch dann als fehlgeschlagener Batch-Schritt, wenn die native Command-Bridge den Transport als erfolgreich meldet. Build-Sites verwenden echte verbundene Spieler und beginnen als Baustelle, nicht als fertiges Gebäude. Neue Kommandos sind erst nach Installation der neuen Mod am laufenden PC verfügbar. Näheres: [Agent API](agent-api.md).

## Performance-Profiler über Hytale Live

Neue allowlistete, serverweite CivAgent-Kommandos (nach Installation der entsprechenden Plugin-Version):

- `agent perf-start`: Profiling beginnen, Laufzeit max. 15 Minuten; wiederholter Start setzt die laufende Aufnahme nicht zurück.
- `agent perf-status`: Aktivzustand, Restzeit und vorhandenen Report abfragen.
- `agent perf-stop`: Aufnahme beenden; behält den letzten Report im Speicher.
- `agent perf-report`: systemweise Aggregation inklusive Messbuchhaltung und Entity-Zahlen als `CIVAGENT_RESULT`.
- `agent perf-samples <offset>`: 10 sekündliche Messpunkte ab nullbasiertem Offset 0–900.

Diese Aktionen verwenden genau dieselbe Aufnahme wie das Dashboard. Änderungen an der normalen Spielwelt erfolgen nicht, die Messung verursacht aber bewusst zusätzlichen Runtime-Aufwand. Der Hytale-Live-Workflow prüft die Kommandos sowohl am GitHub-hosted Gate als auch am Windows-Self-Hosted Runner. Zugriff auf einen laufenden Rechner erfordert weiterhin die in diesem Dokument beschriebenen Zielrechner-/Freigabeanforderungen.
