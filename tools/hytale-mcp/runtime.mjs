import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createHash, randomUUID } from 'node:crypto';
import { readFile, writeFile, mkdir, cp, rm, lstat, realpath, readdir } from 'node:fs/promises';
import { resolve, join, dirname, isAbsolute, basename } from 'node:path';
import { createInterface } from 'node:readline';
import { setTimeout as delay } from 'node:timers/promises';

const exec = promisify(execFile);
const OWNER = '.civ-mcp-owner.json';
export class LogBuffer {
  constructor(limit = 2000) { this.limit = limit; this.sequence = 0; this.lines = []; }
  add(stream, text) {
    this.lines.push({ sequence: ++this.sequence, time: new Date().toISOString(), stream, text: String(text).slice(0, 16000) });
    if (this.lines.length > this.limit) this.lines.shift();
  }
  read(after = 0, limit = 100, contains = '') {
    const lines = this.lines.filter(line => line.sequence > after && line.text.includes(contains)).slice(0, limit);
    return { lines, nextCursor: lines.at(-1)?.sequence ?? after, latestCursor: this.sequence,
      truncatedBefore: this.lines[0]?.sequence ?? 0 };
  }
}
function watch(child, logs) {
  for (const [name, stream] of [['stdout', child.stdout], ['stderr', child.stderr]]) {
    createInterface({ input: stream }).on('line', line => logs.add(name, line));
  }
}
function port(value, fallback) {
  const result = value ?? fallback;
  if (!Number.isInteger(result) || result < 1024 || result > 65535) throw new Error('Ports must be integers between 1024 and 65535');
  return result;
}
export async function configuration(root, configPath = join(root, '.hytale-dev.json'), env = process.env) {
  let settings = {};
  try { settings = JSON.parse(await readFile(configPath, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  for (const key of Object.keys(settings)) if (!['serverJar', 'assetsPath', 'javaExecutable', 'gamePort', 'commandBridgePort', 'maxHeapMb'].includes(key)) throw new Error(`Unknown setting ${key}`);
  const optionalPath = value => value ? resolve(root, value) : null;
  const maxHeapMb = settings.maxHeapMb ?? 2048;
  if (!Number.isInteger(maxHeapMb) || maxHeapMb < 512 || maxHeapMb > 16384) throw new Error('maxHeapMb must be between 512 and 16384');
  const javaExecutable = settings.javaExecutable ?? (env.JAVA_HOME ? join(env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java');
  if (typeof javaExecutable !== 'string' || !javaExecutable.length) throw new Error('javaExecutable must be a path or java');
  return { root: await realpath(root), runtime: join(await realpath(root), '.hytale-dev', 'runtime'),
    serverJar: optionalPath(settings.serverJar ?? env.HYTALE_SERVER_JAR),
    assetsPath: optionalPath(settings.assetsPath ?? env.HYTALE_ASSETS_PATH),
    javaExecutable,
    javaHome: isAbsolute(javaExecutable) && /^java(?:\.exe)?$/i.test(basename(javaExecutable)) ? dirname(dirname(javaExecutable)) : env.JAVA_HOME,
    gamePort: port(settings.gamePort, 5521), commandBridgePort: port(settings.commandBridgePort, 5523), maxHeapMb };
}
export async function ownedDirectory(path, root) {
  // Never follow a user-created link into the normal game installation.
  for (const directory of [resolve(path, '..'), path]) {
    try { if ((await lstat(directory)).isSymbolicLink()) throw new Error(`Development directory must not be a link: ${directory}`); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  await mkdir(path, { recursive: true });
  const marker = join(path, OWNER);
  try {
    const identity = JSON.parse(await readFile(marker, 'utf8'));
    if (identity.root !== root) throw new Error('Runtime directory belongs to a different checkout');
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    if ((await readdir(path)).length) throw new Error('Refusing to adopt a non-empty runtime directory without an ownership marker');
    await writeFile(marker, JSON.stringify({ root }), { flag: 'wx', mode: 0o600 });
  }
}

export class LocalRuntime {
  constructor(config) {
    this.config = config; this.logs = new LogBuffer(); this.jobs = new Map(); this.child = null;
    this.deployed = null; this.busy = false;
  }
  async exclusive(operation) {
    if (this.busy) throw new Error('A lifecycle operation is already running');
    this.busy = true;
    try { return await operation(); } finally { this.busy = false; }
  }
  async revision() {
    const { stdout: revision } = await exec('git', ['rev-parse', 'HEAD'], { cwd: this.config.root });
    const { stdout: changes } = await exec('git', ['status', '--porcelain'], { cwd: this.config.root });
    return { revision: revision.trim(), dirty: Boolean(changes.trim()) };
  }
  async status() {
    let commandBridge;
    try {
      const response = await fetch(`http://127.0.0.1:${this.config.commandBridgePort}/health`, {
        method: 'GET',
        redirect: 'error',
        signal: AbortSignal.timeout(1500)
      });
      commandBridge = response.ok ? await response.json() : { ready: false, reason: `HTTP ${response.status}` };
    } catch (error) {
      commandBridge = { ready: false, reason: error.message };
    }
    return { repository: await this.revision(), configured: Boolean(this.config.serverJar && this.config.assetsPath),
      running: Boolean(this.child), pid: this.child?.pid ?? null, deployed: this.deployed,
      gameEndpoint: `127.0.0.1:${this.config.gamePort}`,
      commandBridgeEndpoint: `127.0.0.1:${this.config.commandBridgePort}`,
      commandBridge,
      jobs: [...this.jobs.values()].map(({ process, logs, ...job }) => job) };
  }
  async build() {
    return this.exclusive(async () => {
      if ([...this.jobs.values()].some(job => job.state === 'running')) throw new Error('A build is already running');
      const job = { id: randomUUID(), state: 'running', started: new Date().toISOString(), source: await this.revision(), logs: new LogBuffer() };
      const env = { ...process.env, ...(this.config.javaHome ? { JAVA_HOME: this.config.javaHome } : {}) };
      const child = process.platform === 'win32'
        ? spawn('cmd.exe', ['/d', '/c', 'gradlew.bat test build --console=plain'], { cwd: this.config.root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
        : spawn(join(this.config.root, 'gradlew'), ['test', 'build', '--console=plain'], { cwd: this.config.root, env, stdio: ['ignore', 'pipe', 'pipe'] });
      job.process = child; this.jobs.set(job.id, job); watch(child, job.logs);
      while (this.jobs.size > 20) this.jobs.delete(this.jobs.keys().next().value);
      child.on('error', error => { job.state = 'failed'; job.logs.add('stderr', error.message); });
      child.on('close', code => { job.state = code === 0 ? 'succeeded' : 'failed'; job.exitCode = code; job.finished = new Date().toISOString(); delete job.process; });
      return { jobId: job.id, state: job.state, instruction: 'Poll hytale_job until succeeded before deployment.' };
    });
  }
  job(id, after = 0) {
    const job = this.jobs.get(id);
    if (!job) throw new Error('Unknown build job');
    const { process, logs, ...result } = job;
    return { ...result, output: logs.read(after, 100) };
  }
  async deploy(jobId) {
    return this.exclusive(async () => {
      if (this.child) throw new Error('Stop the development server before deploying');
      const job = this.jobs.get(jobId);
      if (job?.state !== 'succeeded') throw new Error('Deployment requires a successful build job from this MCP session');
      if ([...this.jobs.keys()].at(-1) !== jobId) throw new Error('Deploy the latest build job to avoid stale artifacts');
      if ([...this.jobs.values()].some(item => item.state === 'running')) throw new Error('Wait for the active build');
      const properties = await readFile(join(this.config.root, 'gradle.properties'), 'utf8');
      const property = key => properties.match(new RegExp(`^${key}=(.+)$`, 'm'))?.[1]?.trim();
      const base = property('artifactBaseName') ?? 'hytale-civ';
      const version = property('projectVersion');
      if (!/^[A-Za-z0-9._-]+$/.test(base) || !version || !/^[A-Za-z0-9._-]+$/.test(version)) throw new Error('Invalid Gradle artifact properties');
      const jar = join(this.config.root, 'build', 'libs', `${base}-${version}.jar`);
      const bytes = await readFile(jar);
      await ownedDirectory(this.config.runtime, this.config.root);
      const mods = join(this.config.runtime, 'mods');
      try { if ((await lstat(mods)).isSymbolicLink()) throw new Error('mods must not be a link'); } catch (error) { if (error.code !== 'ENOENT') throw error; }
      // mods contains only this tool's deployment, never the user's normal Mods directory.
      await rm(mods, { recursive: true, force: true }); await mkdir(mods);
      await writeFile(join(mods, 'hytale-civ.jar'), bytes);
      await cp(join(this.config.root, 'asset-pack'), join(mods, 'hytale-civ-assets'), { recursive: true, dereference: false });
      this.deployed = { sourceAtBuildStart: job.source, jarSha256: createHash('sha256').update(bytes).digest('hex'), deployedAt: new Date().toISOString() };
      return this.deployed;
    });
  }
  async start() {
    return this.exclusive(async () => {
      if (this.child) throw new Error('Development server is already running');
      if (!this.deployed) throw new Error('Build and deploy in this MCP session first');
      if (!this.config.serverJar || !this.config.assetsPath) throw new Error('Configure HYTALE_SERVER_JAR and HYTALE_ASSETS_PATH or .hytale-dev.json');
      if (!(await lstat(this.config.serverJar)).isFile()) throw new Error('serverJar must be a file');
      await lstat(this.config.assetsPath);
      await ownedDirectory(this.config.runtime, this.config.root);
      const child = spawn(this.config.javaExecutable, [`-Xmx${this.config.maxHeapMb}m`,
        `-Dcivilizations.commandBridgePort=${this.config.commandBridgePort}`, '-jar', this.config.serverJar,
        '--assets', this.config.assetsPath, '--bind', `127.0.0.1:${this.config.gamePort}`, '--auth-mode', 'offline', '--disable-sentry'],
      { cwd: this.config.runtime, env: process.env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
      this.child = child; watch(child, this.logs);
      child.on('error', error => this.logs.add('stderr', error.message));
      child.on('close', code => { this.logs.add('lifecycle', `Server exited: ${code}`); if (this.child === child) this.child = null; });
      return { pid: child.pid, state: 'starting', instruction: 'Poll hytale_status until commandBridge.ready is true. No client is launched.' };
    });
  }
  async command(command, timeoutMs = 12000) {
    if (typeof command !== 'string' || !command.trim() || command.length > 4096) throw new Error('command must be a non-empty bounded string');
    let response;
    try {
      response = await fetch(`http://127.0.0.1:${this.config.commandBridgePort}/command`, {
        method: 'POST',
        redirect: 'error',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ command }),
        signal: AbortSignal.timeout(timeoutMs)
      });
    } catch (error) {
      throw new Error(`Hytale command bridge unavailable on 127.0.0.1:${this.config.commandBridgePort}: ${error.message}`);
    }
    let result;
    try { result = await response.json(); }
    catch { throw new Error(`Hytale command bridge returned invalid JSON (HTTP ${response.status})`); }
    if (!response.ok) throw new Error(result.error ?? `Command bridge HTTP ${response.status}`);
    return result;
  }

  serverLogs(after, limit, contains) {
    return this.logs.read(after, limit, contains);
  }
  async stop() {
    return this.exclusive(async () => {
      const child = this.child;
      if (!child) return { state: 'stopped' };
      // Native console shutdown also works before the bridge has finished starting.
      child.stdin.write('stop\n');
      for (let i = 0; i < 150 && this.child === child; i++) await delay(100);
      if (this.child === child) throw new Error('Native shutdown has not completed after 15 seconds; inspect logs. No other process was stopped.');
      return { state: 'stopped' };
    });
  }
  async close() {
    for (const job of this.jobs.values()) {
      if (job.process) {
        if (process.platform === 'win32') await exec('taskkill.exe', ['/PID', String(job.process.pid), '/T', '/F']).catch(() => {});
        else job.process.kill('SIGTERM');
      }
    }
    const child = this.child;
    if (child) {
      child.stdin.write('stop\n');
      for (let i = 0; i < 100 && this.child === child; i++) await delay(100);
      if (this.child === child) child.kill(); // Only the exact Java child created by this instance.
    }
  }
}
