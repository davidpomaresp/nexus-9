package com.nexus9.backend.incident;

import com.nexus9.simulator.StationSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private final IncidentRepository incidents;
    private final List<Rule> rules = Rules.ALL;
    private final DependencyGraph graph = DependencyGraph.STATION;

    public IncidentService(IncidentRepository incidents) {
        this.incidents = incidents;
    }

    /** Must run in the same transaction that stores the snapshot. */
    public void evaluate(UUID simulationId, StationSnapshot snapshot) {
        for (Rule rule : rules) {
            Optional<Incident> open = incidents.findOpen(simulationId, rule.module(), rule.code());
            if (open.isEmpty() && rule.openWhen().test(snapshot)) {
                incidents.insertOpen(new Incident(UUID.randomUUID(), simulationId, rule.module(), rule.code(),
                        rule.priority(), IncidentStatus.OPEN, snapshot.tick(), null,
                        graph.affectedBy(rule.module()), null));
            } else if (open.isPresent() && rule.resolveWhen().test(snapshot)) {
                incidents.resolve(open.get().id(), snapshot.tick());
            }
        }
    }

    /**
     * Marks an open incident as acknowledged at the simulation's last completed tick. Acknowledging
     * it again keeps the first tick, so retries are safe.
     */
    public Incident acknowledge(UUID simulationId, UUID incidentId) {
        incidents.acknowledge(simulationId, incidentId);
        Incident incident = incidents.find(simulationId, incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
        if (incident.acknowledgedAtTick() == null) {
            throw new IncidentResolvedException(incidentId);
        }
        return incident;
    }

    public int countOpen(UUID simulationId) {
        return incidents.countOpen(simulationId);
    }

    public List<Incident> find(UUID simulationId, Optional<IncidentStatus> status) {
        return incidents.findBySimulation(simulationId, status);
    }
}
