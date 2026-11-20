package com.nexus9.backend.simulation;

import java.util.UUID;

public class SimulationNotFoundException extends RuntimeException {

    public SimulationNotFoundException(UUID id) {
        super("Simulation not found: " + id);
    }
}
