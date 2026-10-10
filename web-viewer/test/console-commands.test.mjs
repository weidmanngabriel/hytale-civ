import {test} from 'node:test';
import {strict as assert} from 'node:assert';
import {parseSimulationCommand as parse} from '../src/console-commands.js';
test('parsed simulator commands dispatch to bounded server API',()=>{
  assert.deepEqual(parse('/sim mine place 10 2 -20'),{type:'api',command:'placeMine',args:{x:10,y:2,z:-20}});
  assert.deepEqual(parse('/sim miners 3'),{type:'api',command:'configureMiners',args:{miners:3}});
  assert.deepEqual(parse('/sim block 1 2 3 AIR'),{type:'api',command:'setBlock',args:{x:1,y:2,z:3,category:'AIR'}});
});
test('local overlays and civdev summary do not forward arbitrary native commands',()=>{
  assert.deepEqual(parse('/sim markers off'),{type:'overlay',name:'markers',enabled:false});
  assert.deepEqual(parse('/civdev mines'),{type:'info'});
  assert.throws(()=>parse('/civdev spawn 1'),/Nur \/sim/);
  assert.throws(()=>parse('/hytale_command something'),/Nur \/sim/);
  assert.throws(()=>parse('/sim miners 100'),/Ungültiger/);
});
