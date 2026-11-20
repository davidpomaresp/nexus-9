import { useCallback, useEffect, useRef, useState } from 'react';
import { api, history, type Incident, type Module, type Row, type Simulation } from './api';

export interface SimulationState {
  sim: Simulation | null;
  rows: Row[];
  modules: Module[];
  incidents: Incident[];
}

const EMPTY: SimulationState = { sim: null, rows: [], modules: [], incidents: [] };

/**
 * The server owns the clock: this hook only starts and pauses it, and listens to the simulation's
 * event stream to know when to reload. Several tabs on the same simulation stay in sync.
 */
export function useSimulation() {
  const [state, setState] = useState<SimulationState>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState(false);

  const simRef = useRef<Simulation | null>(null);
  const busy = useRef(false);
  const refreshing = useRef(false);
  const pending = useRef(false);
  // Bumped on every new simulation, so late responses for the previous one are dropped.
  const generation = useRef(0);

  const fail = useCallback((e: unknown) => setError(e instanceof Error ? e.message : String(e)), []);

  /** Reloads history, modules and incidents; calls made while one is running are merged into one more. */
  const refresh = useCallback(async () => {
    if (refreshing.current) {
      pending.current = true;
      return;
    }
    refreshing.current = true;
    try {
      do {
        pending.current = false;
        const sim = simRef.current;
        if (!sim) return;
        const gen = generation.current;
        const [rows, modules, incidents] = await Promise.all([
          history(sim), api.modules(sim.id), api.incidents(sim.id),
        ]);
        if (gen !== generation.current) return;
        setState({ sim, rows, modules, incidents });
      } while (pending.current);
    } catch (e) {
      fail(e);
    } finally {
      refreshing.current = false;
    }
  }, [fail]);

  const accept = useCallback((sim: Simulation) => {
    const current = simRef.current;
    if (!current || current.id !== sim.id) return;
    // Events and responses can arrive out of order; never go back in time.
    if (sim.currentTick < current.currentTick) return;
    simRef.current = sim;
    void refresh();
  }, [refresh]);

  const sim = state.sim;
  const simId = sim?.id;

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

  const create = useCallback(async (seed: number, scenario: string) => {
    setError(null);
    const gen = ++generation.current;
    try {
      if (simRef.current?.running) await api.pause(simRef.current.id);
      const created = await api.create(seed, scenario);
      if (gen !== generation.current) return;
      simRef.current = created;
      setState({ ...EMPTY, sim: created });
      await refresh();
    } catch (e) {
      if (gen === generation.current) fail(e);
    }
  }, [refresh, fail]);

  /** Runs one request at a time against the current simulation. */
  const command = useCallback(async (call: (id: string) => Promise<Simulation>) => {
    const current = simRef.current;
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

  return { ...state, error, live, create, advance, run, pause };
}
