import { useCallback, useEffect, useRef, useState } from 'react';
import { api, history, type Incident, type Module, type Row, type Simulation } from './api';

export interface SimulationState {
  sim: Simulation | null;
  rows: Row[];
  modules: Module[];
  incidents: Incident[];
}

const EMPTY: SimulationState = { sim: null, rows: [], modules: [], incidents: [] };
const PLAY_INTERVAL_MS = 250;

export function useSimulation() {
  const [state, setState] = useState<SimulationState>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [speed, setSpeed] = useState(5);

  const simRef = useRef<Simulation | null>(null);
  const busy = useRef(false);
  // Bumped on every new simulation and every refresh, so late responses for an older state are dropped.
  const generation = useRef(0);
  const refreshSeq = useRef(0);

  const fail = useCallback((e: unknown) => {
    setError(e instanceof Error ? e.message : String(e));
    setRunning(false);
  }, []);

  const refresh = useCallback(async (sim: Simulation) => {
    const gen = generation.current;
    const seq = ++refreshSeq.current;
    const [rows, modules, incidents] = await Promise.all([
      history(sim), api.modules(sim.id), api.incidents(sim.id),
    ]);
    if (gen !== generation.current || seq !== refreshSeq.current) return;
    setState({ sim, rows, modules, incidents });
  }, []);

  const create = useCallback(async (seed: number, scenario: string) => {
    setRunning(false);
    setError(null);
    const gen = ++generation.current;
    try {
      const sim = await api.create(seed, scenario);
      if (gen !== generation.current) return;
      simRef.current = sim;
      await refresh(sim);
    } catch (e) {
      if (gen === generation.current) fail(e);
    }
  }, [refresh, fail]);

  /** Only one advance at a time, whoever started it. */
  const advance = useCallback(async (ticks: number) => {
    const current = simRef.current;
    if (busy.current || !current) return;
    busy.current = true;
    const gen = generation.current;
    try {
      const sim = await api.advance(current.id, ticks);
      if (gen !== generation.current) return;
      simRef.current = sim;
      await refresh(sim);
    } catch (e) {
      if (gen === generation.current) fail(e);
    } finally {
      busy.current = false;
    }
  }, [refresh, fail]);

  useEffect(() => {
    if (!running) return;
    const timer = setInterval(() => void advance(speed), PLAY_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [running, speed, advance]);

  return { ...state, error, running, speed, setRunning, setSpeed, create, advance };
}
