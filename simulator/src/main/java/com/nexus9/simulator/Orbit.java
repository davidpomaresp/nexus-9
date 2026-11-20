package com.nexus9.simulator;

/**
 * Each orbit starts with {@code sunlitTicks} of daylight followed by eclipse.
 */
public record Orbit(int periodTicks, int sunlitTicks) {

    public static final Orbit LOW_EARTH_ORBIT = new Orbit(92, 57);

    public Orbit {
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
        if (sunlitTicks < 0 || sunlitTicks > periodTicks) {
            throw new IllegalArgumentException("sunlitTicks must be between 0 and periodTicks");
        }
    }

    public boolean isSunlit(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        return tick % periodTicks < sunlitTicks;
    }
}
