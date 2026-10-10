// Bounded, opt-in browser performance trace. One diagnostic sample per second.
export class PerformanceRecorder {
  constructor(clock = () => performance.now()) {
    this.clock = clock;
    this.started = null;
    this.samples = [];
    this.lastFrame = null;
    this.frames = [];
    this.costs = new Map();
    this.context = {};
  }
  get active() { return this.started !== null; }
  start(context = {}) {
    this.started = this.clock();
    this.samples = []; this.frames = []; this.costs.clear();
    this.lastFrame = null; this.context = context;
  }
  frame(now) {
    if (!this.active) return;
    if (this.lastFrame !== null) this.frames.push(Math.max(0, now - this.lastFrame));
    this.lastFrame = now;
  }
  measure(name, milliseconds) {
    if (!this.active) return;
    const item = this.costs.get(name) || { count: 0, totalMs: 0, maxMs: 0 };
    item.count++; item.totalMs += milliseconds; item.maxMs = Math.max(item.maxMs, milliseconds);
    this.costs.set(name, item);
  }
  sample(context = {}) {
    if (!this.active || this.samples.length >= 900) return;
    const ordered = [...this.frames].sort((a,b)=>a-b);
    const average = this.frames.length ? this.frames.reduce((a,b)=>a+b,0)/this.frames.length : 0;
    this.samples.push({
      second: Math.round((this.clock()-this.started)/1000),
      fps: this.frames.length,
      frameAverageMs: +average.toFixed(2),
      frameP95Ms: +(ordered[Math.min(ordered.length-1,Math.ceil(ordered.length*.95)-1)]||0).toFixed(2),
      slowFrames33: this.frames.filter(x=>x>33).length,
      costs: Object.fromEntries(this.costs),
      ...context
    });
    this.frames=[]; this.costs.clear();
  }
  stop(context = {}) {
    if (!this.active) return null;
    this.sample(context);
    const result = { schema:'hytale-civ-live-performance', version:1,
      durationSeconds: +( (this.clock()-this.started)/1000 ).toFixed(1),
      context:this.context, samples:this.samples };
    this.started=null;
    return result;
  }
}
export function downloadPerformance(data) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));
  const a=document.createElement('a');a.href=url;a.download='civ-live-performance-'+Date.now()+'.json';
  a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
