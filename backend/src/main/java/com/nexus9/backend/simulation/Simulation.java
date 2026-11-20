package com.nexus9.backend.simulation;

import java.util.UUID;

public record Simulation(UUID id, long seed, String scenario, long currentTick) {
}
