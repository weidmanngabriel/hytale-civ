import test from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { Replay, key, surfaceFaces, visibleVoxel } from '../src/replay.js';
import { sortBranchesByLatestCommit } from '../src/catalog.js';
import { selectRuns, validateBundle } from '../../scripts/collect-simulation-recordings.mjs';

const sample = () => ({schemaVersion:1,id:'mine-north',title:'Mine',description:'Fixture',timeUnit:'semantic-step',status:'completed',initialVoxels:[[0,0,0,1],[1,0,0,1]],markers:[],frames:[
  {step:0,time:0,changes:[],residents:[]},
  {step:1,time:1,changes:[[0,0,0,0]],residents:[]},
  {step:2,time:2,changes:[[0,0,0,3]],residents:[]}
]});
const implicitSample = () => ({schemaVersion:1,id:'mine-xray',title:'Mine X-Ray',description:'Fixture',timeUnit:'semantic-step',status:'completed',initialVoxels:[],rockBounds:[0,0,0,8,3,3],markers:[],frames:[
  {step:0,time:0,changes:[],residents:[]},
  {step:1,time:1,changes:[[3,1,1,0],[4,1,1,0]],residents:[]}
]});
test('seeking backwards reconstructs removed rock and forward support placement exactly',()=>{
  const replay=new Replay(sample());
  replay.seek(2);assert.equal(replay.world.get('0,0,0'),3);
  replay.seek(1);assert.equal(replay.world.has('0,0,0'),false);
  replay.seek(0);assert.equal(replay.world.get('0,0,0'),1);
  replay.seek(2);assert.equal(replay.world.get('1,0,0'),1);
});
test('accepts semantic-slice mine recordings',()=>{
  const replay=new Replay({...sample(),timeUnit:'semantic-slice'});
  assert.equal(replay.data.timeUnit,'semantic-slice');
});
test('reject incompatible replay data instead of showing an old or partial state',()=>{
  assert.throws(()=>new Replay({...sample(),schemaVersion:2}));
  assert.throws(()=>new Replay({...sample(),rockBounds:[0,0,0,0,1,1]}));
  const data=sample();data.frames[1].changes=[[1,2,3,9]];assert.throws(()=>new Replay(data));
});
test('implicit rock keeps the full x-ray volume without expanding it into voxels',()=>{
  const replay=new Replay(implicitSample());
  assert.equal(replay.materialAt(2,1,1),1);
  assert.equal(surfaceFaces(replay.world,replay.data.rockBounds).length,0);
  replay.seek(1);
  assert.equal(replay.materialAt(3,1,1),0);
  assert.equal(replay.materialAt(5,1,1),1);
  assert.equal(surfaceFaces(replay.world,replay.data.rockBounds).length,10);
  assert.deepEqual(visibleVoxel(replay.world,[.5,1.5,1.5],[1,0,0],1000,replay.data.rockBounds).position,[5,1,1]);
  assert.deepEqual(visibleVoxel(replay.world,[-2,1.5,1.5],[1,0,0],1000,replay.data.rockBounds).position,[5,1,1]);
});
test('solid interfaces have outward winding and no faces between neighboring solid materials',()=>{
  const world=new Map([['0,0,0',1],['1,0,0',3]]);
  const faces=surfaceFaces(world);assert.equal(faces.length,10);
  for(const f of faces){
    const [a,b,c]=f.corners.map(v=>new THREE.Vector3(...v));
    const cross=b.sub(a).cross(c.sub(a)).normalize();
    assert.deepEqual(cross.toArray().map(v=>v===0?0:v),f.normal);
  }
});
test('spectator sees the far cavity wall from solid, inside a tunnel and from outside',()=>{
  const world=new Map();
  // Thick wall, a two-block cavity, then another wall; material changes must not create false walls.
  for(let x=0;x<8;x++)if(x!==3&&x!==4)world.set(key(x,0,0),x===1?2:1);
  assert.deepEqual(visibleVoxel(world,[.5,.5,.5],[1,0,0]).position,[5,0,0]);
  assert.deepEqual(visibleVoxel(world,[3.5,.5,.5],[1,0,0]).position,[5,0,0]);
  assert.deepEqual(visibleVoxel(world,[-2,.5,.5],[1,0,0]).position,[0,0,0]);
  assert.deepEqual(visibleVoxel(world,[6.5,.5,.5],[-1,0,0]).position,[2,0,0]);
  assert.equal(visibleVoxel(world,[.5,.5,.5],[-1,0,0]),null);
});
test('WebGL front-side triangles hit the same far wall as the picking oracle',()=>{
  const world=new Map();for(let x=0;x<8;x++)if(x!==3&&x!==4)world.set(key(x,0,0),1);
  const vertices=[];
  for(const f of surfaceFaces(world))for(const i of [0,1,2,0,2,3])vertices.push(...f.corners[i].map((v,j)=>v+[f.x,f.y,f.z][j]));
  const geometry=new THREE.BufferGeometry();geometry.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));
  const mesh=new THREE.Mesh(geometry,new THREE.MeshBasicMaterial({side:THREE.FrontSide}));mesh.updateMatrixWorld();
  const ray=new THREE.Raycaster(new THREE.Vector3(.5,.5,.5),new THREE.Vector3(1,0,0));
  const hit=ray.intersectObject(mesh)[0];assert.equal(hit.point.x,5);
  mesh.geometry.dispose();mesh.material.dispose();
});
const run={id:123,run_attempt:1,head_sha:'a'.repeat(40),head_branch:'feature/miner',created_at:'2026-10-06T00:00:00Z',head_repository:{full_name:'owner/repo'},event:'push',status:'completed',conclusion:'success'};
test('publisher preserves branch/commit/run provenance and rejects path injection',()=>{
  const files={'manifest.json':{schemaVersion:1,commit:run.head_sha,branch:run.head_branch,runId:'123',runAttempt:'1',scenarios:[{id:'mine-north',title:'Mine',file:'mine-north.json',status:'completed',frames:3}]},'mine-north.json':sample(),'ci-status.json':{testStatus:'passed'}};
  assert.equal(validateBundle(files,run).testStatus,'passed');
  assert.throws(()=>validateBundle(files,{...run,head_sha:'b'.repeat(40)}));
  files['manifest.json'].scenarios[0].file='../evil.json';assert.throws(()=>validateBundle(files,run));
});
test('branch selector keeps main first and orders other branches by newest commit',()=>{
  const branches=sortBranchesByLatestCommit([
    {branch:'feature/old',createdAt:'2026-10-07T09:00:00Z',commitAt:'2026-10-07T08:00:00Z'},
    {branch:'main',createdAt:'2026-10-01T09:00:00Z',commitAt:'2026-10-01T08:00:00Z'},
    {branch:'feature/new',createdAt:'2026-10-07T08:00:00Z',commitAt:'2026-10-07T10:00:00Z'},
    {branch:'feature/old',createdAt:'2026-10-07T11:00:00Z',commitAt:'2026-10-07T08:30:00Z'}
  ]);
  assert.deepEqual(branches,['main','feature/new','feature/old']);
});
test('catalog includes failed internal runs, excludes foreign and expired runs, caps each branch',()=>{
  const now=Date.parse('2026-10-06T00:00:00Z');
  const runs=[{...run,head_branch:'other',conclusion:'failure'},...Array.from({length:5},(_,i)=>({...run,id:i+1})),{...run,head_repository:{full_name:'evil/fork'}},{...run,created_at:'2026-01-01T00:00:00Z'}];
  const result=selectRuns(runs,'owner/repo',now);
  assert.equal(result.length,4);assert.equal(result.filter(r=>r.head_branch==='feature/miner').length,3);
  assert.ok(result.some(r=>r.conclusion==='failure'));
});
