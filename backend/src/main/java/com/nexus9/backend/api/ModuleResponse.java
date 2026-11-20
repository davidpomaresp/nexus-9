package com.nexus9.backend.api;

import com.nexus9.backend.domain.StationModule;
import com.nexus9.backend.incident.Priority;
import java.util.Map;

/** {@code highestPriority} is the highest priority among the module's open incidents, or null. */
public record ModuleResponse(StationModule module, ModuleStatus status, Priority highestPriority, Long tick,
        Map<String, Object> values) {
}
