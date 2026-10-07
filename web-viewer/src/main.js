import * as THREE from 'three';
import { Replay, surfaceFaces, visibleVoxel, MATERIALS } from './replay.js';
import { sortBranchesByLatestCommit } from './catalog.js';
import './style.css';

const $ = id => document.getElementById(id);
const canvas = $('scene');
const scene = new THREE.Scene();
scene.background = new THREE.Color('#0d1620');
const camera = new THREE.PerspectiveCamera(65, 1, 0.03, 2000);
let renderer;
try {
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 1.5));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
} catch {
  $('message').hidden = false;
  $('message').textContent = 'Dieser Browser stellt kein WebGL 2 bereit. Öffne den Viewer in einem aktuellen Browser.';
  throw new Error('WebGL 2 unavailable');
}
scene.add(new THREE.HemisphereLight(0xd7edff, 0x637082, 2.1));
const light = new THREE.DirectionalLight(0xffffff, 2.2);
light.position.set(40, 90, 30); scene.add(light);
const palette = ['#000000','#657182','#a7becd','#b48a56','#816340','#567a68','#d4ba77','#dcb06b'].map(c=>new THREE.Color(c));
const terrainMaterial = new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.FrontSide });
const worldGroup = new THREE.Group(), residentGroup = new THREE.Group(), markerGroup = new THREE.Group();
scene.add(worldGroup,residentGroup,markerGroup);
const selection = new THREE.Box3Helper(new THREE.Box3(), 0xffe1a1);
selection.visible = false; scene.add(selection);
const actionBox = new THREE.Box3Helper(new THREE.Box3(), 0x54decf);
actionBox.visible = false; scene.add(actionBox);
const targetLine = new THREE.Line(new THREE.BufferGeometry(), new THREE.LineBasicMaterial({color:0x70bfff}));
scene.add(targetLine);
let replay, catalog, activeRun, residentId, playing = false, budget = 0, loadNumber = 0;
let yaw = 0, pitch = 0, moveSpeed = 10, selectedBlock;
const keys = new Set(), touchMove = {x:0,y:0}, touchLook = {x:0,y:0};
let touchHeight = 0;
const workers = new Map();

