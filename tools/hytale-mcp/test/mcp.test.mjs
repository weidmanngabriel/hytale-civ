import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, symlink, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn, execFileSync } from 'node:child_process';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { createServer } from 'node:net';
import { createProtocol } from '../protocol.mjs';
import { TOOLS } from '../server.mjs';
import { LogBuffer, configuration, ownedDirectory, LocalRuntime } from '../runtime.mjs';

const initialize = { jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2025-11-25', capabilities: {}, clientInfo: { name: 'test', version: '1' } } };
async function ready(handle) {
  assert.equal((await handle(initialize)).result.protocolVersion, '2025-11-25');
  await handle({ jsonrpc: '2.0', method: 'notifications/initialized' });
}
test('MCP lifecycle and invalid mutations never reach runtime', async () => {
  const calls = [];
  const handle = createProtocol(TOOLS, async (name, args) => { calls.push({ name, args }); return { ok: true }; });
  assert.equal((await handle({ jsonrpc: '2.0', id: 0, method: 'tools/list' })).error.code, -32000);
  await ready(handle);
  const toolCall = arguments_ => handle({ jsonrpc: '2.0', id: 2, method: 'tools/call', params: { name: 'hytale_move', arguments: arguments_ } });
  assert.equal((await toolCall({ handle: 'test', x: 0, y: 1, z: 0, command: 'arbitrary' })).error.code, -32602);
  assert.equal((await toolCall({ handle: 'test', x: 30000001, y: 1, z: 0 })).error.code, -32602);
  assert.equal(calls.length, 0);
  assert.equal((await toolCall({ handle: 'test', x: 0, y: 1, z: 0 })).result.isError, undefined);
  assert.equal(calls.length, 1);
  assert.equal((await handle({ jsonrpc: '2.0', id: 3, method: 'tools/list' })).result.tools.length, TOOLS.length);
});
test('tool failures remain MCP tool results, not successful operations', async () => {
  const handle = createProtocol(TOOLS, async () => { throw new Error('Server not ready'); });
  await ready(handle);
  const response = await handle({ jsonrpc: '2.0', id: 2, method: 'tools/call', params: { name: 'hytale_arena', arguments: {} } });
  assert.equal(response.result.isError, true);
  assert.match(response.result.content[0].text, /not ready/);
});
test('log cursor supports bounded incremental observation and reports eviction', () => {
  const logs = new LogBuffer(2);
  logs.add('stdout', 'first'); logs.add('stdout', 'second'); logs.add('stderr', 'third');
  assert.equal(logs.read().truncatedBefore, 2);
  assert.deepEqual(logs.read(1, 1).lines.map(line => line.text), ['second']);
  assert.equal(logs.read(2).nextCursor, 3);
  assert.equal(logs.read(1, 5, 'third').lines.length, 1);
});
test('deployment cannot adopt unrelated data or follow a runtime link', async () => {
  const root = await mkdtemp(join(tmpdir(), 'civ-mcp-'));
  try {
    const path = join(root, 'dev', 'runtime');
    await mkdir(path, { recursive: true }); await writeFile(join(path, 'keep.txt'), 'original');
    await assert.rejects(ownedDirectory(path, root), /non-empty/);
    await rm(path, { recursive: true });
    await ownedDirectory(path, root);
    await assert.rejects(ownedDirectory(path, root + '-other'), /different checkout/);
    await rm(path, { recursive: true });
    await symlink(root, path, process.platform === 'win32' ? 'junction' : 'dir');
    await assert.rejects(ownedDirectory(path, root), /must not be a link/);
  } finally { await rm(root, { recursive: true, force: true }); }
});
test('configuration needs no package install and rejects invalid settings', async () => {
  const root = await mkdtemp(join(tmpdir(), 'civ-config-'));
  try {
    const config = join(root, '.hytale-dev.json');
    assert.equal((await configuration(root, config, {})).serverJar, null);
    await writeFile(config, JSON.stringify({ gamePort: 'bad' }));
    await assert.rejects(configuration(root, config, {}), /Ports/);
    await writeFile(config, JSON.stringify({ command: 'bad' }));
    await assert.rejects(configuration(root, config, {}), /Unknown setting/);
    const runtime = new LocalRuntime(await configuration(root, join(root, 'absent.json'), {}));
    await assert.rejects(runtime.start(), /Build and deploy/);
    await assert.rejects(runtime.deploy('unknown'), /successful build/);
  } finally { await rm(root, { recursive: true, force: true }); }
});
test('actual stdio handshake returns only JSON-RPC and discoverable tools', async () => {
  const child = spawn(process.execPath, [fileURLToPath(new URL('../server.mjs', import.meta.url))], { stdio: ['pipe', 'pipe', 'pipe'] });
  const responses = [];
  const reader = createInterface({ input: child.stdout });
  reader.on('line', line => responses.push(JSON.parse(line)));
  const exit = new Promise(resolve => child.on('close', resolve));
  child.stdin.write(JSON.stringify(initialize) + '\n');
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: 2, method: 'tools/list' }) + '\n');
  for (let i = 0; i < 100 && responses.length < 2; i++) await new Promise(resolve => setTimeout(resolve, 10));
  child.stdin.end();
  assert.equal(await exit, 0);
  assert.equal(responses.length, 2);
  assert.equal(responses[1].result.tools.length, TOOLS.length);
});

