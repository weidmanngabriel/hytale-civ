const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflow = fs.readFileSync(path.join(__dirname, '../.github/workflows/hytale-live.yml'), 'utf8').replace(/\r\n/g, '\n');
const source = workflow.split('        with:\n          script: |\n')[1]
  .split('\n  announce:')[0]
  .split('\n')
  .map(line => line.startsWith('            ') ? line.slice(12) : line)
  .join('\n');
const authorize = new Function('core', 'context', 'Buffer', source);

function check(body, {actor = 'weidmanngabriel', locked = true} = {}) {
  const outputs = {};
  let failure = null;
  const core = {
    setOutput: (k, v) => { outputs[k] = v; },
    setFailed: message => { failure = message; },
  };
  const context = {
    actor,
    payload: {
      issue: {number: 266, locked},
      comment: {body, user: {login: actor}},
      sender: {login: actor},
    },
  };
  authorize(core, context, Buffer);
  return {outputs, failure};
}
const batch = steps => '/hytale-live-batch runner gabe\n' + JSON.stringify({version: 1, steps});

test('accepts ordered read-only commands, wait and assertion', () => {
  const result = check(batch([
    {id:'list', action:'command', command:'npcs'},
    {id:'delay', action:'wait', seconds:2},
    {id:'verify', action:'assert', from:'list', contains:'CIVDEV_NPCS'}
  ]));
  assert.equal(result.failure, null);
  assert.equal(result.outputs.allowed, 'true');
  assert.equal(result.outputs.runner, 'gabe');
  const decoded = JSON.parse(Buffer.from(result.outputs.batch, 'base64').toString('utf8'));
  assert.equal(decoded.steps[0].command, 'civdev npcs');
  assert.equal(decoded.steps[1].seconds, 2);
});
test('keeps legacy single commands working', () => {
  const result = check('/hytale-live runner gabe mines');
  assert.equal(result.outputs.command, 'civdev mines');
  assert.equal(result.outputs.allowed, 'true');
});
test('rejects arbitrary command and batch reset', () => {
  for (const command of ['reset', 'stop', 'profession bad MINER', 'spawn HytaleServer 1 2 3']) {
    const result = check(batch([{id:'cmd', action:'command', command}]));
    assert.notEqual(result.outputs.allowed, 'true');
  }
});
test('rejects repeated ids and invalid assertion source', () => {
  assert.notEqual(check(batch([{id:'a',action:'wait',seconds:1},{id:'a',action:'wait',seconds:1}])).outputs.allowed,'true');
  assert.notEqual(check(batch([{id:'a',action:'assert',from:'missing',contains:'foo'}])).outputs.allowed,'true');
});
test('rejects waiting beyond total budget', () => {
  assert.notEqual(check(batch(Array.from({length:5}, (_,i) => ({id:'wait'+i, action:'wait', seconds:30})))).outputs.allowed, 'true');
});
test('rejects unauthorized actors and unlocked issue', () => {
  const request = batch([{id:'list',action:'command',command:'npcs'}]);
  assert.notEqual(check(request,{actor:'stranger'}).outputs.allowed,'true');
  assert.notEqual(check(request,{locked:false}).outputs.allowed,'true');
});
