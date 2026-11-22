package com.nexus9.backend.simulation;

import java.util.UUID;

/** {@code featured} marks the shared example of the public demo, which nobody can change. */
public record Simulation(UUID id, long seed, String scenario, long currentTick, int modelVersion, boolean running,
        int speed, boolean featured) {

    Simulation withCurrentTick(long tick) {
        return new Simulation(id, seed, scenario, tick, modelVersion, running, speed, featured);
    }

    Simulation withClock(boolean running, int speed) {
        return new Simulation(id, seed, scenario, currentTick, modelVersion, running, speed, featured);
    }
}
