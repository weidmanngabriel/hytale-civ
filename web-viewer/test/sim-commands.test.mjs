import {test} from 'node:test';
import {strict as assert} from 'node:assert';
import {parseSimulationCommand as parse} from '../src/sim-commands.js';
test('supports prefab placement and block edits as typed control actions',()=>{
  assert.deepEqual(parse('/sim mine place -12 8 47'),{kind:'control',command:'placeMine',args:{x:-12,y:8,z:47}});
  assert.deepEqual(parse('/sim block set 1 2 3 air'),{kind:'control',command:'setBlock',args:{x:1,y:2,z:3,category:'AIR'}});
});
test('supports same-name read-only Civ developer diagnostics',()=>{
  assert.equal(parse('/civdev mines').name,'mines');
  assert.equal(parse('/civdebug mine info').name,'mineInfo');
  assert.equal(parse('/sim paths off').value,false);
});
test('rejects arbitrary Hytale commands and invalid coordinates',()=>{
  assert.equal(parse('/give player item').kind,'error');
  assert.equal(parse('/sim mine place nan 0 0').kind,'error');
  assert.equal(parse('/sim block set 1 2 3 TNT').kind,'error');
});
