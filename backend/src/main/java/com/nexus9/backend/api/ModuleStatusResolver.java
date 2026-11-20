package com.nexus9.backend.api;

import com.nexus9.backend.incident.Incident;
import com.nexus9.backend.incident.Priority;
import com.nexus9.backend.simulation.StationModule;
import java.util.List;
import java.util.Map;

final class ModuleStatusResolver {

    private ModuleStatusResolver() {
    }

    static ModuleStatus resolve(StationModule module, Map<String, Object> values, List<Incident> openIncidents) {
        if (isOffline(module, values)) {
            return ModuleStatus.OFFLINE;
        }
        List<Incident> own = openIncidents.stream().filter(i -> i.module() == module).toList();
        if (own.stream().anyMatch(i -> i.priority() == Priority.CRITICAL)) {
            return ModuleStatus.CRITICAL;
        }
        return own.isEmpty() ? ModuleStatus.NOMINAL : ModuleStatus.DEGRADED;
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
