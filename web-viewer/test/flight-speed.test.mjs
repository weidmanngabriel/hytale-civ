import test from 'node:test';
import assert from 'node:assert/strict';
import { adjustFlightSpeed } from '../src/flight-speed.js';

test('three wheel notches change speed gently, not threefold', () => {
  const fast = adjustFlightSpeed(10, -300);
  const slow = adjustFlightSpeed(10, 300);
  assert.ok(fast > 11 && fast < 12);
  assert.ok(slow > 8 && slow < 10);
});
test('fractional wheel movement supports fine control', () => {
  assert.ok(adjustFlightSpeed(10, -10) > 10);
  assert.ok(adjustFlightSpeed(10, -10) < 10.1);
});
test('wheel delta modes and speed bounds are respected', () => {
  assert.equal(adjustFlightSpeed(100, -1000), 100);
  assert.equal(adjustFlightSpeed(0.5, 1000), 0.5);
  assert.ok(adjustFlightSpeed(10, -3, 1) > 10);
  assert.equal(adjustFlightSpeed(10, Number.NaN), 10);
});
