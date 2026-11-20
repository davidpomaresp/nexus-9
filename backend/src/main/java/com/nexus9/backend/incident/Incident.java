package com.nexus9.backend.incident;

import com.nexus9.backend.simulation.StationModule;
import java.util.List;
import java.util.UUID;

public record Incident(
        UUID id,
        UUID simulationId,
        StationModule module,
        String ruleCode,
        Priority priority,
        IncidentStatus status,
        long openedAtTick,
        Long resolvedAtTick,
        List<StationModule> affectedModules) {
}
