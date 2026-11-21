package com.nexus9.backend.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final long maxTick;

    /** {@code maxTick} stops simulations at that tick, to bound storage on a public demo; 0 means no limit. */
    public SimulationClock(SimulationRepository simulations, SimulationService service,
            @Value("${nexus.clock.max-tick:0}") long maxTick) {
        this.simulations = simulations;
        this.service = service;
        this.maxTick = maxTick;
    }

    @Scheduled(fixedDelayString = "${nexus.clock.interval:250ms}")
    public void tick() {
        for (Simulation sim : simulations.findRunning()) {
            try {
                long ticks = maxTick > 0 ? Math.min(sim.speed(), maxTick - sim.currentTick()) : sim.speed();
                if (ticks <= 0) {
                    service.pause(sim.id());
                    continue;
                }
                service.advance(sim.id(), (int) ticks);
            } catch (RuntimeException e) {
                log.warn("Stopping simulation {}: {}", sim.id(), e.getMessage());
                service.pause(sim.id());
            }
        }
    }
}