function message(text, error=false) {
  $('message').textContent = text; $('message').classList.toggle('error',error); $('message').hidden = !text;
}
function clearGroup(group, disposeMaterials=true) {
  for (const object of [...group.children]) {
    group.remove(object);
    object.traverse(o => { o.geometry?.dispose(); if (disposeMaterials) o.material?.dispose(); });
  }
}
function rebuildWorld() {
  clearGroup(worldGroup,false);
  const vertices=[],normals=[],colors=[];
  for (const face of surfaceFaces(replay.world,replay.data.rockBounds)) {
    const color = palette[face.material];
    for (const i of [0,1,2,0,2,3]) {
      const c=face.corners[i];
      vertices.push(face.x+c[0],face.y+c[1],face.z+c[2]);
      normals.push(...face.normal); colors.push(color.r,color.g,color.b);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(normals,3));
  geometry.setAttribute('color',new THREE.Float32BufferAttribute(colors,3));
  geometry.computeBoundingSphere();
  worldGroup.add(new THREE.Mesh(geometry,terrainMaterial));
}
function boxAt(helper,p) {
  helper.box.min.set(p.x,p.y,p.z); helper.box.max.set(p.x+1,p.y+1,p.z+1); helper.visible=true;
}
function markerColor(type) {
  if (type?.startsWith('planned_')) return 0x526b75;
  if (type?.startsWith('developed_')) return 0x91ddb2;
  if (type === 'water_obstacle') return 0x6eb9ff;
  if (type === 'lava_obstacle') return 0xff765f;
  if (type === 'workplace_access') return 0xe6cb73;
  if (type === 'mine_tunnel_connector') return 0x72d8d0;
  return 0x9ddcca;
}
function rebuildMarkers() {
  clearGroup(markerGroup);
  for (const marker of replay.data.markers || []) {
    const [x,y,z,xx,yy,zz]=marker.bounds;
    const b=new THREE.Box3(new THREE.Vector3(x,y,z),new THREE.Vector3(xx,yy,zz));
    const helper=new THREE.Box3Helper(b,markerColor(marker.type)); markerGroup.add(helper);
  }
  markerGroup.visible=$('markers').checked;
}
function updateResidents() {
  const current = new Set(replay.frame.residents.map(r=>r.id));
  for (const [id, mesh] of workers) if (!current.has(id)) {
    residentGroup.remove(mesh);mesh.geometry.dispose();mesh.material.dispose();workers.delete(id);
  }
  for (const resident of replay.frame.residents) {
    let mesh=workers.get(resident.id);
    if (!mesh) {
      mesh=new THREE.Mesh(new THREE.CapsuleGeometry(.25,.9,4,8),new THREE.MeshBasicMaterial({color:0xf5a35c}));
      mesh.userData.id=resident.id; workers.set(resident.id,mesh); residentGroup.add(mesh);
    }
    mesh.position.set(resident.position.x,resident.position.y+.7,resident.position.z);
    mesh.material.color.set(resident.id===residentId?0xffd698:0xdb8050);
  }
  const options = replay.frame.residents.map(r=>r.id);
  if (!options.includes(residentId)) residentId=options[0];
  if ([...$('resident').options].map(o=>o.value).join('|')!==options.join('|'))
    fillSelect('resident',options.map(id=>[id,id]));
  $('resident').value=residentId || '';
}
function renderDetails() {
  const f=replay.frame, r=f.residents.find(r=>r.id===residentId);
  $('details').textContent=r?`${r.id}\n${r.profession}\n\n${r.state}\n\nPosition  ${formatPosition(r.position)}\nZiel      ${r.target?formatPosition(r.target):'–'}\n\n${Object.entries(r.details||{}).map(([k,v])=>`${k}: ${v}`).join('\n')}`:'Kein Arbeiter in diesem Schritt.';
  $('metrics').textContent=Object.entries(f.metrics||{}).map(([k,v])=>`${k}: ${v}`).join('\n');
  targetLine.visible=!!r?.target;
  if (r?.target) {
    targetLine.geometry.dispose();
    targetLine.geometry=new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(r.position.x,r.position.y+.5,r.position.z),
      new THREE.Vector3(r.target.x,r.target.y+.5,r.target.z)]);
  }
  actionBox.visible=!!f.action;
  if (f.action) boxAt(actionBox,f.action);
}
function formatPosition(p) { return [p.x,p.y,p.z].map(v=>v.toFixed(1)).join(' / '); }
function controlsEnabled(enabled) {
  for (const id of ['play','step','step-back','reset','timeline']) $(id).disabled=!enabled;
}
function setPlaying(value) { playing=value;budget=0;$('play').textContent=value?'Ⅱ Pause':'▶ Start'; }
function seek(index) {
  if (!replay) return;
  const previous=replay.index;
  replay.seek(index);
  // Rebuild only when geometry changed, or when moving backwards through prior deltas.
  if (replay.index<previous || replay.data.frames.slice(previous+1,replay.index+1).some(f=>f.changes.length)) rebuildWorld();
  if (selectedBlock) {
    const [x,y,z]=selectedBlock;const material=replay.materialAt(x,y,z);
    selection.visible=!!material;if(material)boxAt(selection,{x,y,z});
    $('block-info').textContent=`${x} / ${y} / ${z}\n${MATERIALS[material]}`;
  } else selection.visible=false;
  updateResidents(); renderDetails();
  $('timeline').value=replay.index;
  const clock=replay.data.timeUnit==='seconds'?`${replay.frame.time.toFixed(2)} s`:'Aktion';
  $('time').textContent=`${clock} · ${replay.index}/${replay.data.frames.length-1}`;
}
function setCamera(position, target) {
  camera.position.copy(position);
  const direction=target.clone().sub(position).normalize();
  pitch=Math.asin(THREE.MathUtils.clamp(direction.y,-1,1));
  yaw=Math.atan2(-direction.x,-direction.z);updateRotation();
}
function overview() {
  if (!replay) return;
  const bounds=new THREE.Box3();
  if (replay.data.rockBounds) {
    const [x,y,z,xx,yy,zz]=replay.data.rockBounds;
    bounds.set(new THREE.Vector3(x,y,z),new THREE.Vector3(xx,yy,zz));
  } else {
    if (!replay.world.size) { setCamera(new THREE.Vector3(12,12,16),new THREE.Vector3());return; }
    for (const k of replay.world.keys()) {const [x,y,z]=k.split(',').map(Number);bounds.expandByPoint(new THREE.Vector3(x,y,z));bounds.expandByPoint(new THREE.Vector3(x+1,y+1,z+1));}
  }
  const center=bounds.getCenter(new THREE.Vector3());
  const size=bounds.getSize(new THREE.Vector3());
  const distance=Math.max(size.x,size.y,size.z,10)*1.35;
  setCamera(center.clone().add(new THREE.Vector3(distance*.8,distance*.65,distance)),center);
  moveSpeed=Math.max(4,Math.max(size.x,size.y,size.z)/4);
}
function toWorker() {
  const r=replay?.frame.residents.find(r=>r.id===residentId);
  if (!r) return;
  const eye=new THREE.Vector3(r.position.x,r.position.y+1.6,r.position.z);
  let direction=new THREE.Vector3(0,0,-1);
  if (r.target) { direction.set(r.target.x-r.position.x,0,r.target.z-r.position.z);if(direction.lengthSq()<.01)direction.set(0,0,-1);direction.normalize(); }
  setCamera(eye.clone().addScaledVector(direction,-2),eye.clone().addScaledVector(direction,4));
}
function updateRotation() { camera.rotation.set(pitch,yaw,0,'YXZ'); }
function fillSelect(id, options) {
  $(id).replaceChildren(...options.map(([value,label])=>{const o=document.createElement('option');o.value=value;o.textContent=label;return o;}));
}
async function json(url) {
  const response=await fetch(url,{cache:'no-store'});
  if(!response.ok) throw new Error(`Datei nicht verfügbar (${response.status}).`);
  return response.json();
}
function runKey(r) { return `${r.commit}/${r.runId}-${r.runAttempt}`; }
async function loadScenario() {
  const request=++loadNumber;setPlaying(false);controlsEnabled(false);
  replay=undefined;clearGroup(worldGroup,false);clearGroup(residentGroup);clearGroup(markerGroup);workers.clear();
  selection.visible=false;actionBox.visible=false;targetLine.visible=false;
  $('details').textContent='Aufzeichnung laden …';message('Aufzeichnung laden …');
  const scenario=activeRun?.scenarios.find(s=>s.id===$('scenario').value);
  if (!scenario) { $('title').textContent='Kein Replay verfügbar'; message(activeRun?.error || 'Dieser Lauf enthält keine Aufzeichnung.',true); return; }
  try {
    const data=await json(new URL(`${activeRun.path}/${scenario.file}`,new URL('data/',document.baseURI)));
    if(request!==loadNumber)return;
    openRecording(data);
    const params=new URLSearchParams({branch:activeRun.branch,run:runKey(activeRun),scenario:data.id});
    history.replaceState(null,'',`${location.pathname}?${params}`);
  } catch(error) {if(request===loadNumber)message(error.message,true);}
}
function openRecording(data) {
  replay=new Replay(data);selectedBlock=undefined;$('block-info').textContent='Klicke eine sichtbare Wand an.';
  $('title').textContent=data.title;
  $('description').textContent=data.description;
  $('scene-label').textContent=data.status==='failed'?'SZENARIO FEHLGESCHLAGEN':'SIMULATION · REPLAY';
  $('timeline').max=data.frames.length-1;
  rebuildWorld();rebuildMarkers();seek(0);overview();controlsEnabled(true);
  message(data.error?`Szenario abgebrochen: ${data.error}`:'',!!data.error);
}
function selectRun(preferredScenario) {
  activeRun=catalog.runs.find(r=>runKey(r)===$('run').value);
  if (!activeRun)return;
  const info=$('run-info');info.replaceChildren();
  const badge=document.createElement('span');badge.className=`badge ${activeRun.testStatus==='passed'?'':'failed'}`;
  badge.textContent=activeRun.testStatus==='passed'?'Tests bestanden':activeRun.testStatus==='failed'?'Tests fehlgeschlagen':'Tests nicht bestätigt';
  const sha=document.createElement('a');sha.href=`https://github.com/weidmanngabriel/hytale-civ/commit/${activeRun.commit}`;sha.textContent=activeRun.commit.slice(0,10);
  const action=document.createElement('a');action.href=`https://github.com/weidmanngabriel/hytale-civ/actions/runs/${activeRun.runId}`;action.textContent=' · Actions ↗';
  info.append(badge,document.createTextNode(' '),sha,action);
  $('source').href=sha.href;
  fillSelect('scenario',activeRun.scenarios.map(s=>[s.id,`${s.title}${s.status==='failed'?' · Fehler':''}`]));
  if(activeRun.scenarios.some(s=>s.id===preferredScenario))$('scenario').value=preferredScenario;
  loadScenario();
}
function selectBranch(preferredRun,preferredScenario) {
  const runs=catalog.runs.filter(r=>r.branch===$('branch').value);
  fillSelect('run',runs.map((r,i)=>[runKey(r),`${i===0?'Letzter Lauf · ':''}${r.commit.slice(0,8)} · ${new Date(r.createdAt).toLocaleString('de-DE',{dateStyle:'short',timeStyle:'short'})}`]));
  if(runs.some(r=>runKey(r)===preferredRun))$('run').value=preferredRun;
  selectRun(preferredScenario);
}
async function loadCatalog() {
  try {
    catalog=await json(new URL('data/index.json',document.baseURI));
    if(catalog.schemaVersion!==1||!Array.isArray(catalog.runs))throw new Error('Unbekannter Laufkatalog.');
    catalog.runs.sort((a,b)=>new Date(b.createdAt)-new Date(a.createdAt));
    if(!catalog.runs.length)throw new Error('Noch kein Szenario-Lauf veröffentlicht. Nach einem Branch-Push erscheint er hier.');
    const branches=sortBranchesByLatestCommit(catalog.runs);
    fillSelect('branch',branches.map(b=>[b,b]));
    const params=new URLSearchParams(location.search);
    if(branches.includes(params.get('branch')))$('branch').value=params.get('branch');
    selectBranch(params.get('run'),params.get('scenario'));
  } catch(error) { $('title').textContent='Simulation Lab';message(`${error.message} Du kannst auch eine lokale JSON-Aufzeichnung öffnen.`,true); }
}

