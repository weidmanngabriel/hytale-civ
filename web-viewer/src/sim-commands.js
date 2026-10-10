/** Small deliberate subset of the normal Civ developer workflow, not a Hytale command emulator. */
export function parseSimulationCommand(raw) {
  const parts=String(raw||'').trim().split(/\s+/).filter(Boolean);
  const cmd=parts.map(x=>x.toLowerCase());
  const local = (name,value)=>({kind:'local',name,value});
  const control = (command,args={})=>({kind:'control',command,args});
  const invalid=(message)=>({kind:'error',message});
  if(cmd[0]==='/civdev'&&cmd[1]==='mines'&&parts.length===2) return local('mines');
  if(cmd[0]==='/civdebug'&&cmd[1]==='mine'&&cmd[2]==='info'&&parts.length===3)return local('mineInfo');
  if(cmd[0]==='/civdev'&&cmd[1]==='npc'&&cmd[2]==='list'&&parts.length===3)return local('npcs');
  if(cmd[0]!=='/sim')return invalid('Unbekannter Befehl. /sim help zeigt unterstützte Befehle.');
  if(cmd[1]==='help'&&parts.length===2)return local('help');
  if(['pause','play','step','reset'].includes(cmd[1])&&parts.length===2)
    return control(cmd[1]);
  if(cmd[1]==='markers'&&['on','off'].includes(cmd[2])&&parts.length===3)
    return local('markers',cmd[2]==='on');
  if(cmd[1]==='paths'&&['on','off'].includes(cmd[2])&&parts.length===3)
    return local('paths',cmd[2]==='on');
  if(cmd[1]==='mine'&&cmd[2]==='info'&&parts.length===3)return local('mineInfo');
  if(cmd[1]==='mine'&&cmd[2]==='place'&&parts.length===6) {
    const values=parts.slice(3).map(Number);
    if(values.every(v=>Number.isSafeInteger(v)&&Math.abs(v)<=100000))
      return control('placeMine',{x:values[0],y:values[1],z:values[2]});
    return invalid('Mine: X, Y und Z müssen ganze Weltkoordinaten sein.');
  }
  if(cmd[1]==='block'&&cmd[2]==='set'&&parts.length===7) {
    const p=parts.slice(3,6).map(Number),category=parts[6].toUpperCase();
    if(p.every(n=>Number.isSafeInteger(n)&&Math.abs(n)<=100000)
      &&['AIR','SOLID','WATER','LAVA'].includes(category))
      return control('setBlock',{x:p[0],y:p[1],z:p[2],category});
    return invalid('Block: /sim block set X Y Z AIR|SOLID|WATER|LAVA');
  }
  return invalid('Nicht unterstützt. /sim help für die vollständige Liste.');
}
export const simulationCommandHelp = [
  '/sim mine place X Y Z  (Basisposition von Mine_01)',
  '/sim mine info | /civdev mines | /civdebug mine info',
  '/civdev npc list',
  '/sim markers on|off  |  /sim paths on|off',
  '/sim block set X Y Z AIR|SOLID|WATER|LAVA',
  '/sim play | pause | step | reset'
].join('\n');
