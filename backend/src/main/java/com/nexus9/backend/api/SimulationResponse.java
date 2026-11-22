package com.nexus9.backend.api;

import com.nexus9.backend.simulation.Simulation;
import java.util.UUID;

public record SimulationResponse(UUID id, long seed, String scenario, long currentTick, int openIncidents,
        boolean running, int speed, boolean featured) {

    static SimulationResponse of(Simulation s, int openIncidents) {
        return new SimulationResponse(s.id(), s.seed(), s.scenario(), s.currentTick(), openIncidents, s.running(),
                s.speed(), s.featured());
    }
}
