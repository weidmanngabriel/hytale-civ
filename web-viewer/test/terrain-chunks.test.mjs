import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { indexTerrain, chunkKey, changedChunks, meshChunk } from '../src/terrain-chunks.js';
test('negative and positive chunk boundaries are stable',()=>{
  assert.equal(chunkKey(-1,0,16),'-1,0,1');
  assert.equal(chunkKey(15,16,0),'0,1,0');
});
test('remeshing marks neighbor chunk when boundary block changes',()=>{
  const before=indexTerrain([[15,0,0,1],[16,0,0,1]]);
  const after=indexTerrain([[15,0,0,1]]);
  const changed=changedChunks(before,after);
  assert.ok(changed.has('0,0,0'));
  assert.ok(changed.has('1,0,0'));
  assert.equal(meshChunk(before,'0,0,0').positions.length,30*3);
  assert.equal(meshChunk(after,'0,0,0').positions.length,36*3);
});
test('unchanged snapshot touches no chunks',()=>{
  const a=indexTerrain([[0,0,0,1]]);
  assert.equal(changedChunks(a,indexTerrain([[0,0,0,1]])).size,0);
});
