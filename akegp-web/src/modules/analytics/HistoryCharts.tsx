import { HistoryPoint, runLabel } from './api';

const colorFor = (key: string) => ({ high: '#d77b87', medium: '#eeb56b', low: '#77d99a', added: '#eeb56b', resolved: '#77d99a', persistent: '#829de5', total: '#77d99a', density: '#829de5' })[key as 'high'] || '#77d99a';
export function HistoryChart({ title, points, series, onSelect, unit = 'Violations' }: {
  title: string; points: HistoryPoint[]; series: { key: 'total' | 'high' | 'medium' | 'low' | 'added' | 'resolved' | 'persistent' | 'density'; label: string }[];
  onSelect: (id: string) => void; unit?: string;
}) {
  if (!points.length) return <section className="analytics-panel"><h2>{title}</h2><p>No observations in the selected date range.</p></section>;
  if (!points.some(p => series.some(s => p[s.key] != null))) return <section className="analytics-panel"><h2>{title}</h2><p>This metric is unavailable for the selected observations. Missing measurements are not zero.</p></section>;
  const max = Math.max(1, ...points.flatMap(p => series.map(s => p[s.key] ?? 0)));
  const start = Date.parse(points[0].at), end = Date.parse(points[points.length - 1].at);
  const x = (i: number) => 58 + (Date.parse(points[i].at) - start) * 690 / Math.max(1, end - start);
  const y = (n: number) => 225 - n / max * 185;
  return <section className="analytics-panel analytics-chart"><h2>{title}</h2>
    <div className="analytics-legend">{series.map(s => <span key={s.key}><i style={{ background: colorFor(s.key) }} />{s.label}</span>)}</div>
    <svg viewBox="0 0 790 280" role="img" aria-label={`${title}. X axis: run date UTC. Y axis: ${unit}. Select a point to inspect the run.`}>
      {[0, .25, .5, .75, 1].map(t => <g key={t}><line x1="58" x2="748" y1={y(max * t)} y2={y(max * t)} stroke="#303944" /><text x="49" y={y(max * t) + 4} textAnchor="end">{(max * t).toFixed(unit.includes('1,000') ? 1 : 0)}</text></g>)}
      <text x="58" y="20">{unit}</text>
      {series.map(s => <g key={s.key}>
        {points.map((p, i) => i > 0 && p[s.key] != null && points[i - 1][s.key] != null ? <line key={p.runId} x1={x(i - 1)} y1={y(points[i - 1][s.key]!)} x2={x(i)} y2={y(p[s.key]!)} stroke={colorFor(s.key)} strokeWidth="2.4" /> : null)}
        {points.map((p, i) => p[s.key] == null ? null : <circle key={p.runId} cx={x(i)} cy={y(p[s.key]!)} r="3.5" fill={colorFor(s.key)} tabIndex={0} role="button"
          aria-label={`${runLabel(p.run)}: ${s.label} ${p[s.key]}`} onClick={() => onSelect(p.runId)} onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(p.runId); } }}>
          <title>{`${runLabel(p.run)} · ${p.at.slice(0, 10)} · ${s.label}: ${p[s.key]} · ${p.reason}`}</title></circle>)}
      </g>)}
      {points.map((p, i) => i === 0 || i === points.length - 1 || i % Math.max(1, Math.floor(points.length / 4)) === 0 && i < points.length - Math.max(2, Math.floor(points.length / 10)) ? <text key={p.runId} x={x(i)} y="247" textAnchor="middle">{p.at.slice(5, 10)}</text> : null)}
      <text x="403" y="272" textAnchor="middle">Run date (UTC)</text>
    </svg><p className="analytics-caption">{points[0].at.slice(0, 10)} – {points[points.length - 1].at.slice(0, 10)} · {points.length} runs · Gaps represent unavailable metrics. Select a point to inspect a run.</p>
  </section>;
}

export function AreaHeatmap({ points, onSelect }: { points: HistoryPoint[]; onSelect: (id: string) => void }) {
  const areas = [...new Set(points.flatMap(p => Object.keys(p.areas)))].sort();
  const max = Math.max(1, ...points.flatMap(p => Object.values(p.areas)));
  return <section className="analytics-panel"><h2>Architectural area × run</h2><p>Active violation count by service. Dark cells mean zero; outlined cells indicate incomplete analysis.</p>
    <div className="analytics-heatmap" role="group" aria-label="Service violation count heatmap">
      {areas.map(area => <div className="analytics-heat-row" key={area}><span>{area}</span><div>{points.map(p => <button key={p.runId}
        className={p.total == null ? 'analytics-heat-unknown' : ''} style={p.total == null ? undefined : { background: `rgba(119,217,154,${.08 + .82 * (p.areas[area] || 0) / max})` }}
        title={`${area} · ${runLabel(p.run)} · ${p.total == null ? 'Unknown' : p.areas[area] || 0} violations`}
        aria-label={`${area}, ${runLabel(p.run)}, ${p.total == null ? 'incomplete analysis' : `${p.areas[area] || 0} violations`}`} onClick={() => onSelect(p.runId)} />)}</div></div>)}
    </div><p className="analytics-caption">Chronological runs → · Hover or focus a cell for details; select it to inspect the run. Counts use the full filtered snapshots.</p>
  </section>;
}
