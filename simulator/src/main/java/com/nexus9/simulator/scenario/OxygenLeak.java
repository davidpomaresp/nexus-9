package com.nexus9.simulator.scenario;

/** From {@code tick} on, the station loses {@code percentPerTick} of O2 every tick. Zero means repaired. */
public record OxygenLeak(long tick, double percentPerTick) implements Fault {

    public OxygenLeak {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        if (!(percentPerTick >= 0 && percentPerTick <= 100)) {
            throw new IllegalArgumentException("percentPerTick must be in [0, 100]");
        }
    }
}
