package com.nexus9.backend.simulation;

import com.nexus9.simulator.Station;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The simulation the public demo opens with, so a first visit already shows a failure chain instead
 * of an empty panel. It is created on first use and is read-only: everyone sees the same failure chain,
 * and trying things out means creating your own simulation.
 */
@Component
@ConditionalOnProperty("nexus.demo.scenario")
public class DemoSimulation {

    private final SimulationRepository simulations;
    private final SimulationService service;
    private final String scenario;
    private final long seed;
    private final long startTick;

    public DemoSimulation(SimulationRepository simulations, SimulationService service,
            @Value("${nexus.demo.scenario}") String scenario, @Value("${nexus.demo.seed:42}") long seed,
            @Value("${nexus.demo.tick:600}") long startTick) {
        this.simulations = simulations;
        this.service = service;
        this.scenario = scenario;
        this.seed = seed;
        this.startTick = startTick;
    }

    /** Returns the featured simulation, advanced at least to the starting tick. */
    public UUID id() {
        UUID id = simulations.findFeaturedId().orElseGet(this::create);
        // Idempotent: does nothing once the simulation is past the starting tick.
        service.prepareExample(id, startTick);
        return id;
    }

    private UUID create() {
        simulations.insertFeaturedIfMissing(new Simulation(UUID.randomUUID(), seed, scenario, 0,
                Station.MODEL_VERSION, false, SimulationService.DEFAULT_SPEED, true));
        // Another request may have won the insert; either way there is exactly one now.
        return simulations.findFeaturedId().orElseThrow();
    }
}
