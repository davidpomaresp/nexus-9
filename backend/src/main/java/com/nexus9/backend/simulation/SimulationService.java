package com.nexus9.backend.simulation;

import com.nexus9.backend.incident.IncidentService;
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
    private final IncidentService incidents;
    private final StationRegistry stations;
    private final TransactionTemplate tx;

    public SimulationService(SimulationRepository simulations, TelemetryRepository telemetry,
            IncidentService incidents, StationRegistry stations, TransactionTemplate tx) {
        this.simulations = simulations;
        this.telemetry = telemetry;
        this.incidents = incidents;
        this.stations = stations;
        this.tx = tx;
    }

    /** State after an advance, read in the same transaction that committed its last tick. */
    public record Progress(Simulation simulation, int openIncidents) {
    }

    public Simulation create(long seed, String scenarioName) {
        Scenario.byName(scenarioName);
        Simulation simulation = new Simulation(UUID.randomUUID(), seed, scenarioName, 0, Station.MODEL_VERSION);
        simulations.insert(simulation);
        return simulation;
    }

    public Simulation get(UUID id) {
        return simulations.findById(id).orElseThrow(() -> new SimulationNotFoundException(id));
    }

    /**
     * Advances {@code ticks} ticks from wherever the simulation is. Not idempotent: a retried request
     * advances again. Each tick commits on its own, so a failure can leave part of the request applied.
     */
    public Progress advance(UUID id, int ticks) {
        if (ticks < 1 || ticks > MAX_TICKS_PER_REQUEST) {
            throw new IllegalArgumentException("ticks must be between 1 and " + MAX_TICKS_PER_REQUEST);
        }
        Progress progress = null;
        for (int i = 0; i < ticks; i++) {
            progress = tx.execute(status -> step(id, Long.MAX_VALUE));
        }
        return progress;
    }

    /**
     * Advances until {@code currentTick == targetTick}. Idempotent: retrying the same request, or
     * sending it when the target was already reached, does nothing more.
     */
    public Progress advanceTo(UUID id, long targetTick) {
        if (targetTick < 0) {
            throw new IllegalArgumentException("toTick must not be negative");
        }
        if (targetTick - get(id).currentTick() > MAX_TICKS_PER_REQUEST) {
            throw new IllegalArgumentException("at most " + MAX_TICKS_PER_REQUEST + " ticks per request");
        }
        while (true) {
            Progress progress = tx.execute(status -> step(id, targetTick));
            if (progress.simulation().currentTick() >= targetTick) {
                return progress;
            }
        }
    }

    /** Runs one tick inside a transaction, unless the simulation is already at {@code targetTick}. */
    private Progress step(UUID id, long targetTick) {
        Simulation simulation = simulations.findByIdForUpdate(id)
                .orElseThrow(() -> new SimulationNotFoundException(id));
        if (simulation.currentTick() >= targetTick) {
            return new Progress(simulation, incidents.countOpen(id));
        }
        if (simulation.modelVersion() != Station.MODEL_VERSION) {
            throw new ModelVersionMismatchException(id, simulation.modelVersion(), Station.MODEL_VERSION);
        }
        Station station = stations.stationAt(simulation);
        try {
            StationSnapshot snapshot = station.advance();
            telemetry.append(id, snapshot);
            incidents.evaluate(id, snapshot);
            long next = snapshot.tick() + 1;
            simulations.updateCurrentTick(id, next);
            return new Progress(simulation.withCurrentTick(next), incidents.countOpen(id));
        } catch (RuntimeException e) {
            // The in-memory station is now ahead of the database; drop it so it gets rebuilt.
            stations.evict(id);
            throw e;
        }
    }
}
