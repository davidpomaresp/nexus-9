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
        return snapshot.execute(status -> modulesAt(simulations.get(id)));
    }

    /**
     * Summary, modules, incidents and the readings from {@code fromTick} on, all at the same tick.
     * At most {@code maxTicks} ticks of readings are returned, the most recent ones.
     */
    StateResponse state(UUID id, long fromTick, int maxTicks) {
        return snapshot.execute(status -> {
            Simulation sim = simulations.get(id);
            long last = sim.currentTick() - 1;
            long from = Math.max(fromTick, last - maxTicks + 1);
            List<TelemetryRecord> readings = from > last ? List.of() : telemetry.findAll(id, from, last);
            List<Incident> all = incidents.find(id, Optional.empty());
            int open = (int) all.stream().filter(i -> i.status() == IncidentStatus.OPEN).count();
            return new StateResponse(SimulationResponse.of(sim, open), modulesAt(sim), all, readings);
        });
    }

    private List<ModuleResponse> modulesAt(Simulation sim) {
        Map<StationModule, TelemetryRecord> records = telemetry.atTick(sim.id(), sim.currentTick() - 1);
        List<Incident> open = incidents.find(sim.id(), Optional.of(IncidentStatus.OPEN));
        return Arrays.stream(StationModule.values())
                .map(module -> ModuleStatusResolver.resolve(module, records.get(module), open))
                .toList();
    }
}
