import * as THREE from 'three';
import { adjustFlightSpeed } from './flight-speed.js';
import { surfaceFaces } from './replay.js';

const $ = id => document.getElementById(id);
const canvas = $('canvas');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 1.5));
const scene = new THREE.Scene();
scene.background = new THREE.Color('#0e1820');
scene.add(new THREE.HemisphereLight(0xd5e3f7, 0x42555b, 2));
const camera = new THREE.PerspectiveCamera(70, 1, .05, 500);
camera.position.set(8, 10, 20);
let yaw = 0, pitch = -.25, flightSpeed = 8;
let world, running = false, currentScenario;
const keys = new Set();
const voxelGroup = new THREE.Group(), terrainGroup = new THREE.Group(), residentsGroup = new THREE.Group();
let importedTerrain = false;
scene.add(terrainGroup,voxelGroup,residentsGroup);
const voxelMaterials = [0x64748b,0x9cb2c2,0xd19d50,0x618d62];
const material = voxelMaterials.map(c=>new THREE.MeshLambertMaterial({color:c,side:THREE.FrontSide}));
const cube = new THREE.BoxGeometry(1,1,1);
const people = new Map();
let abort = false;
function look() { camera.rotation.set(pitch,yaw,0,'YXZ'); }
function showError(error) { $('message').textContent=error.message||String(error); }
async function request(path, value) {
  const response=await fetch('http://localhost:8765/api/'+path,{
    method:value?'POST':'GET',headers:value?{'Content-Type':'application/json'}:{},body:value?JSON.stringify(value):undefined,cache:'no-store'
  });
  if(!response.ok)throw new Error('API '+response.status+': '+(await response.text()));
  return response.json();
}
async function send(command,more={}) {
  try {await request('control',{command,...more}); await update();} catch(e) {showError(e);}
}
function clear(group) {for (const child of [...group.children]) {group.remove(child);child.geometry?.dispose();}}
function drawWorld(data) {
  // For the initial live slice, trees/sites become geometric landmarks.
  // Terrain snapshots will replace/extend this via the future world-import path.
  clear(voxelGroup);
  if (!importedTerrain) {
    const terrain = new THREE.Mesh(new THREE.BoxGeometry(60,1,60),new THREE.MeshLambertMaterial({color:0x455a4c,side:THREE.FrontSide}));
    terrain.position.set(0,-1,0);voxelGroup.add(terrain);
  }
  for(const tree of data.trees||[]) {
    const p=tree.position;
    const trunk=new THREE.Mesh(new THREE.BoxGeometry(.8,3,.8),material[2]);
    trunk.position.set(p.x+.5,1,p.z+.5);voxelGroup.add(trunk);
  }
  for(const site of data.constructionSites||[]) {
    const p=site.workPoint;
    const mesh=new THREE.Mesh(new THREE.BoxGeometry(2,.6,2),material[1]);
    mesh.position.set(p.x,.3,p.z);voxelGroup.add(mesh);
  }
}
function drawImportedTerrain(data) {
  if (!data.loaded) return;
  importedTerrain = true;
  const map = new Map(data.cells.map(([x,y,z,id])=>[`${x},${y},${z}`,id]));
  const colors = [0,0x657182,0x3184b7,0xdb6642,0x5ca5a0].map(x=>new THREE.Color(x));
  const positions = [], normals = [], vertexColors = [];
  for(const face of surfaceFaces(map)){
    const color = colors[face.material];
    for(const index of [0,1,2,0,2,3]){
      const corner = face.corners[index];
      positions.push(face.x+corner[0],face.y+corner[1],face.z+corner[2]);
      normals.push(...face.normal);
      vertexColors.push(color.r,color.g,color.b);
    }
  }
  const geom = new THREE.BufferGeometry();
  geom.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));
  geom.setAttribute('normal',new THREE.Float32BufferAttribute(normals,3));
  geom.setAttribute('color',new THREE.Float32BufferAttribute(vertexColors,3));
  const mesh = new THREE.Mesh(geom,new THREE.MeshLambertMaterial({vertexColors:true,side:THREE.FrontSide}));
  terrainGroup.add(mesh);
  const [minX,minY,minZ,maxX,maxY,maxZ] = data.bounds;
  camera.position.set((minX+maxX)/2,maxY+10,maxZ+15);
  pitch=-.5; yaw=0;look();
  flightSpeed=Math.max(2,Math.min(15,(maxX-minX)/8));
}
function drawResidents(list) {
  const current=new Set(list.map(x=>x.id));
  for(const [id,mesh] of people)if(!current.has(id)){residentsGroup.remove(mesh);mesh.geometry.dispose();people.delete(id);}
  for(const r of list){
    let mesh=people.get(r.id);
    if(!mesh){mesh=new THREE.Mesh(new THREE.CapsuleGeometry(.25,.9,4,8),new THREE.MeshLambertMaterial({color:0xf2a662}));residentsGroup.add(mesh);people.set(r.id,mesh);}
    mesh.position.set(r.position.x,r.position.y+.8,r.position.z);
  }
}
async function update() {
  if(abort)return;
  try {
    const data=await request('state');
    const changed=!world||data.scenario!==currentScenario;
    world=data.world; running=data.running;currentScenario=data.scenario;
    if(changed)$('scenario').value=currentScenario;
    $('play').textContent=running?'Pause':'Start';
    $('tick').textContent='Tick '+data.world.tickCount;
    $('info').textContent='Szenario: '+data.scenario+'\nZeit: '+data.world.elapsedSeconds.toFixed(2)+' s\nTempo: '+data.speed+'×';
    $('workers').textContent=data.world.residents.map(r=>r.id+' · '+r.profession+'\n'+r.state+' · '+[r.position.x,r.position.y,r.position.z].map(x=>x.toFixed(1)).join(', ')).join('\n\n');
    // Rebuild only when the fixture geometry changes.
    const sig=JSON.stringify([data.world.trees,data.world.constructionSites]);
    if(sig!==update.prevSig){update.prevSig=sig;drawWorld(data.world);}
    drawResidents(data.world.residents);
    $('message').textContent='';
  } catch(e){showError(e);}
}
$('configure').onclick=()=>send('configure',{woodcutters:Number($('woodcutters').value),builders:Number($('builders').value)});
$('play').onclick=()=>send(running?'pause':'play');
$('step').onclick=()=>send('step');
$('reset').onclick=()=>send('reset');
$('speed').onchange=()=>send('speed',{value:Number($('speed').value)});
$('scenario').onchange=()=>send('scenario',{id:$('scenario').value});
async function setup() {
  try {
    const scenarios=await request('scenarios');
    for(const s of scenarios){const opt=document.createElement('option');opt.value=s.id;opt.textContent=s.title;$('scenario').append(opt);}
    const terrain=await request('terrain');
    drawImportedTerrain(terrain);
    await update();
  } catch(e){showError(e);}
}
canvas.addEventListener('click',()=>canvas.requestPointerLock());
document.addEventListener('mousemove',e=>{
  if(document.pointerLockElement!==canvas)return;
  yaw-=e.movementX*.0025; pitch=THREE.MathUtils.clamp(pitch-e.movementY*.0025,-Math.PI/2+.01,Math.PI/2-.01);look();
});
window.addEventListener('keydown',e=>{
  if(['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName))return;
  if(['KeyW','KeyA','KeyS','KeyD','Space','ShiftLeft','ShiftRight'].includes(e.code)){keys.add(e.code);e.preventDefault();}
});
window.addEventListener('keyup',e=>keys.delete(e.code));
window.addEventListener('blur',()=>keys.clear());
canvas.addEventListener('wheel',e=>{flightSpeed=adjustFlightSpeed(flightSpeed,e.deltaY,e.deltaMode);e.preventDefault();},{passive:false});
let previous=performance.now();
function frame(time) {
  const dt=Math.min((time-previous)/1000,.1);previous=time;
  const move=new THREE.Vector3(Number(keys.has('KeyD'))-Number(keys.has('KeyA')),0,Number(keys.has('KeyS'))-Number(keys.has('KeyW')));
  move.applyQuaternion(camera.quaternion);
  move.y+=Number(keys.has('Space'))-Number(keys.has('ShiftLeft')||keys.has('ShiftRight'));
  if(move.lengthSq()>1)move.normalize();
  camera.position.addScaledVector(move,dt*flightSpeed);
  const rect=canvas.parentElement.getBoundingClientRect();
  renderer.setSize(rect.width,rect.height,false);
  camera.aspect=rect.width/Math.max(1,rect.height);camera.updateProjectionMatrix();
  $('camera').textContent='Kamera '+camera.position.toArray().map(x=>x.toFixed(1)).join(' / ')+' · Tempo '+flightSpeed.toFixed(1);
  renderer.render(scene,camera);
  requestAnimationFrame(frame);
}
look();requestAnimationFrame(frame);setup();
setInterval(update,100);
window.addEventListener('beforeunload',()=>abort=true);
