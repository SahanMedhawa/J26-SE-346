const { test, before } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const compiled = {};
before(async () => {
  const { transformWithOxc } = await import('vite');
  for (const file of ['api.ts', 'AnalyticsPage.tsx', 'HistoryCharts.tsx']) {
    const filename = path.join(__dirname, '../src/modules/analytics', file);
    compiled[file] = (await transformWithOxc(fs.readFileSync(filename, 'utf8'), filename, { jsx: { runtime: 'automatic' } })).code;
  }
});
function load(file, mocks = {}) {
  const names = [];
  let code = compiled[file].replace(/import\s*['"][^'"]+\.css['"];?/g, '');
  code = code.replace(/import\s*\{([^}]+)\}\s*from\s*(['"][^'"]+['"]);/g, (_, members, source) => `const {${members.replace(/\s+as\s+/g, ': ')}} = require(${source});`);
  code = code.replace(/export (async )?(function|const|class) (\w+)/g, (_, a, kind, name) => { names.push(name); return `${a || ''}${kind} ${name}`; });
  const module = { exports: {} };
  new Function('require', 'module', `${code}\nmodule.exports={${names.join(',')}};`)(name => name in mocks ? mocks[name] : require(name), module);
  return module.exports;
}
function ui(mocks = {}) {
  return load('AnalyticsPage.tsx', { './api': load('api.ts'), '../../shared/projects/SelectedProject': { useSelectedProject: () => ({ selectedProject: null }) }, '../../shared/projects/ProjectWorkspaceLayout': { ProjectWorkspaceLayout: () => null }, './HistoryCharts': { HistoryChart: () => null, AreaHeatmap: () => null }, ...mocks });
}
function text(node) {
  if (node == null || typeof node === 'boolean') return '';
  if (typeof node !== 'object') return String(node);
  return React.Children.toArray(node.props?.children).map(text).join(' ');
}
function mount(request) {
  let cursor = 0, changed = false, tree;
  const slots = [], effects = [], calls = [];
  const hooks = { ...React,
    useState(initial) {
      const i = cursor++; if (!slots[i]) slots[i] = { value: initial };
      return [slots[i].value, value => { const next = typeof value === 'function' ? value(slots[i].value) : value;
        if (!Object.is(next, slots[i].value)) { slots[i].value = next; changed = true; } }];
    },
    useEffect(effect, deps) {
      const i = cursor++; const old = slots[i];
      if (!old || deps.some((v, j) => !Object.is(v, old.deps[j]))) {
        effects.push(() => { old?.cleanup?.(); slots[i] = { deps, cleanup: effect() }; });
      }
    },
  };
  const { AnalyticsWorkspace } = ui({ react: hooks, 'react-router-dom': { useLocation: () => ({ pathname: '/analytics/dashboard' }), Link: 'a' },
    './api': { ...load('api.ts'), analyticsRequest: (...args) => { calls.push(args); return request(...args); } } });
  function render() { let loops = 0; do { assert.ok(loops++ < 20); cursor = 0; changed = false;
    tree = AnalyticsWorkspace({ project: 'test-project' }); while (effects.length) effects.shift()(); } while (changed); }
  render();
  return { calls, get text() { return text(tree); }, async flush() { for (let i = 0; i < 5; i++) { await new Promise(setImmediate); render(); } },
    close() { slots.forEach(s => s.cleanup?.()); } };
}
test('baseline unavailable metrics are not rendered as zero', () => {
  const { ComparisonCards } = ui();
  const html = renderToStaticMarkup(React.createElement(ComparisonCards, { comparison: { currentTotal: 3, newCount: null, resolvedCount: null, persistingCount: null, netChange: null } }));
  assert.equal((html.match(/Unavailable/g) || []).length, 4);
  assert.match(html, />3</);
  assert.equal(load('api.ts').displayCount(0), '0');
});
test('initial loading and empty history remain separate', async () => {
  const h = mount(async () => []);
  assert.match(h.text, /Loading analytics/);
  await h.flush(); assert.match(h.text, /No snapshots yet/);
  assert.equal(h.calls.length, 1); h.close();
});
test('API failure is visible and never triggers a demo fallback', async () => {
  const h = mount(async () => { throw new Error('Access denied'); });
  await h.flush(); assert.match(h.text, /Access denied/); assert.match(h.text, /not substituted/);
  assert.equal(h.calls.length, 1); assert.equal(h.calls[0][0], 'test-project'); h.close();
});
test('insufficient and one-run hotspot windows are explicit', () => {
  const { HotspotTable } = ui();
  const base = { window: { actualRuns: 0, boundaryReason: 'No complete runs', limitedHistory: false }, label: 'Demo snapshot - not upstream analysis', grouping: 'SERVICE', cohortSize: 0, status: 'INSUFFICIENT_DATA' };
  assert.match(renderToStaticMarkup(React.createElement(HotspotTable, { ranking: base, currentName: 'None', onPage() {} })), /Insufficient data/);
  const one = { ...base, status: 'AVAILABLE', window: { ...base.window, actualRuns: 1, limitedHistory: true } };
  assert.match(renderToStaticMarkup(React.createElement(HotspotTable, { ranking: one, currentName: 'Run 1', onPage() {} })), /Limited history: one run/);
});
test('request encodes project and throws on server failure instead of returning fake metrics', async () => {
  const old = global.fetch;
  let url;
  global.fetch = async value => { url = value; return { ok: false, status: 503, json: async () => ({ message: 'Storage unavailable' }) }; };
  try { await assert.rejects(load('api.ts').analyticsRequest('project/x', 'runs', {}), /Storage unavailable/);
    assert.match(url, /project%2Fx\/runs/); } finally { global.fetch = old; }
});

