import { useEffect, useRef, useState } from 'react';
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
const RANK: Record<Priority, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
const MIN_GAP = 18; // px, a bit more than a marker

export function Timeline({ rows, incidents, currentTick, selected, focused, onFocus }: Props) {
  const track = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  // the list keeps the incidents it was opened with, even if the grouping changes while it is open
  const [listed, setListed] = useState<string[]>([]);

  useEffect(() => {
    const el = track.current;
    if (!el) return;
    const watch = new ResizeObserver(() => setWidth(el.clientWidth));
    watch.observe(el);
    return () => watch.disconnect();
  }, [rows.length > 0]);

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

  // incidents too close to tell apart on screen share one marker that opens a list
  const groups: Incident[][] = [];
  const px = (tick: number) => ((tick - first) / span) * width;
  for (const i of incidents.filter(i => i.openedAtTick >= first).sort((a, b) => a.openedAtTick - b.openedAtTick)) {
    const last = groups[groups.length - 1];
    if (last && width > 0 && px(i.openedAtTick) - px(last[0].openedAtTick) < MIN_GAP) last.push(i);
    else groups.push([i]);
  }
  const shown = incidents.filter(i => listed.includes(i.id));

  return (
    <div className="timeline">
      <div className="timeline-track" ref={track}>
        {eclipses.map(([a, b]) =>
          <span key={a} className="timeline-eclipse" style={{ left: at(a), width: `calc(${at(b + 1)} - ${at(a)})` }} />)}
        <span className="timeline-progress" />
        {selected !== null && <span className="timeline-cursor" style={{ left: at(selected) }} />}
        {groups.map(g => {
          if (g.length === 1) {
            const i = g[0];
            return (
              <button key={i.id} type="button" style={{ left: at(i.openedAtTick) }}
                className={`timeline-mark ${LEVEL[i.priority]}${i.id === focused ? ' on' : ''}`}
                title={`${i.ruleCode} · t=${i.openedAtTick}`} aria-label={`${i.ruleCode}, tick ${i.openedAtTick}`}
                aria-pressed={i.id === focused} onClick={() => onFocus(i)} />
            );
          }
          const tick = g[0].openedAtTick;
          const worst = g.reduce((a, b) => (RANK[b.priority] < RANK[a.priority] ? b : a));
          const isOpen = g.every(i => listed.includes(i.id));
          return (
            <button key={g[0].id} type="button" style={{ left: at(tick) }}
              className={`timeline-mark group ${LEVEL[worst.priority]}`}
              aria-label={`${g.length} incidencias desde el tick ${tick}`} aria-expanded={isOpen}
              onClick={() => setListed(isOpen ? [] : g.map(i => i.id))}>{g.length}</button>
          );
        })}
      </div>
      {shown.length > 0 && (
        <ul className="timeline-list" aria-label="Incidencias del grupo">
          {shown.map(i => (
            <li key={i.id}>
              <button type="button" aria-pressed={i.id === focused} onClick={() => onFocus(i)}>
                {i.ruleCode} · t={i.openedAtTick}
              </button>
            </li>
          ))}
          <li><button type="button" onClick={() => setListed([])}>Cerrar</button></li>
        </ul>
      )}
      <div className="timeline-axis" aria-hidden>
        {marks.map((t, n) => <span key={n} style={{ left: at(t) }}>{missionTime(t)}</span>)}
      </div>
    </div>
  );
}
