import { describe, expect, it } from 'vitest';
import type { Row } from './api';
import { eclipses, groupByDistance, missionTime, orbitPhase } from './station';

const row = (tick: number, sunlit: boolean): Row =>
  ({ tick, sunlit, generation: 0, demand: 0, served: 0, battery: 0, oxygen: 0, comms: 'FULL' });

describe('missionTime', () => {
  it('formats ticks as hours and minutes', () => {
    expect(missionTime(0)).toBe('T+00:00');
    expect(missionTime(59)).toBe('T+00:59');
    expect(missionTime(600)).toBe('T+10:00');
    expect(missionTime(6005)).toBe('T+100:05');
  });
});

describe('orbitPhase', () => {
  it('counts down to sunset while in sunlight', () => {
    expect(orbitPhase(0)).toMatchObject({ position: 0, sunlit: true, remaining: 57 });
    expect(orbitPhase(56)).toMatchObject({ sunlit: true, remaining: 1 });
  });

  it('counts down to sunrise in eclipse and wraps each orbit', () => {
    expect(orbitPhase(57)).toMatchObject({ sunlit: false, remaining: 35 });
    expect(orbitPhase(92)).toMatchObject({ position: 0, sunlit: true });
  });
});

describe('eclipses', () => {
  it('merges consecutive eclipse ticks', () => {
    const rows = [row(0, true), row(1, false), row(2, false), row(3, true), row(4, false)];
    expect(eclipses(rows)).toEqual([[1, 2], [4, 4]]);
  });

  it('splits a range when a tick is missing', () => {
    expect(eclipses([row(1, false), row(3, false)])).toEqual([[1, 1], [3, 3]]);
  });

  it('is empty in full sunlight', () => {
    expect(eclipses([row(0, true), row(1, true)])).toEqual([]);
  });
});

describe('groupByDistance', () => {
  it('groups items closer than the gap to the first of the group', () => {
    const groups = groupByDistance([0, 5, 9, 12, 30], x => x, 10);
    expect(groups).toEqual([[0, 5, 9], [12], [30]]);
  });

  it('returns no groups for no items', () => {
    expect(groupByDistance([], x => x, 10)).toEqual([]);
  });
});
