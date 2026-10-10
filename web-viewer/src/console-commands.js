// Deliberately limited local simulation language: never forwards arbitrary Hytale commands.
export function parseSimulationCommand(source) {
  const words=source.trim().split(/\s+/).filter(Boolean);
  const [root,verb,...rest]=words;
  if(root==='/civdev' && ['mines','mine-info'].includes(verb))
    return {type:'info'};
  if(root!=='/sim') throw new Error('Nur /sim-Befehle und /civdev mines/mine-info im lokalen Simulator unterstützt.');
  if(verb==='help')return {type:'help'};
  if(verb==='mine' && rest[0]==='info')return {type:'info'};
  if(verb==='mine' && rest[0]==='auto')return {type:'api',command:'placeMineAuto',args:{}};
  if(verb==='mine' && rest[0]==='place' && rest.length===4){
    const [x,y,z]=rest.slice(1).map(Number);
    if([x,y,z].every(Number.isSafeInteger))return {type:'api',command:'placeMine',args:{x,y,z}};
  }
  if(verb==='miners' && rest.length===1){
    const n=Number(rest[0]);if(Number.isInteger(n)&&n>=1&&n<=20)
      return {type:'api',command:'configureMiners',args:{miners:n}};
  }
  if(['play','pause','step','reset'].includes(verb) && rest.length===0)
    return {type:'api',command:verb,args:{}};
  if(verb==='block'&&rest.length===4){
    const [x,y,z]=rest.slice(0,3).map(Number), category=rest[3].toUpperCase();
    if([x,y,z].every(Number.isSafeInteger)&&['AIR','SOLID','WATER','LAVA'].includes(category))
      return {type:'api',command:'setBlock',args:{x,y,z,category}};
  }
  if(['markers','paths'].includes(verb)&&rest.length===1&&['on','off'].includes(rest[0]))
    return {type:'overlay',name:verb,enabled:rest[0]==='on'};
  throw new Error('Ungültiger Simulationsbefehl. /sim help zeigt unterstützte Befehle.');
}
export const CONSOLE_HELP='/sim mine auto | /sim mine place X Y Z | /sim mine info | /sim miners N | /sim play | /sim pause | /sim step | /sim reset | /sim block X Y Z AIR|SOLID|WATER|LAVA | /sim markers on|off | /sim paths on|off | /civdev mines';
