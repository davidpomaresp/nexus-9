package com.nexus9.backend.api;

import com.nexus9.backend.incident.Incident;
import com.nexus9.backend.telemetry.TelemetryRecord;
import java.util.List;

/** The modules are at tick {@code simulation.currentTick - 1}, the last one with readings. */
public record StateResponse(SimulationResponse simulation, List<ModuleResponse> modules, List<Incident> incidents,
        List<TelemetryRecord> readings) {
}
