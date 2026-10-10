import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { PerformanceRecorder } from '../src/performance-recorder.js';

test('records frame intervals and named costs into a shareable bounded JSON trace',()=>{
  let now=0;
  const recorder=new PerformanceRecorder(()=>now);
  recorder.start({world:'fixture'});
  recorder.frame(0);recorder.frame(20);recorder.frame(60);
  recorder.measure('mesh',42);
  now=1000;recorder.sample({ticks:3,terrainRevision:1});
  const report=recorder.stop();
  assert.equal(report.schema,'hytale-civ-live-performance');
  assert.equal(report.context.world,'fixture');
  assert.equal(report.samples[0].fps,2);
  assert.equal(report.samples[0].costs.mesh.totalMs,42);
  assert.equal(report.samples[0].ticks,3);
  assert.equal(recorder.active,false);
});
