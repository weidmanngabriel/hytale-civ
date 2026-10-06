export const SCHEMA_VERSION = 1;
export const key = (x, y, z) => `${x},${y},${z}`;
export const MATERIALS = ['Luft', 'Fels', 'Prefab', 'Stütze', 'Holz', 'Feld', 'Tür', 'Baustelle'];

export function validateRecording(data) {
  if (data?.schemaVersion !== SCHEMA_VERSION) throw new Error('Unbekannte Aufzeichnungsversion.');
  if (typeof data.id !== 'string' || typeof data.title !== 'string' || typeof data.description !== 'string'
    || !['completed','failed'].includes(data.status) || !['seconds','semantic-step'].includes(data.timeUnit))
    throw new Error('Ungültige Szenario-Metadaten.');
  if (!Array.isArray(data.markers) || !data.markers.every(m => typeof m.type === 'string'
    && Array.isArray(m.bounds) && m.bounds.length === 6 && m.bounds.every(Number.isFinite)
    && m.bounds.slice(0,3).every((v,i) => v <= m.bounds[i+3])))
    throw new Error('Ungültige Marker.');
  if (!Array.isArray(data.initialVoxels) || !Array.isArray(data.frames) || !data.frames.length)
    throw new Error('Aufzeichnung hat keinen Startzustand.');
  const voxel = c => Array.isArray(c) && c.length === 4 && c.every(Number.isInteger)
    && c.slice(0, 3).every(n => Math.abs(n) <= 100000) && c[3] >= 0 && c[3] < MATERIALS.length;
  if (!data.initialVoxels.every(voxel)) throw new Error('Ungültige Blockdaten.');
  for (const [i, f] of data.frames.entries()) {
    if (f.step !== i || !Number.isFinite(f.time) || !Array.isArray(f.changes)
      || !f.changes.every(voxel) || !Array.isArray(f.residents)) throw new Error('Ungültiger Schritt.');
    if (i && f.time < data.frames[i-1].time) throw new Error('Zeit läuft rückwärts.');
    if (i === 0 && f.changes.length) throw new Error('Startschritt darf keine Änderungen enthalten.');
    for (const r of f.residents) {
      if (typeof r.id !== 'string' || ![r.position?.x, r.position?.y, r.position?.z].every(Number.isFinite))
        throw new Error('Ungültige Arbeiterposition.');
    }
  }
  return data;
}

export class Replay {
  constructor(data) {
    this.data = validateRecording(data);
    this.reset();
  }
  reset() {
    this.world = new Map(this.data.initialVoxels.filter(c => c[3]).map(c => [key(...c), c[3]]));
    this.index = 0;
  }
  seek(index) {
    index = Math.max(0, Math.min(this.data.frames.length - 1, Math.trunc(index)));
    if (index < this.index) this.reset();
    while (this.index < index) {
      for (const [x, y, z, material] of this.data.frames[++this.index].changes) {
        const k = key(x, y, z);
        if (material === 0) this.world.delete(k); else this.world.set(k, material);
      }
    }
    return this.frame;
  }
  get frame() { return this.data.frames[this.index]; }
}

// Only solid/air interfaces are meshed. Faces point from solid into air.
// From inside solid, exit faces are back faces and disappear; the next cavity wall faces the camera.
export const FACES = [
  { normal: [1,0,0], corners: [[1,0,0],[1,1,0],[1,1,1],[1,0,1]] },
  { normal: [-1,0,0], corners: [[0,0,1],[0,1,1],[0,1,0],[0,0,0]] },
  { normal: [0,1,0], corners: [[0,1,1],[1,1,1],[1,1,0],[0,1,0]] },
  { normal: [0,-1,0], corners: [[0,0,0],[1,0,0],[1,0,1],[0,0,1]] },
  { normal: [0,0,1], corners: [[1,0,1],[1,1,1],[0,1,1],[0,0,1]] },
  { normal: [0,0,-1], corners: [[0,0,0],[0,1,0],[1,1,0],[1,0,0]] },
];
export function surfaceFaces(world) {
  const result = [];
  for (const [k, material] of world) {
    const [x,y,z] = k.split(',').map(Number);
    for (const face of FACES) {
      const [nx,ny,nz] = face.normal;
      if (!world.has(key(x+nx,y+ny,z+nz))) result.push({ x,y,z,material,...face });
    }
  }
  return result;
}

// A deterministic picking oracle for the same front-facing interface rule used by WebGL.
export function visibleVoxel(world, origin, direction, maxDistance = 1000) {
  const length = Math.hypot(...direction);
  if (!length) return null;
  const d = direction.map(v => v / length);
  const cell = origin.map(Math.floor);
  const sign = d.map(v => Math.sign(v));
  const delta = d.map(v => v ? Math.abs(1/v) : Infinity);
  const next = d.map((v,i) => v > 0 ? (cell[i]+1-origin[i])/v : v < 0 ? (cell[i]-origin[i])/v : Infinity);
  let previous = world.get(key(...cell)) || 0;
  // A face is visible only on an air -> solid transition, including when starting inside solid.
  for (let count = 0; count < 100000; count++) {
    const distance = Math.min(...next);
    if (distance > maxDistance) return null;
    const before = [...cell];
    for (let i = 0; i < 3; i++) if (Math.abs(next[i]-distance) < 1e-9) {
      cell[i] += sign[i]; next[i] += delta[i];
    }
    const material = world.get(key(...cell)) || 0;
    if (!previous && material) return { position: [...cell], adjacent: before, material, distance };
    previous = material;
  }
  return null;
}
