import { useCallback, useEffect, useState } from 'react';
import { api, simulationIdFromPath, simulationPath, type Incident, type ModuleStatus } from './api';
import { Controls } from './components/Controls';
import { IncidentLog } from './components/IncidentLog';
import { MasterCaution } from './components/MasterCaution';
import { OrbitDial } from './components/OrbitDial';
import { StationMap } from './components/StationMap';
import { Telemetry } from './components/Telemetry';
import { Timeline } from './components/Timeline';
import { ORBIT_TICKS, missionTime, orbitPhase } from './station';
import { useSimulation } from './useSimulation';

const SEVERITY: ModuleStatus[] = ['NOMINAL', 'DEGRADED', 'CRITICAL', 'OFFLINE'];

export function App() {
  const station = useSimulation();
  const [scenarios, setScenarios] = useState<string[]>([]);
  // null = following live
  const [selectedTick, setSelectedTick] = useState<number | null>(null);
  const [focusId, setFocusId] = useState<string | null>(null);
  const { sim, modules, rows, incidents, open, close } = station;
  const firstTick = rows.length ? rows[0].tick : null;
  const simId = sim?.id;

  useEffect(() => {
    api.scenarios().then(setScenarios).catch(() => setScenarios([]));
  }, []);

  useEffect(() => {
    const load = () => {
      const id = simulationIdFromPath(window.location.pathname);
      if (id) void open(id);
      else close();
    };
    // On the public demo the home page opens a shared simulation that is already under way.
    if (window.location.pathname === '/') {
      api.demo().then(({ id }) => {
        if (window.location.pathname !== '/') return;
        window.history.replaceState(null, '', simulationPath(id));
        void open(id);
      }).catch(() => undefined);
    }
    load();
    window.addEventListener('popstate', load);
    return () => window.removeEventListener('popstate', load);
  }, [open, close]);

  useEffect(() => {
    setSelectedTick(null);
    setFocusId(null);
    if (simId && simulationIdFromPath(window.location.pathname) !== simId) {
      window.history.pushState(null, '', simulationPath(simId));
    }
  }, [simId]);

  // The history only keeps the last 5,000 ticks: a reviewed tick that falls out of it goes back to live.
  useEffect(() => {
    if (selectedTick !== null && (firstTick === null || selectedTick < firstTick)) setSelectedTick(null);
  }, [selectedTick, firstTick]);

  const backToLive = useCallback(() => {
    setSelectedTick(null);
    setFocusId(null);
  }, []);

  const focusIncident = useCallback((incident: Incident) => {
    setFocusId(incident.id);
    setSelectedTick(firstTick !== null && incident.openedAtTick >= firstTick ? incident.openedAtTick : null);
  }, [firstTick]);

  const phase = sim ? orbitPhase(sim.currentTick) : null;
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
            <p>Control de misión{sim && <> · {sim.scenario} / {sim.seed}</>}</p>
          </div>
        </div>
        {sim && (
          <>
            <div className="clock">
              <span className="label">Tiempo de misión</span>
              <strong>{missionTime(sim.currentTick)}</strong>
              <span className="sub">
                tick {sim.currentTick} · <span className={station.live ? 'live on' : 'live'}>
                  {station.live ? (sim.running ? 'en directo' : 'conectado') : 'sin conexión'}</span>
              </span>
            </div>
            <dl className="stats">
              <div>
                <dt>Órbita</dt>
                <dd>{String(Math.floor(sim.currentTick / ORBIT_TICKS) + 1).padStart(2, '0')} ·{' '}
                  {phase!.position}/{ORBIT_TICKS}</dd>
              </div>
              <div>
                <dt>{phase!.sunlit ? 'Al sol' : 'En sombra'}</dt>
                <dd className={phase!.sunlit ? 'sun' : 'shade'}>−{phase!.remaining} min</dd>
              </div>
              <div className={`s-${overall}`}>
                <dt>Estación</dt>
                <dd className="state">{overall}</dd>
              </div>
            </dl>
            <MasterCaution incidents={incidents} onShow={focusIncident} />
          </>
        )}
      </header>

      <Controls station={station} scenarios={scenarios}>
        {sim && <Timeline rows={rows} incidents={incidents} currentTick={sim.currentTick} selected={selectedTick}
          focused={focusId} onFocus={focusIncident} />}
      </Controls>

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
              <h2>Estación y dependencias <small>estado en directo</small></h2>
              <StationMap modules={modules} incidents={incidents} focus={focus} />
              {focus && (
                <p className="map-focus">
                  <b>{focus.ruleCode}</b> · origen {focus.module} ·{' '}
                  {focus.affectedModules.length
                    ? `puede afectar a ${focus.affectedModules.join(', ')}` : 'no afecta a ningún otro módulo'}
                  {(firstTick === null || focus.openedAtTick < firstTick) &&
                    <> · su lectura (t={focus.openedAtTick}) ya no está en el historial cargado</>}
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
              <h2>Caution &amp; warning <small>{incidents.filter(i => i.status === 'OPEN' && i.acknowledgedAtTick === null).length} sin reconocer</small></h2>
              <IncidentLog incidents={incidents} currentTick={sim.currentTick} focused={focusId}
                onFocus={focusIncident} onAcknowledge={i => station.acknowledge(i.id)} />
            </section>
          </main>
        </>
      )}
    </div>
  );
}
