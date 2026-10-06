/** Trusted-main publisher: branch outputs are bounded JSON data, never scripts or HTML. */
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { Replay } from '../web-viewer/src/replay.js';

const MAX_RUNS = 40;
const MAX_PER_BRANCH = 3;
const MAX_ARCHIVE_BYTES = 32 * 1024 * 1024;
const VALID_ID = /^[a-z0-9][a-z0-9-]{0,79}$/;
const VALID_SHA = /^[a-f0-9]{40}$/;

export function validateBundle(files, run) {
  const manifest = files['manifest.json'];
  if (!manifest || manifest.schemaVersion !== 1 || manifest.commit !== run.head_sha
    || manifest.branch !== run.head_branch || String(manifest.runId) !== String(run.id)
    || String(manifest.runAttempt) !== String(run.run_attempt)
    || !Array.isArray(manifest.scenarios) || manifest.scenarios.length > 30)
    throw new Error('Recording provenance/schema mismatch');
  const names = new Set();
  const scenarios = [];
  for (const entry of manifest.scenarios) {
    if (!VALID_ID.test(entry.id) || entry.file !== `${entry.id}.json` || names.has(entry.id))
      throw new Error('Invalid or duplicate scenario name');
    names.add(entry.id);
    const recording = files[entry.file];
    if (!recording || recording.id !== entry.id || recording.title !== entry.title
      || recording.status !== entry.status || !['completed','failed'].includes(recording.status)
      || recording.frames.length !== entry.frames) throw new Error('Scenario manifest mismatch');
    if (recording.initialVoxels.length > 100000 || recording.frames.length > 10000)
      throw new Error('Recording exceeds viewer limits');
    new Replay(recording);
    scenarios.push({ id:entry.id, title:entry.title, file:entry.file, status:entry.status, frames:entry.frames });
  }
  return { scenarios, testStatus: files['ci-status.json']?.testStatus === 'passed' ? 'passed' : 'failed' };
}

export function selectRuns(runs, repository, now = Date.now()) {
  const threshold = now - 30*24*60*60*1000;
  const counts = new Map();
  return runs.filter(r => r.head_repository?.full_name === repository && VALID_SHA.test(r.head_sha)
      && ['push','workflow_dispatch'].includes(r.event) && r.status === 'completed'
      && !['cancelled','skipped'].includes(r.conclusion) && Date.parse(r.created_at) >= threshold)
    .sort((a,b) => Date.parse(b.created_at)-Date.parse(a.created_at) || b.id-a.id)
    .filter(r => { const count = counts.get(r.head_branch)||0; counts.set(r.head_branch,count+1);return count<MAX_PER_BRANCH; })
    .slice(0,MAX_RUNS);
}

// ZIP paths never reach the filesystem. Reject links, duplicates, nested files and size bombs.
function readArchive(buffer) {
  const python = `
import sys, json, zipfile, io, re, stat
archive=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read()))
entries=archive.infolist()
if len(entries)>32 or sum(e.file_size for e in entries)>33554432: raise ValueError('Archive too large')
files={}
for e in entries:
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,79}\\.json',e.filename): raise ValueError('Invalid archive path')
    if stat.S_ISLNK(e.external_attr>>16) or e.filename in files: raise ValueError('Invalid archive entry')
    files[e.filename]=json.loads(archive.read(e))
print(json.dumps(files))
`;
  return JSON.parse(execFileSync('python3',['-c',python],{input:buffer,maxBuffer:MAX_ARCHIVE_BYTES*2}));
}

async function main() {
  const token=process.env.GH_TOKEN, repository=process.env.GITHUB_REPOSITORY;
  if (!token || !/^[\w.-]+\/[\w.-]+$/.test(repository||'')) throw new Error('Missing repository/token');
  const headers={Authorization:`Bearer ${token}`,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'};
  const api=async path=>{
    const response=await fetch(`https://api.github.com/repos/${repository}/${path}`,{headers});
    if(!response.ok)throw new Error(`GitHub API ${response.status}: ${path}`);
    return response.json();
  };
  const all=[];
  for(let page=1;page<=5;page++) {
    const response=await api(`actions/workflows/simulation-recordings.yml/runs?status=completed&per_page=100&page=${page}`);
    all.push(...response.workflow_runs);
    if(response.workflow_runs.length<100)break;
  }
  const runs=selectRuns(all,repository);
  const destination=resolve(process.argv[2]||'web-viewer/public/data');
  await mkdir(destination,{recursive:true});
  const index=[];
  for(const run of runs) {
    const base={commit:run.head_sha,branch:run.head_branch,runId:String(run.id),runAttempt:String(run.run_attempt),
      createdAt:run.created_at,conclusion:run.conclusion,available:false,testStatus:'failed',scenarios:[]};
    const artifacts=await api(`actions/runs/${run.id}/artifacts?per_page=100`);
    const artifact=artifacts.artifacts.find(a=>a.name==='simulation-recordings'&&!a.expired);
    if(!artifact) { index.push({...base,error:'Keine Aufzeichnung verfügbar (Build fehlgeschlagen oder Artefakt abgelaufen).'});continue; }
    if(artifact.size_in_bytes>MAX_ARCHIVE_BYTES)throw new Error(`Artifact too large: ${run.id}`);
    const response=await fetch(`https://api.github.com/repos/${repository}/actions/artifacts/${artifact.id}/zip`,{headers});
    if(!response.ok)throw new Error(`Artifact download ${response.status}`);
    const buffer=Buffer.from(await response.arrayBuffer());
    if(buffer.length>MAX_ARCHIVE_BYTES)throw new Error('Archive exceeds size limit');
    let files, validated;
    try { files=readArchive(buffer);validated=validateBundle(files,run); }
    catch(error) {index.push({...base,error:`Aufzeichnung nicht verwendbar: ${error.message}`});console.warn(`Rejected artifact for run ${run.id}`);continue;}
    const relative=`runs/${run.head_sha}/${run.id}-${run.run_attempt}`;
    const folder=resolve(destination,relative);await mkdir(folder,{recursive:true});
    for(const s of validated.scenarios) await writeFile(resolve(folder,s.file),JSON.stringify(files[s.file]));
    index.push({...base,...validated,path:relative,available:true});
  }
  await writeFile(resolve(destination,'index.json'),JSON.stringify({schemaVersion:1,generatedAt:new Date().toISOString(),retentionDays:30,runs:index}));
  console.log(`Published catalog with ${index.length} runs (up to ${MAX_PER_BRANCH}/branch, ${MAX_RUNS} total).`);
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url))await main();
