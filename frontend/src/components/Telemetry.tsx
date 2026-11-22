import { useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react';
import type { CommsMode, Incident, Priority, Row } from '../api';
import { BATTERY_THRESHOLDS, OXYGEN_LOW, eclipses, missionTime } from '../station';

interface Props {
  rows: Row[];
  incidents: Incident[];
  selected: number | null;
  focusedIncident: string | null;
  onSelect: (tick: number | null) => void;
}

const W = 1000;
const PAD_L = 40;
const PAD_R = 10;
const MIN_DOMAIN = 600;
const PRIORITY_CLASS: Record<Priority, string> = { LOW: 'ok', MEDIUM: 'warn', HIGH: 'warn', CRITICAL: 'crit' };
const COMMS_CLASS: Record<CommsMode, string> = { FULL: 'ok', MINIMAL: 'warn', OFF: 'off' };

export function Telemetry({ rows, incidents, selected, focusedIncident, onSelect }: Props) {
  const [hover, setHover] = useState<number | null>(null);
  const press = useRef<{ x: number; y: number } | null>(null);
  if (!rows.length) return <p className="muted">Avanza la simulación para ver la telemetría.</p>;

  const first = rows[0].tick;
  const last = rows[rows.length - 1].tick;
  const domain = Math.max(MIN_DOMAIN, rows.length);
  const x = (tick: number) => PAD_L + ((tick - first) / (domain - 1)) * (W - PAD_L - PAD_R);
  const step = (W - PAD_L - PAD_R) / (domain - 1);
  const rowAt = (tick: number | null) => (tick === null ? null : rows[tick - first] ?? null);
  const cursor = hover ?? selected;

  function tickAt(e: PointerEvent<SVGSVGElement>) {
    const box = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - box.left) / box.width) * W;
    const tick = Math.round(first + ((px - PAD_L) / (W - PAD_L - PAD_R)) * (domain - 1));
    return tick >= first && tick <= last ? tick : null;
  }

  function key(e: KeyboardEvent<HTMLDivElement>) {
    const from = selected ?? last;
    const by = e.shiftKey ? 10 : 1;
    const moves: Record<string, number> = {
      ArrowLeft: from - by, ArrowDown: from - by, ArrowRight: from + by, ArrowUp: from + by,
      PageDown: from - 60, PageUp: from + 60, Home: first, End: last,
    };
    if (e.key === 'Escape' && selected !== null) {
      onSelect(null);
    } else if (e.key in moves) {
      onSelect(Math.min(last, Math.max(first, moves[e.key])));
    } else {
      return;
    }
    e.preventDefault();
  }

  // mouse: hover previews, click pins. touch: a tap pins, a drag just scrolls the page
  const pointer = {
    onPointerMove: (e: PointerEvent<SVGSVGElement>) => e.pointerType === 'mouse' && setHover(tickAt(e)),
    onPointerLeave: () => setHover(null),
    onPointerDown: (e: PointerEvent<SVGSVGElement>) => { press.current = { x: e.clientX, y: e.clientY }; },
    onPointerCancel: () => { press.current = null; },
    onPointerUp: (e: PointerEvent<SVGSVGElement>) => {
      const start = press.current;
      press.current = null;
      if (!start || Math.hypot(e.clientX - start.x, e.clientY - start.y) > 10) return;
      const tick = tickAt(e);
      if (tick !== null) onSelect(tick);
    },
  };

  const shared = { rows, incidents, first, domain, x, step, cursor, focusedIncident, pointer };
  const line = (y: (v: number) => number, value: (r: Row) => number) =>
    rows.map((r, i) => `${i ? 'L' : 'M'}${x(r.tick).toFixed(1)} ${y(value(r)).toFixed(1)}`).join('');
  const lastX = x(last);
  const shown = rowAt(cursor);

  return (
    <div className="telemetry-charts">
      <Readout row={shown} tick={cursor} live={cursor === null ? rowAt(last) : null} />
      <div className="scroll-x chart-slider" tabIndex={0} role="slider" onKeyDown={key}
        aria-label="Tick de la telemetría" aria-valuemin={first} aria-valuemax={last}
        aria-valuenow={selected ?? last}
        aria-valuetext={selected === null ? `directo, tick ${last}` : `tick ${selected}, ${missionTime(selected)}`}>
      <Chart {...shared} title="Energía (kW)" height={120} min={0} max={13} grid={[0, 4, 8, 12]}
        legend={[['accent', 'generación'], ['ink', 'demanda'], ['crit', 'sin servir']]}>
        {y => (
          <>
            <path d={`${line(y, r => r.demand - r.served)}L${lastX} ${y(0)}L${x(first)} ${y(0)}Z`} className="area-crit" />
            <path d={line(y, r => r.generation)} className="series accent" />
            <path d={line(y, r => r.demand)} className="series ink" />
          </>
        )}
      </Chart>
      <Chart {...shared} title="Batería (%)" height={110} min={0} max={100} grid={[0, 25, 50, 75, 100]}
        legend={[['accent', 'carga'], ['warn', '30 · 20 · 15'], ['crit', '5']]}>
        {y => (
          <>
            {[BATTERY_THRESHOLDS.commsMinimal, BATTERY_THRESHOLDS.low, BATTERY_THRESHOLDS.commsOff].map(v =>
              <line key={v} x1={PAD_L} x2={W - PAD_R} y1={y(v)} y2={y(v)} className="threshold warn" />)}
            <line x1={PAD_L} x2={W - PAD_R} y1={y(BATTERY_THRESHOLDS.critical)} y2={y(BATTERY_THRESHOLDS.critical)}
              className="threshold crit" />
            <path d={`${line(y, r => r.battery)}L${lastX} ${y(0)}L${x(first)} ${y(0)}Z`} className="area-accent" />
            <path d={line(y, r => r.battery)} className="series accent thick" />
          </>
        )}
      </Chart>
      <Chart {...shared} title="Oxígeno (% del nominal)" height={90} min={0} max={100} grid={[0, 50, 100]}
        legend={[['ok', 'nivel'], ['crit', `umbral ${OXYGEN_LOW}`]]}>
        {y => (
          <>
            <line x1={PAD_L} x2={W - PAD_R} y1={y(OXYGEN_LOW)} y2={y(OXYGEN_LOW)} className="threshold crit" />
            <path d={line(y, r => r.oxygen)} className="series ok thick" />
          </>
        )}
      </Chart>
      <Chart {...shared} title="Comunicaciones" height={22} min={0} max={1} grid={[]}
        legend={[['ok', 'FULL'], ['warn', 'MINIMAL'], ['off', 'OFF']]}>
        {() => commsBands(rows, x, step)}
      </Chart>
      </div>
      <p className="caption">
        Franjas sombreadas: la estación está en la sombra de la Tierra. Líneas discontinuas: apertura de incidencias.
        Con el foco en las gráficas, las flechas mueven el cursor (Mayús: de 10 en 10) y Esc vuelve al directo.
      </p>
    </div>
  );
}