test('build/deploy/start/status/stop lifecycle with owned test child (not Hytale)', { skip: process.platform === 'win32' }, async () => {
  const root = await mkdtemp(join(tmpdir(), 'civ-lifecycle-'));
  let runtime;
  try {
    await mkdir(join(root, 'build', 'libs'), { recursive: true });
    await mkdir(join(root, 'asset-pack'));
    await writeFile(join(root, 'gradle.properties'), 'projectVersion=0.1.0-SNAPSHOT\nartifactBaseName=hytale-civ\n');
    await writeFile(join(root, 'build', 'libs', 'hytale-civ-0.1.0-SNAPSHOT.jar'), 'test plugin');
    await writeFile(join(root, 'asset-pack', 'manifest.json'), '{}');
    await writeFile(join(root, 'gradlew'), '#!/bin/sh\necho test-build-output\nexit 0\n', { mode: 0o755 });
    await writeFile(join(root, '.gitignore'), '.hytale-dev/\n');
    execFileSync('git', ['init', '-q'], { cwd: root });
    execFileSync('git', ['add', '.'], { cwd: root });
    execFileSync('git', ['-c', 'user.name=MCP Test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'test fixture'], { cwd: root });
    const listener = createServer();
    await new Promise(resolve => listener.listen(0, '127.0.0.1', resolve));
    const bridgePort = listener.address().port;
    await new Promise(resolve => listener.close(resolve));
    const fakeJava = join(root, 'test-java');
    await writeFile(fakeJava, `#!/usr/bin/env node
const http = require('node:http');
const readline = require('node:readline');
const port = Number(process.argv.find(a => a.startsWith('-Dcivilizations.devBridgePort=')).split('=')[1]);
const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({ ready: true, sessionId: process.env.CIV_DEV_SESSION }));
}).listen(port, '127.0.0.1');
readline.createInterface({ input: process.stdin }).on('line', line => {
  if (line === 'stop') server.close(() => process.exit(0));
});
`, { mode: 0o755 });
    runtime = new LocalRuntime({ root, runtime: join(root, '.hytale-dev', 'runtime'),
      javaExecutable: fakeJava, serverJar: fakeJava, assetsPath: join(root, 'asset-pack'), bridgePort, gamePort: 5521, maxHeapMb: 512 });
    const build = await runtime.build();
    for (let i = 0; i < 100 && runtime.job(build.jobId).state === 'running'; i++) await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(runtime.job(build.jobId).state, 'succeeded');
    assert.match(runtime.job(build.jobId).output.lines[0].text, /test-build-output/);
    const deployed = await runtime.deploy(build.jobId);
    assert.match(deployed.jarSha256, /^[a-f0-9]{64}$/);
    await runtime.start();
    for (let i = 0; i < 100; i++) {
      if ((await runtime.status()).bridge.ready) break;
      await new Promise(resolve => setTimeout(resolve, 10));
    }
    assert.equal((await runtime.status()).bridge.ready, true);
    await assert.rejects(runtime.deploy(build.jobId), /Stop the development server/);
    assert.equal((await runtime.stop()).state, 'stopped');
    assert.equal((await runtime.status()).running, false);
  } finally {
    if (runtime) await runtime.close();
    await rm(root, { recursive: true, force: true });
  }
});
