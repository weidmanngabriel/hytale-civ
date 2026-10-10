/** Normalize wheel events to small, predictable speed adjustments per wheel notch.
 * Pixel-mode wheels commonly emit ~100 px/notch; trackpads preserve fine movement.
 */
export function adjustFlightSpeed(current, deltaY, deltaMode = 0) {
  if (!Number.isFinite(current) || !Number.isFinite(deltaY)) return current;
  const pixels = deltaY * (deltaMode === 1 ? 16 : deltaMode === 2 ? 800 : 1);
  const notches = pixels / 100;
  return Math.max(0.5, Math.min(100, current * Math.pow(1.05, -notches)));
}
