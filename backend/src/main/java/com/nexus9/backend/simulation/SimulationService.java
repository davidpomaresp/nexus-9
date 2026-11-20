package com.nexus9.backend.simulation;

import com.nexus9.backend.telemetry.TelemetryRepository;
import com.nexus9.simulator.Station;
import com.nexus9.simulator.StationSnapshot;
import com.nexus9.simulator.scenario.Scenario;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SimulationService {

    public static final int MAX_TICKS_PER_REQUEST = 10_000;

    private final SimulationRepository simulations;
    private final TelemetryRepository telemetry;
    private final StationRegistry stations;
    private final TransactionTemplate tx;

    public SimulationService(SimulationRepository simulations, TelemetryRepository telemetry,
            StationRegistry stations, TransactionTemplate tx) {
        this.simulations = simulations;
        this.telemetry = telemetry;
        this.stations = stations;
        this.tx = tx;
    }

    public Simulation create(long seed, String scenarioName) {
        Scenario.byName(scenarioName);
        Simulation simulation = new Simulation(UUID.randomUUID(), seed, scenarioName, 0);
        simulations.insert(simulation);
        return simulation;
    }

    public Simulation get(UUID id) {
        return simulations.findById(id).orElseThrow(() -> new SimulationNotFoundException(id));
    }

    public Simulation advance(UUID id, int ticks) {
        if (ticks < 1 || ticks > MAX_TICKS_PER_REQUEST) {
            throw new IllegalArgumentException("ticks must be between 1 and " + MAX_TICKS_PER_REQUEST);
        }
        Simulation simulation = null;
        for (int i = 0; i < ticks; i++) {
            simulation = tx.execute(status -> advanceOneTick(id));
        }
        return simulation;
    }

    /** Runs inside a transaction: telemetry and the new tick are committed together or not at all. */
    private Simulation advanceOneTick(UUID id) {
        Simulation simulation = simulations.findByIdForUpdate(id)
                .orElseThrow(() -> new SimulationNotFoundException(id));
        Station station = stations.stationAt(simulation);
        try {
            StationSnapshot snapshot = station.advance();
            telemetry.append(id, snapshot);
            long next = snapshot.tick() + 1;
            simulations.updateCurrentTick(id, next);
            return new Simulation(id, simulation.seed(), simulation.scenario(), next);
        } catch (RuntimeException e) {
            // The in-memory station is now ahead of the database; drop it so it gets rebuilt.
            stations.evict(id);
            throw e;
        }
    }
}
