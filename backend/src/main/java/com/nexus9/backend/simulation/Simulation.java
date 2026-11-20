package com.nexus9.backend.simulation;

import java.util.UUID;

public record Simulation(UUID id, long seed, String scenario, long currentTick, int modelVersion, boolean running,
        int speed) {

    Simulation withCurrentTick(long tick) {
        return new Simulation(id, seed, scenario, tick, modelVersion, running, speed);
    }
}
