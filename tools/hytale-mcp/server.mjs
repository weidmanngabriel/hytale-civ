import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { createProtocol } from './protocol.mjs';
import { configuration, LocalRuntime } from './runtime.mjs';

const string = { type: 'string', maxLength: 128 };
const coordinate = { type: 'number', minimum: -30000000, maximum: 30000000 };
const profession = { type: 'string', enum: ['UNEMPLOYED', 'SOLDIER', 'WOODCUTTER', 'FARMER', 'MINER', 'CONSTRUCTION_WORKER'] };
const tool = (name, description, properties = {}, required = [], readOnly = false) => ({ name, description,
  inputSchema: { type: 'object', properties, required, additionalProperties: false },
  annotations: { readOnlyHint: readOnly, destructiveHint: !readOnly, idempotentHint: readOnly, openWorldHint: false } });
export const TOOLS = [
  tool('hytale_connect', 'Attach to a running singleplayer session explicitly enabled with /civmcp on. No game process is launched.', { connectionFile: { type: 'string', minLength: 1, maxLength: 4096 } }),
  tool('hytale_disconnect', 'Detach from the live game without stopping it or deleting NPCs.'),
  tool('hytale_context', 'Read the enabling player, current world and loaded NPCs within 64 blocks. Does not select existing NPCs.', {}, [], true),
  tool('hytale_select', 'Explicitly select an existing loaded Civ NPC by UUID from hytale_context. It will never be deleted by hytale_reset.', { uuid: string }, ['uuid']),
  tool('hytale_status', 'Read configuration, build jobs, deployed artifact identity and owned server readiness.', {}, [], true),
  tool('hytale_build', 'Run Gradle test + build locally. Returns a job ID immediately; poll hytale_job.'),
  tool('hytale_job', 'Read a build result and incremental bounded build output.', { jobId: string, after: { type: 'integer', minimum: 0 } }, ['jobId'], true),
  tool('hytale_deploy', 'Install a successful build into the isolated development runtime. Server must be stopped.', { jobId: string }, ['jobId']),
  tool('hytale_start', 'Start one owned offline Hytale server bound to loopback, with the development bridge. Does not launch a game client.'),
  tool('hytale_stop', 'Request native graceful shutdown of the server started by this MCP instance. Wait up to 15 seconds.'),
  tool('hytale_logs', 'Read bounded incremental server stdout/stderr and native damage diagnostics. A damage filter event alone does not prove applied damage.',
    { after: { type: 'integer', minimum: 0 }, limit: { type: 'integer', minimum: 1, maximum: 300 }, contains: { type: 'string', minLength: 0, maxLength: 128 } }, [], true),
  tool('hytale_roles', 'Search NPC role names from the actual running Hytale server.', { query: { type: 'string', minLength: 0, maxLength: 128 } }, [], true),
  tool('hytale_arena', 'Create or reuse this session\'s flat development arena and preload its chunks.'),
  tool('hytale_soldier_scenario', 'Reset tracked test NPCs, then spawn a real Civ soldier and Chicken_Undead opponent. Native AI/combat continues autonomously.'),
  tool('hytale_spawn', 'Spawn a native role. Live mode requires a loaded position within 64 blocks of the player; isolated mode stays inside arena bounds. Only Civ_Inhabitant can receive a Civ profession.',
    { role: string, x: coordinate, y: { type: 'number', minimum: -1024, maximum: 4096 }, z: coordinate, profession }, ['role', 'x', 'y', 'z']),
  tool('hytale_entities', 'Read selected or spawned NPC handles, validity, native numeric HP, position and Civ work/combat state. An invalid handle is not proof of death.', {}, [], true),
  tool('hytale_move', 'Give a tracked Civ NPC the existing production manual movement order, including normal work interruption/resume.',
    { handle: string, x: coordinate, y: { type: 'number', minimum: -1024, maximum: 4096 }, z: coordinate }, ['handle', 'x', 'y', 'z']),
  tool('hytale_profession', 'Assign a profession through CivUnitRegistry, including the normal bootstrap equipment.', { handle: string, profession }, ['handle', 'profession']),
  tool('hytale_reset', 'Remove only the test NPCs tracked by this bridge. Keeps the arena and server running.'),
  tool('hytale_observe', 'Sample native NPC positions/HP/state for up to 10 seconds, without pausing or speeding up Hytale.',
    { seconds: { type: 'number', minimum: 0.1, maximum: 10 }, intervalMs: { type: 'integer', minimum: 250, maximum: 2000 } }, [], true)
];
export function dispatcher(runtime) {
  return (name, args) => {
    switch (name) {
      case 'hytale_connect': return runtime.connect(args.connectionFile);
      case 'hytale_disconnect': return runtime.disconnect();
      case 'hytale_status': return runtime.status();
      case 'hytale_build': return runtime.build();
      case 'hytale_job': return runtime.job(args.jobId, args.after);
      case 'hytale_deploy': return runtime.deploy(args.jobId);
      case 'hytale_start': return runtime.start();
      case 'hytale_stop': return runtime.stop();
      case 'hytale_logs': return runtime.serverLogs(args.after, args.limit, args.contains);
      case 'hytale_observe': return runtime.observe(args.seconds, args.intervalMs);
      default: return runtime.bridge(name.slice('hytale_'.length), args);
    }
  };
}
async function main() {
  const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
  const runtime = new LocalRuntime(await configuration(root));
  const handle = createProtocol(TOOLS, dispatcher(runtime));
  let closing = false;
  const close = async () => {
    if (closing) return; closing = true;
    await runtime.close(); process.exit(0);
  };
  const input = createInterface({ input: process.stdin, crlfDelay: Infinity });
  // Preserve initialize/initialized ordering. Long-running tool calls do not block status queries.
  let lifecycle = Promise.resolve();
  input.on('line', line => {
    let message;
    try { if (line.length > 65536) throw new Error('Message exceeds 64 KiB'); message = JSON.parse(line); }
    catch { process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: null, error: { code: -32700, message: 'Parse error or oversized request' } }) + '\n'); return; }
    const run = async () => {
      const response = await handle(message);
      if (response && !closing) process.stdout.write(JSON.stringify(response) + '\n');
    };
    if (message?.method === 'initialize' || message?.method === 'notifications/initialized') lifecycle = lifecycle.then(run);
    else lifecycle.then(run).catch(error => process.stderr.write(error.message + '\n'));
  });
  input.on('close', close);
  process.on('SIGINT', close); process.on('SIGTERM', close);
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { process.stderr.write(error.message + '\n'); process.exitCode = 1; });
}
