export const SCHEMA_VERSION = 1;
export const key = (x, y, z) => `${x},${y},${z}`;
export const MATERIALS = ['Luft', 'Fels', 'Prefab', 'Stütze', 'Holz', 'Feld', 'Tür', 'Baustelle'];

function validBounds(bounds) {
  return Array.isArray(bounds) && bounds.length === 6 && bounds.every(Number.isInteger)
    && bounds.every(n => Math.abs(n) <= 100000)
    && bounds.slice(0,3).every((v,i) => v < bounds[i+3]);
}
export function insideBounds(bounds, x, y, z) {
  return !!bounds && x >= bounds[0] && y >= bounds[1] && z >= bounds[2]
    && x < bounds[3] && y < bounds[4] && z < bounds[5];
}
export function materialAt(world, rockBounds, x, y, z) {
  const k=key(x,y,z);
  if (world.has(k)) return world.get(k);
  return insideBounds(rockBounds,x,y,z) ? 1 : 0;
}

export function validateRecording(data) {
  if (data?.schemaVersion !== SCHEMA_VERSION) throw new Error('Unbekannte Aufzeichnungsversion.');
  if (typeof data.id !== 'string' || typeof data.title !== 'string' || typeof data.description !== 'string'
    || !['completed','failed'].includes(data.status) || !['seconds','semantic-step','semantic-slice'].includes(data.timeUnit))
    throw new Error('Ungültige Szenario-Metadaten.');
  if (data.rockBounds != null && !validBounds(data.rockBounds))
    throw new Error('Ungültiger impliziter Felsbereich.');
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
        if (material === 0 && !insideBounds(this.data.rockBounds,x,y,z)) this.world.delete(k);
        else this.world.set(k, material);
      }
    }
    return this.frame;
  }
  materialAt(x,y,z) { return materialAt(this.world,this.data.rockBounds,x,y,z); }
  get frame() { return this.data.frames[this.index]; }
}

// Only solid/air interfaces are meshed. Faces point from solid into air.
// Implicit rock renders only cavity interfaces, never the outer cuboid surface.
export const FACES = [
  { normal: [1,0,0], corners: [[1,0,0],[1,1,0],[1,1,1],[1,0,1]] },
  { normal: [-1,0,0], corners: [[0,0,1],[0,1,1],[0,1,0],[0,0,0]] },
  { normal: [0,1,0], corners: [[0,1,1],[1,1,1],[1,1,0],[0,1,0]] },
  { normal: [0,-1,0], corners: [[0,0,0],[1,0,0],[1,0,1],[0,0,1]] },
  { normal: [0,0,1], corners: [[1,0,1],[1,1,1],[0,1,1],[0,0,1]] },
  { normal: [0,0,-1], corners: [[0,0,0],[0,1,0],[1,1,0],[1,0,0]] },
];
export function surfaceFaces(world, rockBounds=null) {
  if (!rockBounds) {
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

  const result=[], seen=new Set();
  const add=(x,y,z,material,face,index)=>{
    const id=`${x},${y},${z}:${index}`;
    if(!seen.has(id)){seen.add(id);result.push({x,y,z,material,...face});}
  };
  // Every excavated cell exposes any neighboring implicit/explicit solid toward the cavity.
  for (const [k, material] of world) if (material===0) {
    const [ax,ay,az]=k.split(',').map(Number);
    FACES.forEach((face,index)=>{
      const [nx,ny,nz]=face.normal;
      const x=ax-nx,y=ay-ny,z=az-nz;
      const solid=materialAt(world,rockBounds,x,y,z);
      if(solid)add(x,y,z,solid,face,index);
    });
  }
  // Explicit structures inside excavated space need their own exposed faces.
  for (const [k, material] of world) if (material>0) {
    const [x,y,z]=k.split(',').map(Number);
    FACES.forEach((face,index)=>{
      const [nx,ny,nz]=face.normal;
      if(!materialAt(world,rockBounds,x+nx,y+ny,z+nz))add(x,y,z,material,face,index);
    });
  }
  return result;
}

// A deterministic picking oracle for the same front-facing interface rule used by WebGL.
export function visibleVoxel(world, origin, direction, maxDistance = 1000, rockBounds=null) {
  const length = Math.hypot(...direction);
  if (!length) return null;
  const d = direction.map(v => v / length);
  const cell = origin.map(Math.floor);
  const sign = d.map(v => Math.sign(v));
  const delta = d.map(v => v ? Math.abs(1/v) : Infinity);
  const next = d.map((v,i) => v > 0 ? (cell[i]+1-origin[i])/v : v < 0 ? (cell[i]-origin[i])/v : Infinity);
  let previous = materialAt(world,rockBounds,...cell);
  for (let count = 0; count < 100000; count++) {
    const distance = Math.min(...next);
    if (distance > maxDistance) return null;
    const before = [...cell];
    for (let i = 0; i < 3; i++) if (Math.abs(next[i]-distance) < 1e-9) {
      cell[i] += sign[i]; next[i] += delta[i];
    }
    const material = materialAt(world,rockBounds,...cell);
    const explicitCavity = world.get(key(...before)) === 0;
    if (!previous && material && (!rockBounds || explicitCavity))
      return { position: [...cell], adjacent: before, material, distance };
    previous = material;
  }
  return null;
}
