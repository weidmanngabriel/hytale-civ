import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { LocalRuntime } from '../runtime.mjs';

test('command bridge returns direct command output without attach state', async () => {
  const requests = [];
  const server = createServer(async (req, res) => {
    if (req.url === '/health') {
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ ready: true, version: 1 }));
      return;
    }
    let bytes = ''; for await (const chunk of req) bytes += chunk;
    requests.push({ url: req.url, method: req.method, body: JSON.parse(bytes) });
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ success: true, command: 'version', output: ['Hytale test version'] }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const runtime = new LocalRuntime({
    root: process.cwd(),
    commandBridgePort: server.address().port,
    gamePort: 5521,
    serverJar: null,
    assetsPath: null
  });
  try {
    const status = await runtime.status();
    assert.equal(status.commandBridge.ready, true);
    const result = await runtime.command('version');
    assert.equal(result.success, true);
    assert.deepEqual(result.output, ['Hytale test version']);
    assert.deepEqual(requests, [{ url: '/command', method: 'POST', body: { command: 'version' } }]);
    assert.equal(runtime.child, null);
  } finally {
    await runtime.close();
    await new Promise(resolve => server.close(resolve));
  }
});

test('missing command bridge is reported clearly', async () => {
  const runtime = new LocalRuntime({
    root: process.cwd(),
    commandBridgePort: 65534,
    gamePort: 5521,
    serverJar: null,
    assetsPath: null
  });
  await assert.rejects(runtime.command('version', 100), /command bridge unavailable/i);
  const status = await runtime.status();
  assert.equal(status.commandBridge.ready, false);
});
