package com.nexus9.backend.api;

import com.nexus9.backend.domain.StationModule;
import com.nexus9.backend.incident.Incident;
import com.nexus9.backend.incident.Priority;
import com.nexus9.backend.telemetry.TelemetryRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Precedence: OFFLINE, then CRITICAL, then DEGRADED, then NOMINAL. */
final class ModuleStatusResolver {

    private ModuleStatusResolver() {
    }

    static ModuleResponse resolve(StationModule module, TelemetryRecord reading, List<Incident> openIncidents) {
        Map<String, Object> values = reading == null ? Map.of() : reading.values();
        Long tick = reading == null ? null : reading.tick();
        Priority highest = openIncidents.stream()
                .filter(i -> i.module() == module)
                .map(Incident::priority)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ModuleResponse(module, status(module, values, highest), highest, tick, values);
    }

    static ModuleStatus status(StationModule module, Map<String, Object> values, Priority highestOpen) {
        if (isOffline(module, values)) {
            return ModuleStatus.OFFLINE;
        }
        if (highestOpen == null) {
            return ModuleStatus.NOMINAL;
        }
        return highestOpen == Priority.CRITICAL ? ModuleStatus.CRITICAL : ModuleStatus.DEGRADED;
    }

    private static boolean isOffline(StationModule module, Map<String, Object> values) {
        if (values.isEmpty()) {
            return false;
        }
        return switch (module) {
            case POWER -> ((Number) values.get("servedKw")).doubleValue() == 0;
            case OXYGEN -> !((Boolean) values.get("generatorPowered"));
            case COMMS -> "OFF".equals(values.get("mode"));
        };
    }
}
