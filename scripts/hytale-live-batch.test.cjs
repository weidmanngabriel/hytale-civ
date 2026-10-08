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


test('exposes typed player, block, building and mine recovery actions in batch', () => {
  const mine = 'fa733bf0-79b0-47b0-a6d0-9fb60f518120';
  const player = '5a1966f9-7a77-33d5-ad87-11b912ed28da';
  const actions = [
    'agent capabilities', 'agent players', 'agent buildings', 'agent sites',
    'agent block 700 120 400', 'agent set-block 700 120 400 Stone',
    'agent create-site ' + player + ' mine 700 120 400',
    'agent mine-recover ' + mine + ' status'
  ];
  const result = check(batch(actions.map((command, i) =>
    ({id:'agent'+i, action:'command', command}))));
  assert.equal(result.failure, null);
  assert.equal(result.outputs.allowed, 'true');
  const decoded = JSON.parse(Buffer.from(result.outputs.batch, 'base64').toString('utf8'));
  assert.deepEqual(decoded.steps.map(s => s.command), actions.map(s => s.replace(/^agent /, 'civagent ')));
});
test('agent commands work in single-command mode', () => {
  const result = check('/hytale-live runner gabe agent capabilities');
  assert.equal(result.outputs.command, 'civagent capabilities');
  assert.equal(result.outputs.allowed, 'true');
});
test('blocks arbitrary console commands and invalid agent arguments', () => {
  const forbidden = [
    'agent stop', 'agent shutdown', 'agent set-block 1 2 3 Stone; stop',
    'agent set-block 1 2 3 ../../bad', 'agent set-block 1 2 3',
    'agent create-site abc mine 1 2 3', 'agent create-site 00000000-0000-0000-0000-000000000000 unknown 1 2 3',
    'agent mine-recover 00000000-0000-0000-0000-000000000000 reset',
    'agent block 1 2 300000',
    'agent capabilities && stop'
  ];
  for (const command of forbidden) {
    const result = check(batch([{id:'bad', action:'command', command}]));
    assert.notEqual(result.outputs.allowed, 'true', command);
  }
});