$('branch').onchange=()=>selectBranch();$('run').onchange=()=>selectRun();$('scenario').onchange=loadScenario;
$('resident').onchange=()=>{residentId=$('resident').value;updateResidents();renderDetails();};
$('play').onclick=()=>{if(replay.index===replay.data.frames.length-1)seek(0);setPlaying(!playing);};
$('step').onclick=()=>{setPlaying(false);seek(replay.index+1);};
$('step-back').onclick=()=>{setPlaying(false);seek(replay.index-1);};
$('reset').onclick=()=>{setPlaying(false);seek(0);};
$('timeline').oninput=()=>{setPlaying(false);seek(Number($('timeline').value));};
$('inspect-toggle').onclick=()=>document.body.classList.toggle('inspector-open');
$('overview').onclick=overview;$('follow').onclick=toWorker;
$('markers').onchange=()=>markerGroup.visible=$('markers').checked;
$('help-toggle').onclick=()=>$('help').hidden=!$('help').hidden;
$('file').onchange=async event=>{
  const file=event.target.files[0];if(!file)return;
  ++loadNumber;setPlaying(false);controlsEnabled(false);
  try {
    if(file.size>32*1024*1024)throw new Error('Aufzeichnung ist größer als 32 MB.');
    const data=validateLocal(JSON.parse(await file.text()));
    activeRun=null;openRecording(data);$('run-info').textContent='Lokale Aufzeichnung · Commit nicht bestätigt';
    history.replaceState(null,'',location.pathname);
  }catch(error){message(error.message,true);}
};
function validateLocal(data) {new Replay(data);return data;}

