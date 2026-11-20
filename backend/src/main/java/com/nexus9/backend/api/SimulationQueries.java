package com.nexus9.backend.api;

import com.nexus9.backend.domain.StationModule;
import com.nexus9.backend.incident.Incident;
import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.incident.IncidentStatus;
import com.nexus9.backend.simulation.Simulation;
import com.nexus9.backend.simulation.SimulationService;
import com.nexus9.backend.telemetry.TelemetryRecord;
import com.nexus9.backend.telemetry.TelemetryRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads that combine several queries. Each one runs in a single read-only REPEATABLE READ
 * transaction, so all its queries see the same snapshot even if a tick commits in between.
 */
@Service
class SimulationQueries {

    private final SimulationService simulations;
    private final IncidentService incidents;
    private final TelemetryRepository telemetry;
    private final TransactionTemplate snapshot;

    SimulationQueries(SimulationService simulations, IncidentService incidents, TelemetryRepository telemetry,
            PlatformTransactionManager transactionManager) {
        this.simulations = simulations;
        this.incidents = incidents;
        this.telemetry = telemetry;
        this.snapshot = new TransactionTemplate(transactionManager);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.snapshot.setReadOnly(true);
    }

    SimulationResponse summary(UUID id) {
        return snapshot.execute(status -> {
            Simulation sim = simulations.get(id);
            return SimulationResponse.of(sim, incidents.countOpen(id));
        });
    }

    /** Every module at the last completed tick. */
    List<ModuleResponse> modules(UUID id) {
        return snapshot.execute(status -> {
            Simulation sim = simulations.get(id);
            Map<StationModule, TelemetryRecord> records = telemetry.atTick(id, sim.currentTick() - 1);
            List<Incident> open = incidents.find(id, Optional.of(IncidentStatus.OPEN));
            return Arrays.stream(StationModule.values())
                    .map(module -> ModuleStatusResolver.resolve(module, records.get(module), open))
                    .toList();
        });
    }
}
