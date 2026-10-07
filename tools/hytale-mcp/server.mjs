import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { createProtocol } from './protocol.mjs';
import { configuration, LocalRuntime } from './runtime.mjs';

const tool = (name, description, properties = {}, required = [], readOnly = false) => ({ name, description,
  inputSchema: { type: 'object', properties, required, additionalProperties: false },
  annotations: { readOnlyHint: readOnly, destructiveHint: !readOnly, idempotentHint: readOnly, openWorldHint: false } });
export const TOOLS = [
  tool('hytale_command', 'Execute a Hytale server-console command through the localhost command bridge and return the command output directly.',
    { command: { type: 'string', minLength: 1, maxLength: 4096 } }, ['command']),
  tool('hytale_status', 'Read repository/build state and whether the localhost command bridge is reachable.', {}, [], true),
  tool('hytale_build', 'Run Gradle test + build locally. Returns a job ID immediately; poll hytale_job.'),
  tool('hytale_job', 'Read a build result and incremental bounded build output.', { jobId: { type: 'string', maxLength: 128 }, after: { type: 'integer', minimum: 0 } }, ['jobId'], true),
  tool('hytale_deploy', 'Install a successful build into the isolated development runtime. Server must be stopped.', { jobId: { type: 'string', maxLength: 128 } }, ['jobId']),
  tool('hytale_start', 'Start one owned offline Hytale server bound to loopback. Does not launch a game client.'),
  tool('hytale_stop', 'Request native graceful shutdown of the server started by this MCP instance. Wait up to 15 seconds.'),
  tool('hytale_logs', 'Read bounded incremental stdout/stderr from a server process started by this MCP instance.',
    { after: { type: 'integer', minimum: 0 }, limit: { type: 'integer', minimum: 1, maximum: 300 }, contains: { type: 'string', minLength: 0, maxLength: 128 } }, [], true)
];

export function dispatcher(runtime) {
  return (name, args) => {
    switch (name) {
      case 'hytale_command': return runtime.command(args.command);
      case 'hytale_status': return runtime.status();
      case 'hytale_build': return runtime.build();
      case 'hytale_job': return runtime.job(args.jobId, args.after);
      case 'hytale_deploy': return runtime.deploy(args.jobId);
      case 'hytale_start': return runtime.start();
      case 'hytale_stop': return runtime.stop();
      case 'hytale_logs': return runtime.serverLogs(args.after, args.limit, args.contains);
      default: throw new Error(`Unsupported tool ${name}`);
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
