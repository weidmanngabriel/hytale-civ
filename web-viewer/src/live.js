import * as THREE from 'three';
import { adjustFlightSpeed } from './flight-speed.js';
import { indexTerrain, changedChunks, chunkKey, meshChunk } from './terrain-chunks.js';
import { PerformanceRecorder, downloadPerformance } from './performance-recorder.js';
import {parseSimulationCommand,CONSOLE_HELP} from './console-commands.js';
const profiler = new PerformanceRecorder();
let lastState = null, lastTerrainStats = {cells:0, triangles:0};
let indexedTerrain=new Map();
const terrainMeshes=new Map();


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
let mineMode = false, lastDebug = null;
const debugGroup = new THREE.Group();
scene.add(debugGroup);
let lastTerrainRevision = -1;
let lastTerrainEpoch = -1;
let selectedResident = "";
scene.add(terrainGroup,voxelGroup,residentsGroup);
const voxelMaterials = [0x64748b,0x9cb2c2,0xd19d50,0x618d62];
const material = voxelMaterials.map(c=>new THREE.MeshLambertMaterial({color:c,side:THREE.FrontSide}));
const cube = new THREE.BoxGeometry(1,1,1);
const people = new Map();
let abort = false;
function look() { camera.rotation.set(pitch,yaw,0,'YXZ'); }
function showError(error) { $('message').textContent=error.message||String(error); }
async function request(path, value) {
  const started=performance.now();
  const response=await fetch('http://localhost:8765/api/'+path,{
    method:value?'POST':'GET',headers:value?{'Content-Type':'application/json'}:{},body:value?JSON.stringify(value):undefined,cache:'no-store'
  });
  if(!response.ok)throw new Error('API '+response.status+': '+(await response.text()));
  const data=await response.json();
  profiler.measure('api:'+path,performance.now()-started);
  return data;
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
  const begun=performance.now();
  let dirty;
  if (Array.isArray(data.changes)) {
    dirty=new Set();
    for(const [x,y,z,type] of data.changes) {
      const id=x+','+y+','+z;
      const previous=indexedTerrain.get(id)||0;
      if(previous===type)continue;
      if(type===0)indexedTerrain.delete(id);else indexedTerrain.set(id,type);
      for(const [dx,dy,dz] of [[0,0,0],[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]])
        dirty.add(chunkKey(x+dx,y+dy,z+dz));
    }
  } else {
    const next=indexTerrain(data.cells);
    dirty=changedChunks(indexedTerrain,next);
    indexedTerrain=next;
  }
  profiler.measure('terrain:diff',performance.now()-begun);
  const colors=[0,0x657182,0x3184b7,0xdb6642,0x5ca5a0].map(c=>new THREE.Color(c));
  const meshStarted=performance.now();
  for(const key of dirty){
    const old=terrainMeshes.get(key);
    if(old){terrainGroup.remove(old);old.geometry.dispose();terrainMeshes.delete(key);}
    const data=meshChunk(indexedTerrain,key);
    if(!data.positions.length)continue;
    const vertexColors=[];
    for(const type of data.materials){
      const color=colors[type]||colors[1];
      vertexColors.push(color.r,color.g,color.b);
    }
    const geom=new THREE.BufferGeometry();
    geom.setAttribute('position',new THREE.Float32BufferAttribute(data.positions,3));
    geom.setAttribute('normal',new THREE.Float32BufferAttribute(data.normals,3));
    geom.setAttribute('color',new THREE.Float32BufferAttribute(vertexColors,3));
    const mesh=new THREE.Mesh(geom,new THREE.MeshLambertMaterial({vertexColors:true,side:THREE.FrontSide}));
    terrainGroup.add(mesh);terrainMeshes.set(key,mesh);
  }
  profiler.measure('terrain:meshChunks',performance.now()-meshStarted);
  lastTerrainStats={cells:indexedTerrain.size, chunks:terrainMeshes.size,
    triangles:[...terrainMeshes.values()].reduce((sum,m)=>sum+m.geometry.getAttribute('position').count/3,0),
    dirtyChunks:dirty.size};
  if (!drawImportedTerrain.initialized && data.bounds) {
    const [minX,minY,minZ,maxX,maxY,maxZ] = data.bounds;
    camera.position.set((minX+maxX)/2,maxY+10,maxZ+15);
    pitch=-.5; yaw=0;look();
    flightSpeed=Math.max(2,Math.min(15,(maxX-minX)/8));
    drawImportedTerrain.initialized = true;
  }
}

