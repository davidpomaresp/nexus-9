import type { ReactNode } from 'react';
import type { Incident, Module, ModuleName } from '../api';

interface Props {
  modules: Module[];
  incidents: Incident[];
}

interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

const BOX: Record<ModuleName, Box> = {
  POWER: { x: 10, y: 40, w: 250, h: 200 },
  OXYGEN: { x: 400, y: 10, w: 230, h: 120 },
  COMMS: { x: 400, y: 150, w: 230, h: 120 },
};

const fmt = (v: number | undefined, digits = 1) => (typeof v === 'number' ? v.toFixed(digits) : '—');

export function StationMap({ modules, incidents }: Props) {
  const byName = new Map(modules.map(m => [m.module, m]));
  const power = byName.get('POWER');
  const oxygen = byName.get('OXYGEN');
  const comms = byName.get('COMMS');
  // Modules exposed to an open power incident, as the dependency graph says.
  const exposed = new Set(incidents.filter(i => i.status === 'OPEN' && i.module === 'POWER')
    .flatMap(i => i.affectedModules));

  return (
    <div className="scroll-x"><svg className="station-map" viewBox="0 0 640 280" role="img"
      aria-label="Esquema de la estación: energía alimenta a oxígeno y comunicaciones">
      <Link to="OXYGEN" live={oxygen?.values.generatorPowered === true} exposed={exposed.has('OXYGEN')} />
      <Link to="COMMS" live={comms?.values.mode !== undefined && comms.values.mode !== 'OFF'}
        exposed={exposed.has('COMMS')} />

      <Node module={power} name="POWER" title="Energía">
        <Metric y={80} label="Generación" value={`${fmt(power?.values.generationKw, 2)} kW`} />
        <Metric y={102} label="Demanda" value={`${fmt(power?.values.demandKw)} kW`} />
        <Metric y={124} label="Servido" value={`${fmt(power?.values.servedKw, 2)} kW`} />
        <Metric y={146} label="Batería" value={`${fmt(power?.values.batteryPercent)} %`} />
        <Bar y={160} w={BOX.POWER.w - 32} percent={power?.values.batteryPercent ?? 0} />
      </Node>

      <Node module={oxygen} name="OXYGEN" title="Oxígeno">
        <Metric y={80} label="Nivel" value={`${fmt(oxygen?.values.levelPercent, 2)} %`} />
        <Metric y={102} label="Generador"
          value={oxygen?.values.generatorPowered === undefined ? '—'
            : oxygen.values.generatorPowered ? 'alimentado' : 'sin energía'} />
      </Node>

      <Node module={comms} name="COMMS" title="Comunicaciones">
        <Metric y={80} label="Modo" value={comms?.values.mode ?? '—'} />
        <Metric y={102} label="Consumo"
          value={{ FULL: '1.5 kW', MINIMAL: '0.4 kW', OFF: '0 kW' }[comms?.values.mode ?? 'OFF']} />
      </Node>
    </svg></div>
  );
}

function Node({ module, name, title, children }: {
  module: Module | undefined; name: ModuleName; title: string; children: ReactNode;
}) {
  const { x, y, w, h } = BOX[name];
  const status = module?.status ?? 'NOMINAL';
  return (
    <g transform={`translate(${x} ${y})`} className={`node s-${status}`}>
      <rect width={w} height={h} rx={10} className="node-box" />
      <rect width={4} height={h - 20} x={0} y={10} rx={2} className="node-edge" />
      <text x={16} y={28} className="node-name">{name}</text>
      <text x={16} y={44} className="node-title">{title}</text>
      <g transform={`translate(${w - 16} 16)`}>
        <rect x={-90} width={90} height={22} rx={11} className="node-pill" />
        <text x={-45} y={15} textAnchor="middle" className="node-pill-text">{status}</text>
      </g>
      {module?.highestPriority && (
        <text x={w - 16} y={54} textAnchor="end" className="node-priority">
          máx. {module.highestPriority}
        </text>
      )}
      <g transform="translate(16 0)">{children}</g>
    </g>
  );
}

function Metric({ y, label, value }: { y: number; label: string; value: string }) {
  return (
    <>
      <text y={y} className="metric-label">{label}</text>
      <text x={200} y={y} textAnchor="end" className="metric-value">{value}</text>
    </>
  );
}

function Bar({ y, w, percent }: { y: number; w: number; percent: number }) {
  const level = percent < 5 ? 'crit' : percent < 30 ? 'warn' : 'ok';
  return (
    <g transform={`translate(0 ${y})`}>
      <rect width={w} height={8} rx={4} className="bar-track" />
      <rect width={(w * Math.max(0, Math.min(100, percent))) / 100} height={8} rx={4} className={`bar-${level}`} />
      {[30, 15, 5].map(t => <line key={t} x1={(w * t) / 100} x2={(w * t) / 100} y1={-2} y2={10} className="bar-mark" />)}
    </g>
  );
}

function Link({ to, live, exposed }: { to: ModuleName; live: boolean; exposed: boolean }) {
  const from = BOX.POWER;
  const target = BOX[to];
  const x1 = from.x + from.w, y1 = from.y + from.h / 2;
  const x2 = target.x, y2 = target.y + target.h / 2;
  const d = `M${x1} ${y1} C${x1 + 70} ${y1}, ${x2 - 70} ${y2}, ${x2} ${y2}`;
  return (
    <g className={`link ${live ? 'live' : 'dead'} ${exposed ? 'exposed' : ''}`}>
      <path d={d} className="link-base" />
      <path d={d} className="link-flow" />
    </g>
  );
}
