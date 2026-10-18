import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync(new URL('../live.html', import.meta.url), 'utf8');
const js = readFileSync(new URL('../src/live.js', import.meta.url), 'utf8');

test('live viewer defines every DOM id used by live.js', () => {
  const declared = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
  const referenced = [...js.matchAll(/\$\('([^']+)'\)/g)].map(match => match[1]);
  const duplicateIds = declared.filter((id, index) => declared.indexOf(id) !== index);
  assert.deepEqual(duplicateIds, [], 'HTML element IDs must be unique');
  const missing = [...new Set(referenced)].filter(id => !declared.includes(id));
  assert.deepEqual(missing, [], 'live.js references IDs missing from live.html');
});
