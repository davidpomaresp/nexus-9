import { describe, expect, it } from 'vitest';
import { simulationIdFromPath, simulationPath, toRows, type StationState } from './api';

const power = (tick: number) => ({
  module: 'POWER' as const, tick,
  values: { generationKw: 10, demandKw: 8, servedKw: 8, batteryPercent: 90, sunlit: true },
});
const oxygen = (tick: number) => ({ module: 'OXYGEN' as const, tick, values: { levelPercent: 100 } });
const comms = (tick: number) => ({ module: 'COMMS' as const, tick, values: { mode: 'FULL' } });

describe('toRows', () => {
  it('joins the three modules of each tick, sorted by tick', () => {
    const readings = [comms(1), power(1), oxygen(1), power(0), oxygen(0), comms(0)] as StationState['readings'];
    const rows = toRows(readings);
    expect(rows.map(r => r.tick)).toEqual([0, 1]);
    expect(rows[0]).toEqual({
      tick: 0, sunlit: true, generation: 10, demand: 8, served: 8, battery: 90, oxygen: 100, comms: 'FULL',
    });
  });

  it('skips ticks missing a module', () => {
    const readings = [power(0), oxygen(0), comms(0), power(1), oxygen(1)] as StationState['readings'];
    expect(toRows(readings).map(r => r.tick)).toEqual([0]);
  });
});

describe('simulation paths', () => {
  const id = '3f2b8c1e-9a4d-4e6f-8b1a-2c3d4e5f6a7b';

  it('reads the id back from the path', () => {
    expect(simulationIdFromPath(simulationPath(id))).toBe(id);
    expect(simulationIdFromPath(`/simulations/${id}/`)).toBe(id);
  });

  it('ignores other paths', () => {
    expect(simulationIdFromPath('/')).toBeNull();
    expect(simulationIdFromPath('/simulations/not-an-id')).toBeNull();
    expect(simulationIdFromPath(`/simulations/${id}/extra`)).toBeNull();
  });
});
