export type ModuleName = 'POWER' | 'OXYGEN' | 'COMMS';
export type ModuleStatus = 'NOMINAL' | 'DEGRADED' | 'CRITICAL' | 'OFFLINE';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type CommsMode = 'FULL' | 'MINIMAL' | 'OFF';

export interface Simulation {
  id: string;
  seed: number;
  scenario: string;
  currentTick: number;
  openIncidents: number;
}

export interface PowerValues {
  sunlit: boolean;
  generationKw: number;
  demandKw: number;
  servedKw: number;
  unservedKw: number;
  batteryKwh: number;
  batteryPercent: number;
}

export interface OxygenValues {
  generatorPowered: boolean;
  levelPercent: number;
}

export interface CommsValues {
  mode: CommsMode;
}

export interface Module {
  module: ModuleName;
  status: ModuleStatus;
  highestPriority: Priority | null;
  tick: number | null;
  values: Partial<PowerValues & OxygenValues & CommsValues>;
}

export interface Reading<T> {
  tick: number;
  module: ModuleName;
  values: T;
}

export interface Incident {
  id: string;
  module: ModuleName;
  ruleCode: string;
  priority: Priority;
  status: 'OPEN' | 'RESOLVED';
  openedAtTick: number;
  resolvedAtTick: number | null;
  affectedModules: ModuleName[];
}

/** One row per tick with the three modules joined. */
export interface Row {
  tick: number;
  sunlit: boolean;
  generation: number;
  demand: number;
  served: number;
  battery: number;
  oxygen: number;
  comms: CommsMode;
}

export class ApiError extends Error {}

async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch('/api' + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json();
  if (!res.ok) throw new ApiError(data.detail ?? res.statusText);
  return data as T;
}

export const api = {
  scenarios: () => call<string[]>('GET', '/scenarios'),
  create: (seed: number, scenario: string) => call<Simulation>('POST', '/simulations', { seed, scenario }),
  advance: (id: string, ticks: number) => call<Simulation>('POST', `/simulations/${id}/advance?ticks=${ticks}`),
  modules: (id: string) => call<Module[]>('GET', `/simulations/${id}/modules`),
  incidents: (id: string) => call<Incident[]>('GET', `/simulations/${id}/incidents`),
  readings: <T>(id: string, module: ModuleName, toTick: number) =>
    call<Reading<T>[]>('GET', `/simulations/${id}/modules/${module}/readings?toTick=${toTick}`),
};

/** Last 5000 ticks up to the simulation's current tick, joined by tick. */
export async function history(sim: Simulation): Promise<Row[]> {
  if (sim.currentTick === 0) return [];
  const to = sim.currentTick - 1;
  const [power, oxygen, comms] = await Promise.all([
    api.readings<PowerValues>(sim.id, 'POWER', to),
    api.readings<OxygenValues>(sim.id, 'OXYGEN', to),
    api.readings<CommsValues>(sim.id, 'COMMS', to),
  ]);
  const o2 = new Map(oxygen.map(r => [r.tick, r.values]));
  const co = new Map(comms.map(r => [r.tick, r.values]));
  return power
    .filter(p => o2.has(p.tick) && co.has(p.tick))
    .map(p => ({
      tick: p.tick,
      sunlit: p.values.sunlit,
      generation: p.values.generationKw,
      demand: p.values.demandKw,
      served: p.values.servedKw,
      battery: p.values.batteryPercent,
      oxygen: o2.get(p.tick)!.levelPercent,
      comms: co.get(p.tick)!.mode,
    }));
}
