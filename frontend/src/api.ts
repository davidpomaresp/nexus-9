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
  running: boolean;
  speed: number;
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

export interface StationState {
  simulation: Simulation;
  modules: Module[];
  incidents: Incident[];
  readings: Reading<PowerValues | OxygenValues | CommsValues>[];
}

export const api = {
  scenarios: () => call<string[]>('GET', '/scenarios'),
  create: (seed: number, scenario: string) => call<Simulation>('POST', '/simulations', { seed, scenario }),
  advance: (id: string, ticks: number) => call<Simulation>('POST', `/simulations/${id}/advance?ticks=${ticks}`),
  run: (id: string, speed: number) => call<Simulation>('POST', `/simulations/${id}/run?speed=${speed}`),
  pause: (id: string) => call<Simulation>('POST', `/simulations/${id}/pause`),
  state: (id: string, fromTick: number) => call<StationState>('GET', `/simulations/${id}/state?fromTick=${fromTick}`),
};

/** Joins the readings of the three modules into one row per tick; ticks missing a module are skipped. */
export function toRows(readings: StationState['readings']): Row[] {
  const byTick = new Map<number, Partial<PowerValues & OxygenValues & CommsValues>>();
  for (const r of readings) byTick.set(r.tick, { ...byTick.get(r.tick), ...r.values });
  const rows: Row[] = [];
  for (const [tick, v] of byTick) {
    if (v.generationKw === undefined || v.levelPercent === undefined || v.mode === undefined) continue;
    rows.push({
      tick,
      sunlit: v.sunlit!,
      generation: v.generationKw,
      demand: v.demandKw!,
      served: v.servedKw!,
      battery: v.batteryPercent!,
      oxygen: v.levelPercent,
      comms: v.mode,
    });
  }
  return rows.sort((a, b) => a.tick - b.tick);
}

/** Path of a simulation in the panel, and the reverse. */
export const simulationPath = (id: string) => `/simulations/${id}`;
export function simulationIdFromPath(path: string): string | null {
  return /^\/simulations\/([0-9a-f-]{36})\/?$/i.exec(path)?.[1] ?? null;
}
