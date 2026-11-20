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
