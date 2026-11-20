import { useEffect, useState } from 'react';
import { api, type ModuleStatus } from './api';
import { Controls } from './components/Controls';
import { IncidentLog } from './components/IncidentLog';
import { OrbitDial } from './components/OrbitDial';
import { StationMap } from './components/StationMap';
import { Telemetry } from './components/Telemetry';
import { missionTime } from './station';
import { useSimulation } from './useSimulation';

const SEVERITY: ModuleStatus[] = ['NOMINAL', 'DEGRADED', 'CRITICAL', 'OFFLINE'];

export function App() {
  const station = useSimulation();
  const [scenarios, setScenarios] = useState<string[]>([]);
  const { sim, modules, rows, incidents } = station;

  useEffect(() => {
    api.scenarios().then(setScenarios).catch(() => setScenarios([]));
  }, []);

  const overall = modules.reduce<ModuleStatus>(
    (worst, m) => (SEVERITY.indexOf(m.status) > SEVERITY.indexOf(worst) ? m.status : worst), 'NOMINAL');

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="logo" aria-hidden />
          <div>
            <h1>NEXUS-9</h1>
            <p>Centro de control de la estación</p>
          </div>
        </div>
        {sim && (
          <div className="mission">
            <div className="clock">
              <span className="label">Tiempo de misión</span>
              <strong>{missionTime(sim.currentTick)}</strong>
              <span className="sub">tick {sim.currentTick}</span>
            </div>
            <div className={`overall s-${overall}`}>
              <span className="label">Estación</span>
              <strong>{overall}</strong>
              <span className="sub">{sim.openIncidents} incidencias abiertas</span>
            </div>
          </div>
        )}
      </header>

      <Controls station={station} scenarios={scenarios} />

      {station.error && <div className="error" role="alert">{station.error}</div>}

      {!sim ? (
        <section className="panel empty-state">
          <h2>Ninguna simulación en curso</h2>
          <p>
            Elige un escenario y una semilla y pulsa <b>Nueva simulación</b>. Con <b>solar-degradation</b> fallan
            los paneles solares en el tick 120 y verás cómo la avería se propaga a la batería, las comunicaciones
            y el oxígeno.
          </p>
        </section>
      ) : (
        <main className="grid">
          <section className="panel map">
            <h2>Estación</h2>
            <StationMap modules={modules} incidents={incidents} />
          </section>
          <section className="panel orbit">
            <h2>Órbita</h2>
            <OrbitDial tick={sim.currentTick} />
          </section>
          <section className="panel telemetry">
            <h2>Telemetría</h2>
            <Telemetry rows={rows} incidents={incidents} />
          </section>
          <section className="panel incidents">
            <h2>Incidencias</h2>
            <IncidentLog incidents={incidents} currentTick={sim.currentTick} />
          </section>
        </main>
      )}
    </div>
  );
}