function drawDebug(data) {
  for(const item of [...debugGroup.children]) {
    debugGroup.remove(item);item.geometry?.dispose();item.material?.dispose();
  }
  if(!mineMode || !data)return;
  const showMarkers=$('show-markers').checked,showPaths=$('show-paths').checked;
  const mine=data.mine||{},prefab=data.prefab||{};
  const markerColor={building_bounds:0x6d9cef,workplace_access:0x68dfa1,
    mine_tunnel_connector:0xefb966,construction_ground_level:0xd0da85};
  if(showMarkers) {
    for(const marker of prefab.markers||[]){
      const b=marker.bounds;
      const size=new THREE.Vector3(b.maxX-b.minX,b.maxY-b.minY,b.maxZ-b.minZ);
      const geo=new THREE.EdgesGeometry(new THREE.BoxGeometry(Math.max(.05,size.x),Math.max(.05,size.y),Math.max(.05,size.z)));
      const line=new THREE.LineSegments(geo,new THREE.LineBasicMaterial({color:markerColor[marker.type]||0xffffff}));
      line.position.set((b.minX+b.maxX)/2,(b.minY+b.maxY)/2,(b.minZ+b.maxZ)/2);
      debugGroup.add(line);
    }
    if(mine.loaded) {
      const plan=(mine.plannedSlices||[]).map(p=>new THREE.Vector3(p.x+.5,p.y+1,p.z+.5));
      if(plan.length>=2) {
        const route=new THREE.Line(new THREE.BufferGeometry().setFromPoints(plan),
          new THREE.LineBasicMaterial({color:0xc285f0}));debugGroup.add(route);
      }
      if(mine.workFront) {
        const p=mine.workFront;
        const marker=new THREE.Mesh(new THREE.SphereGeometry(.55,10,8),
          new THREE.MeshBasicMaterial({color:0xffc857,transparent:true,opacity:.75}));
        marker.position.set(p.x+.5,p.y+1,p.z+.5);debugGroup.add(marker);
      }
      for(const task of mine.infrastructureTasks||[]){
        const p=task.anchor;
        const taskMarker=new THREE.Mesh(new THREE.OctahedronGeometry(.36),
          new THREE.MeshBasicMaterial({color:task.type==='BUILD_SUPPORT'?0x5bdf90:0xcf80e6,
            wireframe:true}));
        taskMarker.position.set(p.x+.5,p.y+1.4,p.z+.5);debugGroup.add(taskMarker);
      }
    }
  }
  if(showPaths)for(const w of mine.workers||[]){
    const resident=world?.residents?.find(r=>r.id===w.id);
    const points=[];
    if(resident)points.push(new THREE.Vector3(resident.position.x,resident.position.y+.8,resident.position.z));
    for(const p of w.route||[])points.push(new THREE.Vector3(p.x+.5,p.y+.8,p.z+.5));
    if(points.length>=2){
      const blocked=w.state==='NAVIGATION_BLOCKED';
      debugGroup.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(points),
        new THREE.LineBasicMaterial({color:blocked?0xff5757:0x50e2ee})));
    }
    if(w.target&&resident){
      const p=w.target;
      const dot=new THREE.Mesh(new THREE.SphereGeometry(.2,8,6),
        new THREE.MeshBasicMaterial({color:w.state==='NAVIGATION_BLOCKED'?0xff5555:0x50e2ee}));
      dot.position.set(p.x,p.y+.8,p.z);debugGroup.add(dot);
    }
  }
}
function focusMine() {
  const b=(lastDebug?.prefab?.markers||[]).find(m=>m.type==='workplace_access')?.bounds;
  if(!b){consoleLog('Noch kein Mine_01-Prefab platziert.');return;}
  const target=new THREE.Vector3((b.minX+b.maxX)/2,(b.minY+b.maxY)/2,(b.minZ+b.maxZ)/2);
  camera.position.copy(target).add(new THREE.Vector3(13,10,18));
  const d=target.clone().sub(camera.position).normalize();
  yaw=Math.atan2(-d.x,-d.z);
  pitch=Math.asin(d.y);
  look();
}
function consoleLog(message) {
  const output=$('console-output');
  output.textContent=(output.textContent+'\n'+message).split('\n').slice(-16).join('\n');
  output.scrollTop=output.scrollHeight;
}
async function executeConsole() {
  const source=$('command-input').value.trim();
  if(!source)return;
  consoleLog('> '+source);
  try {
    const parsed=parseSimulationCommand(source);
    if(parsed.type==='help')consoleLog(CONSOLE_HELP);
    else if(parsed.type==='overlay'){
      $(parsed.name==='markers'?'show-markers':'show-paths').checked=parsed.enabled;
      drawDebug(lastDebug);consoleLog(parsed.name+' '+(parsed.enabled?'AN':'AUS'));
    }else if(parsed.type==='info'){
      const m=lastDebug?.mine;
      consoleLog(m?.loaded?('Mine: '+m.sliceIndex+'/'+m.sliceCount+' Slices, '+m.excavatedBlocks+' Blöcke abgebaut')
        :'Keine simulierte Mine aktiv.');
    }else{
      const answer=await request('control',{command:parsed.command,...parsed.args});
      consoleLog(answer.ok?'OK: '+parsed.command:'Keine Bestätigung');
      await update();
    }
  } catch(e){consoleLog('FEHLER: '+(e.message||String(e)));}
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
    lastState=data;
    const changed=!world||data.scenario!==currentScenario;
    world=data.world; running=data.running;currentScenario=data.scenario;
    if(changed)$('scenario').value=currentScenario;
    $('play').textContent=running?'Pause':'Start';
    $('tick').textContent='Tick '+data.world.tickCount;
    $('info').textContent='Szenario: '+data.scenario+'\nZeit: '+data.world.elapsedSeconds.toFixed(2)+' s\nTempo: '+data.speed+'×';
    $('events').textContent=(data.events||[]).slice(-30).map(e=>`#${e.tick} ${e.kind}: ${e.message}`).join('\n') || 'Noch keine Ereignisse.';
    $('workers').textContent=data.world.residents.map(r=>r.id+' · '+r.profession+'\n'+r.state+' · '+[r.position.x,r.position.y,r.position.z].map(x=>x.toFixed(1)).join(', ')).join('\n\n');
    // Rebuild only when the fixture geometry changes.
    const sig=JSON.stringify([data.world.trees,data.world.constructionSites]);
    if(sig!==update.prevSig){update.prevSig=sig;drawWorld(data.world);}
    drawResidents(data.world.residents);
    if(mineMode){
      lastDebug=await request('debug');
      drawDebug(lastDebug);
      const m=lastDebug.mine;
      $('mine-info').textContent=(lastDebug.prefab.placed?'Prefab platziert: '+JSON.stringify(lastDebug.prefab.origin):'Kein Prefab platziert')
        +(m.loaded?'\nFront '+m.sliceIndex+'/'+m.sliceCount+' · Abbau '+m.excavatedBlocks+' Blöcke\n'
           +(m.workers||[]).map(w=>w.id+': '+w.state+(w.reason?' ('+w.reason+')':'')).join('\n'):'');
    }
    const ids=data.world.residents.map(r=>r.id);
    if(!ids.includes(selectedResident))selectedResident=ids[0]||'';
    const selector=$('resident');
    if([...selector.options].map(o=>o.value).join(',')!==ids.join(',')){
      selector.replaceChildren(...ids.map(id=>{const o=document.createElement('option');o.value=id;o.textContent=id;return o;}));
    }
    selector.value=selectedResident;
    // A reset/new prefab creates an independent voxel world. Its revision counter
    // can accidentally equal the previous world's; force a full terrain snapshot.
    if (data.terrainEpoch !== lastTerrainEpoch) {
      lastTerrainEpoch = data.terrainEpoch;
      lastTerrainRevision = -1;
    }
    if (data.worldRevision !== lastTerrainRevision) {
      const previousRevision=lastTerrainRevision;
      lastTerrainRevision = data.worldRevision;
      const terrain=await request('terrain'+(previousRevision>=0?'?since='+previousRevision:''));
      drawImportedTerrain(terrain);
      update.prevSig=null;
      drawWorld(data.world);
    }
    $('message').textContent='';
  } catch(e){showError(e);}
}
$('resident').onchange=()=>{selectedResident=$('resident').value;};
$('move').onclick=()=>send('move',{
  id:$('resident').value,
  x:Number($('target-x').value),y:Number($('target-y').value),z:Number($('target-z').value)
});
$('configure').onclick=()=>send('configure',{woodcutters:Number($('woodcutters').value),builders:Number($('builders').value)});
$('configureMiners').onclick=()=>send('configureMiners',{miners:Number($('miners').value)});
$('place-auto').onclick=async()=>{await send('placeMineAuto');focusMine();};
$('focus-mine').onclick=focusMine;
$('place-mine').onclick=()=>send('placeMine',{x:Number($('mine-x').value),y:Number($('mine-y').value),z:Number($('mine-z').value)});
$('show-markers').onchange=()=>drawDebug(lastDebug);
$('show-paths').onchange=()=>drawDebug(lastDebug);
$('command-run').onclick=executeConsole;
$('command-input').onkeydown=e=>{if(e.key==='Enter')executeConsole();};
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
    mineMode=!!terrain.loaded;
    $('worker-controls').hidden=mineMode;
    $('miner-controls').hidden=!mineMode;
    $('mine-lab').hidden=!mineMode;
    if(mineMode&&terrain.bounds){
      $('mine-x').value=Math.floor((terrain.bounds[0]+terrain.bounds[3])/2);
      $('mine-y').value=terrain.bounds[1]+8;
      $('mine-z').value=Math.floor((terrain.bounds[2]+terrain.bounds[5])/2);
    }
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
  const w=Math.max(1,Math.floor(rect.width)),h=Math.max(1,Math.floor(rect.height));
  if(renderer.domElement.width!==Math.floor(w*renderer.getPixelRatio())||renderer.domElement.height!==Math.floor(h*renderer.getPixelRatio())) {
    renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();
  }
  $('camera').textContent='Kamera '+camera.position.toArray().map(x=>x.toFixed(1)).join(' / ')+' · Tempo '+flightSpeed.toFixed(1);
  const renderStart=performance.now();
  renderer.render(scene,camera);
  profiler.measure('render:three',performance.now()-renderStart);
  profiler.frame(time);
  requestAnimationFrame(frame);
}
look();requestAnimationFrame(frame);setup();
let updating=false;
setInterval(async()=>{if(updating)return;updating=true;try{await update();}finally{updating=false;}},250);
$('record').onclick=()=>{
  if (!profiler.active) {
    profiler.start({ userAgent:navigator.userAgent, viewport:[innerWidth,innerHeight],pixelRatio:renderer.getPixelRatio() });
    $('record').textContent='Aufnahme stoppen & JSON speichern';
  } else {
    const report=profiler.stop({ticks:lastState?.world?.tickCount||0,...lastTerrainStats});
    if(report)downloadPerformance(report);
    $('record').textContent='Performance aufnehmen';
  }
};
setInterval(()=>{
 if(!profiler.active)return;
 profiler.sample({ticks:lastState?.world?.tickCount||0,simulationRunning:!!lastState?.running,
  residents:lastState?.world?.residents?.length||0,terrainRevision:lastState?.worldRevision||0,...lastTerrainStats,
  drawCalls:renderer.info.render.calls,trianglesDrawn:renderer.info.render.triangles,
  minerStates:(lastState?.world?.residents||[]).map(r=>({id:r.id,state:r.state,
    autonomousState:r.autonomousState,position:r.position})),
  recentEvents:(lastState?.events||[]).slice(-8)});
 $('record-status').textContent=profiler.samples.length+' s erfasst';
},1000);
window.addEventListener('beforeunload',()=>abort=true);
