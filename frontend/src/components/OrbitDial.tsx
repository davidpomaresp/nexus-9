import { ORBIT_TICKS, SUNLIT_TICKS, orbitPhase } from '../station';

const R = 70;
const C = 90;

function arc(from: number, to: number) {
  const a0 = from * 2 * Math.PI - Math.PI / 2;
  const a1 = to * 2 * Math.PI - Math.PI / 2;
  const large = to - from > 0.5 ? 1 : 0;
  return `M${C + R * Math.cos(a0)} ${C + R * Math.sin(a0)} A${R} ${R} 0 ${large} 1 ${C + R * Math.cos(a1)} ${C + R * Math.sin(a1)}`;
}

export function OrbitDial({ tick }: { tick: number }) {
  const phase = orbitPhase(tick);
  const sunlitEnd = SUNLIT_TICKS / ORBIT_TICKS;
  const angle = phase.fraction * 2 * Math.PI - Math.PI / 2;
  return (
    <div className="orbit-dial">
      <svg viewBox="0 0 180 180" role="img"
        aria-label={`${phase.sunlit ? 'Al sol' : 'En sombra'}, ${phase.remaining} minutos para el cambio`}>
        <path d={arc(0, sunlitEnd)} className="orbit-sun" />
        <path d={arc(sunlitEnd, 1)} className="orbit-shade" />
        <circle cx={C} cy={C} r={30} className="planet" />
        <circle cx={C + R * Math.cos(angle)} cy={C + R * Math.sin(angle)} r={7}
          className={phase.sunlit ? 'craft lit' : 'craft'} />
      </svg>
      <div className="orbit-text">
        <strong>{phase.sunlit ? 'Al sol' : 'En sombra'}</strong>
        <span>{phase.remaining} min para {phase.sunlit ? 'la sombra' : 'el sol'}</span>
        <span>Órbita {Math.floor(tick / ORBIT_TICKS) + 1} · {ORBIT_TICKS} min</span>
      </div>
    </div>
  );
}
