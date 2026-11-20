package com.nexus9.simulator;

public final class SimulationTime {

    public static final int SECONDS_PER_TICK = 60;
    public static final double HOURS_PER_TICK = SECONDS_PER_TICK / 3600.0;

    /** Tolerance for power comparisons, so that values like 2.4 - 2.0 = 0.39999999999999997 still fit 0.4. */
    public static final double EPSILON_KW = 1e-9;

    private SimulationTime() {
    }
}
