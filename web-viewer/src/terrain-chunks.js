import { FACES } from './replay.js';
export const CHUNK_SIZE=16;
export const chunkKey=(x,y,z)=>[x,y,z].map(v=>Math.floor(v/CHUNK_SIZE)).join(',');
export const cellKey=(x,y,z)=>x+','+y+','+z;
export function indexTerrain(cells) {
  return new Map(cells.map(([x,y,z,type])=>[cellKey(x,y,z),type]));
}
export function changedChunks(oldCells, nextCells) {
  const keys=new Set([...oldCells.keys(),...nextCells.keys()]);
  const dirty=new Set();
  for (const key of keys) {
    if (oldCells.get(key)===nextCells.get(key))continue;
    const [x,y,z]=key.split(',').map(Number);
    for(const [dx,dy,dz] of [[0,0,0],[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]])
      dirty.add(chunkKey(x+dx,y+dy,z+dz));
  }
  return dirty;
}
export function meshChunk(cells, chunk) {
  const [cx,cy,cz]=chunk.split(',').map(Number), min=[cx,cy,cz].map(v=>v*CHUNK_SIZE);
  const positions=[],normals=[],materials=[];
  for(let x=min[0];x<min[0]+CHUNK_SIZE;x++)
    for(let y=min[1];y<min[1]+CHUNK_SIZE;y++)
      for(let z=min[2];z<min[2]+CHUNK_SIZE;z++){
        const material=cells.get(cellKey(x,y,z));
        if(!material)continue;
        for(const face of FACES){
          const [nx,ny,nz]=face.normal;
          if(cells.has(cellKey(x+nx,y+ny,z+nz)))continue;
          for(const index of [0,1,2,0,2,3]){
            const corner=face.corners[index];
            positions.push(x+corner[0],y+corner[1],z+corner[2]);
            normals.push(nx,ny,nz);
            materials.push(material);
          }
        }
      }
  return {positions,normals,materials};
}
