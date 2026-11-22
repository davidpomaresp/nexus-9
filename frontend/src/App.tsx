import { useCallback, useEffect, useState } from 'react';
import { api, simulationIdFromPath, simulationPath, type Incident, type ModuleStatus } from './api';
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
  // null = following live
  const [selectedTick, setSelectedTick] = useState<number | null>(null);
  const [focusId, setFocusId] = useState<string | null>(null);
  const { sim, modules, rows, incidents, open } = station;
  const simId = sim?.id;

  useEffect(() => {
    api.scenarios().then(setScenarios).catch(() => setScenarios([]));
  }, []);

  useEffect(() => {
    const load = () => {
      const id = simulationIdFromPath(window.location.pathname);
      if (id) void open(id);
    };
    load();
    window.addEventListener('popstate', load);
    return () => window.removeEventListener('popstate', load);
  }, [open]);

  useEffect(() => {
    setSelectedTick(null);
    setFocusId(null);
    if (simId && simulationIdFromPath(window.location.pathname) !== simId) {
      window.history.pushState(null, '', simulationPath(simId));
    }
  }, [simId]);

  const backToLive = useCallback(() => {
    setSelectedTick(null);
    setFocusId(null);
  }, []);

  const focusIncident = useCallback((incident: Incident) => {
    setFocusId(incident.id);
    setSelectedTick(incident.openedAtTick);
  }, []);

  const focus = incidents.find(i => i.id === focusId) ?? null;
  const reviewing = selectedTick !== null;
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
              <span className="sub">
                tick {sim.currentTick} · <span className={station.live ? 'live on' : 'live'}>
                  {station.live ? (sim.running ? 'en directo' : 'conectado') : 'sin conexión'}</span>
              </span>
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
        <>
          <div className={`viewbar ${reviewing ? 'past' : 'now'}`} role="status">
            {reviewing ? (
              <>
                <span>
                  <b>Revisando tick {selectedTick}</b> ({missionTime(selectedTick)})
                  {focus && <> · {focus.ruleCode}</>} · el directo sigue en tick {sim.currentTick}
                </span>
                <button type="button" onClick={backToLive}>Volver al directo</button>
              </>
            ) : (
              <span><b>Siguiendo el directo</b> · elige una incidencia o un punto de las gráficas para revisarlo</span>
            )}
          </div>
          <main className="grid">
            <section className="panel map">
              <h2>Estación <small>estado en directo</small></h2>
              <StationMap modules={modules} incidents={incidents} focus={focus} />
              {focus && (
                <p className="map-focus">
                  <b>{focus.ruleCode}</b> · origen {focus.module} · puede afectar a{' '}
                  {focus.affectedModules.join(', ') || 'ningún otro módulo'}
                </p>
              )}
            </section>
            <section className="panel orbit">
              <h2>Órbita</h2>
              <OrbitDial tick={sim.currentTick} />
            </section>
            <section className="panel telemetry">
              <h2>Telemetría</h2>
              <Telemetry rows={rows} incidents={incidents} selected={selectedTick} focusedIncident={focusId}
                onSelect={tick => (tick === null ? backToLive() : setSelectedTick(tick))} />
            </section>
            <section className="panel incidents">
              <h2>Incidencias</h2>
              <IncidentLog incidents={incidents} currentTick={sim.currentTick} focused={focusId}
                onFocus={focusIncident} />
            </section>
          </main>
        </>
      )}
    </div>
  );
}