window.addEventListener('keydown',event=>{
  if(['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName))return;
  if(['KeyW','KeyA','KeyS','KeyD','KeyQ','KeyE','ShiftLeft','ShiftRight'].includes(event.code)){keys.add(event.code);event.preventDefault();}
});
window.addEventListener('keyup',event=>keys.delete(event.code));
window.addEventListener('blur',()=>{keys.clear();touchMove.x=touchMove.y=touchLook.x=touchLook.y=touchHeight=0;});
let drag;
canvas.addEventListener('contextmenu',e=>e.preventDefault());
canvas.addEventListener('pointerdown',event=>{
  canvas.focus({preventScroll:true});
  if(event.pointerType==='touch'||event.button===2){drag={id:event.pointerId,x:event.clientX,y:event.clientY};canvas.setPointerCapture(event.pointerId);}
});
canvas.addEventListener('pointermove',event=>{
  if(!drag||event.pointerId!==drag.id)return;
  yaw-=(event.clientX-drag.x)*.004;pitch=THREE.MathUtils.clamp(pitch-(event.clientY-drag.y)*.004,-Math.PI/2+.01,Math.PI/2-.01);
  drag.x=event.clientX;drag.y=event.clientY;updateRotation();
});
for(const type of ['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(type,()=>drag=undefined);
canvas.addEventListener('wheel',event=>{moveSpeed=THREE.MathUtils.clamp(moveSpeed*Math.exp(-event.deltaY*.001),1,100);event.preventDefault();},{passive:false});
canvas.addEventListener('click',event=>{
  if(!replay||event.button!==0)return;
  const rect=canvas.getBoundingClientRect();
  const ray=new THREE.Raycaster();ray.setFromCamera(new THREE.Vector2((event.clientX-rect.left)/rect.width*2-1,-(event.clientY-rect.top)/rect.height*2+1),camera);
  const wall=visibleVoxel(replay.world,ray.ray.origin.toArray(),ray.ray.direction.toArray(),1000,replay.data.rockBounds);
  const worker=ray.intersectObjects(residentGroup.children)[0];
  if(worker&&(!wall||worker.distance<wall.distance)){
    residentId=worker.object.userData.id;$('resident').value=residentId;updateResidents();renderDetails();return;
  }
  if(wall){selectedBlock=wall.position;const [x,y,z]=wall.position;boxAt(selection,{x,y,z});$('block-info').textContent=`${x} / ${y} / ${z}\n${MATERIALS[wall.material]}`;}
  else {selectedBlock=undefined;selection.visible=false;$('block-info').textContent='Keine sichtbare Wand getroffen.';}
});
function pad(id,state) {
  const el=$(id);let pointer;
  const update=event=>{const r=el.getBoundingClientRect();state.x=THREE.MathUtils.clamp((event.clientX-r.left-r.width/2)/(r.width/2),-1,1);state.y=THREE.MathUtils.clamp((event.clientY-r.top-r.height/2)/(r.height/2),-1,1);el.querySelector('i').style.transform=`translate(${state.x*25}px,${state.y*25}px)`;};
  el.onpointerdown=e=>{pointer=e.pointerId;el.setPointerCapture(pointer);update(e);};
  el.onpointermove=e=>{if(e.pointerId===pointer)update(e);};
  const stop=()=>{pointer=undefined;state.x=state.y=0;el.querySelector('i').style.transform='';};
  el.onpointerup=stop;el.onpointercancel=stop;el.onlostpointercapture=stop;
}
pad('move-pad',touchMove);pad('look-pad',touchLook);
for(const [id,value]of [['up',1],['down',-1]]){
  $(id).onpointerdown=e=>{touchHeight=value;$(id).setPointerCapture(e.pointerId);};
  for(const name of ['onpointerup','onpointercancel','onlostpointercapture'])$(id)[name]=()=>touchHeight=0;
}
const resize=new ResizeObserver(()=>{const r=canvas.parentElement.getBoundingClientRect();renderer.setSize(r.width,r.height,false);camera.aspect=r.width/Math.max(r.height,1);camera.updateProjectionMatrix();});resize.observe(canvas.parentElement);
let last=performance.now();
function animate(now) {
  const dt=Math.min((now-last)/1000,.1);last=now;
  if(!document.hidden){
    yaw-=touchLook.x*dt*1.8;pitch=THREE.MathUtils.clamp(pitch-touchLook.y*dt*1.8,-Math.PI/2+.01,Math.PI/2-.01);updateRotation();
    const right=Number(keys.has('KeyD'))-Number(keys.has('KeyA'))+touchMove.x;
    const forward=Number(keys.has('KeyW'))-Number(keys.has('KeyS'))-touchMove.y;
    const vertical=Number(keys.has('KeyE'))-Number(keys.has('KeyQ'))+touchHeight;
    const velocity=new THREE.Vector3(right,0,-forward).applyQuaternion(camera.quaternion);velocity.y+=vertical;
    if(velocity.lengthSq()>1)velocity.normalize();
    camera.position.addScaledVector(velocity,dt*moveSpeed*(keys.has('ShiftLeft')||keys.has('ShiftRight')?3:1));
    if(playing&&replay){
      // General scenarios preserve 50ms ticks; mine fixtures contain semantic actions, played at 10/s.
      budget+=dt*Number($('speed').value)*(replay.data.timeUnit==='seconds'?20:10);
      if(budget>=1){const advance=Math.floor(budget);budget-=advance;seek(replay.index+advance);}
      if(replay.index===replay.data.frames.length-1)setPlaying(false);
    }
    $('camera-info').textContent=`Kamera ${formatPosition(camera.position)} · Tempo ${moveSpeed.toFixed(0)}`;
    renderer.render(scene,camera);
  }
  requestAnimationFrame(animate);
}
requestAnimationFrame(animate);loadCatalog();
