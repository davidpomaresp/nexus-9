import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError, toRows, type Incident, type Module, type Row, type Simulation } from './api';

export interface SimulationState {
  sim: Simulation | null;
  rows: Row[];
  modules: Module[];
  incidents: Incident[];
}

const EMPTY: SimulationState = { sim: null, rows: [], modules: [], incidents: [] };
const MAX_ROWS = 5000;

/**
 * The server owns the clock: this hook only starts and pauses it, and listens to the simulation's
 * event stream to know when to reload. Several tabs on the same simulation stay in sync.
 */
export function useSimulation() {
  const [state, setState] = useState<SimulationState>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState(false);

  const stateRef = useRef<SimulationState>(EMPTY);
  const busy = useRef(false);
  const refreshing = useRef(false);
  const pending = useRef(false);
  // changes when another simulation is opened
  const generation = useRef(0);

  const fail = useCallback((e: unknown) => setError(e instanceof Error ? e.message : String(e)), []);

  // true while the simulation that started a request is still the one on screen
  const still = useCallback((gen: number, id: string) =>
    gen === generation.current && stateRef.current.sim?.id === id, []);

  const publish = useCallback((next: SimulationState) => {
    stateRef.current = next;
    setState(next);
  }, []);

  // Only asks for readings we don't have yet. Calls made while one is running are queued as one more.
  const refresh = useCallback(async () => {
    if (refreshing.current) {
      pending.current = true;
      return;
    }
    refreshing.current = true;
    try {
      do {
        pending.current = false;
        const current = stateRef.current;
        if (!current.sim) break;
        const id = current.sim.id;
        const gen = generation.current;
        const from = current.rows.length ? current.rows[current.rows.length - 1].tick + 1 : 0;
        try {
          const next = await api.state(id, from);
          const latest = stateRef.current;
          // another simulation was opened meanwhile, load that one instead
          if (gen !== generation.current || latest.sim?.id !== id) {
            pending.current = true;
            continue;
          }
          // older than what we already have
          if (next.simulation.currentTick < latest.sim.currentTick) continue;
          const fresh = toRows(next.readings);
          // we were more than a window behind, start over
          const keep = fresh.length && fresh[0].tick !== from ? [] : latest.rows;
          const rows = [...keep.filter(r => r.tick < (fresh[0]?.tick ?? Infinity)), ...fresh].slice(-MAX_ROWS);
          publish({ sim: next.simulation, rows, modules: next.modules, incidents: next.incidents });
        } catch (e) {
          if (still(gen, id)) fail(e);
        }
      } while (pending.current);
    } finally {
      refreshing.current = false;
    }
  }, [fail, publish, still]);

  // Events only say that something changed. The screen is always updated from a full /state answer,
  // so the summary, modules, charts and incidents never come from different ticks.
  const changed = useCallback((sim: Simulation) => {
    if (stateRef.current.sim?.id === sim.id) void refresh();
  }, [refresh]);

  const simId = state.sim?.id;

  useEffect(() => {
    if (!simId) return;
    const source = new EventSource(`/api/simulations/${simId}/events`);
    source.onopen = () => setLive(true);
    source.onerror = () => setLive(false);
    source.addEventListener('state', e => changed(JSON.parse((e as MessageEvent).data)));
    return () => {
      source.close();
      setLive(false);
    };
  }, [simId, changed]);

  const open = useCallback(async (id: string) => {
    setError(null);
    const gen = ++generation.current;
    try {
      const loaded = await api.state(id, 0);
      if (gen !== generation.current) return;
      publish({
        sim: loaded.simulation,
        rows: toRows(loaded.readings).slice(-MAX_ROWS),
        modules: loaded.modules,
        incidents: loaded.incidents,
      });
      return loaded.simulation;
    } catch (e) {
      if (gen === generation.current) {
        publish(EMPTY);
        if (e instanceof ApiError && e.status === 404) setError('Esta simulación no existe o ya ha caducado');
        else fail(e);
      }
    }
  }, [publish, fail]);

  /** Back to no simulation, for example when going back to the home page. */
  const close = useCallback(() => {
    generation.current++;
    setError(null);
    publish(EMPTY);
  }, [publish]);

  const create = useCallback(async (seed: number, scenario: string) => {
    setError(null);
    const gen = ++generation.current;
    try {
      const previous = stateRef.current.sim;
      if (previous?.running) await api.pause(previous.id);
      const created = await api.create(seed, scenario);
      if (gen !== generation.current) return;
      publish({ ...EMPTY, sim: created });
      await refresh();
      return created;
    } catch (e) {
      if (gen === generation.current) fail(e);
    }
  }, [publish, refresh, fail]);

  /**
   * Runs one request at a time against the current simulation. The lock is only held for the
   * request itself, so a reload kept busy by live events never blocks Pause.
   */
  const command = useCallback(async (call: (id: string) => Promise<Simulation>) => {
    const current = stateRef.current.sim;
    if (busy.current || !current) return;
    busy.current = true;
    const gen = generation.current;
    setError(null);
    try {
      await call(current.id);
    } catch (e) {
      if (still(gen, current.id)) fail(e);
      return;
    } finally {
      busy.current = false;
    }
    void refresh();
  }, [refresh, fail, still]);

  const advance = useCallback((ticks: number) => command(id => api.advance(id, ticks)), [command]);
  const run = useCallback((speed: number) => command(id => api.run(id, speed)), [command]);
  const pause = useCallback(() => command(id => api.pause(id)), [command]);

  const acknowledge = useCallback(async (incident: string) => {
    const current = stateRef.current.sim;
    if (!current) return;
    setError(null);
    try {
      await api.acknowledge(current.id, incident);
      await refresh();
    } catch (e) {
      fail(e);
    }
  }, [refresh, fail]);

  return { ...state, error, live, open, close, create, advance, run, pause, acknowledge };
}