function commsBands(rows: Row[], x: (t: number) => number, step: number) {
  const bands: ReactNode[] = [];
  let start = 0;
  for (let i = 1; i <= rows.length; i++) {
    if (i === rows.length || rows[i].comms !== rows[start].comms) {
      bands.push(<rect key={start} x={x(rows[start].tick)} y={3} height={16}
        width={x(rows[i - 1].tick) - x(rows[start].tick) + step} className={`band ${COMMS_CLASS[rows[start].comms]}`} />);
      start = i;
    }
  }
  return bands;
}

interface ChartProps {
  title: string;
  height: number;
  min: number;
  max: number;
  grid: number[];
  legend: [string, string][];
  rows: Row[];
  incidents: Incident[];
  first: number;
  domain: number;
  x: (tick: number) => number;
  step: number;
  cursor: number | null;
  focusedIncident: string | null;
  pointer: {
    onPointerMove: (e: PointerEvent<SVGSVGElement>) => void;
    onPointerLeave: () => void;
    onPointerDown: (e: PointerEvent<SVGSVGElement>) => void;
    onPointerCancel: () => void;
    onPointerUp: (e: PointerEvent<SVGSVGElement>) => void;
  };
  children: (y: (v: number) => number) => ReactNode;
}

function Chart(p: ChartProps) {
  const y = (v: number) => 4 + (1 - (v - p.min) / (p.max - p.min)) * (p.height - 8);
  const every = p.domain > 1500 ? 500 : 100;
  const axis: number[] = [];
  for (let t = Math.ceil(p.first / every) * every; t < p.first + p.domain; t += every) axis.push(t);

  return (
    <figure className="chart">
      <figcaption>
        <strong>{p.title}</strong>
        {p.legend.map(([c, l]) => <span key={l}><i className={`swatch ${c}`} />{l}</span>)}
      </figcaption>
      <svg viewBox={`0 0 ${W} ${p.height + 18}`} {...p.pointer} aria-hidden>
        {eclipses(p.rows).map(([from, to]) =>
          <rect key={from} x={p.x(from)} y={0} width={p.x(to) - p.x(from) + p.step} height={p.height} className="eclipse" />)}
        {p.grid.map(v => (
          <g key={v}>
            <line x1={PAD_L} x2={W - PAD_R} y1={y(v)} y2={y(v)} className="gridline" />
            <text x={PAD_L - 6} y={y(v) + 3} textAnchor="end" className="axis">{v}</text>
          </g>
        ))}
        {p.incidents.filter(i => i.openedAtTick >= p.first).map(i =>
          <line key={i.id} x1={p.x(i.openedAtTick)} x2={p.x(i.openedAtTick)} y1={0} y2={p.height}
            className={`marker ${PRIORITY_CLASS[i.priority]}${i.id === p.focusedIncident ? ' focused' : ''}`} />)}
        {p.children(y)}
        {p.cursor !== null && <line x1={p.x(p.cursor)} x2={p.x(p.cursor)} y1={0} y2={p.height} className="cursor" />}
        {axis.map(t => <text key={t} x={p.x(t)} y={p.height + 14} textAnchor="middle" className="axis">{t}</text>)}
      </svg>
    </figure>
  );
}

function Readout({ row, tick, live }: { row: Row | null; tick: number | null; live: Row | null }) {
  const shown = row ?? live;
  return (
    // don't announce every live tick
    <div className="readout" aria-live={row ? 'polite' : 'off'}>
      {shown ? (
        <>
          <span><b>{row ? `tick ${row.tick}` : `directo · tick ${shown.tick}`}</b> · {missionTime(shown.tick)} ·{' '}
            {shown.sunlit ? 'sol' : 'sombra'}</span>
          <span>gen {shown.generation.toFixed(2)} kW</span>
          <span>demanda {shown.demand.toFixed(1)} kW</span>
          <span>servido {shown.served.toFixed(2)} kW</span>
          <span>batería {shown.battery.toFixed(1)} %</span>
          <span>O₂ {shown.oxygen.toFixed(2)} %</span>
          <span>comms {shown.comms}</span>
        </>
      ) : <span className="muted">Sin lectura cargada para el tick {tick}.</span>}
    </div>
  );
}
