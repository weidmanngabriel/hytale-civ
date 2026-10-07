export function sortBranchesByLatestCommit(runs) {
  const latest = new Map();
  for (const run of runs) {
    const timestamp = Date.parse(run.commitAt || run.createdAt);
    const current = latest.get(run.branch);
    if (current == null || timestamp > current) latest.set(run.branch, timestamp);
  }
  return [...latest.keys()].sort((a, b) => {
    if (a === 'main') return -1;
    if (b === 'main') return 1;
    const byDate = (latest.get(b) || 0) - (latest.get(a) || 0);
    return byDate || a.localeCompare(b);
  });
}
