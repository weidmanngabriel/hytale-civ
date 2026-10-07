import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { LocalRuntime } from '../runtime.mjs';

async function fixture(run) {
  const root = await mkdtemp(join(tmpdir(), 'civ-live-'));
  const token = 'local-test-token-32-characters-long';
  const sessionId = 'test-live-session';
  const requests = [];
  let mismatch = false;
  const server = createServer(async (req, res) => {
    let bytes = ''; for await (const chunk of req) bytes += chunk;
    const input = JSON.parse(bytes); requests.push(input);
    assert.equal(req.headers.authorization, `Bearer ${token}`);
    assert.equal(req.headers['x-civ-session'], sessionId);
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ ready: true, mode: 'live', bridgeVersion: 2, world: 'normal-save', worldUuid: 'world-id',
      sessionId: mismatch ? 'wrong-session' : sessionId, entities: [] }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const file = join(root, 'connection.json');
  const connection = { version: 1, mode: 'live', port: server.address().port, token, sessionId };
  await writeFile(file, JSON.stringify(connection));
  const runtime = new LocalRuntime({ root });
  try { await run({ runtime, file, server, requests, connection, mismatch: () => { mismatch = true; } }); }
  finally { await runtime.close(); await new Promise(resolve => server.close(resolve)); await rm(root, { recursive: true, force: true }); }
}

test('attach routes authenticated game actions but lifecycle never owns the external server', async () => {
  await fixture(async ({ runtime, file, server, requests }) => {
    const connected = await runtime.connect(file);
    assert.equal(connected.world, 'normal-save');
    assert.equal(runtime.child, null);
    assert.equal(JSON.stringify(connected).includes('local-test-token'), false);
    await runtime.bridge('context');
    await runtime.bridge('select', { uuid: 'existing-npc' });
    await assert.rejects(runtime.start(), /Disconnect/);
    await assert.rejects(runtime.deploy('job'), /Disconnect/);
    await assert.rejects(runtime.stop(), /cannot stop your live game/);
    assert.throws(() => runtime.serverLogs(), /stdout is not owned/);
    await runtime.disconnect();
    assert.equal(server.listening, true);
    await assert.rejects(runtime.bridge('context'), /Connect/);
    await runtime.connect(file);
    await runtime.close();
    assert.equal(server.listening, true);
    assert.deepEqual(requests.map(r => r.action), ['status', 'context', 'select', 'status']);
  });
});

test('invalid descriptors and stale sessions cannot establish attachment', async () => {
  await fixture(async ({ runtime, file, connection, mismatch, requests }) => {
    await writeFile(file, JSON.stringify({ ...connection, port: 80 }));
    await assert.rejects(runtime.connect(file), /Invalid live connection/);
    assert.equal(requests.length, 0);
    await writeFile(file, JSON.stringify(connection));
    mismatch();
    await assert.rejects(runtime.connect(file), /session mismatch/);
    assert.equal(runtime.attached, null);
  });
});
