import type { Row } from './api';

// Mirrors the simulator's constants, only for display.
export const ORBIT_TICKS = 92;
export const SUNLIT_TICKS = 57;
export const BATTERY_THRESHOLDS = { commsMinimal: 30, low: 20, commsOff: 15, critical: 5 };
export const OXYGEN_LOW = 95;

export function missionTime(tick: number): string {
  const h = Math.floor(tick / 60);
  const m = tick % 60;
  return `T+${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export function orbitPhase(tick: number) {
  const position = tick % ORBIT_TICKS;
  const sunlit = position < SUNLIT_TICKS;
  const remaining = sunlit ? SUNLIT_TICKS - position : ORBIT_TICKS - position;
  return { position, sunlit, remaining, fraction: position / ORBIT_TICKS };
}

/** Consecutive eclipse ticks merged into [first, last] ranges. */
export function eclipses(rows: Row[]): [number, number][] {
  const ranges: [number, number][] = [];
  for (const r of rows) {
    if (r.sunlit) continue;
    const last = ranges[ranges.length - 1];
    if (last && last[1] === r.tick - 1) last[1] = r.tick;
    else ranges.push([r.tick, r.tick]);
  }
  return ranges;
}

/**
 * Groups items whose position is closer than minGap to the first item of the current group.
 * Items must come sorted by position.
 */
export function groupByDistance<T>(items: T[], position: (item: T) => number, minGap: number): T[][] {
  const groups: T[][] = [];
  for (const item of items) {
    const last = groups[groups.length - 1];
    if (last && position(item) - position(last[0]) < minGap) last.push(item);
    else groups.push([item]);
  }
  return groups;
}