test('recorded history is selected ahead of demo and feature branch snapshots', async () => {
  const h = mount(async (_project, endpoint) => {
    if (endpoint === 'runs') return [
      { id: 'demo-main', provenance: 'DEMO', dataset: 'demo-v1', branchRef: 'refs/heads/main', sequence: 90 },
      { id: 'real-main', provenance: 'UPSTREAM', dataset: '', branchRef: 'refs/heads/main', sequence: 5 },
      { id: 'real-feature', provenance: 'UPSTREAM', dataset: '', branchRef: 'refs/heads/feature', sequence: 99 },
    ].map(r => ({ externalRunId: r.id, streamKey: r.id, analyzedAt: '2026-10-19T09:00:00Z', analysisStatus: 'COMPLETE', scope: [], ...r }));
    if (endpoint === 'comparisons') return { currentRunId: 'real-main', violations: [], reason: 'Complete snapshots' };
    if (endpoint === 'explorer') return { points: [], services: [], rules: [], completeRuns: 1, incompleteRuns: 0 };
    return { rows: [], window: { currentRunId: 'real-main', runIds: ['real-main'], actualRuns: 1 } };
  });
  await h.flush();
  const comparisons = h.calls.filter(c => c[1] === 'comparisons');
  assert.ok(comparisons.length > 0);
  assert.ok(comparisons.every(c => c[2].currentRunId === 'real-main'));
  assert.match(h.text, /Recorded architecture snapshots/); h.close();
});

test('charts preserve missing observations and show an explicit unavailable metric', () => {
  const { HistoryChart } = load('HistoryCharts.tsx', { './api': load('api.ts') });
  const points = [
    { runId: 'a', run: 'Run A', at: '2026-07-28T09:00:00Z', total: 3, reason: 'Baseline' },
    { runId: 'b', run: 'Run B', at: '2026-07-29T09:00:00Z', total: null, reason: 'Incomplete' },
    { runId: 'c', run: 'Run C', at: '2026-07-30T09:00:00Z', total: 2, reason: 'New baseline' },
  ];
  const html = renderToStaticMarkup(React.createElement(HistoryChart, { title: 'Active violations', points, series: [{ key: 'total', label: 'Active' }], onSelect() {} }));
  assert.equal((html.match(/<circle/g) || []).length, 2);
  assert.match(html, /Run date \(UTC\)/);
  const absent = renderToStaticMarkup(React.createElement(HistoryChart, { title: 'Density', points, series: [{ key: 'density', label: 'Density' }], onSelect() {} }));
  assert.match(absent, /metric is unavailable/); assert.doesNotMatch(absent, /<svg/);
});

test('run and date modes are explicit and never silently override one another', () => {
  const { windowParams } = load('api.ts');
  const filters = { from: '2026-10-01', to: '2026-10-20', service: '', rule: '', severity: '', search: '' };
  const runs = windowParams('r84', { mode: 'runs', runs: '5', grouping: 'MODULE' }, filters);
  assert.equal(runs.runs, '5'); assert.equal(runs.grouping, 'MODULE'); assert.equal(runs.from, undefined);
  const dates = windowParams('r84', { mode: 'time', runs: '20', grouping: 'RULE' }, filters);
  assert.equal(dates.from, '2026-10-01T00:00:00Z'); assert.equal(dates.to, '2026-10-20T00:00:00Z');
  assert.throws(() => windowParams('r84', { mode: 'time', runs: '20', grouping: 'RULE' }, { ...filters, to: '2026-09-01' }), /start date/);
});

test('baseline choices stop at failed runs and comparison cards share model totals', () => {
  const { comparableBaselines } = load('api.ts');
  const history = [1,2,3,4].map(n => ({ id: String(n), sequence: n, streamKey: 'main', analysisStatus: n === 2 ? 'FAILED' : 'COMPLETE', ruleSetVersion: 'v1', scope: [] }));
  assert.deepEqual(comparableBaselines(history, '4').map(r => r.id), ['3']);
  const { ComparisonSummary } = ui();
  const html = renderToStaticMarkup(React.createElement(ComparisonSummary, { runName: id => id || 'None', comparison: {
    currentRunId: 'N', predecessorRunId: 'N-1', comparable: true, currentTotal: 4, previousTotal: 3,
    newCount: 2, persistingCount: 2, resolvedCount: 1, netChange: 1,
  } }));
  assert.match(html, /Active 4 = New 2 \+ Persistent 2/);
  assert.match(html, /Previous 3 = Resolved 1 \+ Persistent 2/);
  assert.match(html, /Net change \+1 = New 2/);
});
