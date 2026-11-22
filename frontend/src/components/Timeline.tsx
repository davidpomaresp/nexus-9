import type { Incident, Priority, Row } from '../api';
import { missionTime } from '../station';

interface Props {
  rows: Row[];
  incidents: Incident[];
  currentTick: number;
  selected: number | null;
  focused: string | null;
  onFocus: (incident: Incident) => void;
}

const LEVEL: Record<Priority, string> = { LOW: 'ok', MEDIUM: 'warn', HIGH: 'warn', CRITICAL: 'crit' };

export function Timeline({ rows, incidents, currentTick, selected, focused, onFocus }: Props) {
  if (!rows.length) return null;
  const first = rows[0].tick;
  const span = Math.max(1, currentTick - first);
  const at = (tick: number) => `${(((tick - first) / span) * 100).toFixed(3)}%`;

  const eclipses: [number, number][] = [];
  for (const r of rows) {
    if (r.sunlit) continue;
    const last = eclipses[eclipses.length - 1];
    if (last && last[1] === r.tick - 1) last[1] = r.tick;
    else eclipses.push([r.tick, r.tick]);
  }
  const marks = [0, 0.25, 0.5, 0.75, 1].map(f => Math.round(first + f * span));

  return (
    <div className="timeline">
      <div className="timeline-track">
        {eclipses.map(([a, b]) =>
          <span key={a} className="timeline-eclipse" style={{ left: at(a), width: `calc(${at(b + 1)} - ${at(a)})` }} />)}
        <span className="timeline-progress" />
        {selected !== null && <span className="timeline-cursor" style={{ left: at(selected) }} />}
        {incidents.filter(i => i.openedAtTick >= first).map(i => (
          <button key={i.id} type="button" style={{ left: at(i.openedAtTick) }}
            className={`timeline-mark ${LEVEL[i.priority]}${i.id === focused ? ' on' : ''}`}
            title={`${i.ruleCode} · t=${i.openedAtTick}`} aria-label={`${i.ruleCode}, tick ${i.openedAtTick}`}
            aria-pressed={i.id === focused} onClick={() => onFocus(i)} />
        ))}
      </div>
      <div className="timeline-axis" aria-hidden>
        {marks.map((t, n) => <span key={n} style={{ left: at(t) }}>{missionTime(t)}</span>)}
      </div>
    </div>
  );
}
