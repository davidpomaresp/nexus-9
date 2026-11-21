import { useCallback, useEffect, useRef, useState } from 'react';
import { api, toRows, type Incident, type Module, type Row, type Simulation } from './api';

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
  // Bumped every time another simulation is opened, so late responses for the previous one are dropped.
  const generation = useRef(0);

  const fail = useCallback((e: unknown) => setError(e instanceof Error ? e.message : String(e)), []);

  const publish = useCallback((next: SimulationState) => {
    stateRef.current = next;
    setState(next);
  }, []);

  /**
   * Loads the state of the current simulation, asking only for the readings not loaded yet.
   * Calls made while one is running are merged into one more.
   */
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
        if (!current.sim) return;
        const gen = generation.current;
        const from = current.rows.length ? current.rows[current.rows.length - 1].tick + 1 : 0;
        const next = await api.state(current.sim.id, from);
        if (gen !== generation.current) return;
        const latest = stateRef.current;
        // A response can be older than an event already applied; never go back in time.
        if (latest.sim && next.simulation.currentTick < latest.sim.currentTick) continue;
        const fresh = toRows(next.readings);
        // If the server skipped ticks (more than one window behind), start the history again.
        const keep = fresh.length && fresh[0].tick !== from ? [] : latest.rows;
        const rows = [...keep.filter(r => r.tick < (fresh[0]?.tick ?? Infinity)), ...fresh].slice(-MAX_ROWS);
        publish({ sim: next.simulation, rows, modules: next.modules, incidents: next.incidents });
      } while (pending.current);
    } catch (e) {
      fail(e);
    } finally {
      refreshing.current = false;
    }
  }, [fail, publish]);

  const accept = useCallback((sim: Simulation) => {
    const current = stateRef.current.sim;
    if (!current || current.id !== sim.id) return;
    // Events and responses can arrive out of order; never go back in time.
    if (sim.currentTick < current.currentTick) return;
    publish({ ...stateRef.current, sim });
    void refresh();
  }, [publish, refresh]);

  const simId = state.sim?.id;

  useEffect(() => {
    if (!simId) return;
    const source = new EventSource(`/api/simulations/${simId}/events`);
    source.onopen = () => setLive(true);
    source.onerror = () => setLive(false);
    source.addEventListener('state', e => accept(JSON.parse((e as MessageEvent).data)));
    return () => {
      source.close();
      setLive(false);
    };
  }, [simId, accept]);

  /** Shows an existing simulation, for example one opened from its URL. */
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
        fail(e);
      }
    }
  }, [publish, fail]);

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

  /** Runs one request at a time against the current simulation. */
  const command = useCallback(async (call: (id: string) => Promise<Simulation>) => {
    const current = stateRef.current.sim;
    if (busy.current || !current) return;
    busy.current = true;
    setError(null);
    try {
      accept(await call(current.id));
    } catch (e) {
      fail(e);
    } finally {
      busy.current = false;
    }
  }, [accept, fail]);

  const advance = useCallback((ticks: number) => command(id => api.advance(id, ticks)), [command]);
  const run = useCallback((speed: number) => command(id => api.run(id, speed)), [command]);
  const pause = useCallback(() => command(id => api.pause(id)), [command]);

  return { ...state, error, live, open, create, advance, run, pause };
}
