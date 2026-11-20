package com.nexus9.backend.api;

import com.nexus9.backend.simulation.StationModule;
import java.util.Map;

public record ModuleResponse(StationModule module, ModuleStatus status, Long tick, Map<String, Object> values) {
}
