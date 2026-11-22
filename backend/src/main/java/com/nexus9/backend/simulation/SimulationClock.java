package com.nexus9.backend.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Advances running simulations on the server, so every viewer sees the same live simulation. The
 * running flag is stored in the database, so running simulations resume after a restart.
 */
@Component
public class SimulationClock {

    private static final Logger log = LoggerFactory.getLogger(SimulationClock.class);

    private final SimulationRepository simulations;
    private final SimulationService service;

    public SimulationClock(SimulationRepository simulations, SimulationService service) {
        this.simulations = simulations;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${nexus.clock.interval:250ms}")
    public void tick() {
        for (Simulation sim : simulations.findRunning()) {
            step(sim);
        }
    }

    void step(Simulation sim) {
        try {
            service.clockStep(sim.id());
        } catch (RuntimeException e) {
            log.warn("Stopping simulation {}: {}", sim.id(), e.getMessage());
            try {
                service.pause(sim.id());
            } catch (SimulationNotFoundException deleted) {
                // Deleted in the meantime; nothing left to stop.
            }
        }
    }
}
